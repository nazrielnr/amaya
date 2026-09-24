package com.amaya.intelligence.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTtlAndDecayTest {

    private val classifier = MemoryClassifier(
        safetyFilter = MemorySafetyFilter(),
        contentNormalizer = MemoryContentNormalizer()
    )

    @Test
    fun `volatility mapping assigns correct decay multipliers and default TTLs`() {
        assertEquals(MemoryVolatility.STABLE, MemoryVolatility.fromType(MemoryType.USER_PROFILE))
        assertEquals(0.97, MemoryVolatility.STABLE.decayMultiplier, 0.001)
        assertNull(MemoryVolatility.STABLE.defaultTTLMillis)

        assertEquals(MemoryVolatility.MODERATE, MemoryVolatility.fromType(MemoryType.WORKSPACE_FACT))
        assertEquals(0.90, MemoryVolatility.MODERATE.decayMultiplier, 0.001)
        assertEquals(90L * 24 * 60 * 60 * 1000L, MemoryVolatility.MODERATE.defaultTTLMillis)

        assertEquals(MemoryVolatility.MODERATE, MemoryVolatility.fromType(MemoryType.DECISION))
        assertEquals(0.75, MemoryVolatility.PERISHABLE.decayMultiplier, 0.001)
        assertEquals(14L * 24 * 60 * 60 * 1000L, MemoryVolatility.PERISHABLE.defaultTTLMillis)
    }

    @Test
    fun `user profile memories default to no expiration`() {
        val proposal = classifier.classify(
            content = "User prefers concise answers in Indonesian",
            requestedType = MemoryType.USER_PROFILE
        )

        assertEquals(MemoryVolatility.STABLE, proposal.volatility)
        assertNull(proposal.expiresAt)
    }

    @Test
    fun `workspace facts default to 90-day TTL`() {
        val before = System.currentTimeMillis()
        val proposal = classifier.classify(
            content = "The project uses AGP 8.8 with Kotlin DSL",
            requestedType = MemoryType.WORKSPACE_FACT
        )
        val after = System.currentTimeMillis()

        assertEquals(MemoryVolatility.MODERATE, proposal.volatility)
        assertNotNull(proposal.expiresAt)
        val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000L
        assertTrue(proposal.expiresAt!! >= before + ninetyDaysMs)
        assertTrue(proposal.expiresAt!! <= after + ninetyDaysMs)
    }

    @Test
    fun `explicit ttlMillis overrides default volatility TTL`() {
        val before = System.currentTimeMillis()
        val customTtl = 7L * 24 * 60 * 60 * 1000L // 7 days
        val proposal = classifier.classify(
            content = "Temporary dev server is active on port 8080",
            requestedType = MemoryType.WORKSPACE_FACT,
            ttlMillis = customTtl
        )
        val after = System.currentTimeMillis()

        assertNotNull(proposal.expiresAt)
        assertTrue(proposal.expiresAt!! >= before + customTtl)
        assertTrue(proposal.expiresAt!! <= after + customTtl)
    }

    @Test
    fun `explicit expiresAt timestamp is honored directly`() {
        val fixedExpiry = 1893456000000L
        val proposal = classifier.classify(
            content = "Sprint 42 feature flag deadline",
            requestedType = MemoryType.WORKSPACE_FACT,
            expiresAt = fixedExpiry
        )

        assertEquals(fixedExpiry, proposal.expiresAt)
    }

    @Test
    fun `perishable volatility expires in 14 days`() {
        val before = System.currentTimeMillis()
        val proposal = classifier.classify(
            content = "Dynamic dev build failure observed in CI",
            requestedType = MemoryType.WORKSPACE_FACT,
            requestedVolatility = MemoryVolatility.PERISHABLE
        )
        val after = System.currentTimeMillis()

        assertEquals(MemoryVolatility.PERISHABLE, proposal.volatility)
        val fourteenDaysMs = 14L * 24 * 60 * 60 * 1000L
        assertNotNull(proposal.expiresAt)
        assertTrue(proposal.expiresAt!! >= before + fourteenDaysMs)
        assertTrue(proposal.expiresAt!! <= after + fourteenDaysMs)
    }
}
