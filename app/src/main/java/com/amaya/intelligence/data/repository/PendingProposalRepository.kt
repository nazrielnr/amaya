package com.amaya.intelligence.data.repository

import android.content.Context
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalAction
import com.amaya.intelligence.domain.memory.PendingProposalStatus
import com.amaya.intelligence.domain.memory.PendingProposalType
import com.amaya.intelligence.domain.skills.Skill
import com.amaya.intelligence.domain.skills.SkillMetadata
import com.amaya.intelligence.domain.skills.SkillStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProposalApplyResult(
    val proposalId: String,
    val success: Boolean,
    val target: String,
    val message: String
)

interface PendingProposalRepository {
    suspend fun addProposal(proposal: PendingProposal): Result<Unit>
    suspend fun listPending(limit: Int = 50): List<PendingProposal>
    suspend fun approve(id: String): Result<Unit>
    suspend fun reject(id: String): Result<Unit>
    suspend fun applyApproved(id: String): Result<Unit>
    suspend fun applyApprovedWithResult(id: String): Result<ProposalApplyResult>
    suspend fun applyAllApproved(): Result<Int>
    suspend fun applyAllApprovedWithResults(): Result<List<ProposalApplyResult>>
    suspend fun expireOldProposals(maxAgeDays: Int = 30): Result<Int>
}

