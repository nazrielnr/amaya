package com.amaya.intelligence.domain.memory

/**
 * Volatility class of a saved memory, derived automatically from the memory type.
 *
 * Decides how fast a memory's retrieval priority decays with age (biomimetic decay, self-improving
 * memory scheme §2). `STABLE` memories (user preferences) are the most durable and fade the
 * slowest; `PERISHABLE` ones (environment/site facts) fade the fastest. The multiplier is applied
 * per 30-day period since [MemoryRecord.updatedAt].
 */
enum class MemoryVolatility(
    val decayMultiplier: Double,
    val defaultTTLMillis: Long? = null
) {
    /** Preferences and core user profile facts never expire automatically. */
    STABLE(0.97, defaultTTLMillis = null),
    /** Project facts and design decisions: default TTL 90 days if not reaffirmed. */
    MODERATE(0.90, defaultTTLMillis = 90L * 24L * 60L * 60L * 1000L),
    /** Temporary environment facts, dynamic states, site observations: default TTL 14 days. */
    PERISHABLE(0.75, defaultTTLMillis = 14L * 24L * 60L * 60L * 1000L);

    companion object {
        /** Automatic mapping: preference → stable, project fact/decision → moderate, environment → perishable. */
        fun fromType(type: MemoryType): MemoryVolatility = when (type) {
            MemoryType.USER_PROFILE -> STABLE
            MemoryType.WORKSPACE_FACT -> MODERATE
            // Decisions persist until explicitly superseded — they decay like project facts so a
            // newer decision naturally outranks an older one in retrieval (Phase B supersededBy).
            MemoryType.DECISION -> MODERATE
        }

        fun parse(name: String?): MemoryVolatility? = when (name?.trim()?.uppercase()) {
            "STABLE" -> STABLE
            "MODERATE" -> MODERATE
            "PERISHABLE" -> PERISHABLE
            else -> null
        }
    }
}
