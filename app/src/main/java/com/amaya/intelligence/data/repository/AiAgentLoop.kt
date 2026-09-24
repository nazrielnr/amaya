package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.*
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.UiMessage
import com.amaya.intelligence.domain.models.conversationEvent
import com.amaya.intelligence.domain.models.conversationEventProviderContent
import com.amaya.intelligence.tools.ClarificationRequest
import com.amaya.intelligence.tools.ConfirmationRequest
import com.amaya.intelligence.tools.ToolResult
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import com.amaya.intelligence.util.StreamDebugLog

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Conservative detection of a user correction: the message says the previous outcome was wrong.
 * Short and imperative only, so normal follow-ups ("jangan lupa…", "tolong buat…") never match.
 */
internal fun isUserCorrectionMessage(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length !in 2..220) return false
    if ('?' in trimmed || '\n' in trimmed) return false
    val lower = trimmed.lowercase()
    if (CORRECTION_MARKERS.none { lower.startsWith(it) || it in lower }) return false
    // A task request that merely contains a marker word is not a correction of past output.
    return CORRECTION_TASK_VERBS.none { it in lower }
}

private val CORRECTION_MARKERS = listOf(
    "bukan begitu", "bukan gitu", "bukan seperti itu", "bukan itu", "itu bukan", "tidak seperti itu",
    "jangan begitu", "jangan gitu", "jangan seperti itu", "kamu salah", "anda salah", "itu salah",
    "salah, ", "salah!", "salah.", "that's wrong", "that is wrong", "that was wrong", "not like that",
    "wrong, ", "wrong!", "wrong.", "you got it wrong", "no, i meant", "no that's not", "no, that's not",
    "that's not what", "that is not what", "salah besar", "salah total"
)

private val CORRECTION_TASK_VERBS = listOf(
    "buat", "buatkan", "tulis", "perbaiki", "implement", "create", "add ", "write ", "fix ",
    "refactor", "jangan lupa", "jangan lupa untuk", "please", "tolong"
)

internal fun queuedConversationEventMessage(event: UiMessage): ChatMessage? {
    if (event.role != MessageRole.SYSTEM) return null
    return ChatMessage(
        MessageRole.SYSTEM,
        event.conversationEventProviderContent() ?: event.content
    )
}

