package com.amaya.intelligence.domain.skills

/**
 * One skill usage observation, collected in memory during a session and flushed to disk as a
 * single batch at end-of-session housekeeping (scheme §1.4 `skill_usage_log`). Kept as a plain
 * data record so the repo can buffer a whole session's worth of entries and write them once.
 */
data class SkillUsageEntry(
    val skillName: String,
    val sessionId: String,
    /** True when the turn that used the skill finished without a terminal error. */
    val outcome: Boolean,
    /** Optional free-form note (e.g. the failure hint extracted by the pipeline). */
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
