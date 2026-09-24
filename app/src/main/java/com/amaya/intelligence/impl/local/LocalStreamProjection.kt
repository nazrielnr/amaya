package com.amaya.intelligence.impl.local


import com.amaya.intelligence.data.remote.api.MessageRole

import com.amaya.intelligence.data.repository.AgentEvent

import com.amaya.intelligence.domain.ai.PendingClarification
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.util.LocalStreamPerfLog
import com.amaya.intelligence.tools.ClarificationRequest
import com.amaya.intelligence.tools.ConfirmationRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject


internal suspend fun LocalIntelligenceService.awaitInlineToolConfirmation(request: ConfirmationRequest, turnId: Long): Boolean {
        val toolCallId = request.toolCallId ?: return false
        if (turnsById[turnId] == null) return false
        val approvalId = "$turnId:$toolCallId"
        pendingConfirmationUi[toolCallId] = request
        pendingApprovalIds[toolCallId] = approvalId
        return try {
            pendingToolConfirmations.await(approvalId, turnId) {
                if (turnsById[turnId] == null) return@await
                updateTurnToolExecution(turnsById[turnId]!!, toolCallId) { tool ->
                    tool.copy(
                        status = ToolStatus.PENDING,
                        metadata = tool.metadata + approvalMetadata(request, approvalId)
                    )
                }
            }
        } finally {
            pendingConfirmationUi.remove(toolCallId, request)
            pendingApprovalIds.remove(toolCallId, approvalId)
        }
    }

internal suspend fun LocalIntelligenceService.awaitInlineToolClarification(
    request: ClarificationRequest,
    turnId: Long
): String? {
    val toolCallId = request.toolCallId ?: return null
    if (turnsById[turnId] == null) return null
    val clarificationId = "$turnId:$toolCallId"
    pendingClarificationUi[toolCallId] = request
    pendingClarificationIds[toolCallId] = clarificationId
    _pendingClarification.value = PendingClarification(toolCallId, request.question, request.options)
    return try {
        pendingClarifications.await(clarificationId, turnId) {
            if (turnsById[turnId] == null) return@await
            updateTurnToolExecution(turnsById[turnId]!!, toolCallId) { tool ->
                tool.copy(
                    status = ToolStatus.PENDING,
                    metadata = tool.metadata + clarificationMetadata(request, clarificationId)
                )
            }
        }
    } finally {
        pendingClarificationUi.remove(toolCallId, request)
        pendingClarificationIds.remove(toolCallId, clarificationId)
        if (_pendingClarification.value?.toolCallId == toolCallId) {
            _pendingClarification.value = null
        }
    }
}

internal fun LocalIntelligenceService.clarificationMetadata(
    request: ClarificationRequest,
    clarificationId: String
): Map<String, String> = mapOf(
    "clarificationRequired" to "true",
    "clarificationState" to "pending",
    "clarificationId" to clarificationId,
    "clarificationQuestion" to request.question,
    "clarificationOptions" to request.options.joinToString("\n")
)

internal fun LocalIntelligenceService.approvalMetadata(request: ConfirmationRequest, approvalId: String): Map<String, String> = mapOf(
        "approvalRequired" to "true",
        "approvalState" to "pending",
        "approvalReason" to request.reason,
        "approvalDetails" to request.details,
        "riskLevel" to request.riskLevel.name.lowercase(),
        "approvalId" to approvalId
    )

internal fun LocalIntelligenceService.updateToolExecution(toolCallId: String, transform: (ToolExecution) -> ToolExecution) {
        updateCurrentAssistantMessage { msg ->
            val updatedTools = msg.toolExecutions.map { tool ->
                if (tool.toolCallId == toolCallId) transform(tool) else tool
            }
            val updatedSteps = msg.steps.map { step ->
                if (step is MessageStep.ToolCall && step.execution.toolCallId == toolCallId) {
                    step.copy(execution = transform(step.execution))
                } else step
            }
            msg.copy(toolExecutions = updatedTools, steps = updatedSteps)
        }
    }

internal fun LocalIntelligenceService.bufferAssistantTextDelta(delta: String) {
        if (delta.isEmpty()) return
        assistantTextBuffer.append(delta)
        LocalStreamPerfLog.onInboundDelta(delta.length, assistantTextBuffer.length)
        if (assistantTextBuffer.length >= 256) {
            assistantFlushJob?.cancel()
            assistantFlushJob = null
            flushAssistantTextBuffer()
        } else if (assistantFlushJob?.isActive != true) {
            assistantFlushJob = scope.launch {
                delay(24)
                flushAssistantTextBuffer()
                assistantFlushJob = null
            }
        }
    }