@Singleton
class FilePendingProposalRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val classifier: MemoryClassifier
) : PendingProposalRepository {
    private val file = File(context.filesDir, "memory/pending-proposals.jsonl")
    private val fileMutex = Mutex()

    override suspend fun addProposal(proposal: PendingProposal): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
            val safety = classifier.checkSafety(proposal.content)
            if (!safety.safe) return@runCatching
            val safeProposal = proposal.copy(
                content = safety.redactedContent,
                action = normalizeActionForCreation(proposal.action, safety.redactedContent),
                reason = proposal.reason,
                status = PendingProposalStatus.PENDING
            )
            val proposals = readAll().toMutableList()
            val duplicate = proposals.any {
                it.status == PendingProposalStatus.PENDING &&
                    it.type == safeProposal.type &&
                    it.target == safeProposal.target &&
                    it.workspacePath == safeProposal.workspacePath &&
                    normalize(it.content) == normalize(safeProposal.content)
            }
            if (!duplicate) {
                proposals.add(safeProposal)
                writeAll(proposals)
            }
            }
        }
    }

    override suspend fun listPending(limit: Int): List<PendingProposal> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            readAll()
                .filter { it.status == PendingProposalStatus.PENDING || it.status == PendingProposalStatus.APPROVED }
                .sortedByDescending { it.createdAt }
                .take(limit.coerceIn(1, 200))
        }
    }

    override suspend fun approve(id: String): Result<Unit> = updateStatus(id, PendingProposalStatus.APPROVED, allowed = setOf(PendingProposalStatus.PENDING))

    override suspend fun reject(id: String): Result<Unit> = updateStatus(id, PendingProposalStatus.REJECTED, allowed = setOf(PendingProposalStatus.PENDING, PendingProposalStatus.APPROVED))

    override suspend fun applyApproved(id: String): Result<Unit> = applyApprovedWithResult(id).map { }

    override suspend fun applyApprovedWithResult(id: String): Result<ProposalApplyResult> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
            val proposals = readAll().toMutableList()
            val index = proposals.indexOfFirst { it.id == id }
            require(index >= 0) { "Proposal not found: $id" }
            val proposal = proposals[index]
            require(proposal.status == PendingProposalStatus.APPROVED) { "Proposal must be APPROVED before apply." }
            val message = apply(proposal).getOrThrow()
            proposals[index] = proposal.copy(status = PendingProposalStatus.APPLIED)
            writeAll(proposals)
            ProposalApplyResult(
                proposalId = proposal.id,
                success = true,
                target = proposal.target,
                message = "$message This will affect the next chat."
            )
            }
        }
    }

    override suspend fun applyAllApproved(): Result<Int> = applyAllApprovedWithResults().map { results -> results.count { it.success } }

    override suspend fun applyAllApprovedWithResults(): Result<List<ProposalApplyResult>> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
            val results = mutableListOf<ProposalApplyResult>()
            val proposals = readAll().toMutableList()
            proposals.forEachIndexed { index, proposal ->
                if (proposal.status == PendingProposalStatus.APPROVED) {
                    val applyResult = apply(proposal)
                    if (applyResult.isSuccess) {
                        proposals[index] = proposal.copy(status = PendingProposalStatus.APPLIED)
                        results.add(ProposalApplyResult(
                            proposalId = proposal.id,
                            success = true,
                            target = proposal.target,
                            message = "${applyResult.getOrThrow()} This will affect the next chat."
                        ))
                    } else {
                        results.add(ProposalApplyResult(
                            proposalId = proposal.id,
                            success = false,
                            target = proposal.target,
                            message = applyResult.exceptionOrNull()?.message ?: "Apply failed."
                        ))
                    }
                }
            }
            writeAll(proposals)
            results
            }
        }
    }

    override suspend fun expireOldProposals(maxAgeDays: Int): Result<Int> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
            val cutoff = System.currentTimeMillis() - maxAgeDays.coerceAtLeast(1) * DAY_MS
            var expired = 0
            val updated = readAll().map { proposal ->
                if (proposal.status == PendingProposalStatus.PENDING && proposal.createdAt < cutoff) {
                    expired++
                    proposal.copy(status = PendingProposalStatus.EXPIRED)
                } else proposal
            }
            writeAll(updated)
            expired
            }
        }
    }

    private suspend fun updateStatus(id: String, status: PendingProposalStatus, allowed: Set<PendingProposalStatus>): Result<Unit> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            runCatching {
            val proposals = readAll().toMutableList()
            val index = proposals.indexOfFirst { it.id == id }
            require(index >= 0) { "Proposal not found: $id" }
            require(proposals[index].status in allowed) { "Cannot change ${proposals[index].status} to $status" }
            proposals[index] = proposals[index].copy(status = status)
            writeAll(proposals)
            }
        }
    }

    private suspend fun apply(proposal: PendingProposal): Result<String> {
        val currentProposal = if (proposal.type == PendingProposalType.WORKSPACE_FACT && proposal.workspaceId != null) {
            val binding = memoryRepository.listWorkspaceBindings().firstOrNull { it.id == proposal.workspaceId }
                ?: return Result.failure(IllegalArgumentException("Workspace memory binding not found: ${proposal.workspaceId}"))
            proposal.copy(workspacePath = binding.root)
        } else proposal
        val safety = classifier.checkSafety(currentProposal.content)
        if (!safety.safe) return Result.failure(IllegalArgumentException("Unsafe proposal cannot be applied: ${safety.reasons.joinToString()}"))
        return when (currentProposal.type) {
            PendingProposalType.USER_PROFILE,
            PendingProposalType.WORKSPACE_FACT,
            PendingProposalType.DECISION -> {
                val applied = memoryRepository.applyProposal(currentProposal.toMemoryProposal())
                if (applied.isSuccess) {
                    // Scheme §4 confidence breaker: the user approved this proposal, which is
                    // independent validation (V_independent) for the written memory.
                    runCatching { memoryRepository.confirmMemory(currentProposal.id, currentProposal.workspacePath) }
                }
                applied
            }
            PendingProposalType.SKILL_CREATE -> createSkill(currentProposal).map { "Created skill ${currentProposal.target}" }
            PendingProposalType.SKILL_PATCH -> skillRepository.patchSkill(currentProposal.target, currentProposal.content).map { "Patched skill ${currentProposal.target}" }
            PendingProposalType.SKILL_UPDATE -> skillRepository.updateSkill(currentProposal.target, currentProposal.content).map { "Updated skill ${currentProposal.target}" }
        }
    }

    private suspend fun createSkill(proposal: PendingProposal): Result<Unit> {
        val now = System.currentTimeMillis()
        val name = proposal.target.ifBlank { proposal.title }
        return skillRepository.createSkill(
            Skill(
                metadata = SkillMetadata(
                    name = name,
                    description = proposal.title.take(180),
                    status = SkillStatus.ACTIVE,
                    usageCount = 0,
                    successCount = 0,
                    failureCount = 0,
                    createdAt = now,
                    updatedAt = now,
                    lastUsedAt = null,
                    createdBy = "agent",
                    version = "1.0.0",
                    tags = listOf("pending-approved")
                ),
                content = proposal.content
            )
        )
    }

    private fun readAll(): List<PendingProposal> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
                ?.takeUnless { it.optString("type") in setOf("LONG_TERM_MEMORY", "DAILY_LOG") }
                ?.let { runCatching { it.toPendingProposal() }.getOrNull() }
        }
    }.getOrDefault(emptyList())

    private fun writeAll(proposals: List<PendingProposal>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(proposals.joinToString("\n") { it.toJson().toString() } + if (proposals.isNotEmpty()) "\n" else "")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun PendingProposal.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("sourceSessionId", sourceSessionId)
        .put("type", type.name)
        .put("target", target)
        .put("action", action.name)
        .put("title", title)
        .put("content", content)
        .put("reason", reason)
        .put("confidence", confidence)
        .put("createdAt", createdAt)
        .put("status", status.name)
        .put("workspacePath", workspacePath)
        .put("workspaceId", workspaceId)
        .put("sourceSessionIds", JSONArray(sourceSessionIds))
        .put("evidence", JSONArray(evidence))

    private fun JSONObject.toPendingProposal(): PendingProposal = PendingProposal(
        id = optString("id"),
        sourceSessionId = optString("sourceSessionId"),
        type = runCatching { PendingProposalType.valueOf(optString("type")) }.getOrNull()
            ?: throw IllegalArgumentException("Unsupported legacy proposal type: ${optString("type")}"),
        target = optString("target"),
        action = runCatching { PendingProposalAction.valueOf(optString("action")) }.getOrDefault(PendingProposalAction.ADD),
        title = optString("title"),
        content = optString("content"),
        reason = optString("reason"),
        confidence = optDouble("confidence", 0.0),
        createdAt = optLong("createdAt", 0L),
        status = runCatching { PendingProposalStatus.valueOf(optString("status")) }.getOrDefault(PendingProposalStatus.PENDING),
        workspacePath = optString("workspacePath").takeIf { it.isNotBlank() },
        workspaceId = optString("workspaceId").takeIf { it.isNotBlank() },
        sourceSessionIds = optJSONArray("sourceSessionIds")?.let { array -> List(array.length()) { array.optString(it) } }?.filter(String::isNotBlank)
            ?: listOf(optString("sourceSessionId")).filter(String::isNotBlank),
        evidence = optJSONArray("evidence")?.let { array -> List(array.length()) { array.optString(it) } }?.filter(String::isNotBlank).orEmpty()
    )

    private fun normalizeActionForCreation(action: PendingProposalAction, content: String): PendingProposalAction {
        return when {
            action == PendingProposalAction.REPLACE && !hasReplacementDelimiter(content) -> PendingProposalAction.ADD
            else -> action
        }
    }

    private fun hasReplacementDelimiter(content: String): Boolean {
        return listOf("=>", "->", "→").any { delimiter ->
            val index = content.indexOf(delimiter)
            index > 0 && index < content.lastIndex
        }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9\\p{L}]+"), " ").trim()

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
