package com.amaya.intelligence.data.repository

import android.content.Context
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.PrimedState
import com.amaya.intelligence.domain.memory.PrimedStateStatus
import com.amaya.intelligence.domain.memory.PrimedTriggerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

interface PrimedStateRepository {
    /**
     * Add a new primed state, or reinforce an existing one with the same deterministic id
     * (bump reinforcement count and refresh it back to PRIMED). Idempotent per workflow.
     */
    suspend fun addOrReinforce(state: PrimedState): Result<Unit>
    suspend fun listAll(): List<PrimedState>
    /** Exact (SHA-256 of the normalized message) trigger match. */
    suspend fun matchingExact(userMessage: String): List<PrimedState>
    /** Fuzzy (embedding similarity) trigger match; empty when embeddings are not configured. */
    suspend fun matchingFuzzy(userMessage: String, limit: Int = 3): List<PrimedState>
    /** The workflow [fingerprint] recurred (successfully or not); reinforce matching states. */
    suspend fun reinforce(fingerprint: String, success: Boolean): Result<Unit>
    /** Batch end-of-session pass: PRIMED→FADING→CLEARED by reinforcement age, then prune. */
    suspend fun runHousekeeping(): Result<Int>
}

/** Deterministic id for a primed state, keyed on its workflow fingerprint/trigger. */
internal fun deterministicPrimedStateId(state: PrimedState): String =
    "primed_" + sha256Hex(state.fingerprint.ifBlank { state.triggerSignature }).take(24)

