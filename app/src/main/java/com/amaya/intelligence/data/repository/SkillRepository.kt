package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.local.files.FileSkillStore
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.skills.Skill
import com.amaya.intelligence.domain.skills.SkillMetadata
import com.amaya.intelligence.domain.skills.SkillPatchApplier
import com.amaya.intelligence.domain.skills.SkillStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface SkillRepository {
    suspend fun listSkills(): List<SkillMetadata>
    suspend fun getSkill(name: String): Skill?
    suspend fun createSkill(skill: Skill): Result<Unit>
    suspend fun updateSkill(name: String, content: String): Result<Unit>
    suspend fun patchSkill(name: String, patch: String): Result<Unit>
    suspend fun deleteSkill(name: String): Result<Unit>
    suspend fun recordSkillViewed(name: String)
    suspend fun recordSkillUsage(name: String, success: Boolean)
    suspend fun updateSkillMetadata(metadata: SkillMetadata): Result<Unit>

    /**
     * Batch end-of-session pass (scheme §1.4): recompute `dynamicReputation` for every skill as
     * 0.6×SuccessRate + 0.4×FrequencyNorm (no cost term) and persist it once per session.
     */
    suspend fun computeDynamicReputations(): Result<Unit>

    /**
     * Consolidates two or more micro-skills into a unified target skill, combining their usage
     * telemetry and tags, then archiving the source skills.
     */
    suspend fun mergeSkills(sourceNames: List<String>, targetSkill: Skill): Result<Unit>
}

