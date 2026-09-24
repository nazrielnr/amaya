package com.amaya.intelligence.domain.skills

data class Skill(
    val metadata: SkillMetadata,
    val content: String
)

data class SkillMetadata(
    val name: String,
    val description: String,
    val status: SkillStatus,
    val usageCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
    val createdBy: String,
    val version: String,
    val tags: List<String>,
    val enabled: Boolean = true,
    val needsReview: Boolean = false,
    val reviewReason: String? = null,
    /** Scheme §4: w1×SuccessRate + w2×FrequencyNorm (no cost term), recomputed at end-of-session
     * housekeeping so frequently-failing skills rank below reliable ones without extra I/O. */
    val dynamicReputation: Double = 0.0
)

enum class SkillStatus {
    ACTIVE,
    STALE,
    ARCHIVED
}