internal fun sha256Hex(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

/** Normalized trigger key for exact matching (case/punctuation-insensitive). */
internal fun normalizeTrigger(text: String): String = text.lowercase()
    .replace(Regex("[^a-z0-9\\p{L}]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")

@Singleton
class FilePrimedStateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsManager: AiSettingsManager,
    private val embeddingClient: EmbeddingClient
) : PrimedStateRepository {
    private val file = File(context.filesDir, "memory/primed-states.jsonl")
    private val fileLock = Any()

    override suspend fun addOrReinforce(state: PrimedState): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                val id = state.id.ifBlank { deterministicPrimedStateId(state) }
                val now = System.currentTimeMillis()
                val existing = readAll()
                val index = existing.indexOfFirst { it.id == id }
                val updated = if (index >= 0) {
                    val current = existing[index]
                    existing.toMutableList().apply {
                        this[index] = current.copy(
                            primedAction = state.primedAction.ifBlank { current.primedAction },
                            triggerText = state.triggerText.ifBlank { current.triggerText },
                            reinforcementCount = current.reinforcementCount + 1,
                            lastReinforcedAt = now,
                            status = PrimedStateStatus.PRIMED
                        )
                    }
                } else {
                    existing + state.copy(
                        id = id,
                        createdAt = state.createdAt.takeIf { it > 0L } ?: now,
                        lastReinforcedAt = state.lastReinforcedAt.takeIf { it > 0L } ?: now
                    )
                }
                writeAll(updated.takeLast(MAX_STATES))
            }
        }
    }

    override suspend fun listAll(): List<PrimedState> = withContext(Dispatchers.IO) {
        synchronized(fileLock) { readAll() }
    }

    override suspend fun matchingExact(userMessage: String): List<PrimedState> {
        if (userMessage.isBlank()) return emptyList()
        val hash = sha256Hex(normalizeTrigger(userMessage))
        return withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                readAll()
                    .filter { it.status != PrimedStateStatus.CLEARED }
                    .filter { it.triggerType == PrimedTriggerType.EXACT && it.triggerSignature == hash }
            }
        }
    }

    override suspend fun matchingFuzzy(userMessage: String, limit: Int): List<PrimedState> {
        val candidates = withContext(Dispatchers.IO) {
            synchronized(fileLock) { readAll().filter { it.status != PrimedStateStatus.CLEARED } }
        }.take(FUZZY_CANDIDATE_LIMIT)
        if (candidates.isEmpty() || userMessage.isBlank()) return emptyList()
        // Settings and credential reads can throw on broken stores; matching must never crash.
        val settings = runCatching { settingsManager.getSettings() }.getOrNull() ?: return emptyList()
        val config = settings.memoryEmbedding
        if (!config.enabled || config.endpoint.isBlank() || config.model.isBlank()) return emptyList()
        val apiKey = runCatching { settingsManager.getEmbeddingApiKey(config) }.getOrNull()
        if (apiKey.isNullOrBlank()) return emptyList()
        // Embedding is a network call and must run outside the file lock.
        return runCatching {
            val queryVector = embeddingClient.embed(listOf(userMessage.take(MAX_TRIGGER_CHARS)), config, apiKey)
                .getOrThrow().first()
            val texts = candidates.map { it.triggerText.take(MAX_TRIGGER_CHARS) }
            val vectors = embeddingClient.embed(texts, config, apiKey).getOrThrow()
            candidates.mapIndexedNotNull { index, state ->
                val vector = vectors.getOrNull(index) ?: return@mapIndexedNotNull null
                val similarity = cosineSimilarity(queryVector, vector)
                if (similarity >= FUZZY_THRESHOLD) state to similarity else null
            }
                .sortedByDescending { it.second }
                .take(limit.coerceIn(1, MAX_FUZZY_RESULTS))
                .map { it.first }
        }.getOrElse { emptyList() }
    }

    override suspend fun reinforce(fingerprint: String, success: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        if (fingerprint.isBlank()) return@withContext Result.success(Unit)
        synchronized(fileLock) {
            runCatching {
                val now = System.currentTimeMillis()
                val existing = readAll()
                if (existing.none { it.fingerprint == fingerprint }) return@runCatching
                writeAll(existing.map { state ->
                    if (state.fingerprint != fingerprint) state
                    else state.copy(
                        reinforcementCount = state.reinforcementCount + 1,
                        lastReinforcedAt = now,
                        status = PrimedStateStatus.PRIMED
                    )
                })
            }
        }
    }

    override suspend fun runHousekeeping(): Result<Int> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                val now = System.currentTimeMillis()
                var pruned = 0
                val kept = readAll().mapNotNull { state ->
                    val ageMs = (now - state.lastReinforcedAt).coerceAtLeast(0L)
                    when {
                        state.status == PrimedStateStatus.CLEARED && ageMs > PRUNE_AFTER_MS -> { pruned++; null }
                        state.status == PrimedStateStatus.PRIMED && ageMs > CLEARED_AFTER_MS -> state.copy(status = PrimedStateStatus.CLEARED)
                        state.status == PrimedStateStatus.PRIMED && ageMs > FADING_AFTER_MS -> state.copy(status = PrimedStateStatus.FADING)
                        state.status == PrimedStateStatus.FADING && ageMs > CLEARED_AFTER_MS -> state.copy(status = PrimedStateStatus.CLEARED)
                        else -> state
                    }
                }
                writeAll(kept)
                pruned
            }
        }
    }

    private fun readAll(): List<PrimedState> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line).toPrimedState() }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun writeAll(states: List<PrimedState>) {
        file.parentFile?.mkdirs()
        val content = states.joinToString("\n") { it.toJson().toString() } + if (states.isEmpty()) "" else "\n"
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun PrimedState.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("triggerType", triggerType.name)
        .put("triggerSignature", triggerSignature)
        .put("triggerText", triggerText)
        .put("primedAction", primedAction)
        .put("fingerprint", fingerprint)
        .put("relatedSkillId", relatedSkillId)
        .put("reinforcementCount", reinforcementCount)
        .put("lastReinforcedAt", lastReinforcedAt)
        .put("status", status.name)
        .put("createdAt", createdAt)
        .put("workspacePath", workspacePath)
        .put("siteHost", siteHost)

    private fun JSONObject.toPrimedState(): PrimedState = PrimedState(
        id = optString("id"),
        triggerType = runCatching { PrimedTriggerType.valueOf(optString("triggerType")) }
            .getOrDefault(PrimedTriggerType.FUZZY),
        triggerSignature = optString("triggerSignature"),
        triggerText = optString("triggerText"),
        primedAction = optString("primedAction"),
        fingerprint = optString("fingerprint"),
        relatedSkillId = optString("relatedSkillId").takeIf(String::isNotBlank),
        reinforcementCount = optInt("reinforcementCount", 1).coerceAtLeast(1),
        lastReinforcedAt = optLong("lastReinforcedAt", optLong("createdAt", 0L)),
        status = runCatching { PrimedStateStatus.valueOf(optString("status")) }
            .getOrDefault(PrimedStateStatus.PRIMED),
        createdAt = optLong("createdAt", 0L),
        workspacePath = optString("workspacePath").takeIf(String::isNotBlank),
        siteHost = optString("siteHost").takeIf(String::isNotBlank)
    )

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    companion object {
        private const val MAX_STATES = 200
        private const val FUZZY_CANDIDATE_LIMIT = 20
        private const val MAX_FUZZY_RESULTS = 5
        private const val MAX_TRIGGER_CHARS = 320
        /** Minimum cosine similarity for a fuzzy trigger match (context-only priming). */
        private const val FUZZY_THRESHOLD = 0.62
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val FADING_AFTER_MS = 21 * DAY_MS
        private const val CLEARED_AFTER_MS = 42 * DAY_MS
        private const val PRUNE_AFTER_MS = 60 * DAY_MS
    }
}
