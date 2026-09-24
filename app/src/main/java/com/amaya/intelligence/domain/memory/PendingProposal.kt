package com.amaya.intelligence.domain.memory

data class PendingProposal(
    val id: String,
    val sourceSessionId: String,
    val type: PendingProposalType,
    val target: String,
    val action: PendingProposalAction,
    val title: String,
    val content: String,
    val reason: String,
    val confidence: Double,
    val createdAt: Long,
    val status: PendingProposalStatus,
    val workspacePath: String? = null,
    val workspaceId: String? = null,
    val sourceSessionIds: List<String> = listOf(sourceSessionId),
    val evidence: List<String> = emptyList()
)

enum class PendingProposalType {
    /** Durable user preference/profie fact, proposed by auto-consolidation (approval-gated). */
    USER_PROFILE,
    SKILL_CREATE,
    SKILL_PATCH,
    SKILL_UPDATE,
    WORKSPACE_FACT,
    /** Project Intelligence System phase A: a project design decision with rationale (approval-gated). */
    DECISION
}

enum class PendingProposalAction {
    ADD,
    REPLACE,
    CREATE,
    PATCH,
    UPDATE,
    IGNORE
}

enum class PendingProposalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    APPLIED,
    EXPIRED
}
