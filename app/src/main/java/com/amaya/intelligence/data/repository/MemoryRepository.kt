package com.amaya.intelligence.data.repository

import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryProposal
import com.amaya.intelligence.domain.memory.MemoryScope
import com.amaya.intelligence.domain.memory.MemoryStatus
import com.amaya.intelligence.domain.memory.MemoryType
import com.amaya.intelligence.domain.memory.MemoryVolatility

data class WorkspaceMemoryBinding(
    val id: String,
    val root: String,
    val recordCount: Int,
    val rootExists: Boolean
)

data class MemoryRecord(
    val id: String,
    val type: MemoryType,
    val action: MemoryAction,
    val scope: MemoryScope,
    val target: String,
    val label: String,
    val title: String,
    val content: String,
    val reason: String,
    val confidence: Double,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val expiresAt: Long? = null,
    val source: String = "index",
    val version: Int = 1,
    val workspacePath: String? = null,
    val workspaceId: String? = null,
    val subject: String = "memory",
    val attribute: String = "",
    val status: MemoryStatus = MemoryStatus.ACTIVE,
    val sourceConversationId: String? = null,
    /** Decay class derived from the memory type (scheme §1.1). Stable for preferences, moderate for project facts. */
    val volatility: MemoryVolatility = MemoryVolatility.fromType(MemoryType.USER_PROFILE),
    /** Scheme §4 confidence status: true only after independent validation (explicit user confirm or
     * an approved proposal), never from usage or source reputation alone. */
    val verified: Boolean = false,
    val verifyCount: Int = 0,
    val lastConfirmedAt: Long? = null,
    /** Provenance (project intelligence phase A): source, session, verification of each fact. */
    val evidence: List<String> = emptyList(),
    /** Phase B temporal validity: id of the memory that replaced this one (valid → superseded → archived). */
    val supersededById: String? = null
)

/** Result of one batch end-of-session housekeeping run (decay, archiving, and cap enforcement). */
data class MemoryHousekeepingReport(
    val archivedCount: Int = 0,
    val cappedCount: Int = 0,
    val decayedCount: Int = 0,
    val expiredCount: Int = 0
)

interface MemoryRepository {
    suspend fun applyProposal(proposal: MemoryProposal): Result<String>
    suspend fun readUserProfile(): String
    suspend fun readWorkspaceFacts(workspacePath: String? = null): String
    suspend fun listWorkspaceBindings(): List<WorkspaceMemoryBinding>
    suspend fun remapWorkspace(workspaceId: String, newRoot: String): Result<Unit>
    suspend fun compactStoredMemory(): Result<Unit>

    /**
     * Batch end-of-session housekeeping: recompute decay scores from volatility + age, archive
     * memories that decayed below the floor, and enforce the per-scope cap. Runs as one batched
     * pass so flash I/O is bounded to a single session boundary (scheme §5).
     */
    suspend fun runHousekeeping(): Result<MemoryHousekeepingReport>
    suspend fun listMemoryRecords(
        type: MemoryType? = null,
        query: String? = null,
        limit: Int = 50,
        workspacePath: String? = null
    ): List<MemoryRecord>
    suspend fun updateMemoryById(id: String, content: String, expectedVersion: Int, workspacePath: String? = null): Result<String>
    suspend fun deleteMemoryById(id: String, expectedVersion: Int, workspacePath: String? = null): Result<String>

    /**
     * Scheme §4: promote a memory to verified (independent validation) without changing its content.
     * The only callers are explicit user confirmations (memory_manage update) and approved pending
     * proposals — usage alone can never confirm a memory.
     */
    suspend fun confirmMemory(id: String, workspacePath: String? = null): Result<String>

    /**
     * Provenance (project intelligence): append a verification evidence line to an active memory
     * without changing its content. Used by the recommendation system so that when a recommendation
     * is VERIFIED, the proof is linked back to every memory it drew from ("where do you know this
     * from?"). Bumps the record version like any other mutation.
     */
    suspend fun appendEvidence(id: String, evidenceLine: String, workspacePath: String? = null): Result<Unit>
}
