package com.amaya.intelligence.domain.memory

import java.util.UUID

/**
 * A proposed memory update. Proposals are classified and deduplicated before any write happens.
 */
data class MemoryProposal(
    val id: String = UUID.randomUUID().toString(),
    val type: MemoryType,
    val action: MemoryAction,
    val scope: MemoryScope,
    val title: String,
    val content: String,
    val reason: String,
    val confidence: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val volatility: MemoryVolatility? = null,
    /** Host-owned canonical root for workspace-scoped memory. */
    val workspacePath: String? = null,
    val workspaceId: String? = null,
    val sourceConversationId: String? = null,
    val subject: String = "",
    val attribute: String = "",
    /** Provenance lines attached to the stored record (source, session, verification). */
    val evidence: List<String> = emptyList()
)

enum class MemoryType {
    USER_PROFILE,
    WORKSPACE_FACT,
    /** Project Intelligence System phase A: durable design decisions with their rationale. */
    DECISION
}

enum class MemoryAction {
    ADD,
    REPLACE,
    IGNORE
}

enum class MemoryScope {
    USER,
    WORKSPACE
}