internal fun AiRepository.chatImpl(
        message: String,
        userImages: List<ChatImage> = emptyList(),
        conversationHistory: List<ChatMessage> = emptyList(),
        projectId: Long? = null,
        workspacePath: String? = null,
        assistantMode: AssistantMode = AssistantMode.forWorkspace(workspacePath),
        ownerId: String? = null,
        agentId: Long? = null,
        conversationId: Long? = null,
        connectionId: String? = null,
        selectedModel: String? = null,
        effort: ThinkingEffort? = null,
        runtimeTarget: AgentRuntimeTarget = AgentRuntimeTarget.LOCAL,
        onConfirmation: suspend (ConfirmationRequest) -> Boolean = { false },
        onClarification: suspend (ClarificationRequest) -> String? = { null },
        pendingConversationEvents: suspend () -> List<UiMessage> = { emptyList() },
        onConversationEventsInjected: suspend (List<UiMessage>) -> Unit = {},
        messageRole: MessageRole = MessageRole.USER
    ): Flow<AgentEvent> = channelFlow {

        val settings = settingsManager.getSettings()

        val activeSelection = settings.activeSelection
        val resolvedConnectionId = connectionId ?: activeSelection?.connectionId
        val initialConnection = settings.connections.firstOrNull { it.id == resolvedConnectionId }

        if (initialConnection == null) {
            send(AgentEvent.Error("No model selected. Open Settings → Manage Models and select a model.", retryable = false))
            return@channelFlow
        }
        var connection: ProviderConnection = initialConnection

        var model = selectedModel?.takeIf { it.isNotBlank() }
            ?: activeSelection?.takeIf { it.connectionId == connection.id }?.modelId
            ?: ""
        if (model.isBlank() || connection.visibleModels.none { it.id == model && it.enabled }) {
            send(AgentEvent.Error("The selected model is unavailable. Open Settings → Manage Models and select another model.", retryable = false))
            return@channelFlow
        }
        var provider = resolveProvider(connection)
        var modelConfig = connection.visibleModels.first { it.id == model }
        var maxOutputTokens = modelConfig.maxOutputTokens?.coerceIn(256, 32_768) ?: AiRepository.DEFAULT_MAX_OUTPUT_TOKENS
        var providerContextWindowTokens = modelConfig.contextWindowTokens?.coerceAtLeast(maxOutputTokens + 1) ?: 32_768
        var maxInputTokens = modelConfig.maxInputTokens
            ?.coerceIn(1, providerContextWindowTokens - maxOutputTokens)
            ?: providerContextWindowTokens - maxOutputTokens
        var contextWindowTokens = minOf(providerContextWindowTokens, maxInputTokens + maxOutputTokens)
        if (userImages.isNotEmpty() && !modelConfig.supportsImages) {
            send(AgentEvent.Error("The selected model does not support image input.", retryable = false))
            return@channelFlow
        }
        debugLog("AiRepository") { "chat() resolved connection=${connection.id}, model=$model" }

        val sessionId = conversationId?.toString() ?: "session_${UUID.randomUUID()}"
        // Learn from user corrections: a follow-up that says the previous outcome was wrong marks
        // the previous turn's workflow evidence as failed and proposes a correction lesson. Fired
        // off the hot path — it never blocks the current turn.
        if (runtimeTarget == AgentRuntimeTarget.LOCAL && isUserCorrectionMessage(message)) {
            repoScope.launch {
                runCatching { selfImprovementPipeline.recordUserCorrection(sessionId, message, workspacePath) }
                    .onFailure { errorLog("AiRepository", "Failed to record user correction", it) }
            }
        }
        val workspaceId = workspacePath?.takeIf(String::isNotBlank)?.let { workspaceMemoryStore.resolve(it)?.id }
        val completedUserMessages = mutableListOf(message)
        val completedAssistantMessages = mutableListOf<String>()
        val completedToolCalls = mutableListOf<String>()
        val completedToolResults = mutableListOf<String>()
        val viewedSkills = linkedSetOf<String>()
        if (runtimeTarget == AgentRuntimeTarget.LOCAL) {
            repoScope.launch {
                runCatching {
                    sessionMemoryRepository.saveMessage(SessionMessage(sessionId = sessionId, role = "user", content = message, workspacePath = workspacePath, workspaceId = workspaceId, assistantMode = assistantMode.name, ownerId = ownerId))
                }.onFailure { errorLog("AiRepository", "Failed to save user session message", it) }
            }
        }

        val agentGroup = if (assistantMode == AssistantMode.AGENT) {
            ownerId?.toLongOrNull()?.let { agentDao.getGroupById(it) }
        } else null
        if (assistantMode == AssistantMode.AGENT && agentGroup == null) {
            send(AgentEvent.Error("The active agent group no longer exists. Open AI Agents and select a group.", retryable = false))
            return@channelFlow
        }
        val activeAgent = if (assistantMode == AssistantMode.AGENT) {
            agentId?.let { agentDao.getById(it) }?.takeIf { it.groupId == agentGroup?.id }
        } else null
        if (assistantMode == AssistantMode.AGENT && activeAgent == null) {
            send(AgentEvent.Error("The selected agent is missing or does not belong to the active group. Select the agent again.", retryable = false))
            return@channelFlow
        }
        val agentCapabilityProfile = activeAgent?.capabilityProfile?.let(com.amaya.intelligence.domain.models.AgentCapabilityProfile::decode)
        val agentGroupMembers = agentGroup?.let { agentDao.getByGroup(it.id) }.orEmpty()
        val delegationMembers = agentGroupMembers.filter { it.id != activeAgent?.id }
        val tools = if (modelConfig.supportsTools) {
            withContext(Dispatchers.Default) {
                buildToolDefinitions(
                    runtimeTarget,
                    assistantMode,
                    workspacePath != null,
                    agentCapabilityProfile,
                    delegationMembers.map { it.localId }
                )
            }
        } else emptyList()
        // A host/system continuation consumes already-completed delegation events. It must never
        // create a second delegation while replaying that batch, even if the provider ignores the
        // prompt rule and emits a stale delegate_agent call.
        val requestTools = if (messageRole == MessageRole.SYSTEM) {
            tools.filterNot { it.name == "delegate_agent" }
        } else tools
        val toolSchemaTokens = withContext(Dispatchers.Default) { TokenEstimator.toolSchemas(requestTools) }
        val agentMemoryContext = activeAgent?.let { agent ->
            agentMemoryRepository.list(agent.id, query = message, limit = 8).takeIf(List<com.amaya.intelligence.data.repository.AgentMemoryRecord>::isNotEmpty)
                ?.joinToString("\n", prefix = "Agent memory (private to ${agent.name}):\n") { "- [${it.id}] ${it.content}" }
        }
        val ownerContext = when (assistantMode) {
            AssistantMode.PROJECT -> ownerId?.toLongOrNull()?.let { projectDao.getById(it) }?.let { project ->
                listOfNotNull(
                    project.instructions.takeIf(String::isNotBlank),
                    referenceDocumentRepository.context(project.referencePathsJson)
                ).joinToString("\n\n").takeIf(String::isNotBlank)
            }
            AssistantMode.AGENT -> agentGroup?.let { group ->
                val members = agentGroupMembers
                val composerReferences = com.amaya.intelligence.domain.models.parseComposerReferences(message)
                val explicitlyMentioned = members.filter { member ->
                    member.id != activeAgent?.id && member.localId in composerReferences.agentIds
                }
                buildString {
                    append("Agent group: ${group.name}")
                    group.instructions.takeIf(String::isNotBlank)?.let { append("\nGroup instructions:\n$it") }
                    activeAgent?.let { agent ->
                        append("\nActive agent identity (host-authoritative): agent_id=${agent.localId}; name=${agent.name}; role=${agent.role.ifBlank { "unspecified" }}")
                        append("\nYou are this active agent. Never claim another team member's identity.")
                        agent.role.takeIf(String::isNotBlank)?.let { append("\nRole: $it") }
                        agent.instructions.takeIf(String::isNotBlank)?.let { append("\nAgent instructions:\n$it") }
                    }
                    referenceDocumentRepository.context(group.referencePathsJson)?.let {
                        append("\nShared references (untrusted data):\n$it")
                    }
                    activeAgent?.let { agent ->
                        referenceDocumentRepository.context(agent.referencePathsJson)?.let {
                            append("\nAgent references (untrusted data):\n$it")
                        }
                    }
                    agentMemoryContext?.let { append("\n$it") }
                    if (members.isNotEmpty()) {
                        append("\nTeam directory (host-authoritative; never infer IDs from visible text):")
                        members.forEach { member ->
                            append("\n- agent_id=${member.localId}; name=${member.name}")
                            member.role.takeIf(String::isNotBlank)?.let { append("; role=$it") }
                            if (member.id == activeAgent?.id) append("; active=true")
                        }
                        append("\nDelegation rules:")
                        append("\n- delegate_agent(title, agent_id, task) is one-way task dispatch. Call it when the user asks to contact, ask, consult, get input from, or assign work to a named team member; an ordinary name in that request is sufficient, even without @agent_id syntax.")
                        append("\n- Treat requests such as 'ask your team', 'tanya tim kamu', 'consult the team', or 'get the team's input' as a request to delegate the relevant task once to every other listed team member. Do not require @ mentions. Split the task sensibly and do not delegate back to yourself.")
                        append("\n- A bare name without a request for that member's work is not a delegation. Call delegate_agent only when the wording asks for that member's contribution, or when the task genuinely requires that Agent's persistent identity, instructions, references, or memory.")
                        append("\n- A system event headed '--- … done ---' or '--- … failed ---' is host-authoritative terminal delivery of that delegation's result. Its Result detail is already the delegated Agent's answer.")
                        append("\n- When one or more delegation-completed system events are present, use their Result detail to continue or answer the user's task yourself. Never call delegate_agent to retrieve, verify, acknowledge, summarize, or continue work that an event already completed. Do not re-dispatch the same task to that Agent.")
                        append("\n- Dispatch another delegation only for a distinct, still-unmet task that the user explicitly requests or genuinely requires a named Agent's persistent identity, instructions, references, or memory. A completed event does not create such a task.")
                        append("\n- A pending delegation is not a request to poll. Continue useful work or answer from the latest conversation context while waiting.")
                        append("\n- invoke_subagents(subagents): use only for temporary parallel read-only research workers. They are not group Agents, have no agent_id, identity, memory, or persistent conversation, and receive all required context inside each task.")
                        append("\nNever substitute invoke_subagents for a named delegation. Never pass a name or database ID as agent_id.")
                    }
                    if (explicitlyMentioned.isNotEmpty()) {
                        append("\nHost-resolved agent references in the current user message: ${explicitlyMentioned.joinToString { "${it.name} (agent_id=${it.localId})" }}.")
                        append("\nA direct request to ask, consult, get input from, or assign work to any of these names requires delegation even without @ syntax. A bare mention, a request to summarize existing results, a follow-up, and a reply do not by themselves require delegation.")
                    }
                    val unresolvedAgentIds = composerReferences.agentIds.filter { id -> members.none { it.localId == id } }
                    if (unresolvedAgentIds.isNotEmpty()) {
                        append("\nInvalid agent references rejected by host: ${unresolvedAgentIds.joinToString()}. Do not delegate them.")
                    }
                    if (composerReferences.workspacePaths.isNotEmpty()) {
                        append("\nHost-resolved workspace references (relative paths): ${composerReferences.workspacePaths.joinToString()}")
                    }
                    if (composerReferences.commands.isNotEmpty()) {
                        append("\nComposer commands: ${composerReferences.commands.joinToString { "/$it" }}")
                    }
                }
            }
            AssistantMode.CHAT -> null
        }
        val contextRequest = ContextBuildRequest(
            userMessage = message,
            conversationHistory = conversationHistory,
            workspacePath = workspacePath,
            conversationId = conversationId,
            maxOutputTokens = maxOutputTokens,
            contextWindowTokens = contextWindowTokens,
            toolSchemaTokens = toolSchemaTokens,
            userImages = userImages,
            assistantMode = assistantMode,
            ownerId = ownerId,
            ownerContext = ownerContext,
            userMessageRole = messageRole
        )
        val managedContext = if (runtimeTarget == AgentRuntimeTarget.WINDOWS_BRIDGE) {
            withContext(Dispatchers.Default) { contextManager.buildWindowsBridgeContext(contextRequest) }
        } else {
            withContext(Dispatchers.Default) { contextManager.buildContext(contextRequest) }
        }
        val allowedToolNames = requestTools.map { it.name }.toSet()

        var messages = managedContext.messages
        val conversationGoal = conversationGoal(messages, message)
        val ledgerBudgetTokens = (minOf(maxInputTokens, contextWindowTokens - maxOutputTokens) * AiRepository.LEDGER_BUDGET_FRACTION)
            .toInt()
            .coerceIn(AiRepository.LEDGER_MIN_TOKENS, AiRepository.LEDGER_MAX_TOKENS)
        val ledgerEpoch = ledgerStore.epoch(sessionId)
        val cachedLedger = ledgerStore.get(sessionId)
        var ledger: TaskLedger? = cachedLedger?.ledger
            ?: managedContext.autoSummary?.let { parseRenderedLedger(it, conversationGoal) }
        var ledgerCoverage = cachedLedger?.coveredThrough ?: 0
        var compactedThisTurn = false
        var compactionEventSent = false
        val costCache = CostCache()
        val basePrompt = managedContext.baseSystemPrompt
        val manualSummary = managedContext.manualSummary
        var autoSummary: String? = ledger?.render() ?: managedContext.autoSummary
        var persistedLedgerText: String? = managedContext.autoSummary
        // Scheme E: host-pinned plan mirroring the visible todo list (survives compaction).
        // Declared before composeWithPlan because the local function captures it.
        var taskPlan: TaskPlan = TaskPlan(emptyList())
        fun composeWithPlan(): String {
            val composed = composeSystemPrompt(basePrompt, manualSummary, autoSummary, ledgerBudgetTokens)
            return if (taskPlan.steps.isEmpty()) composed else "$composed\n\n${taskPlan.renderSection()}"
        }
        var systemPromptWithAutoContext = composeWithPlan()

        var continueLoop = true
        var iterations = 0
        var browserTaskStarted = false
        var terminalError = false
        var streamContinuations = 0
        /** Cumulative rejected tool calls across iterations; capped at [MAX_FAILED_TOOL_ATTEMPTS]. */
        var failedToolAttempts = 0
        // Per-tool repeated-failure tracking for self-correction warnings (browser + any tool).
        val lastToolErrorSignature = mutableMapOf<String, String>()
        val repeatedToolErrors = mutableMapOf<String, Int>()
        val lastExecutedCallSignature = mutableMapOf<String, String>()
        val repeatedIdenticalCallCount = mutableMapOf<String, Int>()
        val callSignatureFrequency = mutableMapOf<String, Int>()
        val invalidToolArgumentErrors = mutableMapOf<String, Throwable>()
        // Scheme C: how many tool calls actually executed this turn + how many verification
        // passes have run (bounded by MAX_VERIFICATION_PASSES).
        var executedToolCalls = 0
        var verificationPasses = 0
        /** Scheme C: the verification pass is host-internal meta-commentary ("VERIFIED — evidence: …").
         *  While true, the next provider reply is not streamed to the user nor persisted as the
         *  answer, so the model's real final text stays the visible output. */
        var verificationActive = false
        /** Last successful tool result of the turn, surfaced as the final answer when the model
         *  packs its whole reply into a tool call (e.g. saving it to memory) and emits no text. */
        var lastToolResultContent: String? = null

        while (continueLoop) {
            iterations++
            val queuedEvents = pendingConversationEvents()
            if (queuedEvents.isNotEmpty()) {
                messages = messages + queuedEvents.mapNotNull(::queuedConversationEventMessage)
                onConversationEventsInjected(queuedEvents)
                StreamDebugLog.event(conversationId, null, "EVENTS_INJECTED", "iteration=$iterations count=${queuedEvents.size}")
            }
            StreamDebugLog.event(conversationId, null, "ITERATION_START", "iteration=$iterations history=${messages.size}")

            if (iterations > 1) send(AgentEvent.NewIteration)

            val requestInputBudget = minOf(maxInputTokens, contextWindowTokens - maxOutputTokens)
            var systemPromptTokens = TokenEstimator.text(systemPromptWithAutoContext)
            var historyBudget = requestInputBudget - toolSchemaTokens - AiRepository.CONTEXT_SAFETY_RESERVE_TOKENS - systemPromptTokens
            if (historyBudget <= 0) {
                send(AgentEvent.Error("Selected model context window is too small for its instructions and tools", retryable = false))
                terminalError = true
                break
            }
            var plan = withContext(Dispatchers.Default) { planContextWindow(messages, historyBudget, cache = costCache) }
            var passes = 0
            var budgetExhausted = false
            while (plan.evictionBoundary > ledgerCoverage && passes < AiRepository.MAX_COMPACTION_PASSES) {
                passes++
                val newlyEvicted = messages
                    .subList(ledgerCoverage, plan.evictionBoundary)
                    .filterNot { it === plan.pinnedAnchor }
                if (newlyEvicted.isEmpty()) {
                    ledgerCoverage = plan.evictionBoundary
                    break
                }
                val evictedToolResults = newlyEvicted.count { it.toolResult != null }
                val reclaimable = withContext(Dispatchers.Default) { TokenEstimator.messages(newlyEvicted, cache = costCache) }
                val worthModelCall = passes == 1 && !compactedThisTurn &&
                    reclaimable >= AiRepository.COMPACTION_MIN_RECLAIM_TOKENS
                val ledgerBeforeEviction = ledger
                if (worthModelCall) {
                    compactedThisTurn = true
                    send(AgentEvent.Compacting(newlyEvicted.size, reclaimable))
                }
                // Never hold the user's turn behind a second provider request. Continue immediately
                // with the deterministic ledger; a model can refine the same snapshot in background.
                ledger = mechanicalLedger(ledgerBeforeEviction, conversationGoal, newlyEvicted, evictedToolResults)
                ledgerCoverage = plan.evictionBoundary
                ledgerStore.put(sessionId, ledger, coveredThrough = ledgerCoverage, epoch = ledgerEpoch)
                autoSummary = ledger.render()
                if (!compactionEventSent) {
                    compactionEventSent = true
                    send(AgentEvent.Compacted(
                        ledger = autoSummary.orEmpty(),
                        evictedMessages = newlyEvicted.size,
                        reclaimedTokens = reclaimable,
                        usedFallback = true
                    ))
                }
                if (worthModelCall) {
                    val refinementCoverage = ledgerCoverage
                    repoScope.launch {
                        val refined = runCatching {
                            kotlinx.coroutines.withTimeoutOrNull(AiRepository.COMPACTION_TIMEOUT_MS) {
                                updateLedger(
                                    provider = provider,
                                    connection = connection,
                                    model = model,
                                    current = ledgerBeforeEviction,
                                    goal = conversationGoal,
                                    evicted = newlyEvicted,
                                    evictedToolResults = evictedToolResults,
                                    maxInputTokens = maxInputTokens
                                )
                            }
                        }.onFailure { errorLog("AiRepository", "Background ledger refinement failed", it) }.getOrNull()
                        if (refined != null && ledgerStore.put(sessionId, refined, refinementCoverage, ledgerEpoch)) {
                            StreamDebugLog.event(conversationId, null, "COMPACTION_REFINED", "coverage=$refinementCoverage")
                            runCatching {
                                sessionMemoryRepository.saveSummary(
                                    SessionSummary(
                                        sessionId = "$sessionId$AUTO_COMPACTION_SUMMARY_SUFFIX",
                                        summary = refined.render(),
                                        tags = listOf("auto_compacted"),
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        workspacePath = workspacePath,
                                        workspaceId = workspaceId,
                                        assistantMode = assistantMode.name,
                                        ownerId = ownerId
                                    )
                                )
                            }.onFailure { errorLog("AiRepository", "Failed to persist refined context", it) }
                        }
                    }
                }
                if (runtimeTarget == AgentRuntimeTarget.LOCAL && autoSummary != persistedLedgerText) {
                    val snapshot = autoSummary.orEmpty()
                    persistedLedgerText = snapshot
                    repoScope.launch {
                        runCatching {
                            sessionMemoryRepository.saveSummary(
                                SessionSummary(
                                    sessionId = "$sessionId$AUTO_COMPACTION_SUMMARY_SUFFIX",
                                    summary = snapshot,
                                    tags = listOf("auto_compacted"),
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    workspacePath = workspacePath,
                                    workspaceId = workspaceId,
                                    assistantMode = assistantMode.name,
                                    ownerId = ownerId
                                )
                            )
                        }.onFailure { errorLog("AiRepository", "Failed to persist auto-compacted context", it) }
                    }
                }

                systemPromptWithAutoContext = composeWithPlan()
                systemPromptTokens = TokenEstimator.text(systemPromptWithAutoContext)
                historyBudget = requestInputBudget - toolSchemaTokens - AiRepository.CONTEXT_SAFETY_RESERVE_TOKENS - systemPromptTokens
                if (historyBudget <= 0) {
                    send(AgentEvent.Error("Selected model context window is too small for its instructions and tools", retryable = false))
                    terminalError = true
                    budgetExhausted = true
                    break
                }
                plan = withContext(Dispatchers.Default) { planContextWindow(messages, historyBudget, cache = costCache) }
            }
            if (budgetExhausted) break
            if (messages.isNotEmpty() && plan.messages.isEmpty()) {
                send(AgentEvent.Error("The latest user input or required tool-call metadata exceeds the selected model context window", retryable = false))
                terminalError = true
                break
            }
            debugLog("AiRepository") {
                "context iteration=$iterations window=$contextWindowTokens input=$requestInputBudget system=$systemPromptTokens tools=$toolSchemaTokens history=${plan.usedTokens} output=$maxOutputTokens evicted=${plan.evicted.size} truncated=${plan.truncatedOriginals.size} compacted=$compactedThisTurn"
            }
            val requestMessages = plan.messages
            if (!hasProviderUserQuery(requestMessages)) {
                send(AgentEvent.Error("No user query remains in the request context", retryable = false))
                terminalError = true
                break
            }
            val request = ChatRequest(
                model        = model,
                messages     = requestMessages,
                systemPrompt = systemPromptWithAutoContext,
                tools        = requestTools,
                maxTokens    = maxOutputTokens,
                stream       = true,
                connectionId = connection.id,
                sessionId    = sessionId,
                providerId   = connection.providerId,
                effort       = effort
            )

            var textBuffer = StringBuilder()
            val toolCalls = mutableListOf<ToolCallMessage>()
            val responseItems = mutableListOf<String>()
            var hasToolCalls = false
            var providerTerminal = false
            var retryableFailure: String? = null
            /** Provider could not parse a model tool call's arguments; needs failure feedback. */
            var providerParseFailure: String? = null
            /** Tool calls rejected in this request that still need failure feedback to the model. */
            val rejectedToolCalls = mutableListOf<ToolCallMessage>()
            // A verification reply is host meta-commentary, not the answer. Capture the flag for
            // this request only, then clear it so the next real answer streams normally.
            val suppressUserStreaming = verificationActive
            verificationActive = false

            provider.chat(request).withInactivityTimeout().collect { response ->
                if (providerTerminal) {
                    // Some providers flush buffered events after the terminal one (Done/Incomplete/
                    // Error). They are stale by definition — ignore them instead of failing the whole
                    // turn with "Provider emitted an event after its terminal event".
                    StreamDebugLog.event(conversationId, null, "POST_TERMINAL_IGNORED", response.javaClass.simpleName)
                    return@collect
                }
                when (response) {
                    is ChatResponse.TextDelta -> {
                        textBuffer.append(response.text)
                        StreamDebugLog.event(conversationId, null, "TEXT_DELTA", "chars=${response.text.length} total=${textBuffer.length}")
                        if (!suppressUserStreaming) {
                            send(AgentEvent.TextDelta(response.text))
                        }
                    }

                    is ChatResponse.ThinkingDelta -> {
                        send(AgentEvent.ThinkingDelta(response.text))
                    }

                    is ChatResponse.ToolCall -> {
                        val sanitizedName = sanitizeToolName(response.name, allowedToolNames)
                        StreamDebugLog.event(conversationId, null, "TOOL_CALL", "id=${response.id} rawName=${response.name} name=$sanitizedName")
                        val rawId = response.id.ifBlank { "synth_call_${System.currentTimeMillis()}" }
                        val isDuplicateId = rawId in toolCalls.map { it.id }.toSet()
                        val callId = if (isDuplicateId) "${rawId}_dup_${System.currentTimeMillis()}" else rawId
                        val isRecognized = sanitizedName in allowedToolNames

                        if (!isRecognized) {
                            failedToolAttempts++
                            val available = allowedToolNames.take(16).joinToString()
                            invalidToolArgumentErrors[callId] = IllegalArgumentException(
                                "Tool '${response.name}' is not recognized or available in this environment. Available tools: $available. Please select from available tools or answer the user directly."
                            )
                            StreamDebugLog.event(conversationId, null, "TOOL_CALL_UNRECOGNIZED", "name=${response.name} attempts=$failedToolAttempts")
                        } else if (isDuplicateId) {
                            failedToolAttempts++
                            invalidToolArgumentErrors[callId] = IllegalArgumentException(
                                "Duplicate tool call ID detected for '$sanitizedName'. Each tool invocation must provide a unique call ID."
                            )
                            StreamDebugLog.event(conversationId, null, "TOOL_CALL_DUPLICATE_ID", "name=$sanitizedName attempts=$failedToolAttempts")
                        } else {
                            val validation = validateToolArguments(sanitizedName, response.arguments, tools)
                            validation.onFailure { error ->
                                invalidToolArgumentErrors[callId] = error
                            }
                        }

                        if (failedToolAttempts >= MAX_FAILED_TOOL_ATTEMPTS) {
                            send(AgentEvent.Error(
                                "The model issued $failedToolAttempts consecutive invalid tool calls (last: ${response.name}); stopping the tool loop after $MAX_FAILED_TOOL_ATTEMPTS failures.",
                                retryable = false
                            ))
                            terminalError = true
                            providerTerminal = true
                            continueLoop = false
                            return@collect
                        }

                        val resolvedArguments = if (!isRecognized || isDuplicateId) {
                            response.arguments
                        } else {
                            validateToolArguments(sanitizedName, response.arguments, tools).getOrElse { response.arguments }
                        }

                        hasToolCalls = true
                        send(AgentEvent.ToolCallStart(callId, sanitizedName, resolvedArguments, response.metadata))

                        toolCalls.add(ToolCallMessage(
                            id = callId,
                            name = sanitizedName,
                            arguments = resolvedArguments,
                            metadata = response.metadata
                        ))
                    }

                    is ChatResponse.ResponseItem -> {
                        responseItems.add(response.json)
                        send(AgentEvent.ResponseItem(response.json))
                    }

                    is ChatResponse.Done -> {
                        StreamDebugLog.event(conversationId, null, "PROVIDER_DONE", "toolCalls=${toolCalls.size} textChars=${textBuffer.length}")
                        providerTerminal = true
                        response.usage?.let { usage ->
                            send(AgentEvent.Usage(usage.inputTokens, usage.outputTokens))
                        }
                    }

                    is ChatResponse.Incomplete -> {
                        StreamDebugLog.event(conversationId, null, "PROVIDER_INCOMPLETE", response.reason)
                        providerTerminal = true
                        retryableFailure = response.reason.takeIf { canContinueStream(response, hasToolCalls) }
                        if (retryableFailure == null && !shouldExecuteReceivedToolCalls(response, hasToolCalls)) {
                            send(AgentEvent.Incomplete(response.reason, response.retryable))
                            terminalError = true
                            continueLoop = false
                        }
                    }

                    is ChatResponse.Error -> {
                        StreamDebugLog.event(conversationId, null, "PROVIDER_ERROR", response.message)
                        providerTerminal = true
                        if (response.message.startsWith(INVALID_TOOL_ARGUMENTS_PREFIX)) {
                            // The model emitted tool-call arguments the provider could not parse.
                            // That is a recoverable call failure, not a terminal error: record it
                            // and feed it back so the model can re-issue the call, bounded by
                            // MAX_FAILED_TOOL_ATTEMPTS so a pathological model cannot loop forever.
                            failedToolAttempts++
                            providerParseFailure = response.message
                            StreamDebugLog.event(conversationId, null, "TOOL_ARGS_PARSE_FAILED", "attempts=$failedToolAttempts")
                            if (failedToolAttempts >= MAX_FAILED_TOOL_ATTEMPTS) {
                                send(AgentEvent.Error(
                                    "The model issued $failedToolAttempts tool calls with unparseable arguments (last: ${response.message.take(120)}); stopping the tool loop after $MAX_FAILED_TOOL_ATTEMPTS failures.",
                                    retryable = false
                                ))
                                terminalError = true
                                continueLoop = false
                            }
                            return@collect
                        }

                        val errorCategory = classifyError(
                            message = response.message,
                            code = response.code,
                            isEmptyResponse = false
                        )

                        // 1. Auth error (API key salah): berhenti dan minta user periksa settings
                        if (errorCategory == ErrorCategory.AUTH_ERROR) {
                            send(AgentEvent.Error(
                                message = "API key salah atau belum diatur untuk provider '${connection.name.ifBlank { connection.providerId }}'. Silakan periksa di Settings → Manage Models.",
                                retryable = false
                            ))
                            terminalError = true
                            continueLoop = false
                            return@collect
                        }

                        // 2. Model error: coba beralih ke model cadangan
                        if (errorCategory == ErrorCategory.MODEL_ERROR) {
                            val fallbackCandidate = findFallbackCandidate(
                                currentConnectionId = connection.id,
                                currentModelId = model,
                                requiresImages = userImages.isNotEmpty(),
                                requiresTools = tools.isNotEmpty(),
                                connections = settings.connections,
                                hasApiKey = { connId -> settingsManager.getConnectionApiKey(connId).isNotBlank() }
                            )
                            if (fallbackCandidate != null) {
                                val (fallbackConn, fallbackMod) = fallbackCandidate
                                send(AgentEvent.TextDelta("\n\n*[Model error pada '$model': ${response.message}. Beralih ke model cadangan: ${fallbackMod.displayName.ifBlank { fallbackMod.id }}]*\n\n"))
                                StreamDebugLog.event(conversationId, null, "MODEL_FALLBACK", "from=$model to=${fallbackMod.id}")
                                connection = fallbackConn
                                model = fallbackMod.id
                                modelConfig = fallbackMod
                                provider = resolveProvider(fallbackConn)
                                maxOutputTokens = fallbackMod.maxOutputTokens?.coerceIn(256, 32_768) ?: AiRepository.DEFAULT_MAX_OUTPUT_TOKENS
                                providerContextWindowTokens = fallbackMod.contextWindowTokens?.coerceAtLeast(maxOutputTokens + 1) ?: 32_768
                                maxInputTokens = fallbackMod.maxInputTokens?.coerceIn(1, providerContextWindowTokens - maxOutputTokens) ?: (providerContextWindowTokens - maxOutputTokens)
                                contextWindowTokens = minOf(providerContextWindowTokens, maxInputTokens + maxOutputTokens)
                                streamContinuations = 0
                                textBuffer.clear()
                                return@collect
                            }
                        }

                        retryableFailure = response.message.takeIf { canContinueStream(response, hasToolCalls) }
                        if (retryableFailure == null && !shouldExecuteReceivedToolCalls(response, hasToolCalls)) {
                            // Non-retryable error and no tool calls: attempt fallback before failing
                            val fallbackCandidate = findFallbackCandidate(
                                currentConnectionId = connection.id,
                                currentModelId = model,
                                requiresImages = userImages.isNotEmpty(),
                                requiresTools = tools.isNotEmpty(),
                                connections = settings.connections,
                                hasApiKey = { connId -> settingsManager.getConnectionApiKey(connId).isNotBlank() }
                            )
                            if (fallbackCandidate != null) {
                                val (fallbackConn, fallbackMod) = fallbackCandidate
                                send(AgentEvent.TextDelta("\n\n*[Kendala pada model '$model': ${response.message}. Beralih ke model cadangan: ${fallbackMod.displayName.ifBlank { fallbackMod.id }}]*\n\n"))
                                StreamDebugLog.event(conversationId, null, "MODEL_FALLBACK", "from=$model to=${fallbackMod.id}")
                                connection = fallbackConn
                                model = fallbackMod.id
                                modelConfig = fallbackMod
                                provider = resolveProvider(fallbackConn)
                                maxOutputTokens = fallbackMod.maxOutputTokens?.coerceIn(256, 32_768) ?: AiRepository.DEFAULT_MAX_OUTPUT_TOKENS
                                providerContextWindowTokens = fallbackMod.contextWindowTokens?.coerceAtLeast(maxOutputTokens + 1) ?: 32_768
                                maxInputTokens = fallbackMod.maxInputTokens?.coerceIn(1, providerContextWindowTokens - maxOutputTokens) ?: (providerContextWindowTokens - maxOutputTokens)
                                contextWindowTokens = minOf(providerContextWindowTokens, maxInputTokens + maxOutputTokens)
                                streamContinuations = 0
                                textBuffer.clear()
                                return@collect
                            }

                            send(AgentEvent.Error(response.message, response.retryable))
                            terminalError = true
                            continueLoop = false
                        }
                    }
                }
            }

            if (terminalError) break

            // Feed rejected tool calls back as an explicit failure so the model can correct its
            // next call instead of the loop silently ending (previously a hard turn-terminating
            // error on the first mis-call).
            if (rejectedToolCalls.isNotEmpty() && !hasToolCalls) {
                if (textBuffer.isNotBlank()) {
                    messages = messages + ChatMessage(role = MessageRole.ASSISTANT, content = textBuffer.toString())
                }
                val lastRejected = rejectedToolCalls.last()
                val available = allowedToolNames.take(16).joinToString()
                messages = messages + ChatMessage(
                    role = MessageRole.USER,
                    content = "Host tool-loop feedback: the previous response called '${lastRejected.name}' but the host rejected it (blank call ID, unadvertised tool, or duplicate ID). Available tools: $available. Fix the call and retry, or answer directly. (Failure $failedToolAttempts/$MAX_FAILED_TOOL_ATTEMPTS.)"
                )
                StreamDebugLog.event(conversationId, null, "TOOL_CALL_REJECTED_FEEDBACK", "attempts=$failedToolAttempts name=${lastRejected.name}")
                continue
            }

            // Unparseable tool-call arguments must not kill the turn either: append the parse
            // failure as user feedback so the next request re-issues the call correctly (bounded
            // by MAX_FAILED_TOOL_ATTEMPTS). When valid tool calls were also received they are
            // executed below, and this feedback still lets the model correct the malformed one
            // on the next iteration.
            if (providerParseFailure != null) {
                if (textBuffer.isNotBlank()) {
                    messages = messages + ChatMessage(role = MessageRole.ASSISTANT, content = textBuffer.toString())
                }
                messages = messages + ChatMessage(
                    role = MessageRole.USER,
                    content = "Host tool-loop feedback: the provider could not parse the arguments of the tool call in the previous response: $providerParseFailure. Re-issue the tool call with valid JSON arguments (quote all keys and string values, no trailing commas) or answer directly. (Failure $failedToolAttempts/$MAX_FAILED_TOOL_ATTEMPTS.)"
                )
                StreamDebugLog.event(conversationId, null, "TOOL_ARGS_PARSE_FAILED_FEEDBACK", "attempts=$failedToolAttempts")
                if (!hasToolCalls) continue
            }

            if (!providerTerminal && !hasToolCalls) retryableFailure = "Provider stream ended without a terminal event"
            if (providerTerminal && !hasToolCalls && textBuffer.isBlank() && responseItemOutputText(responseItems).isNullOrBlank()) {
                retryableFailure = "Empty response: Provider completed without output text or tool calls"
            }

            if (retryableFailure != null) {
                val errorCategory = classifyError(
                    message = retryableFailure!!,
                    code = null,
                    isEmptyResponse = (textBuffer.isBlank() && !hasToolCalls)
                )

                if (streamContinuations >= MAX_STREAM_CONTINUATIONS) {
                    val fallbackCandidate = findFallbackCandidate(
                        currentConnectionId = connection.id,
                        currentModelId = model,
                        requiresImages = userImages.isNotEmpty(),
                        requiresTools = tools.isNotEmpty(),
                        connections = settings.connections,
                        hasApiKey = { connId -> settingsManager.getConnectionApiKey(connId).isNotBlank() }
                    )
                    if (fallbackCandidate != null) {
                        val (fallbackConn, fallbackMod) = fallbackCandidate
                        send(AgentEvent.TextDelta("\n\n*[Model '$model' gagal setelah $streamContinuations kali percobaan ($retryableFailure). Beralih ke model cadangan: ${fallbackMod.displayName.ifBlank { fallbackMod.id }}]*\n\n"))
                        StreamDebugLog.event(conversationId, null, "MODEL_FALLBACK_AFTER_RETRIES", "from=$model to=${fallbackMod.id}")
                        connection = fallbackConn
                        model = fallbackMod.id
                        modelConfig = fallbackMod
                        provider = resolveProvider(fallbackConn)
                        maxOutputTokens = fallbackMod.maxOutputTokens?.coerceIn(256, 32_768) ?: AiRepository.DEFAULT_MAX_OUTPUT_TOKENS
                        providerContextWindowTokens = fallbackMod.contextWindowTokens?.coerceAtLeast(maxOutputTokens + 1) ?: 32_768
                        maxInputTokens = fallbackMod.maxInputTokens?.coerceIn(1, providerContextWindowTokens - maxOutputTokens) ?: (providerContextWindowTokens - maxOutputTokens)
                        contextWindowTokens = minOf(providerContextWindowTokens, maxInputTokens + maxOutputTokens)
                        streamContinuations = 0
                        textBuffer.clear()
                        continue
                    } else {
                        send(AgentEvent.Incomplete(retryableFailure!!, retryable = true))
                        terminalError = true
                        break
                    }
                }
                streamContinuations++
                val backoffDelay = if (errorCategory == ErrorCategory.RATE_LIMIT) {
                    calculateRateLimitBackoff(streamContinuations)
                } else {
                    calculateBackoffWithJitter(streamContinuations, MAX_STREAM_BACKOFF_MS)
                }
                if (textBuffer.isNotBlank()) {
                    messages = messages + ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = textBuffer.toString()
                    ) + ChatMessage(role = MessageRole.USER, content = STREAM_CONTINUATION_PROMPT)
                }
                delay(backoffDelay)
                continue
            }

            if (textBuffer.isNotBlank()) {
                val assistantText = textBuffer.toString()
                if (!suppressUserStreaming) {
                    completedAssistantMessages.add(assistantText)
                    if (runtimeTarget == AgentRuntimeTarget.LOCAL) {
                        repoScope.launch {
                            runCatching {
                                sessionMemoryRepository.saveMessage(SessionMessage(sessionId = sessionId, role = "assistant", content = assistantText, workspacePath = workspacePath, workspaceId = workspaceId, assistantMode = assistantMode.name, ownerId = ownerId))
                            }.onFailure { errorLog("AiRepository", "Failed to save assistant session message", it) }
                        }
                    }
                }
            }

            if (!hasToolCalls) {
                if (textBuffer.isBlank()) {
                    responseItemOutputText(responseItems)?.let { itemText ->
                        if (!suppressUserStreaming) send(AgentEvent.TextDelta(itemText))
                    }
                }
                // Scheme C: one bounded verification pass when a tool-using turn stops. The model
                // must confirm the goal is fully done with evidence, or continue working. Never
                // runs on plain Q&A (no tools), internal continuations, or bridge turns.
                val shouldVerify = shouldRunVerificationPass(
                    verificationPasses = verificationPasses,
                    maxPasses = MAX_VERIFICATION_PASSES,
                    messageRole = messageRole,
                    runtimeTarget = runtimeTarget,
                    executedToolCalls = executedToolCalls,
                    failedToolAttempts = failedToolAttempts,
                    hasPlanSteps = taskPlan.steps.isNotEmpty()
                )
                if (shouldVerify) {
                    verificationPasses++
                    if (textBuffer.isNotBlank()) {
                        messages = messages + ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = textBuffer.toString()
                        )
                    }
                    messages = messages + ChatMessage(
                        role = MessageRole.USER,
                        content = verificationPrompt(conversationGoal)
                    )
                    verificationActive = true
                    StreamDebugLog.event(conversationId, null, "VERIFY_PASS", "goal=${conversationGoal.take(80)}")
                    continueLoop = true
                } else {
                    continueLoop = false
                }
            } else {
                messages = messages + ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = textBuffer.toString().takeIf { it.isNotEmpty() },
                    toolCalls = toolCalls,
                    responseItems = responseItems
                )

                val executedBatchSignatures = mutableMapOf<String, String>()

                for (toolCall in toolCalls) {
                    val channel = this
                    val executionArguments = if (toolCall.name == "browser") {
                        val shouldResetBrowserTask = !browserTaskStarted
                        browserTaskStarted = true
                        if (shouldResetBrowserTask && toolCall.arguments["reset_task"] == null) {
                            toolCall.arguments + ("reset_task" to true)
                        } else toolCall.arguments
                    } else {
                        toolCall.arguments
                    }

                    completedToolCalls.add("${toolCall.name}: ${toolCall.arguments}")
                    executedToolCalls++
                    StreamDebugLog.event(conversationId, null, "TOOL_EXECUTE", "id=${toolCall.id} name=${toolCall.name}")

                    val currentCallSig = "${toolCall.name}:${JSONObject(toolCall.arguments)}"
                    val batchCachedResult = executedBatchSignatures[currentCallSig]

                    val result = invalidToolArgumentErrors.remove(toolCall.id)?.let { error ->
                        ToolResult.Error(
                            message = error.message.orEmpty().ifBlank { "Invalid arguments for ${toolCall.name}" },
                            errorType = com.amaya.intelligence.tools.ErrorType.VALIDATION_ERROR,
                            recoverable = true
                        )
                    } ?: if (batchCachedResult != null) {
                        StreamDebugLog.event(conversationId, null, "TOOL_DEDUP_BATCH", "tool=${toolCall.name}")
                        ToolResult.Success(
                            "[Duplicate Call Deduplicated]: Tool '${toolCall.name}' with identical arguments was already executed in this batch. Reusing previous output:\n$batchCachedResult"
                        )
                    } else {
                        val identicalCallCount = if (lastExecutedCallSignature[toolCall.name] == currentCallSig) {
                            (repeatedIdenticalCallCount[toolCall.name] ?: 0) + 1
                        } else 1
                        lastExecutedCallSignature[toolCall.name] = currentCallSig
                        repeatedIdenticalCallCount[toolCall.name] = identicalCallCount

                        val totalFrequency = (callSignatureFrequency[currentCallSig] ?: 0) + 1
                        callSignatureFrequency[currentCallSig] = totalFrequency

                        val isConsecutiveBreak = shouldCircuitBreakConsecutiveCalls(identicalCallCount)
                        val isFrequencyBreak = shouldCircuitBreakFrequency(totalFrequency)

                        if (isConsecutiveBreak || isFrequencyBreak) {
                            val triggerReason = if (isConsecutiveBreak) {
                                "invoked $identicalCallCount times consecutively"
                            } else {
                                "invoked $totalFrequency times in this turn"
                            }
                            StreamDebugLog.event(conversationId, null, "TOOL_CIRCUIT_BREAKER", "tool=${toolCall.name} reason=$triggerReason")
                            ToolResult.Error(
                                message = "[Circuit Breaker Triggered]: Tool '${toolCall.name}' has been $triggerReason with identical arguments without strategy change. Invocation suppressed to prevent an infinite loop. Please analyze existing data, alter parameters, or conclude your answer directly.",
                                errorType = com.amaya.intelligence.tools.ErrorType.VALIDATION_ERROR,
                                recoverable = true
                            )
                        } else if (runtimeTarget == AgentRuntimeTarget.WINDOWS_BRIDGE && toolCall.name !in allowedToolNames) {
                            ToolResult.Error(
                                message = "Tool '${toolCall.name}' is not available in Windows Bridge chat.",
                                errorType = com.amaya.intelligence.tools.ErrorType.PERMISSION_ERROR,
                                recoverable = false
                            )
                        } else {
                            try {
                                mcpToolExecutor.execute(
                                    toolName = toolCall.name,
                                    arguments = executionArguments,
                                    workspacePath = workspacePath,
                                    toolCallId = toolCall.id,
                                    onEvent = { event -> if (event is AgentEvent) channel.send(event) },
                                    onConfirmationRequired = onConfirmation,
                                    onClarificationRequired = onClarification,
                                    providerConnection = connection,
                                    selectedModelId = model,
                                    conversationId = sessionId,
                                    ownerId = ownerId,
                                    agentId = activeAgent?.id,
                                    assistantMode = assistantMode,
                                    agentCapabilityProfile = agentCapabilityProfile
                                )
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (e: Throwable) {
                                errorLog("AiAgentLoop", "Tool execution threw exception: ${toolCall.name}", e)
                                StreamDebugLog.event(conversationId, null, "TOOL_EXEC_EXCEPTION", "tool=${toolCall.name} error=${e.message}")
                                ToolResult.Error(
                                    message = "Tool execution exception in '${toolCall.name}': ${e.message ?: e.javaClass.simpleName}. You may check parameters or try an alternative method.",
                                    errorType = com.amaya.intelligence.tools.ErrorType.EXECUTION_ERROR,
                                    recoverable = true
                                )
                            }
                        }
                    }

                    val rawResultContent = when (result) {
                        is ToolResult.Success -> result.output
                        is ToolResult.Deferred -> result.output
                        is ToolResult.Error -> "Error: ${result.message}"
                        is ToolResult.RequiresConfirmation -> "Error: Approval could not be completed: ${result.reason}"
                    }
                    var resultContent = rawResultContent
                    if (result is ToolResult.Success || result is ToolResult.Deferred) {
                        executedBatchSignatures[currentCallSig] = rawResultContent
                    }
                    val identicalCallCount = repeatedIdenticalCallCount[toolCall.name] ?: 1
                    if (identicalCallCount == 2) {
                        resultContent += "\n\n[Circuit Breaker Notice]: Tool '${toolCall.name}' was executed with identical arguments $identicalCallCount times consecutively. If you are stuck in a loop or receiving the same outcome, do not repeat this exact call again. Alter your strategy, verify required prerequisites, or proceed with available evidence."
                    }
                    // Repeated identical tool failures get a self-correction hint appended to the
                    // result, for browser and every other tool (Hermes-style in-loop recovery).
                    val errorSignature = toolErrorSignature(resultContent)
                    if (errorSignature != null) {
                        val repeated = if (lastToolErrorSignature[toolCall.name] == errorSignature) {
                            (repeatedToolErrors[toolCall.name] ?: 0) + 1
                        } else 1
                        lastToolErrorSignature[toolCall.name] = errorSignature
                        repeatedToolErrors[toolCall.name] = repeated
                        repeatedToolFailureWarning(toolCall.name, errorSignature, repeated)?.let { warning ->
                            resultContent += "\n\n$warning"
                            // Scheme E: the same action keeps failing → steer the model back to its
                            // plan instead of letting it burn the failure budget on identical calls.
                            if (repeated >= REPLAN_AFTER_REPEATED_FAILURES && taskPlan.steps.isNotEmpty()) {
                                resultContent += "\n\nPlan revision required: the same action has now failed $repeated times. " +
                                    "Update your plan with update_todo (merge=false to revise it) or switch approach before retrying; do not repeat the identical call."
                            }
                        }
                    } else {
                        lastToolErrorSignature.remove(toolCall.name)
                        repeatedToolErrors.remove(toolCall.name)
                    }

                    completedToolResults.add("${toolCall.name}: $resultContent")
                    if (result is ToolResult.Success && toolCall.name == "skill" && toolCall.arguments["operation"] == "view") {
                        ((toolCall.arguments["skill_id"] ?: toolCall.arguments["name"]) as? String)
                            ?.takeIf(String::isNotBlank)
                            ?.let(viewedSkills::add)
                    }
                    if (runtimeTarget == AgentRuntimeTarget.LOCAL) {
                        repoScope.launch {
                            runCatching {
                                sessionMemoryRepository.saveToolCall(
                                    SessionToolCall(
                                        sessionId = sessionId,
                                        toolCallId = toolCall.id,
                                        toolName = toolCall.name,
                                        argumentsJson = JSONObject(toolCall.arguments).toString(),
                                        resultJson = resultContent,
                                        workspacePath = workspacePath,
                                        workspaceId = workspaceId,
                                        assistantMode = assistantMode.name,
                                        ownerId = ownerId
                                    )
                                )
                            }.onFailure { errorLog("AiRepository", "Failed to save session tool call", it) }
                        }
                    }

                    val toolFailed = result is ToolResult.Error || result is ToolResult.RequiresConfirmation
                    val isHousekeeping = isInternalOrHousekeepingTool(toolCall.name)
                    if (!toolFailed && (!isHousekeeping || lastToolResultContent == null)) {
                        lastToolResultContent = resultContent
                    }
                    StreamDebugLog.event(conversationId, null, "TOOL_RESULT", "id=${toolCall.id} name=${toolCall.name} error=$toolFailed deferred=${result is ToolResult.Deferred} chars=${resultContent.length}")
                    send(AgentEvent.ToolCallResult(
                        toolCallId = toolCall.id,
                        toolName = toolCall.name,
                        result = resultContent,
                        isError = result is ToolResult.Error || result is ToolResult.RequiresConfirmation,
                        deferredTaskId = (result as? ToolResult.Deferred)?.taskId
                    ))

                    val resultMetadata = toolCall.metadata.toMutableMap()
                    resultMetadata["toolName"] = toolCall.name  // Gemini needs the function name
                    if (result is ToolResult.Success) {
                        (result.metadata["bridge_image_base64"] as? String)?.let { resultMetadata["bridge_image_base64"] = it }
                        (result.metadata["bridge_image_format"] as? String)?.let { resultMetadata["bridge_image_format"] = it }
                    }

                    // Scheme E: mirror the visible todo list into the pinned plan state after
                    // every update_todo call, so the plan section stays current for the next request.
                    if (toolCall.name == "update_todo") {
                        taskPlan = TaskPlan.from(todoRepository.getItems())
                    }

                    messages = messages + ChatMessage(
                        role = MessageRole.TOOL,
                        toolResult = ToolResultMessage(
                            toolCallId = toolCall.id, // OpenAI requires original tool_call_id
                            content = resultContent,
                            isError = toolFailed,
                            metadata = resultMetadata
                        )
                    )

                }
                // Mixed-response recovery: the model also emitted tool calls the host rejected
                // (blank ID, unadvertised name, duplicate ID). The rejection could not be fed back
                // before the tool results without breaking the provider's required tool-result
                // ordering, so append it after them — otherwise the model never learns the call
                // was invalid and can repeat it until MAX_FAILED_TOOL_ATTEMPTS errors the turn.
                if (rejectedToolCalls.isNotEmpty()) {
                    val lastRejected = rejectedToolCalls.last()
                    val available = allowedToolNames.take(16).joinToString()
                    messages = messages + ChatMessage(
                        role = MessageRole.USER,
                        content = "Host tool-loop feedback: the previous response also called '${lastRejected.name}' but the host rejected it (blank call ID, unadvertised tool, or duplicate ID). Available tools: $available. Fix the call if you retry it. (Failure $failedToolAttempts/$MAX_FAILED_TOOL_ATTEMPTS.)"
                    )
                    StreamDebugLog.event(conversationId, null, "TOOL_CALL_REJECTED_FEEDBACK", "attempts=$failedToolAttempts name=${lastRejected.name} mixed=true")
                }
            }
        }

        val reflectionContext = CompletedInteractionContext(
            sessionId = sessionId,
            userMessages = completedUserMessages.toList(),
            assistantMessages = completedAssistantMessages.toList(),
            toolCalls = completedToolCalls.toList(),
            toolResults = completedToolResults.toList(),
            timestamp = System.currentTimeMillis(),
            workspacePath = workspacePath,
            workspaceId = workspaceId,
            successful = !terminalError
        )

        if (viewedSkills.isNotEmpty()) {
            val outcome = !terminalError
            viewedSkills.forEach { skillName ->
                runCatching { skillRepository.recordSkillUsage(skillName, success = outcome) }
                    .onFailure { errorLog("AiRepository", "Failed to record skill outcome", it) }
                // Batched usage log (scheme §1.4): buffer in memory, flush once at end-of-session
                // housekeeping so flash I/O stays bounded to a single session boundary.
                runCatching { skillUsageLogRepository.recordUsage(skillName, sessionId, outcome) }
                    .onFailure { errorLog("AiRepository", "Failed to buffer skill usage log", it) }
            }
        }
        if (runtimeTarget == AgentRuntimeTarget.LOCAL) {
            repoScope.launch {
                runCatching { selfImprovementPipeline.analyzeAndImprove(reflectionContext) }
                    .onFailure { errorLog("AiRepository", "Post-chat reflection failed", it) }
            }
        }
        if (!terminalError && !compactedThisTurn) {
            val settledMessages = messages
            val settledLedger = ledger
            val settledCoverage = ledgerCoverage
            val warmBudget = minOf(maxInputTokens, contextWindowTokens - maxOutputTokens) -
                toolSchemaTokens - AiRepository.CONTEXT_SAFETY_RESERVE_TOKENS - TokenEstimator.text(systemPromptWithAutoContext)
            if (warmBudget > 0 && TokenEstimator.messages(settledMessages, cache = costCache) > warmBudget * AiRepository.COMPACTION_WARM_WATER) {
                repoScope.launch {
                    runCatching {
                        val plan = planContextWindow(settledMessages, warmBudget)
                        val newlyEvicted = if (plan.evictionBoundary > settledCoverage) {
                            settledMessages.subList(settledCoverage, plan.evictionBoundary)
                                .filterNot { it === plan.pinnedAnchor }
                        } else emptyList()
                        if (newlyEvicted.isNotEmpty()) {
                            val toolResults = newlyEvicted.count { it.toolResult != null }
                            val warmed = kotlinx.coroutines.withTimeoutOrNull(AiRepository.COMPACTION_TIMEOUT_MS) {
                                updateLedger(
                                    provider = provider,
                                    connection = connection,
                                    model = model,
                                    current = settledLedger,
                                    goal = conversationGoal,
                                    evicted = newlyEvicted,
                                    evictedToolResults = toolResults,
                                    maxInputTokens = maxInputTokens
                                )
                            } ?: mechanicalLedger(settledLedger, conversationGoal, newlyEvicted, toolResults)
                            ledgerStore.put(
                                sessionId,
                                warmed,
                                coveredThrough = plan.evictionBoundary,
                                epoch = ledgerEpoch
                            )
                        }
                    }.onFailure { errorLog("AiRepository", "Proactive ledger warm-up failed", it) }
                }
            }
        }
        if (terminalError) return@channelFlow

        // The model may pack its whole answer into a tool call (e.g. saving it to memory) and end
        // the turn without emitting text. Surface the last successful tool result so the final
        // bubble is never empty and the user still reads the substance of the answer. The first
        // argument is `hasAssistantText` (isNotEmpty): the fallback must fire only when NO visible
        // assistant text exists, so a normal answered turn never gets tool-result text appended.
        if (needsFinalAnswerFallback(completedAssistantMessages.isNotEmpty(), executedToolCalls, lastToolResultContent)) {
            val fallback = extractAnswerLikeText(lastToolResultContent!!)
            send(AgentEvent.TextDelta(fallback))
            StreamDebugLog.event(conversationId, null, "FINAL_TEXT_FALLBACK", "chars=${fallback.length}")
            // Persist the surfaced answer like any other assistant text so semantic recall finds
            // it verbatim (the underlying tool call is stored separately). Off the hot path.
            if (runtimeTarget == AgentRuntimeTarget.LOCAL) {
                repoScope.launch {
                    runCatching {
                        sessionMemoryRepository.saveMessage(SessionMessage(sessionId = sessionId, role = "assistant", content = fallback, workspacePath = workspacePath, workspaceId = workspaceId, assistantMode = assistantMode.name, ownerId = ownerId))
                    }.onFailure { errorLog("AiRepository", "Failed to save fallback assistant message", it) }
                }
            }
        }

        StreamDebugLog.event(conversationId, null, "TURN_DONE", "iterations=$iterations")
        send(AgentEvent.Done)
    }

