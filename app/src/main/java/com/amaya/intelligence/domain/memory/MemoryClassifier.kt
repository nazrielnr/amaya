package com.amaya.intelligence.domain.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryClassifier @Inject constructor(
    private val safetyFilter: MemorySafetyFilter,
    private val contentNormalizer: MemoryContentNormalizer
) {
    fun classify(
        content: String,
        requestedType: MemoryType? = null,
        requestedAction: MemoryAction = MemoryAction.ADD,
        requestedScope: MemoryScope? = null,
        requestedTitle: String? = null,
        reason: String = "Requested by agent",
        confidence: Double = 0.8,
        workspacePath: String? = null,
        workspaceId: String? = null,
        sourceConversationId: String? = null,
        evidence: List<String> = emptyList(),
        ttlMillis: Long? = null,
        expiresAt: Long? = null,
        requestedVolatility: MemoryVolatility? = null
    ): MemoryProposal {
        val trimmed = content.trim()
        val safeConfidence = confidence.coerceIn(0.0, 1.0)
        val safety = safetyFilter.check(trimmed)
        val safeContent = safety.redactedContent.trim()
        val unsafe = safeContent.isBlank() || safeConfidence < MIN_CONFIDENCE || !safety.safe
        val type = requestedType ?: inferType(safeContent)
        val action = if (unsafe) MemoryAction.IGNORE else normalizeAction(requestedAction, safeContent)
        val scope = requestedScope ?: defaultScope(type)
        val volatility = requestedVolatility ?: MemoryVolatility.fromType(type)
        val now = System.currentTimeMillis()
        val computedExpiresAt = when {
            expiresAt != null -> expiresAt
            ttlMillis != null && ttlMillis > 0L -> now + ttlMillis
            volatility.defaultTTLMillis != null -> now + volatility.defaultTTLMillis
            else -> null
        }
        val normalized = if (unsafe) {
            NormalizedMemoryText("Rejected memory", safeContent, if (!safety.safe) "Rejected by memory safety rules: ${safety.reasons.joinToString()}" else "Rejected by memory safety/classification rules")
        } else {
            contentNormalizer.normalize(safeContent, type, action, reason, requestedTitle)
        }

        val instructionLike = !unsafe && contentNormalizer.isInstructionLike(normalized.content)
        return MemoryProposal(
            type = type,
            action = if (instructionLike) MemoryAction.IGNORE else action,
            scope = scope,
            title = normalized.title,
            content = normalized.content,
            reason = if (instructionLike) "Rejected because memory must be a declarative fact, not an instruction." else normalized.reason,
            confidence = safeConfidence,
            createdAt = now,
            expiresAt = computedExpiresAt,
            volatility = volatility,
            workspacePath = workspacePath,
            workspaceId = workspaceId,
            sourceConversationId = sourceConversationId,
            evidence = evidence
        )
    }

    fun containsSecret(text: String): Boolean = !safetyFilter.check(text).safe

    fun checkSafety(text: String): SafetyCheckResult = safetyFilter.check(text)

    private fun inferType(content: String): MemoryType {
        val lower = content.lowercase()
        return when {
            listOf("prefers", "preference", "call me", "nickname", "likes replies", "bahasa", "language").any { it in lower } -> MemoryType.USER_PROFILE
            listOf("workspace", "project", "repo", "repository", "environment").any { it in lower } -> MemoryType.WORKSPACE_FACT
            // Design decisions with rationale (project intelligence phase A). Only workspace-anchored
            // language qualifies — "I decided to always reply in Indonesian" stays a user preference.
            (listOf("decided", "decision", "we chose", "we decided", "we picked", "keputusan", "memutuskan", "chose to use", "opted for").any { it in lower }) &&
                (listOf("workspace", "project", "repo", "repository", "app", "library", "dependency", "build", "sdk", "architecture").any { it in lower }) -> MemoryType.DECISION
            else -> MemoryType.USER_PROFILE
        }
    }

    private fun normalizeAction(action: MemoryAction, content: String): MemoryAction {
        return when {
            action == MemoryAction.REPLACE && !hasReplacementDelimiter(content) -> MemoryAction.ADD
            else -> action
        }
    }

    private fun hasReplacementDelimiter(content: String): Boolean {
        return listOf("=>", "->", "→").any { delimiter ->
            val index = content.indexOf(delimiter)
            index > 0 && index < content.lastIndex
        }
    }

    private fun defaultScope(type: MemoryType): MemoryScope = when (type) {
        MemoryType.USER_PROFILE -> MemoryScope.USER
        MemoryType.WORKSPACE_FACT -> MemoryScope.WORKSPACE
        MemoryType.DECISION -> MemoryScope.WORKSPACE
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.55

    }
}
