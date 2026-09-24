package com.amaya.intelligence.data.repository

import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalAction
import com.amaya.intelligence.domain.memory.PendingProposalStatus
import com.amaya.intelligence.domain.memory.PendingProposalType
import com.amaya.intelligence.domain.memory.PrimedState
import com.amaya.intelligence.domain.memory.PrimedTriggerType
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationPriority
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CompletedInteractionContext(
    val sessionId: String,
    val userMessages: List<String>,
    val assistantMessages: List<String>,
    val toolCalls: List<String>,
    val toolResults: List<String>,
    val timestamp: Long,
    val workspacePath: String? = null,
    val workspaceId: String? = null,
    val successful: Boolean = true
)

data class SelfImprovementResult(
    val skillProposals: List<PendingProposal> = emptyList()
)

internal data class WorkflowEvidence(
    val sessionId: String,
    val fingerprint: String,
    val trigger: String,
    val tools: List<String>,
    val successful: Boolean,
    val activeSkill: String? = null,
    /** First observed failure reason for this workflow, used to learn reusable pitfalls. */
    val failureHint: String? = null,
    /** Host of the site an automated browser workflow operated on (site-scoped learning). */
    val siteHost: String? = null,
    val timestamp: Long,
    val workspacePath: String?
)

@Singleton
class SelfImprovementPipeline @Inject constructor(
    private val classifier: MemoryClassifier,
    private val pendingProposalRepository: PendingProposalRepository,
    private val memoryRepository: MemoryRepository,
    private val primedStateRepository: PrimedStateRepository,
    private val skillRepository: SkillRepository,
    // Project Intelligence System phase B/D: per-workspace live state and Android capability matrix.
    private val projectStateRepository: ProjectStateRepository,
    private val androidCapabilityRepository: AndroidCapabilityRepository,
    // Project Intelligence System: evidence-grounded implementation recommendations.
    private val recommendationRepository: RecommendationRepository,
    // Scheme §1.4: buffered skill usage log, flushed once as a batch at end-of-session housekeeping.
    private val skillUsageLogRepository: SkillUsageLogRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val evidenceFile: File get() = File(context.filesDir, "skills/workflow-evidence.jsonl")
    private val evidenceLock = Any()

    suspend fun analyzeAndImprove(context: CompletedInteractionContext): SelfImprovementResult {
        val skillProposals = extractSkillCandidates(context)
        val factProposals = extractDurableFacts(context)
        // Phase A: project design decisions with rationale (approval-gated, workspace-scoped).
        val decisionProposals = extractDecisions(context)
        val recoveryProposals = extractRecoveryLessons(context)
        (skillProposals + factProposals + decisionProposals + recoveryProposals).forEach { pendingProposalRepository.addProposal(it) }
        // Phase 3: persist primed states learned from repeated failures / user corrections.
        primedStatesFor(context).forEach { state ->
            runCatching { primedStateRepository.addOrReinforce(state) }
                .onFailure { android.util.Log.w("AmayaMemory", "Primed state write failed: ${it.message}") }
        }
        // A later successful recurrence of a previously-failing workflow reinforces its primed state.
        if (context.successful && context.toolCalls.isNotEmpty()) {
            runCatching { primedStateRepository.reinforce(workflowMeta(context).fingerprint, success = true) }
                .onFailure { android.util.Log.w("AmayaMemory", "Primed state reinforce failed: ${it.message}") }
        }
        // Phase B: update the per-workspace project state (goal, blockers, changes, build outcome)
        // from this turn's evidence, and Phase D: mark ABIs verified when a build succeeded.
        if (!context.workspacePath.isNullOrBlank()) {
            runCatching { projectStateRepository.recordTurn(context) }
                .onFailure { android.util.Log.w("AmayaMemory", "Project state update failed: ${it.message}") }
            runCatching {
                androidCapabilityRepository.recordBuildOutcome(context.workspacePath, successful = context.successful)
            }.onFailure { android.util.Log.w("AmayaMemory", "Capability outcome failed: ${it.message}") }
            // Recommendations: a failed turn suggests concrete, evidence-grounded next steps so the
            // agent keeps pursuing blockers instead of repeating the same mistake.
            runCatching { suggestBlockerRecommendations(context) }
                .onFailure { android.util.Log.w("AmayaMemory", "Recommendation suggest failed: ${it.message}") }
        }
        // End-of-turn batch housekeeping (scheme §5): recompute decay, archive memories that decayed
        // below the floor, and enforce the per-scope cap in one bounded pass. Any failure is
        // non-fatal — the next turn retries it.
        runCatching { memoryRepository.runHousekeeping() }
            .onFailure { android.util.Log.w("AmayaMemory", "Housekeeping failed: ${it.message}") }
        runCatching { primedStateRepository.runHousekeeping() }
            .onFailure { android.util.Log.w("AmayaMemory", "Primed state housekeeping failed: ${it.message}") }
        // Phase 4: one batched pass recomputing every skill's dynamic reputation.
        runCatching { skillRepository.computeDynamicReputations() }
            .onFailure { android.util.Log.w("AmayaMemory", "Skill reputation pass failed: ${it.message}") }
        // Scheme §1.4: flush the whole session's buffered skill usage as one batch write. Any failure
        // is non-fatal — the buffer is retained and retried on the next housekeeping pass.
        runCatching { skillUsageLogRepository.flush() }
            .onFailure { android.util.Log.w("AmayaMemory", "Skill usage log flush failed: ${it.message}") }
        return SelfImprovementResult(skillProposals)
    }

    /**
     * Suggests blocker recommendations from a failed turn. Build failures become HIGH-priority
     * recommendations whose verification rule is a successful build; other failures become
     * MEDIUM-priority recommendations with no rule (any evidence). Deduplicates against active
     * recommendations and caps the per-turn suggestion count. Non-fatal, best-effort.
     */
    internal suspend fun suggestBlockerRecommendations(context: CompletedInteractionContext) {
        val workspacePath = context.workspacePath ?: return
        val hasFailedBuild = context.toolResults.any { result ->
            val lower = result.lowercase()
            BUILD_FAILURE_PATTERNS.any { lower.contains(it) }
        }
        val blockers = context.toolResults
            .flatMap { result -> result.lineSequence().map { it.trim() }.filter { line -> line.isNotBlank() && FAILURE_TERMS.any { it in line.lowercase() } } }
            .distinct()
            .map { it.take(120) }
            .take(MAX_RECOMMENDATIONS_PER_TURN)
        if (!hasFailedBuild && blockers.isEmpty()) return
        val active = recommendationRepository.list(workspacePath = workspacePath)
            .filter { it.status in Recommendation.ACTIVE_STATUSES }
            .map { it.title.lowercase().trim() }
            .toSet()
        if (hasFailedBuild && "Fix the failed build".lowercase() !in active) {
            recommendationRepository.suggest(
                workspacePath = workspacePath,
                title = "Fix the failed build",
                rationale = "The last turn reported a failed build.",
                priority = RecommendationPriority.HIGH,
                verificationRule = "build successful, build succeeded, compilation succeeded",
                sourceSessionId = context.sessionId
            )
        }
        blockers.take(MAX_RECOMMENDATIONS_PER_TURN - 1).forEach { blocker ->
            val title = "Resolve: $blocker"
            if (title.lowercase() !in active) {
                recommendationRepository.suggest(
                    workspacePath = workspacePath,
                    title = title,
                    rationale = "Blocked progress in the last turn.",
                    priority = RecommendationPriority.MEDIUM,
                    verificationRule = "",
                    sourceSessionId = context.sessionId
                )
            }
        }
    }

    internal fun extractSkillCandidates(context: CompletedInteractionContext): List<PendingProposal> {
        val meta = workflowMeta(context)
        val explicitTeach = context.userMessages.any { message -> TEACH_TERMS.any { it in message.lowercase() } }
        val failedResults = context.toolResults.filter { result -> FAILURE_TERMS.any { it in result.lowercase() } }
        val successful = context.successful &&
            failedResults.isEmpty() &&
            (explicitTeach || context.assistantMessages.isNotEmpty())
        if (meta.sequence.isEmpty() && !explicitTeach) return emptyList()

        val trigger = meta.trigger
        val sequence = meta.sequence
        val siteHost = meta.siteHost
        val fingerprint = meta.fingerprint
        val failureHint = meta.failureHint
        val evidence = WorkflowEvidence(
            sessionId = context.sessionId,
            fingerprint = fingerprint,
            trigger = trigger,
            tools = sequence,
            successful = successful,
            activeSkill = viewedSkillName(context),
            failureHint = failureHint,
            siteHost = siteHost,
            timestamp = context.timestamp,
            workspacePath = context.workspacePath
        )
        val previousEvidence = readEvidence().filter { it.fingerprint == fingerprint }.distinctBy { it.sessionId }
        saveEvidence(evidence)
        val proposals = mutableListOf<PendingProposal>()

        // Hermes-style learning from mistakes: when the same workflow keeps failing with the
        // same error and never succeeds, propose a durable "pitfall" lesson for user approval
        // (the write-approval gate) instead of silently forgetting the failure.
        if (failureHint != null) {
            val allMatches = (previousEvidence + evidence).distinctBy { it.sessionId }
            val sameFailures = allMatches.filter { !it.successful && it.failureHint == failureHint }
            if (allMatches.none { it.successful } && sameFailures.size >= REQUIRED_FAILURE_SESSIONS) {
                proposals += buildFailureLessonProposal(context, sequence, fingerprint, trigger, failureHint, allMatches, siteHost)
            }
        }

        if (!successful) return proposals

        // A skill that failed repeatedly then completed successfully gets a recovery patch.
        val viewedSkill = evidence.activeSkill
        val failedSessions = previousEvidence.filter { !it.successful && it.activeSkill == viewedSkill }.map { it.sessionId }
        if (viewedSkill != null && failedSessions.size >= REQUIRED_FAILURE_SESSIONS) {
            val sourceSessions = (failedSessions.takeLast(REQUIRED_FAILURE_SESSIONS) + context.sessionId).distinct()
            proposals += buildSkillPatchProposal(viewedSkill, sequence, sourceSessions, context)
        }

        val matching = (previousEvidence + evidence).filter { it.successful }.distinctBy { it.sessionId }
        if (!explicitTeach && matching.size < REQUIRED_SUCCESSFUL_SESSIONS) return proposals
        val sourceSessions = matching.map { it.sessionId }.takeLast(REQUIRED_SUCCESSFUL_SESSIONS).ifEmpty { listOf(context.sessionId) }
        val name = skillName(sequence, trigger)
        val content = buildString {
            appendLine("---")
            appendLine("name: $name")
            appendLine("description: Reviewed workflow candidate based on verified successful sessions.")
            appendLine("version: 0.1.0")
            appendLine("createdBy: self-improvement")
            appendLine("---")
            appendLine()
            appendLine("# Workflow")
            appendLine()
            appendLine("## Trigger")
            appendLine("- $trigger")
            appendLine()
            appendLine(if (sequence.isEmpty()) "## Source Procedure" else "## Verified Sequence")
            if (sequence.isEmpty()) appendLine("- $trigger") else sequence.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Evidence")
            sourceSessions.forEach { appendLine("- Successful session $it") }
            appendLine()
            appendLine("Review scope, procedure details, and task-specific assumptions before activation.")
        }.trim()
        proposals += PendingProposal(
            id = "skill_${name}_${sourceSessions.joinToString("_")}".replace(Regex("[^A-Za-z0-9_-]"), "_").take(180),
            sourceSessionId = sourceSessions.last(),
            type = PendingProposalType.SKILL_CREATE,
            target = name,
            action = PendingProposalAction.CREATE,
            title = "Review reusable workflow: $name",
            content = content,
            reason = if (explicitTeach) "User explicitly asked to save this successful workflow." else "Same workflow succeeded across ${sourceSessions.size} sessions.",
            confidence = if (explicitTeach) 0.9 else 0.78,
            createdAt = context.timestamp,
            status = PendingProposalStatus.PENDING,
            workspacePath = context.workspacePath,
            workspaceId = context.workspaceId,
            sourceSessionIds = sourceSessions,
            evidence = sourceSessions.map {
                if (sequence.isEmpty()) "Explicitly taught in session $it" else "Successful session $it with sequence: ${sequence.joinToString(" → ")}"
            }
        )
        return proposals
    }

    private fun buildFailureLessonProposal(
        context: CompletedInteractionContext,
        sequence: List<String>,
        fingerprint: String,
        trigger: String,
        failureHint: String,
        matches: List<WorkflowEvidence>,
        siteHost: String?
    ): PendingProposal {
        val failedCount = matches.count { !it.successful }
        val target = "avoid-" + fingerprint
            .lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .take(50)
            .ifBlank { "tool-pitfall" }
        val sourceSessions = matches.map { it.sessionId }.takeLast(REQUIRED_FAILURE_SESSIONS + 2)
        val content = buildString {
            appendLine("---")
            appendLine("name: $target")
            appendLine("description: Lesson learned from repeated tool failures. Read before attempting this workflow.")
            appendLine("version: 0.1.0")
            appendLine("createdBy: self-improvement")
            appendLine("---")
            appendLine()
            appendLine("# Pitfall")
            appendLine()
            appendLine("This workflow failed $failedCount times with the same error and never completed successfully:")
            appendLine()
            appendLine("## Trigger")
            appendLine("- ${trigger.ifBlank { sequence.joinToString(" → ") }}")
            appendLine()
            appendLine("## Failing Sequence")
            if (sequence.isEmpty()) appendLine("- $trigger") else sequence.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Observed Error")
            appendLine("> $failureHint")
            siteHost?.let {
                appendLine()
                appendLine("## Site")
                appendLine("- $it")
            }
            appendLine()
            appendLine("Do not repeat this sequence unchanged. Verify tool arguments, available tools, and prerequisites first; prefer smaller steps.")
            appendLine()
            appendLine("## Evidence")
            sourceSessions.forEach { appendLine("- Failed session $it") }
        }.trim()
        return PendingProposal(
            id = "lesson_${fingerprint}_${failureHint.hashCode()}".replace(Regex("[^A-Za-z0-9_-]"), "_").take(180),
            sourceSessionId = sourceSessions.last(),
            type = PendingProposalType.SKILL_CREATE,
            target = target,
            action = PendingProposalAction.CREATE,
            title = "Tool pitfall to avoid: ${sequence.take(3).joinToString(" → ").ifBlank { trigger.take(40) }}",
            content = content,
            reason = "The same tool workflow failed $failedCount times with the same error and never succeeded.",
            confidence = 0.7,
            createdAt = context.timestamp,
            status = PendingProposalStatus.PENDING,
            workspacePath = context.workspacePath,
            sourceSessionIds = sourceSessions,
            evidence = sourceSessions.map { "Failed session $it${if (siteHost != null) " on $siteHost" else ""}: $failureHint" }
        )
    }

    private fun buildSkillPatchProposal(
        skillName: String,
        sequence: List<String>,
        sourceSessions: List<String>,
        context: CompletedInteractionContext
    ): PendingProposal {
        val patch = buildString {
            appendLine("# Verified Recovery")
            appendLine()
            appendLine("After repeated failures, this sequence completed successfully:")
            sequence.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Validate the recovery steps against the existing skill before applying.")
        }.trim()
        return PendingProposal(
            id = "skill_patch_${skillName}_${sequence.joinToString("|").hashCode()}".replace(Regex("[^A-Za-z0-9_-]"), "_"),
            sourceSessionId = context.sessionId,
            type = PendingProposalType.SKILL_PATCH,
            target = skillName,
            action = PendingProposalAction.PATCH,
            title = "Review recovery for $skillName",
            content = patch,
            reason = "The same skill workflow failed repeatedly, then completed successfully.",
            confidence = 0.78,
            createdAt = context.timestamp,
            status = PendingProposalStatus.PENDING,
            workspacePath = context.workspacePath,
            workspaceId = context.workspaceId,
            sourceSessionIds = sourceSessions,
            evidence = sourceSessions.mapIndexed { index, session ->
                if (index == sourceSessions.lastIndex) "Successful recovery session $session" else "Failed session $session"
            }
        )
    }

    private fun viewedSkillName(context: CompletedInteractionContext): String? = context.toolCalls.firstNotNullOfOrNull { call ->
        if (!call.startsWith("skill:")) return@firstNotNullOfOrNull null
        Regex("(?:skill_id|name)=([^,}]+)").find(call)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
    }

    /** Shared identity for a workflow across the evidence/pitfall/skill and priming sides. */
    private data class WorkflowMeta(
        val sequence: List<String>,
        val siteHost: String?,
        val fingerprint: String,
        val trigger: String,
        val failureHint: String?
    )

    private fun workflowMeta(context: CompletedInteractionContext): WorkflowMeta {
        val tools = context.toolCalls.map { it.substringBefore(':').trim() }
            .filter { it.isNotBlank() && it !in SELF_IMPROVEMENT_TOOLS }
        val sequence = tools.distinct()
        val siteHost = siteHostFromContext(context)
        val trigger = sanitizeEvidence(context.userMessages.firstOrNull().orEmpty()).take(220)
        // Browser workflows are fingerprinted per site so learning is scoped per host
        // (e.g. browser|login.example.com) instead of one bucket for all sites.
        val fingerprint = when {
            sequence.isEmpty() -> "explicit:${trigger.lowercase()}"
            siteHost != null -> (sequence + listOf("site:$siteHost")).joinToString("|").lowercase()
            else -> sequence.joinToString("|").lowercase()
        }
        val failureHint = sanitizeEvidence(
            context.toolResults.filter { result -> FAILURE_TERMS.any { it in result.lowercase() } }
                .firstOrNull().orEmpty()
        ).take(180).takeIf(String::isNotBlank)
        return WorkflowMeta(sequence, siteHost, fingerprint, trigger, failureHint)
    }

    // ====================================================================
    // PRIMED STATES (self-improving memory scheme §3)
    // ====================================================================

    /**
     * Phase 3 creation side: when a workflow keeps failing with the same error and never succeeds
     * (the same condition that produces an approval-gated pitfall lesson), record durable primed
     * states. EXACT matches an identical repeated trigger (SHA-256); FUZZY matches paraphrases via
     * embedding similarity. Both are injected as context at turn start — never auto-executed.
     */
    internal fun primedStatesFor(context: CompletedInteractionContext): List<PrimedState> {
        if (context.toolCalls.isEmpty()) return emptyList()
        val meta = workflowMeta(context)
        val failureHint = meta.failureHint ?: return emptyList()
        val allMatches = readEvidence()
            .filter { it.fingerprint == meta.fingerprint }
            .distinctBy { it.sessionId }
        if (allMatches.isEmpty()) return emptyList()
        val sameFailures = allMatches.filter { !it.successful && it.failureHint == failureHint }
        if (allMatches.none { it.successful } && sameFailures.size >= REQUIRED_FAILURE_SESSIONS) {
            val count = sameFailures.size
            val action = buildString {
                append("This workflow failed $count times with: ")
                append(failureHint)
                append(". Before acting, verify tool arguments, available tools, and page/workspace state; prefer smaller steps and do not repeat the identical sequence unchanged.")
            }.trim()
            return listOfNotNull(
                PrimedState(
                    id = "",
                    triggerType = PrimedTriggerType.EXACT,
                    triggerSignature = sha256Hex(normalizeTrigger(meta.trigger)),
                    triggerText = meta.trigger,
                    primedAction = action,
                    fingerprint = meta.fingerprint,
                    relatedSkillId = null,
                    lastReinforcedAt = context.timestamp,
                    createdAt = context.timestamp,
                    workspacePath = context.workspacePath,
                    siteHost = meta.siteHost
                ),
                PrimedState(
                    id = "",
                    triggerType = PrimedTriggerType.FUZZY,
                    triggerSignature = meta.trigger,
                    triggerText = meta.trigger,
                    primedAction = action,
                    fingerprint = meta.fingerprint,
                    relatedSkillId = null,
                    lastReinforcedAt = context.timestamp,
                    createdAt = context.timestamp,
                    workspacePath = context.workspacePath,
                    siteHost = meta.siteHost
                )
            )
        }
        return emptyList()
    }

    // ====================================================================
    // PROACTIVE PITFALL RECALL (the retrieval side of self-learning)
    // ====================================================================

    /**
     * Turn-start retrieval: match the current user message against stored failed-workflow evidence
     * and return short "known pitfall" lines so the model avoids repeating a sequence that already
     * failed. Site-scoped: same-site evidence ranks highest, then workspace match, then overlapping
     * tools, then recency. Conservative — only explicitly failed sessions with a failure hint count.
     */
    suspend fun matchingPitfalls(
        userMessage: String,
        siteHost: String?,
        workspacePath: String?,
        limit: Int = 3
    ): List<String> {
        if (userMessage.isBlank()) return emptyList()
        val lower = userMessage.lowercase()
        val matched = readEvidence()
            .filter { !it.successful && !it.failureHint.isNullOrBlank() }
            .mapNotNull { evidence ->
                val toolOverlap = evidence.tools.count { tool -> tool.isNotBlank() && lower.contains(tool.lowercase()) }
                var score = 0.0
                if (siteHost != null && evidence.siteHost == siteHost) score += 3.0
                else if (evidence.siteHost != null && lower.contains(evidence.siteHost)) score += 2.5
                if (workspacePath != null && evidence.workspacePath == workspacePath) score += 1.5
                score += toolOverlap * 1.5
                val ageDays = (System.currentTimeMillis() - evidence.timestamp).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)
                score += 1.0 / (1.0 + ageDays / 14.0)
                if (score >= PITFALL_MIN_MATCH_SCORE) evidence to score else null
            }
            .sortedByDescending { it.second }
            .take(limit)
        return matched.map { (evidence, _) ->
            val site = evidence.siteHost?.let { " on $it" }.orEmpty()
            val tools = evidence.tools.take(4).joinToString(" → ").ifBlank { evidence.trigger.take(80) }
            "- $tools$site failed with: ${evidence.failureHint.orEmpty().take(160)}"
        }
    }

    /**
     * Learn from user corrections. The user's follow-up says the previous outcome was wrong:
     * mark the previous turn's evidence as failed (so `successful` reflects reality) and propose
     * a correction lesson for approval. Approval-gated like every other durable write.
     */
    suspend fun recordUserCorrection(sessionId: String, correction: String, workspacePath: String?): SelfImprovementResult {
        val hint = sanitizeEvidence(correction).take(160).ifBlank { "User corrected the outcome" }
        val updated = synchronized(evidenceLock) {
            val records = readEvidence()
            var changed = false
            val rewritten = records.map { record ->
                if (record.sessionId == sessionId && record.successful) {
                    changed = true
                    record.copy(successful = false, failureHint = hint, timestamp = System.currentTimeMillis())
                } else record
            }
            if (changed) writeEvidenceLines(rewritten)
            rewritten
        }
        val failed = updated.filter { it.sessionId == sessionId }
        if (failed.isEmpty()) return SelfImprovementResult()
        val proposal = PendingProposal(
            id = "corr_${sessionId}_${correction.hashCode()}".replace(Regex("[^A-Za-z0-9_-]"), "_").take(180),
            sourceSessionId = sessionId,
            type = PendingProposalType.SKILL_CREATE,
            target = "avoid-user-corrected-outcome",
            action = PendingProposalAction.CREATE,
            title = "Outcome was corrected by the user",
            content = buildString {
                appendLine("---")
                appendLine("name: avoid-user-corrected-outcome")
                appendLine("description: Lesson from an outcome the user explicitly corrected.")
                appendLine("version: 0.1.0")
                appendLine("createdBy: self-improvement")
                appendLine("---")
                appendLine()
                appendLine("# Pitfall")
                appendLine()
                appendLine("The previous result was wrong and the user corrected it:")
                appendLine()
                appendLine("> $hint")
                appendLine()
                appendLine("Re-verify the actual output/state before claiming success; re-read the request and the tool evidence.")
            }.trim(),
            reason = "The user corrected the previous outcome; treat that workflow as failed until proven otherwise.",
            confidence = 0.65,
            createdAt = System.currentTimeMillis(),
            status = PendingProposalStatus.PENDING,
            workspacePath = workspacePath,
            workspaceId = null,
            sourceSessionIds = failed.map { it.sessionId }.distinct(),
            evidence = failed.map { "Corrected in session ${it.sessionId}: $hint" }
        )
        pendingProposalRepository.addProposal(proposal)
        // Phase 3: also prime the corrected outcome so the next similar request starts with the
        // correction in mind. Context-only priming — the model still decides how to act.
        runCatching {
            primedStateRepository.addOrReinforce(
                PrimedState(
                    id = "",
                    triggerType = PrimedTriggerType.FUZZY,
                    triggerSignature = hint,
                    triggerText = hint,
                    primedAction = "The user corrected a previous outcome: $hint. Re-verify the actual result/state before claiming success; re-read the request and the tool evidence.",
                    fingerprint = "user-corrected",
                    lastReinforcedAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    workspacePath = workspacePath
                )
            )
        }.onFailure { android.util.Log.w("AmayaMemory", "Correction primed state failed: ${it.message}") }
        return SelfImprovementResult(listOf(proposal))
    }

    private fun writeEvidenceLines(records: List<WorkflowEvidence>) {
        val content = records.joinToString("\n") { it.toJson().toString() } + if (records.isEmpty()) "" else "\n"
        evidenceFile.parentFile?.mkdirs()
        val tmp = File(evidenceFile.parentFile, "${evidenceFile.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(evidenceFile)) {
            evidenceFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    // ====================================================================
    // AUTO MEMORY CONSOLIDATION (Hermes-style self-learning)
    // ====================================================================

    /**
     * Hermes-style memory consolidation. After a successful interaction, scan the user's own
     * messages for explicit durable preferences and workspace/environment facts and propose them
     * for approval (the write-approval gate), so memory accumulates even when the model never
     * called a memory tool. Conservative: only explicit markers, short declarative sentences,
     * and never questions or task requests.
     */
    internal fun extractDurableFacts(context: CompletedInteractionContext): List<PendingProposal> {
        if (!context.successful || context.assistantMessages.isEmpty()) return emptyList()
        val workspace = context.workspacePath?.takeIf(String::isNotBlank)
        val proposals = mutableListOf<PendingProposal>()
        val seen = mutableSetOf<String>()
        for (message in context.userMessages) {
            if (proposals.size >= MAX_FACTS_PER_TURN) break
            val lower = message.lowercase()
            for ((kind, markers) in DURABLE_FACT_PATTERNS) {
                if (proposals.size >= MAX_FACTS_PER_TURN) break
                val marker = markers.firstOrNull { it in lower } ?: continue
                val fact = extractFactSentence(message, lower.indexOf(marker))
                    ?.let(::cleanFact)
                    ?.takeIf { seen.add(it.lowercase()) }
                    ?.let { buildFactProposal(it, kind, workspace, context) }
                    ?: continue
                proposals += fact
            }
        }
        return proposals
    }

    /**
     * Phase A: extract durable design decisions (with rationale) from successful interactions, e.g.
     * "we chose SQLite over X because...". Workspace-scoped, approval-gated like every other write.
     * Returned so callers can surface them; the write still goes through the pending-proposal gate.
     */
    internal fun extractDecisions(context: CompletedInteractionContext): List<PendingProposal> {
        if (!context.successful || context.userMessages.isEmpty()) return emptyList()
        val workspace = context.workspacePath?.takeIf(String::isNotBlank) ?: return emptyList()
        val proposals = mutableListOf<PendingProposal>()
        val seen = mutableSetOf<String>()
        for (message in context.userMessages) {
            if (proposals.size >= MAX_DECISIONS_PER_TURN) break
            val lower = message.lowercase()
            val marker = DECISION_MARKERS.firstOrNull { it in lower } ?: continue
            val sentence = extractDecisionSentence(message, lower.indexOf(marker)) ?: continue
            val decision = cleanDecision(sentence) ?: continue
            if (!seen.add(decision.lowercase())) continue
            proposals += PendingProposal(
                id = "decision_${java.util.UUID.randomUUID().toString().take(12)}",
                sourceSessionId = context.sessionId,
                type = PendingProposalType.DECISION,
                target = "decision-" + decision.lowercase()
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')
                    .take(40)
                    .ifBlank { "project-decision" },
                action = PendingProposalAction.ADD,
                title = "Recorded project decision",
                content = decision,
                reason = "Detected a project design decision with rationale from a successful interaction (auto-consolidation).",
                confidence = 0.72,
                createdAt = context.timestamp,
                status = PendingProposalStatus.PENDING,
                workspacePath = workspace,
                workspaceId = context.workspaceId,
                sourceSessionIds = listOf(context.sessionId),
                evidence = listOf("Detected from user message in session ${context.sessionId}")
            )
        }
        return proposals
    }

    /**
     * Extracts learned lessons when a task encountered tool/execution errors during the turn
     * but successfully recovered and achieved the goal. Distills the exact recovery into a high-value lesson.
     */
    internal fun extractRecoveryLessons(context: CompletedInteractionContext): List<PendingProposal> {
        if (!context.successful || context.toolCalls.isEmpty() || context.toolResults.isEmpty()) return emptyList()
        val errorIndices = context.toolResults.mapIndexedNotNull { index, res ->
            if (res.startsWith("Error:") || res.contains("Error: ") || res.contains("failed with:")) index else null
        }
        if (errorIndices.isEmpty()) return emptyList()

        val lastErrorIdx = errorIndices.last()
        // If the error was at the very end, there was no recovery
        if (lastErrorIdx >= context.toolCalls.size - 1) return emptyList()

        val failedTool = context.toolCalls.getOrNull(lastErrorIdx)?.substringBefore(':')?.trim().orEmpty()
        val failedErrorSnippet = context.toolResults.getOrNull(lastErrorIdx)?.take(140)?.trim().orEmpty()
        val recoveryTools = context.toolCalls.drop(lastErrorIdx + 1).map { it.substringBefore(':').trim() }.distinct()

        if (failedTool.isBlank() || recoveryTools.isEmpty()) return emptyList()

        val trigger = context.userMessages.firstOrNull()?.take(80)?.trim().orEmpty()
        val proposalId = "recovery_${context.sessionId}_${failedTool.hashCode()}".replace(Regex("[^A-Za-z0-9_-]"), "_").take(180)
        val lessonContent = buildString {
            appendLine("---")
            appendLine("name: recovery-${failedTool.lowercase()}-workflow")
            appendLine("description: Automated recovery pattern distilled from successful error resolution.")
            appendLine("version: 0.1.0")
            appendLine("createdBy: self-improvement-distiller")
            appendLine("---")
            appendLine()
            appendLine("# Error Recovery Lesson")
            appendLine()
            appendLine("## Initial Problem")
            appendLine("- Tool `$failedTool` failed with: $failedErrorSnippet")
            appendLine()
            appendLine("## Successful Resolution")
            appendLine("- Recovered using workflow: ${recoveryTools.joinToString(" → ")}")
            appendLine()
            appendLine("## Recommendation")
            appendLine("- When executing similar tasks ('$trigger'), verify tool preconditions or use the recovery sequence (${recoveryTools.joinToString(", ")}) directly.")
        }.trim()

        return listOf(
            PendingProposal(
                id = proposalId,
                sourceSessionId = context.sessionId,
                type = PendingProposalType.SKILL_CREATE,
                target = "recovery-${failedTool.lowercase()}",
                action = PendingProposalAction.CREATE,
                title = "Learned recovery for $failedTool error",
                content = lessonContent,
                reason = "Agent successfully recovered from an error in '$failedTool' using '${recoveryTools.joinToString(" → ")}'.",
                confidence = 0.85,
                createdAt = context.timestamp,
                status = PendingProposalStatus.PENDING,
                workspacePath = context.workspacePath,
                workspaceId = context.workspaceId,
                sourceSessionIds = listOf(context.sessionId),
                evidence = listOf("Initial error: $failedErrorSnippet", "Recovery steps: ${recoveryTools.joinToString(" → ")}")
            )
        )
    }

    /** Sentence containing [index]; stops at sentence punctuation or the length cap. */
    private fun extractDecisionSentence(message: String, index: Int): String? {
        val sentenceEnd = message.indexOfAny(charArrayOf('.', '!', '\n', '?'), index)
            .let { if (it < 0) message.length else it + 1 }
        return message.substring(index, sentenceEnd.coerceAtMost(index + MAX_FACT_LENGTH)).trim()
    }

    /** Keep the decision's rationale; reject tasks, questions, URLs, and secrets. */
    private fun cleanDecision(sentence: String): String? {
        val text = sentence.trim()
            .trimEnd('.', '!', '?')
            .replace(Regex("(?i)^(we|i|the project|the app|kita|saya)\\s+(decided|chose|chose to use|memilih|memutuskan|pakai|gunakan)\\s+"), "The project decided to use ")
            .trim()
        if (text.length !in MIN_FACT_LENGTH..MAX_FACT_LENGTH) return null
        val lower = text.lowercase()
        if (lower.contains('?')) return null
        if (lower.contains("http://") || lower.contains("https://")) return null
        if (!classifier.checkSafety(text).safe) return null
        return text
    }

    /** Sentence containing [index]; stops at sentence punctuation or the length cap. */
    private fun extractFactSentence(message: String, index: Int): String? {
        val sentenceEnd = message.indexOfAny(charArrayOf('.', '!', '\n', '?'), index)
            .let { if (it < 0) message.length else it + 1 }
        return message.substring(index, sentenceEnd.coerceAtMost(index + MAX_FACT_LENGTH)).trim()
    }

    /** Strip politeness/reminder prefixes and reject tasks, questions, URLs, and secrets. */
    private fun cleanFact(sentence: String): String? {
        val text = sentence.trim()
            .trimEnd('.', '!', '?')
            .replace(Regex("^(?i)(please always|tolong selalu|remember that|please|tolong|remember|ingat bahwa|ingat)\\s*"), "")
            .trim()
        if (text.length !in MIN_FACT_LENGTH..MAX_FACT_LENGTH) return null
        val lower = text.lowercase()
        if (lower.contains('?')) return null
        if (FACT_TASK_VERBS.any { lower.contains(it) }) return null
        if (lower.contains("http://") || lower.contains("https://")) return null
        if (!classifier.checkSafety(text).safe) return null
        return text
    }

    private fun buildFactProposal(
        fact: String,
        kind: FactKind,
        workspacePath: String?,
        context: CompletedInteractionContext
    ): PendingProposal? {
        if (kind == FactKind.WORKSPACE && workspacePath == null) return null
        val isWorkspace = kind == FactKind.WORKSPACE
        val type = if (isWorkspace) PendingProposalType.WORKSPACE_FACT else PendingProposalType.USER_PROFILE
        val lower = fact.lowercase()
        val title = when {
            !isWorkspace && listOf("name", "call me", "panggil", "nama").any { it in lower } -> "User name"
            !isWorkspace && listOf("language", "bahasa", "respond", "reply", "jawab", "speak").any { it in lower } -> "Language preference"
            isWorkspace && listOf("build", "gradle", "maven", "npm", "bun", "yarn", "package manager").any { it in lower } -> "Build tooling"
            isWorkspace && "test" in lower -> "Testing convention"
            else -> if (isWorkspace) "Learned workspace fact" else "Learned user preference"
        }
        val target = (if (isWorkspace) "fact-" else "profile-") + lower
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifBlank { if (isWorkspace) "workspace" else "user" }
        return PendingProposal(
            id = "mem_${java.util.UUID.randomUUID().toString().take(12)}",
            sourceSessionId = context.sessionId,
            type = type,
            target = target,
            action = PendingProposalAction.ADD,
            title = title,
            content = fact,
            reason = if (isWorkspace) {
                "Detected durable workspace fact from a successful interaction (auto-consolidation)."
            } else {
                "Detected durable user preference from a successful interaction (auto-consolidation)."
            },
            confidence = 0.7,
            createdAt = context.timestamp,
            status = PendingProposalStatus.PENDING,
            workspacePath = if (isWorkspace) workspacePath else null,
            workspaceId = if (isWorkspace) context.workspaceId else null,
            sourceSessionIds = listOf(context.sessionId),
            evidence = listOf("Detected from user message in session ${context.sessionId}")
        )
    }

    // ====================================================================
    // SITE-SCOPED BROWSER SELF-LEARNING
    // ====================================================================

    /** Host of the site an automated browser workflow operated on, for site-scoped learning. */
    private fun siteHostFromContext(context: CompletedInteractionContext): String? =
        context.toolCalls.firstNotNullOfOrNull(::siteHostFromToolCall)

    private fun siteHostFromToolCall(call: String): String? {
        if (!call.startsWith("browser:")) return null
        val url = Regex("(?:url|active_url)=(\"?)([^,\"}]+)\\1")
            .find(call)?.groupValues?.getOrNull(2)?.trim() ?: return null
        if (url.isBlank()) return null
        return runCatching {
            val raw = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            java.net.URI(raw).host?.removePrefix("www.")
        }.getOrNull()
    }

    private fun saveEvidence(evidence: WorkflowEvidence) = synchronized(evidenceLock) {
        val existing = readEvidence()
        if (existing.any { it.sessionId == evidence.sessionId && it.fingerprint == evidence.fingerprint }) return@synchronized
        evidenceFile.parentFile?.mkdirs()
        evidenceFile.appendText(evidence.toJson().toString() + "\n")
        compactEvidence(existing + evidence)
    }

    private fun compactEvidence(records: List<WorkflowEvidence>) {
        val cutoff = System.currentTimeMillis() - EVIDENCE_MAX_AGE_MS
        val kept = records.filter { it.timestamp >= cutoff }
            .filter { it.trigger.isNotBlank() }
            .distinctBy { "${it.sessionId}:${it.fingerprint}" }
            .takeLast(MAX_EVIDENCE_RECORDS)
        val tmp = File(evidenceFile.parentFile, "${evidenceFile.name}.tmp")
        tmp.writeText(kept.joinToString("\n") { it.toJson().toString() } + if (kept.isEmpty()) "" else "\n")
        if (!tmp.renameTo(evidenceFile)) {
            evidenceFile.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun readEvidence(): List<WorkflowEvidence> = synchronized(evidenceLock) {
        runCatching {
            if (!evidenceFile.exists()) return@synchronized emptyList()
            evidenceFile.readLines().mapNotNull { line -> runCatching { JSONObject(line).toWorkflowEvidence() }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun WorkflowEvidence.toJson(): JSONObject = JSONObject()
        .put("sessionId", sessionId)
        .put("fingerprint", fingerprint)
        .put("trigger", trigger)
        .put("tools", JSONArray(tools))
        .put("successful", successful)
        .put("activeSkill", activeSkill)
        .put("failureHint", failureHint)
        .put("siteHost", siteHost)
        .put("timestamp", timestamp)
        .put("workspacePath", workspacePath)

    private fun JSONObject.toWorkflowEvidence(): WorkflowEvidence = WorkflowEvidence(
        sessionId = optString("sessionId"),
        fingerprint = optString("fingerprint"),
        trigger = optString("trigger"),
        tools = optJSONArray("tools")?.let { array -> List(array.length()) { array.optString(it) } } ?: emptyList(),
        successful = optBoolean("successful"),
        activeSkill = optString("activeSkill").takeIf(String::isNotBlank),
        failureHint = optString("failureHint").takeIf(String::isNotBlank),
        siteHost = optString("siteHost").takeIf(String::isNotBlank),
        timestamp = optLong("timestamp"),
        workspacePath = optString("workspacePath").takeIf(String::isNotBlank)
    )

    private fun sanitizeEvidence(text: String): String {
        if (!classifier.checkSafety(text).safe) return ""
        return text.replace(Regex("(?is)<think>.*?</think>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun skillName(tools: List<String>, trigger: String): String =
        (tools.take(3).joinToString("-") + "-" + trigger.split(Regex("\\s+")).take(3).joinToString("-"))
            .lowercase()
            .replace(Regex("[^a-z0-9-]+"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "learned-workflow" }

    private enum class FactKind { USER, WORKSPACE }

    companion object {
        private const val REQUIRED_SUCCESSFUL_SESSIONS = 2
        private const val REQUIRED_FAILURE_SESSIONS = 2
        private const val MAX_EVIDENCE_RECORDS = 500
        /** Minimum relevance for a pitfall to be injected at turn start (site match alone passes). */
        private const val PITFALL_MIN_MATCH_SCORE = 2.5
        private const val EVIDENCE_MAX_AGE_MS = 90L * 24L * 60L * 60L * 1000L
        private const val MAX_FACTS_PER_TURN = 2
        private const val MAX_DECISIONS_PER_TURN = 1
        private const val MIN_FACT_LENGTH = 8
        private const val MAX_FACT_LENGTH = 180
        private val DECISION_MARKERS = listOf(
            "we decided", "we chose", "we picked", "decided to use", "chose to use", "opted for",
            "memutuskan", "memilih", "kita pilih", "kita putuskan", "kami memilih", "better than",
            "instead of", "rather than", "decision was"
        )
        private val TEACH_TERMS = listOf("save this workflow", "remember this workflow", "teach this workflow", "simpan workflow", "pelajari workflow", "jadikan skill")
        private val FAILURE_TERMS = listOf("error", "failed", "timeout", "cancelled", "gagal")
        private val BUILD_FAILURE_PATTERNS = listOf("build failed", "assemble failed", "execution failed", "compilation failed", "failed to build", "error: failed")
        private const val MAX_RECOMMENDATIONS_PER_TURN = 2
        private val SELF_IMPROVEMENT_TOOLS = setOf("memory", "skill", "update_memory", "memory_manage", "skill_view", "skill_manage", "session_search", "update_todo")
        /** Imperative/task phrases that disqualify a sentence from being a durable fact. */
        private val FACT_TASK_VERBS = listOf(
            " fix ", " implement ", " create ", " add ", " write ", " edit ", " update ",
            " delete ", " remove ", " refactor ", " build ", " compile ", " install ", " explain ",
            " show ", " compare ", " generate ", " buat ", " buatkan ", " tulis ", " perbaiki ",
            " tambahkan ", " hapus ", " ubah ", " instal ", " jelaskan ", " bantu ",
            "please fix", "tolong buat", "tolong perbaiki", "can you", "bisa tolong"
        )
        /** Marker phrases that signal a durable fact, grouped by target memory type (longest first). */
        private val DURABLE_FACT_PATTERNS = listOf(
            FactKind.WORKSPACE to listOf(
                "this repository", "this repo", "the repository", "the workspace", "the project",
                "uses gradle", "uses maven", "uses npm", "uses bun", "uses yarn", "package manager",
                "build command", "test command", "build system", "ci uses", "the app is built"
            ),
            FactKind.USER to listOf(
                "please always", "tolong selalu", "i'd prefer", "i would prefer", "i prefer",
                "jangan pernah", "remember that", "ingat bahwa", "call me", "my name is",
                "respond in", "reply in", "my language", "i speak", "panggil aku", "panggil saya",
                "nama saya", "saya lebih suka", "saya suka", "saya biasanya", "i usually",
                "prefer to", "i like", "i love", "i work at", "my email", "i am a", "i am an",
                "bahasa indonesia", "bahasa inggris", "preferens", "selalu", "always ", "never "
            )
        )
    }
}