internal fun LocalIntelligenceService.bufferAssistantThinkingDelta(delta: String) {
        if (delta.isEmpty()) return
        assistantThinkingBuffer.append(delta)
        if (assistantThinkingBuffer.length >= 256) {
            thinkingFlushJob?.cancel()
            thinkingFlushJob = null
            flushAssistantThinkingBuffer()
        } else if (thinkingFlushJob?.isActive != true) {
            thinkingFlushJob = scope.launch {
                delay(24)
                flushAssistantThinkingBuffer()
                thinkingFlushJob = null
            }
        }
    }

internal fun LocalIntelligenceService.flushAssistantThinkingBuffer() {
        thinkingFlushJob?.cancel()
        thinkingFlushJob = null
        if (assistantThinkingBuffer.isEmpty()) return
        val chunk = assistantThinkingBuffer.toString()
        assistantThinkingBuffer.clear()
        ensureAssistantMessage()
        updateCurrentAssistantMessage { it.appendThinkingStep(chunk) }
    }

internal fun LocalIntelligenceService.flushAssistantTextBuffer(now: Long = System.currentTimeMillis()) {
        assistantFlushJob?.cancel()
        assistantFlushJob = null
        if (assistantTextBuffer.isEmpty()) return
        val chunk = assistantTextBuffer.toString()
        assistantTextBuffer.clear()
        lastAssistantTextUiEmitAt = now
        val startNs = System.nanoTime()
        var totalAssistantChars = 0
        var stepCount = 0
        ensureAssistantMessage()
        updateCurrentAssistantMessage { msg ->
            val newContent = msg.content + chunk
            val newSteps = msg.steps.finishThinking().appendText(chunk)
            totalAssistantChars = newContent.length
            stepCount = newSteps.size
            msg.finishThinking().copy(
                content = newContent,
                steps = newSteps,
                canonicalHistory = msg.canonicalHistory + JSONObject()
                    .put("kind", "assistant_text")
                    .put("text", chunk)
                    .toString()
            )
        }
        LocalStreamPerfLog.onUiFlush(
            chunkChars = chunk.length,
            totalAssistantChars = totalAssistantChars,
            messages = _uiState.value.messages.size,
            steps = stepCount,
            updateMs = (System.nanoTime() - startNs) / 1_000_000
        )
    }

internal fun LocalIntelligenceService.ensureAssistantMessage() {
        val assistantMetadata = currentAssistantMetadata()
        val assistantId = currentAssistantMessageId
        val state = _uiState.value
        val msgs = state.messages.toMutableList()
        val currentIdx = assistantId?.let { id -> msgs.indexOfLast { it.id == id } } ?: -1

        if (currentIdx == -1) {
            val assistantMsg = UiMessage(
                role = MessageRole.ASSISTANT,
                content = "",
                metadata = assistantMetadata
            )
            currentAssistantMessageId = assistantMsg.id
            _uiState.value = state.copy(messages = msgs + assistantMsg)
            return
        }

        val existing = msgs[currentIdx]
        if (existing.metadata.isEmpty() && assistantMetadata.isNotEmpty()) {
            msgs[currentIdx] = existing.copy(metadata = assistantMetadata)
        }
        _uiState.value = state.copy(messages = msgs)
    }
internal fun LocalIntelligenceService.currentAssistantMetadata(): Map<String, String> {
        val state = _uiState.value
        val model = state.modelOptions.firstOrNull { it.id == state.activeModelKey }

        return buildMap {
            put("source", "local")
            model?.name?.takeIf { it.isNotBlank() }?.let { put("agent_name", it) }
            state.selectedModel.takeIf { it.isNotBlank() }?.let { put("model_id", it) }
                ?: model?.modelId?.takeIf { it.isNotBlank() }?.let { put("model_id", it) }
            if (!containsKey("agent_name")) {
                model?.id?.takeIf { it.isNotBlank() }?.let { put("agent_name", it) }
            }
        }
    }

internal fun LocalIntelligenceService.updateRunningSession(conversationId: Long, status: String, detail: String) {
        val state = _uiState.value
        val title = _conversations.value.firstOrNull { it.id == conversationId }?.title
            ?: state.messages.firstOrNull { it.role == MessageRole.USER }?.content?.take(48)
            ?: "AI session"
        val item = RunningSession(conversationId, title, state.assistantMode, state.ownerId, state.agentId, status, detail)
        _runningSessions.update { current -> current.filterNot { it.conversationId == conversationId } + item }
    }

