package com.amaya.intelligence.domain.memory

/**
 * Project Intelligence System: implementation recommendations with a verification lifecycle.
 *
 * The lifecycle distinguishes a *user claim* from *system proof*:
 * - `COMPLETED` is reached when the user (or agent, on their behalf) says the work is done.
 * - `VERIFIED` is only reachable through [RecommendationRepository.verify], which requires evidence
 *   text that satisfies the recommendation's [Recommendation.verificationRule] (e.g. a build log
 *   containing "build successful"). "The user said it is done" and "the system proved it is done"
 *   are intentionally different states.
 */
enum class RecommendationStatus {
    SUGGESTED,
    ACCEPTED,
    IN_PROGRESS,
    VERIFIED,
    COMPLETED,
    ARCHIVED;

    companion object {
        fun fromString(raw: String?): RecommendationStatus? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

enum class RecommendationPriority {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromString(raw: String?): RecommendationPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

data class Recommendation(
    val id: String,
    val workspacePath: String,
    val title: String,
    val rationale: String = "",
    val priority: RecommendationPriority = RecommendationPriority.MEDIUM,
    val status: RecommendationStatus = RecommendationStatus.SUGGESTED,
    val sourceSessionId: String? = null,
    val sourceMessageId: String? = null,
    val relatedMemoryIds: List<String> = emptyList(),
    val relatedSkillIds: List<String> = emptyList(),
    /** Comma/semicolon-separated keywords that must ALL appear (case-insensitive) in verification
     *  evidence. A blank rule means any non-blank evidence is accepted by [verify]. */
    val verificationRule: String = "",
    /** Provenance: verification evidence lines appended when the rule is satisfied. */
    val evidence: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val implementedAt: Long? = null,
    val archivedAt: Long? = null
) {
    companion object {
        /** Statuses that are still actionable and should be surfaced in context. */
        val ACTIVE_STATUSES = setOf(
            RecommendationStatus.SUGGESTED,
            RecommendationStatus.ACCEPTED,
            RecommendationStatus.IN_PROGRESS,
            RecommendationStatus.VERIFIED
        )

        /** Guarded lifecycle. Everything can be archived (dropped) until it is completed; a completed
         *  recommendation is kept as history. */
        fun canTransition(from: RecommendationStatus, to: RecommendationStatus): Boolean = when (to) {
            RecommendationStatus.ARCHIVED -> from != RecommendationStatus.COMPLETED && from != RecommendationStatus.ARCHIVED
            RecommendationStatus.ACCEPTED -> from == RecommendationStatus.SUGGESTED
            RecommendationStatus.IN_PROGRESS -> from == RecommendationStatus.ACCEPTED
            RecommendationStatus.VERIFIED -> from == RecommendationStatus.ACCEPTED || from == RecommendationStatus.IN_PROGRESS
            RecommendationStatus.COMPLETED -> from == RecommendationStatus.ACCEPTED || from == RecommendationStatus.IN_PROGRESS || from == RecommendationStatus.VERIFIED
            RecommendationStatus.SUGGESTED -> false
        }

        /** True when every non-blank keyword in [rule] appears (case-insensitive) in [evidence].
         *  A blank rule requires only non-blank evidence. */
        fun ruleMatches(rule: String, evidence: String): Boolean {
            val terms = rule.split(',', ';').map(String::trim).filter(String::isNotEmpty)
            if (terms.isEmpty()) return evidence.isNotBlank()
            val lower = evidence.lowercase()
            return terms.all { lower.contains(it.lowercase()) }
        }
    }
}
