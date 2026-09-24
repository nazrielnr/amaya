package com.amaya.intelligence.data.repository

import android.content.Context
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationPriority
import com.amaya.intelligence.domain.memory.RecommendationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Intelligence System: implementation recommendations with a verification lifecycle.
 *
 * A recommendation is a concrete, evidence-grounded next step ("support arm64-v8a", "fix the
 * armv7a build crash") that carries a [Recommendation.verificationRule]. Advancing to VERIFIED
 * requires evidence that satisfies the rule (e.g. a build log), so "the user says it is done"
 * (COMPLETED) and "the system proved it is done" (VERIFIED) stay distinct — mirroring the
 * confidence breaker used for memories.
 */
interface RecommendationRepository {
    /**
     * Create a new SUGGESTED recommendation. Deduplicates against an active recommendation with the
     * same normalized title in the same workspace. Returns the recommendation id.
     */
    suspend fun suggest(
        workspacePath: String,
        title: String,
        rationale: String = "",
        priority: RecommendationPriority = RecommendationPriority.MEDIUM,
        verificationRule: String = "",
        sourceSessionId: String? = null,
        sourceMessageId: String? = null,
        relatedMemoryIds: List<String> = emptyList(),
        relatedSkillIds: List<String> = emptyList()
    ): Result<String>

    suspend fun list(
        workspacePath: String? = null,
        statuses: Set<RecommendationStatus>? = null,
        limit: Int = 50
    ): List<Recommendation>

    suspend fun get(id: String): Recommendation?

    /** Guarded lifecycle transition (suggested → accepted → in_progress → verified → completed; archive). */
    suspend fun transition(id: String, target: RecommendationStatus): Result<Recommendation>

    /**
     * Evidence-gated verification. Fails unless the recommendation is ACCEPTED or IN_PROGRESS and the
     * evidence text satisfies its verification rule. On success the evidence line is appended as
     * provenance and the status becomes VERIFIED.
     */
    suspend fun verify(id: String, evidence: String): Result<Recommendation>

    suspend fun archive(id: String): Result<Recommendation>

    /** Renders active (suggested/accepted/in_progress/verified) recommendations for prompt injection. */
    suspend fun renderForContext(workspacePath: String?): String
}

@Singleton
class FileRecommendationRepository @Inject constructor(
    @ApplicationContext context: Context,
    /** Provenance linking: a VERIFIED recommendation appends its evidence to related memories. */
    private val memoryRepository: MemoryRepository
) : RecommendationRepository {
    private val file = File(context.filesDir, "memory/recommendations.jsonl")
    private val fileLock = Any()

    override suspend fun suggest(
        workspacePath: String,
        title: String,
        rationale: String,
        priority: RecommendationPriority,
        verificationRule: String,
        sourceSessionId: String?,
        sourceMessageId: String?,
        relatedMemoryIds: List<String>,
        relatedSkillIds: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                val cleanTitle = title.trim()
                require(cleanTitle.isNotEmpty()) { "Title is required" }
                val records = readAll().toMutableList()
                val normalized = cleanTitle.lowercase().trim()
                require(records.none {
                    it.workspacePath == workspacePath &&
                        it.status in Recommendation.ACTIVE_STATUSES &&
                        it.title.lowercase().trim() == normalized
                }) { "An active recommendation with this title already exists" }
                val now = System.currentTimeMillis()
                val recommendation = Recommendation(
                    id = UUID.randomUUID().toString(),
                    workspacePath = workspacePath,
                    title = cleanTitle,
                    rationale = rationale.trim(),
                    priority = priority,
                    status = RecommendationStatus.SUGGESTED,
                    sourceSessionId = sourceSessionId,
                    sourceMessageId = sourceMessageId,
                    relatedMemoryIds = relatedMemoryIds,
                    relatedSkillIds = relatedSkillIds,
                    verificationRule = verificationRule.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                records.add(recommendation)
                writeAll(records)
                recommendation.id
            }
        }
    }

    override suspend fun list(
        workspacePath: String?,
        statuses: Set<RecommendationStatus>?,
        limit: Int
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            readAll()
                .filter { workspacePath == null || it.workspacePath == workspacePath }
                .filter { statuses == null || it.status in statuses }
                .sortedByDescending { it.updatedAt }
                .take(limit.coerceIn(1, 200))
        }
    }

    override suspend fun get(id: String): Recommendation? = withContext(Dispatchers.IO) {
        synchronized(fileLock) { readAll().firstOrNull { it.id == id } }
    }

    override suspend fun transition(id: String, target: RecommendationStatus): Result<Recommendation> =
        withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                runCatching {
                    val records = readAll().toMutableList()
                    val index = records.indexOfFirst { it.id == id }
                    if (index < 0) return@runCatching error("Recommendation not found: $id")
                    val current = records[index]
                    if (!Recommendation.canTransition(current.status, target)) {
                        return@runCatching error("Illegal transition ${current.status} -> $target")
                    }
                    val now = System.currentTimeMillis()
                    val next = current.copy(
                        status = target,
                        updatedAt = now,
                        implementedAt = if (target == RecommendationStatus.COMPLETED) now else current.implementedAt,
                        archivedAt = if (target == RecommendationStatus.ARCHIVED) now else current.archivedAt
                    )
                    records[index] = next
                    writeAll(records)
                    next
                }
            }
        }

    override suspend fun verify(id: String, evidence: String): Result<Recommendation> =
        withContext(Dispatchers.IO) {
            val cleanEvidence = evidence.trim()
            // The read-modify-write is atomic under the file lock; the provenance linking is a
            // suspend call, so it runs strictly AFTER the critical section is released.
            val result = synchronized(fileLock) {
                runCatching {
                    if (cleanEvidence.isBlank()) return@runCatching error("Verification evidence is required")
                    val records = readAll().toMutableList()
                    val index = records.indexOfFirst { it.id == id }
                    if (index < 0) return@runCatching error("Recommendation not found: $id")
                    val current = records[index]
                    if (current.status !in setOf(RecommendationStatus.ACCEPTED, RecommendationStatus.IN_PROGRESS)) {
                        return@runCatching error("Only accepted or in-progress recommendations can be verified (current: ${current.status})")
                    }
                    if (!Recommendation.ruleMatches(current.verificationRule, cleanEvidence)) {
                        val rule = current.verificationRule.ifBlank { "<any non-blank evidence>" }
                        return@runCatching error("Evidence does not satisfy verification rule: $rule")
                    }
                    val now = System.currentTimeMillis()
                    val next = current.copy(
                        status = RecommendationStatus.VERIFIED,
                        evidence = (current.evidence + cleanEvidence.take(300)).take(MAX_EVIDENCE_LINES),
                        implementedAt = now,
                        updatedAt = now
                    )
                    records[index] = next
                    writeAll(records)
                    next
                }
            }
            result.getOrNull()?.let { verified ->
                linkEvidenceToRelatedMemories(verified, cleanEvidence)
            }
            result
        }

    override suspend fun archive(id: String): Result<Recommendation> = transition(id, RecommendationStatus.ARCHIVED)

    override suspend fun renderForContext(workspacePath: String?): String = withContext(Dispatchers.IO) {
        if (workspacePath.isNullOrBlank()) return@withContext ""
        synchronized(fileLock) {
            val active = readAll()
                .filter { it.workspacePath == workspacePath && it.status in Recommendation.ACTIVE_STATUSES }
                .sortedWith(
                    compareByDescending<Recommendation> { it.priority.ordinal }
                        .thenByDescending { it.updatedAt }
                )
                .take(MAX_CONTEXT_ITEMS)
            if (active.isEmpty()) return@withContext ""
            buildString {
                appendLine("Active implementation recommendations (advance with recommendation_manage):")
                active.forEach { recommendation ->
                    val rule = recommendation.verificationRule.ifBlank { "any evidence" }
                    val line = buildString {
                        append("- [")
                        append(recommendation.status.name.lowercase().replace('_', ' '))
                        append("] ")
                        append(recommendation.title)
                        if (recommendation.rationale.isNotBlank()) {
                            append(" — ")
                            append(recommendation.rationale)
                        }
                        append(" (verify: ")
                        append(rule)
                        append(")")
                    }
                    appendLine(line.take(180))
                }
            }.trimEnd()
        }
    }

    /**
     * Provenance (phase A): when the system proves a recommendation with evidence, link that proof
     * back to every memory the recommendation drew from, so each memory can answer "where do you
     * know this from?". Best-effort and non-fatal — the recommendation itself is already persisted.
     */
    private suspend fun linkEvidenceToRelatedMemories(recommendation: Recommendation, evidence: String) {
        if (recommendation.relatedMemoryIds.isEmpty()) return
        val evidenceLine = "verified by recommendation '${recommendation.title}': $evidence"
        recommendation.relatedMemoryIds.forEach { memoryId ->
            runCatching {
                memoryRepository.appendEvidence(memoryId, evidenceLine, recommendation.workspacePath)
            }.onFailure {
                android.util.Log.w("AmayaMemory", "Evidence link to memory $memoryId failed: ${it.message}")
            }
        }
    }

    private fun readAll(): List<Recommendation> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line).toRecommendation() }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun writeAll(records: List<Recommendation>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(records.joinToString("\n") { it.toJson().toString() } + if (records.isEmpty()) "" else "\n")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun Recommendation.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("workspacePath", workspacePath)
        .put("title", title)
        .put("rationale", rationale)
        .put("priority", priority.name)
        .put("status", status.name)
        .put("sourceSessionId", sourceSessionId)
        .put("sourceMessageId", sourceMessageId)
        .put("relatedMemoryIds", JSONArray(relatedMemoryIds))
        .put("relatedSkillIds", JSONArray(relatedSkillIds))
        .put("verificationRule", verificationRule)
        .put("evidence", JSONArray(evidence))
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("implementedAt", implementedAt)
        .put("archivedAt", archivedAt)

    private fun JSONObject.toRecommendation(): Recommendation = Recommendation(
        id = getString("id"),
        workspacePath = optString("workspacePath", ""),
        title = optString("title"),
        rationale = optString("rationale"),
        priority = RecommendationPriority.fromString(optString("priority")),
        status = RecommendationStatus.fromString(optString("status")) ?: RecommendationStatus.SUGGESTED,
        sourceSessionId = if (has("sourceSessionId") && !isNull("sourceSessionId")) optString("sourceSessionId") else null,
        sourceMessageId = if (has("sourceMessageId") && !isNull("sourceMessageId")) optString("sourceMessageId") else null,
        relatedMemoryIds = stringList("relatedMemoryIds"),
        relatedSkillIds = stringList("relatedSkillIds"),
        verificationRule = optString("verificationRule"),
        evidence = stringList("evidence"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        implementedAt = if (has("implementedAt") && !isNull("implementedAt")) optLong("implementedAt") else null,
        archivedAt = if (has("archivedAt") && !isNull("archivedAt")) optLong("archivedAt") else null
    )

    private fun JSONObject.stringList(key: String): List<String> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) } }
            ?.filter(String::isNotBlank).orEmpty()

    companion object {
        private const val MAX_CONTEXT_ITEMS = 6
        private const val MAX_EVIDENCE_LINES = 5
    }
}