internal fun LocalIntelligenceService.removeRunningSession(conversationId: Long) {
        _runningSessions.update { current -> current.filterNot { it.conversationId == conversationId } }
    }

internal fun LocalIntelligenceService.toolEventDetail(name: String, arguments: Map<String, Any?>): String =
        listOf("path", "query", "command", "task", "url").firstNotNullOfOrNull { key ->
            arguments[key]?.toString()?.takeIf(String::isNotBlank)?.let { "$key: ${it.take(100)}" }
        } ?: name

internal fun LocalIntelligenceService.currentAssistantTextLength(): Int {
        val assistantId = currentAssistantMessageId ?: return 0
        return _uiState.value.messages.lastOrNull { it.id == assistantId }?.content?.length ?: 0
    }

internal fun LocalIntelligenceService.markCurrentAssistantCompleted() = markCurrentAssistantTerminal("completed")

    /**
     * Reasoning is finalized the moment any *non-thinking* event follows a
     * [AgentEvent.ThinkingDelta] (text delta, tool call start, tool result,
     * stream end, error). Without this, the ThinkingCard would stay in
     * "pending" until [markCurrentAssistantTerminal] runs at the end of the
     * agent turn, which is exactly the race condition that hides reasoning
     * behind every other timeline event.
     *
     * Captures duration if not already set, and is a no-op when reasoning
     * was already finalised or never started.
     */
internal fun LocalIntelligenceService.finalizeThinkingIfActive() {
        updateCurrentAssistantMessage { it.finishThinking() }
    }

internal fun UiMessage.appendThinkingStep(delta: String): UiMessage {
        val updatedSteps = steps.appendThinking(delta)
        val current = updatedSteps.last() as MessageStep.Thinking
        return copy(
            thinking = current.text,
            isThinking = true,
            thinkingStartedAt = current.startedAt,
            steps = updatedSteps
        )
    }

internal fun UiMessage.finishThinking(nowMs: Long = System.currentTimeMillis()): UiMessage {
        val finishedSteps = steps.finishThinking(nowMs)
        if (!isThinking && thinkingDurationMs != null && finishedSteps === steps) return this
        val durationMs = thinkingStartedAt?.let { (nowMs - it).coerceAtLeast(0L) }
        return copy(isThinking = false, thinkingDurationMs = thinkingDurationMs ?: durationMs, steps = finishedSteps)
    }

internal fun LocalIntelligenceService.markCurrentAssistantTerminal(status: String) {
        val nowMs = System.currentTimeMillis()
        val now = nowMs.toString()
        updateCurrentAssistantMessage { msg ->
            val durationMs = msg.thinkingStartedAt?.let { (nowMs - it).coerceAtLeast(0L) }
            msg.copy(
                metadata = msg.metadata + mapOf("completedAt" to now, "turnStatus" to status),
                isThinking = false,
                thinkingDurationMs = msg.thinkingDurationMs ?: durationMs
            )
        }
    }

internal fun LocalIntelligenceService.markActiveToolsStopped() {
        updateCurrentAssistantMessage { msg ->
            val active = msg.toolExecutions.filter {
                it.status == ToolStatus.RUNNING || it.status == ToolStatus.PENDING
            }
            fun stopped(tool: ToolExecution): ToolExecution = if (tool in active) tool.copy(
                status = ToolStatus.ERROR,
                result = tool.result ?: "Stopped by user",
                metadata = tool.metadata + mapOf(
                    "approvalRequired" to "false",
                    "approvalState" to "cancelled"
                )
            ) else tool
            val terminalItems = active.map { tool ->
                JSONObject()
                    .put("kind", "tool_result")
                    .put("id", tool.toolCallId)
                    .put("name", tool.name)
                    .put("result", tool.result ?: "Stopped by user")
                    .put("isError", true)
                    .toString()
            }
            msg.copy(
                toolExecutions = msg.toolExecutions.map(::stopped),
                steps = msg.steps.map { step ->
                    if (step is MessageStep.ToolCall) step.copy(execution = stopped(step.execution)) else step
                },
                canonicalHistory = msg.canonicalHistory + terminalItems
            )
        }
    }

internal fun LocalIntelligenceService.updateCurrentAssistantMessage(update: (UiMessage) -> UiMessage) {
        val assistantId = currentAssistantMessageId
        if (assistantId == null) return

        val state = _uiState.value
        val msgs = state.messages.toMutableList()
        val assistantIdx = msgs.indexOfLast { it.id == assistantId }
        if (assistantIdx == -1) return

        msgs[assistantIdx] = update(msgs[assistantIdx])
        _uiState.value = state.copy(messages = msgs)
    }