@Singleton
class FileSkillRepository @Inject constructor(
    private val store: FileSkillStore,
    private val memoryClassifier: MemoryClassifier,
    private val patchApplier: SkillPatchApplier
) : SkillRepository {
    private val fileMutex = Mutex()

    override suspend fun listSkills(): List<SkillMetadata> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            store.rootDir.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { dir -> readMetadata(File(dir, "metadata.json")) }
                ?.sortedWith(compareBy<SkillMetadata> { it.status.ordinal }.thenBy { it.name })
                ?: emptyList()
        }
    }

    override suspend fun getSkill(name: String): Skill? = withContext(Dispatchers.IO) {
        fileMutex.withLock { readSkillLocked(name) }
    }

    override suspend fun createSkill(skill: Skill): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                require(!memoryClassifier.containsSecret(skill.content)) { "Skill content appears to contain a secret or credential." }
                val safeName = store.sanitizeName(skill.metadata.name)
                val dir = store.skillDir(safeName)
                val existingContent = store.skillContentFile(safeName)
                require(!existingContent.exists()) { "Skill already exists: $safeName" }
                val now = System.currentTimeMillis()
                val metadata = skill.metadata.copy(
                    name = safeName,
                    createdAt = if (skill.metadata.createdAt == 0L) now else skill.metadata.createdAt,
                    updatedAt = now
                )
                writeSkillLocked(safeName, metadata, skill.content)
                dir
            }.map { }
        }
    }

    override suspend fun updateSkill(name: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            updateSkillLocked(name, content)
        }
    }

    override suspend fun patchSkill(name: String, patch: String): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                require(!memoryClassifier.containsSecret(patch)) { "Skill patch appears to contain a secret or credential." }
                val skill = readSkillLocked(name) ?: throw IllegalArgumentException("Skill not found: ${store.sanitizeName(name)}")
                val updatedContent = patchApplier.applyPatch(skill.content, patch)
                updateSkillLocked(skill.metadata.name, updatedContent).getOrThrow()
            }
        }
    }

    override suspend fun deleteSkill(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                val safeName = store.sanitizeName(name)
                val dir = File(store.rootDir, safeName)
                if (!dir.exists()) throw IllegalArgumentException("Skill not found: $safeName")
                dir.deleteRecursively()
            }.map { }
        }
    }

    override suspend fun recordSkillViewed(name: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val safeName = store.sanitizeName(name)
            val metadata = readMetadata(store.metadataFile(safeName)) ?: return@withLock
            atomicWrite(store.metadataFile(safeName), metadata.copy(updatedAt = System.currentTimeMillis()).toJson().toString(2))
        }
    }

    override suspend fun recordSkillUsage(name: String, success: Boolean) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val safeName = store.sanitizeName(name)
            val metadata = readMetadata(store.metadataFile(safeName)) ?: return@withLock
            val newSuccess = metadata.successCount + if (success) 1 else 0
            val newFailure = metadata.failureCount + if (success) 0 else 1
            val outcomeCount = (newSuccess + newFailure).coerceAtLeast(metadata.usageCount + 1)
            val successRate = if (outcomeCount == 0) 1.0 else newSuccess.toDouble() / outcomeCount.toDouble()
            val needsReview = newFailure >= 3 && successRate < 0.5
            val now = System.currentTimeMillis()
            val updated = metadata.copy(
                usageCount = outcomeCount,
                successCount = newSuccess,
                failureCount = newFailure,
                updatedAt = now,
                lastUsedAt = now,
                needsReview = needsReview,
                reviewReason = if (needsReview) "Failure count >= 3 and success rate < 50%." else metadata.reviewReason
            )
            atomicWrite(store.metadataFile(safeName), updated.toJson().toString(2))
        }
    }

    override suspend fun updateSkillMetadata(metadata: SkillMetadata): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                val safeName = store.sanitizeName(metadata.name)
                val file = store.metadataFile(safeName)
                require(file.exists()) { "Skill not found: $safeName" }
                atomicWrite(file, metadata.copy(name = safeName).toJson().toString(2))
            }
        }
    }

    override suspend fun computeDynamicReputations(): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                val all = store.rootDir.listFiles().orEmpty()
                    .filter(File::isDirectory)
                    .mapNotNull { dir ->
                        val file = store.metadataFile(dir.name)
                        readMetadata(file)?.let { it to file }
                    }
                val maxUsage = all.maxOfOrNull { it.first.usageCount } ?: 0
                all.forEach { (metadata, file) ->
                    val outcomes = metadata.successCount + metadata.failureCount
                    val successRate = if (outcomes <= 0) 0.5 else metadata.successCount.toDouble() / outcomes.toDouble()
                    val frequencyNorm = if (maxUsage <= 0) 0.0 else metadata.usageCount.toDouble() / maxUsage.toDouble()
                    val reputation = REPUTATION_SUCCESS_WEIGHT * successRate + REPUTATION_FREQUENCY_WEIGHT * frequencyNorm
                    atomicWrite(file, metadata.copy(dynamicReputation = reputation.coerceIn(0.0, 1.0)).toJson().toString(2))
                }
            }
        }
    }

    override suspend fun mergeSkills(sourceNames: List<String>, targetSkill: Skill): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
                require(!memoryClassifier.containsSecret(targetSkill.content)) { "Merged skill content appears to contain a secret." }
                val targetSafeName = store.sanitizeName(targetSkill.metadata.name)
                val now = System.currentTimeMillis()

                // Collect sources
                val sourceSkills = sourceNames.mapNotNull { readSkillLocked(it) }
                val totalUsage = sourceSkills.sumOf { it.metadata.usageCount } + targetSkill.metadata.usageCount
                val totalSuccess = sourceSkills.sumOf { it.metadata.successCount } + targetSkill.metadata.successCount
                val totalFailure = sourceSkills.sumOf { it.metadata.failureCount } + targetSkill.metadata.failureCount
                val mergedTags = (sourceSkills.flatMap { it.metadata.tags } + targetSkill.metadata.tags).distinct()
                val earliestCreated = (sourceSkills.map { it.metadata.createdAt } + targetSkill.metadata.createdAt).filter { it > 0 }.minOrNull() ?: now

                val mergedMetadata = targetSkill.metadata.copy(
                    name = targetSafeName,
                    usageCount = totalUsage,
                    successCount = totalSuccess,
                    failureCount = totalFailure,
                    tags = mergedTags,
                    createdAt = earliestCreated,
                    updatedAt = now,
                    status = SkillStatus.ACTIVE,
                    enabled = true
                )

                // Write/update the target skill
                writeSkillLocked(targetSafeName, mergedMetadata, targetSkill.content)

                // Archive the sources (except if source is the target itself)
                sourceNames.forEach { sourceName ->
                    val sourceSafe = store.sanitizeName(sourceName)
                    if (sourceSafe != targetSafeName) {
                        val sourceMeta = readMetadata(store.metadataFile(sourceSafe))
                        if (sourceMeta != null) {
                            val archived = sourceMeta.copy(
                                status = SkillStatus.ARCHIVED,
                                enabled = false,
                                updatedAt = now,
                                reviewReason = "Merged into skill '$targetSafeName'"
                            )
                            atomicWrite(store.metadataFile(sourceSafe), archived.toJson().toString(2))
                        }
                    }
                }
            }
        }
    }

    private fun readSkillLocked(name: String): Skill? {
        val safeName = store.sanitizeName(name)
        val metadata = readMetadata(store.metadataFile(safeName)) ?: return null
        val contentFile = store.skillContentFile(safeName)
        if (!contentFile.exists()) return null
        return Skill(metadata = metadata, content = contentFile.readText())
    }

    private fun updateSkillLocked(name: String, content: String): Result<Unit> = runCatching {
        require(!memoryClassifier.containsSecret(content)) { "Skill content appears to contain a secret or credential." }
        val safeName = store.sanitizeName(name)
        val metadata = readMetadata(store.metadataFile(safeName)) ?: throw IllegalArgumentException("Skill not found: $safeName")
        val updated = metadata.copy(updatedAt = System.currentTimeMillis())
        writeSkillLocked(safeName, updated, content)
    }

    private fun writeSkillLocked(safeName: String, metadata: SkillMetadata, content: String) {
        atomicWrite(store.skillContentFile(safeName), ensureFrontMatter(metadata, content))
        atomicWrite(store.metadataFile(safeName), metadata.toJson().toString(2))
    }

    private fun readMetadata(file: File): SkillMetadata? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        SkillMetadata(
            name = json.optString("name"),
            description = json.optString("description"),
            status = runCatching { SkillStatus.valueOf(json.optString("status", "active").uppercase()) }.getOrDefault(SkillStatus.ACTIVE),
            usageCount = json.optInt("usageCount", 0),
            successCount = json.optInt("successCount", 0),
            failureCount = json.optInt("failureCount", 0),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            lastUsedAt = json.optLongOrNull("lastUsedAt"),
            createdBy = json.optString("createdBy", "agent"),
            version = json.optString("version", "1.0.0"),
            tags = json.optJSONArray("tags")?.let { array -> List(array.length()) { idx -> array.optString(idx) } } ?: emptyList(),
            enabled = json.optBoolean("enabled", true),
            needsReview = json.optBoolean("needsReview", false),
            reviewReason = json.optString("reviewReason").takeIf { it.isNotBlank() },
            dynamicReputation = json.optDouble("dynamicReputation", 0.0).coerceIn(0.0, 1.0)
        )
    }.getOrNull()

    private fun SkillMetadata.toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("status", status.name.lowercase())
        .put("usageCount", usageCount)
        .put("successCount", successCount)
        .put("failureCount", failureCount)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("lastUsedAt", lastUsedAt)
        .put("createdBy", createdBy)
        .put("version", version)
        .put("tags", JSONArray(tags))
        .put("enabled", enabled)
        .put("needsReview", needsReview)
        .put("reviewReason", reviewReason)
        .put("dynamicReputation", dynamicReputation)

    private fun ensureFrontMatter(metadata: SkillMetadata, content: String): String {
        if (content.trimStart().startsWith("---")) return content.trimEnd() + "\n"
        return """
            ---
            name: ${metadata.name}
            description: ${metadata.description}
            version: ${metadata.version}
            createdBy: ${metadata.createdBy}
            ---

        """.trimIndent() + content.trim() + "\n"
    }

    private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

    private fun atomicWrite(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            file.writeText(content)
            tmp.delete()
        }
    }

    private companion object {
        /** Scheme §1.4 weights (renormalized without the cost term). */
        const val REPUTATION_SUCCESS_WEIGHT = 0.6
        const val REPUTATION_FREQUENCY_WEIGHT = 0.4
    }
}
