package com.amaya.intelligence.domain.memory

/** How a primed state's trigger is matched at turn start (scheme §3). */
enum class PrimedTriggerType {
    /** SHA-256 hash match: fires only on an identical (normalized) trigger. */
    EXACT,
    /** Embedding similarity match: fires on semantically similar triggers (paraphrase-tolerant). */
    FUZZY
}

enum class PrimedStateStatus {
    PRIMED,
    FADING,
    CLEARED
}

/**
 * A "primed state" (self-improving memory scheme §3): durable, pre-emptive guidance created after
 * a workflow repeatedly failed with the same error (or the user corrected an outcome). At turn
 * start the retrieval layer injects [primedAction] into the prompt BEFORE the model acts.
 *
 * Fuzzy matches are strictly context-only — they never auto-trigger tool execution; the model
 * makes the final decision. EXACT states fire on an identical normalized trigger (SHA-256),
 * FUZZY states on embedding similarity to [triggerText].
 */
data class PrimedState(
    val id: String,
    val triggerType: PrimedTriggerType,
    /** EXACT: sha256(normalize(trigger)); FUZZY: the raw trigger text embedded for similarity. */
    val triggerSignature: String,
    /** Human-readable trigger text (also the fuzzy embedding input). */
    val triggerText: String,
    /** Instruction injected before the action, phrased as a caution, never an auto-execution. */
    val primedAction: String,
    /** Workflow fingerprint (tool sequence + site) this state was learned from; used to reinforce. */
    val fingerprint: String = "",
    val relatedSkillId: String? = null,
    val reinforcementCount: Int = 1,
    val lastReinforcedAt: Long,
    val status: PrimedStateStatus = PrimedStateStatus.PRIMED,
    val createdAt: Long,
    val workspacePath: String? = null,
    val siteHost: String? = null
)