/**
 * Scheme C: whether a tool-using turn gets one extra verification pass before finalizing. The
 * model must confirm the goal is fully done with evidence, or continue working. Never runs on
 * plain Q&A (no tools), internal continuations, or bridge turns.
 */
internal fun shouldRunVerificationPass(
    verificationPasses: Int,
    maxPasses: Int,
    messageRole: MessageRole,
    runtimeTarget: AgentRuntimeTarget,
    executedToolCalls: Int,
    failedToolAttempts: Int,
    hasPlanSteps: Boolean
): Boolean = verificationPasses < maxPasses &&
    messageRole == MessageRole.USER &&
    runtimeTarget == AgentRuntimeTarget.LOCAL &&
    (executedToolCalls > 0 || failedToolAttempts > 0 || hasPlanSteps)

internal fun isInternalOrHousekeepingTool(name: String): Boolean = name in setOf(
    "memory_manage", "update_memory", "agent_memory", "agent_memory_internal",
    "skill_manage", "skill_view", "update_todo", "recommendation_manage", "session_search"
)

/**
 * Whether a tool-using turn that produced no assistant text should surface the last tool result
 * as the final answer (the model packed its reply into a tool call instead of writing it).
 */
internal fun needsFinalAnswerFallback(hasAssistantText: Boolean, executedToolCalls: Int, lastToolResult: String?): Boolean =
    !hasAssistantText && executedToolCalls > 0 && !lastToolResult.isNullOrBlank()

