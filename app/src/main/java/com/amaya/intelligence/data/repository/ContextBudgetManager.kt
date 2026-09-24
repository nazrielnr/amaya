package com.amaya.intelligence.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Intelligence System phase C: the context budget manager.
 *
 * Retrieval must not be a bucket of "everything relevant" — every token in the prompt has a cost.
 * This component selects ranked candidates greedily by score-per-token within a per-section token
 * budget and reports how many were deferred (progressive disclosure: the model is told the rest is
 * reachable through a tool instead of silently dropping it). This applies the proposal's
 * `context_score = relevance × confidence × urgency × freshness ÷ token_cost` where the score is
 * the already-fused priority and token cost is the estimated prompt cost of the item.
 */
@Singleton
class ContextBudgetManager @Inject constructor() {

    data class Selection<T>(
        val selected: List<T>,
        val deferredCount: Int,
        val estimatedTokens: Int
    )

    /**
     * Greedy selection: always keep the highest-score item, then fill with the best
     * score-per-token among the remaining until the budget is exhausted. Items that do not fit are
     * deferred, never silently dropped — the caller appends a progressive-disclosure line.
     */
    fun <T> selectWithinBudget(
        candidates: List<T>,
        budgetTokens: Int,
        scoreOf: (T) -> Double,
        tokensOf: (T) -> Int
    ): Selection<T> {
        if (candidates.isEmpty()) return Selection(emptyList(), 0, 0)
        if (budgetTokens <= 0) return Selection(emptyList(), candidates.size, 0)
        val first = candidates.first()
        val firstCost = tokensOf(first).coerceAtLeast(1)
        if (firstCost > budgetTokens) {
            // Even the best item cannot fit; keep it anyway as a one-line digest rather than lose
            // all context, and defer the rest.
            return Selection(listOf(first), (candidates.size - 1).coerceAtLeast(0), firstCost)
        }
        val selected = mutableListOf(first)
        var used = firstCost
        val remaining = candidates.drop(1)
            .map { it to (scoreOf(it) / tokensOf(it).coerceAtLeast(1)) }
            .sortedByDescending { it.second }
        for ((candidate, _) in remaining) {
            val cost = tokensOf(candidate).coerceAtLeast(1)
            if (used + cost > budgetTokens) continue
            selected.add(candidate)
            used += cost
        }
        return Selection(
            selected = selected,
            deferredCount = (candidates.size - selected.size).coerceAtLeast(0),
            estimatedTokens = used
        )
    }

    /** Per-section token allowance, scaled to the model's context window. */
    fun sectionBudget(contextWindowTokens: Int, sectionWeight: Double): Int {
        if (contextWindowTokens <= 0) return DEFAULT_SECTION_BUDGET
        return (contextWindowTokens * sectionWeight).toInt().coerceIn(MIN_SECTION_BUDGET, MAX_SECTION_BUDGET)
    }

    companion object {
        private const val DEFAULT_SECTION_BUDGET = 900
        private const val MIN_SECTION_BUDGET = 400
        private const val MAX_SECTION_BUDGET = 2_000
    }
}