/**
 * Best-effort extraction of the human-readable substance from a tool result before it is shown as
 * the final-answer fallback. Memory tools echo a JSON document with a `content` field; plain text
 * results are returned unchanged.
 */
internal fun extractAnswerLikeText(result: String): String {
    val trimmed = result.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        return runCatching { JSONObject(trimmed) }
            .getOrNull()
            ?.optString("content")
            ?.takeIf(String::isNotBlank)
            ?: trimmed
    }
    return trimmed
}

internal fun shouldCircuitBreakConsecutiveCalls(consecutiveCount: Int): Boolean = consecutiveCount >= 3

internal fun computeToolCallSignature(toolName: String, arguments: Map<String, Any?>): String =
    "$toolName:${JSONObject(arguments)}"

internal fun sanitizeToolName(name: String, allowedToolNames: Set<String>): String {
    val trimmed = name.trim()
    if (trimmed in allowedToolNames) return trimmed
    // Strip special tokens e.g. <|channel|>commentary, <|call:...|>, <|thought|>, etc.
    val strippedTokens = trimmed.substringBefore("<|").substringBefore("[:").substringBefore("(").trim()
    if (strippedTokens in allowedToolNames) return strippedTokens
    // Strip namespace prefix e.g. tools.browser, functions.browser, tool:browser
    val afterNamespace = strippedTokens.substringAfterLast('.').substringAfterLast(':').trim()
    if (afterNamespace in allowedToolNames) return afterNamespace
    // Match prefix against allowed tool names (e.g. browser_xxx or browser...)
    val prefixMatch = allowedToolNames.firstOrNull { allowed ->
        strippedTokens.startsWith(allowed, ignoreCase = true) &&
            (strippedTokens.length == allowed.length || !strippedTokens[allowed.length].isLetterOrDigit())
    }
    if (prefixMatch != null) return prefixMatch
    return trimmed
}

