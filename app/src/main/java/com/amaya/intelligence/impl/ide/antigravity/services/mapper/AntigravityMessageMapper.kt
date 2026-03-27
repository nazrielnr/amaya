package com.amaya.intelligence.impl.ide.antigravity.services.mapper

import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.domain.models.*
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteChatMessage
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteToolExecution
import com.amaya.intelligence.impl.ide.antigravity.AntigravityProtocol
import com.amaya.intelligence.impl.ide.antigravity.tools.AntigravityToolMapper

/**
 * Maps remote messages from Antigravity to domain UiMessage objects.
 * Handles thinking tool synthesis, message consolidation, and deduplication.
 */
object AntigravityMessageMapper {
    
    fun mapRemoteMessages(
        messages: List<RemoteChatMessage>,
        isStreaming: Boolean
    ): List<UiMessage> {
        if (messages.isEmpty()) return emptyList()

        val mapped = mutableListOf<UiMessage>()
        val lastAssistantIndex = messages.indexOfLast { it.role == "assistant" }

        var currentUiMessage: UiMessage? = null

        messages.forEachIndexed { index, msg ->
            val role = if (msg.role == "assistant") MessageRole.ASSISTANT else MessageRole.USER
            val mappedRemoteTools = msg.toolExecutions.map { it.toToolExecution() }.toMutableList()
            
            val hasThinkingField = !msg.thinking.isNullOrBlank()
            val hasThinkingTools = mappedRemoteTools.any {
                it.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true" ||
                    it.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true) ||
                    it.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY] == "true"
            }

            if (role == MessageRole.ASSISTANT && hasThinkingField && !hasThinkingTools) {
                val thoughtText = msg.thinking?.trim().orEmpty()
                val thoughtTitle = extractThoughtTitle(thoughtText)
                val isRunning = isStreaming && index == lastAssistantIndex

                val meta = mutableMapOf(
                    AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY to "true",
                    "source" to "remote"
                )
                if (!thoughtTitle.isNullOrBlank()) {
                    meta["thoughtTitle"] = thoughtTitle
                }

                mappedRemoteTools.add(0, ToolExecution(
                    toolCallId = "remote-$index-thinking-legacy",
                    name = AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME,
                    arguments = mapOf(
                        "source" to "state_sync_legacy",
                        "thoughtTitle" to (thoughtTitle ?: "")
                    ),
                    result = msg.thinking,
                    status = if (isRunning) ToolStatus.RUNNING else ToolStatus.SUCCESS,
                    metadata = meta,
                    uiMetadata = ToolUiMetadata(
                        category = ToolCategory.TASK_MANAGEMENT,
                        label = thoughtTitle ?: "Thinking",
                        actionIcon = ToolInfoIcon.BRAIN,
                        targetIcon = ToolInfoIcon.GENERATE,
                        badges = listOf("THINKING")
                    )
                ))
            }

            val msgSteps = mutableListOf<MessageStep>()
            mappedRemoteTools.forEach { exec ->
                msgSteps.add(MessageStep.ToolCall(execution = exec))
            }
            if (msg.content.isNotBlank()) {
                // Ensure unique ID for multiple text fragments in a grouped message by appending the original remote index
                val stepIdx = msg.metadata["startStepIndex"]
                val textId = if (!stepIdx.isNullOrBlank()) "text-$stepIdx" else "text-remote-$index"
                msgSteps.add(MessageStep.Text(id = textId, content = msg.content))
            }

            if (currentUiMessage != null && currentUiMessage!!.role == role && role == MessageRole.ASSISTANT) {
                // Group contiguous assistant chunks into one cohesive chat bubble
                val combinedContent = if (currentUiMessage!!.content.isBlank()) msg.content else if (msg.content.isBlank()) currentUiMessage!!.content else "${currentUiMessage!!.content}\n\n${msg.content}"
                currentUiMessage = currentUiMessage!!.copy(
                    content = combinedContent,
                    toolExecutions = currentUiMessage!!.toolExecutions + mappedRemoteTools,
                    steps = currentUiMessage!!.steps + msgSteps,
                    attachments = currentUiMessage!!.attachments + (msg.attachments ?: emptyList())
                )
            } else {
                if (currentUiMessage != null) {
                    mapped.add(currentUiMessage!!)
                }
                currentUiMessage = UiMessage(
                    id = stableRemoteMessageId(msg.metadata, role, index),
                    role = role,
                    content = msg.content,
                    thinking = null,
                    isThinking = false,
                    intent = msg.intent,
                    metadata = msg.metadata + mapOf("source" to "remote"),
                    toolExecutions = mappedRemoteTools,
                    steps = msgSteps,
                    attachments = msg.attachments
                )
            }
        }

        if (currentUiMessage != null) {
            mapped.add(currentUiMessage!!)
        }

        return mapped
    }
    

    
    private fun stableRemoteMessageId(metadata: Map<String, String>, role: MessageRole, fallbackIndex: Int): String {
        val start = metadata["startStepIndex"]?.trim().orEmpty()
        val end = metadata["endStepIndex"]?.trim().orEmpty()
        if (start.isNotBlank() || end.isNotBlank()) {
            return "remote-${role.name.lowercase()}-${start.ifBlank { "x" }}-${end.ifBlank { "x" }}"
        }
        return "remote-${role.name.lowercase()}-$fallbackIndex"
    }
    
    private fun isThinkingTool(tool: ToolExecution): Boolean {
        return tool.name.equals(AntigravityProtocol.ToolMarkers.THINKING_TOOL_NAME, ignoreCase = true) ||
            tool.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY].equals("true", ignoreCase = true) ||
            tool.metadata[AntigravityProtocol.ToolMarkers.THINKING_TOOL_META_KEY].equals("true", ignoreCase = true)
    }


    
    fun normalizeUserText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
    
    fun extractThoughtTitle(markdown: String): String? {
        val lines = markdown
            .trim()
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        fun normalizeTitle(raw: String): String {
            return raw
                .trim()
                .removeSuffix(":")
                .trim()
        }

        // Prefer explicit markdown headings (e.g. ### Title)
        val headingRegex = Regex("""^#{1,6}\s+(.+)$""")
        for (line in lines) {
            val heading = headingRegex.find(line)?.groupValues?.getOrNull(1)
            if (!heading.isNullOrBlank()) {
                val candidate = normalizeTitle(heading)
                if (candidate.length >= 3) return candidate
            }
        }

        // Then prefer bold-only heading-like lines near the top.
        val boldLineRegex = Regex("""^(?:[-*]\s*)?(?:\*\*|__)(.+?)(?:\*\*|__)\s*:?(?:\s+.*)?$""")
        for (line in lines.take(5)) {
            val boldLine = boldLineRegex.find(line)?.groupValues?.getOrNull(1)
            if (!boldLine.isNullOrBlank()) {
                val candidate = normalizeTitle(boldLine)
                if (candidate.length >= 3) return candidate
            }
        }

        // Fallback: any bold segment from the first lines only
        val boldRegex = Regex("""(?:\*\*|__)(.+?)(?:\*\*|__)""")
        for (line in lines.take(5)) {
            val match = boldRegex.find(line)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) {
                val candidate = normalizeTitle(match)
                if (candidate.length >= 3) return candidate
            }
        }

        // Final Fallback: use the first sentence or truncated paragraph
        var firstLine = lines.first().trim()
        firstLine = firstLine.removePrefix("- ").removePrefix("* ").trim()

        var truncated = firstLine
        val sentenceEnd = firstLine.indexOfAny(charArrayOf('.', '!', '?'))
        if (sentenceEnd != -1) {
            truncated = firstLine.substring(0, sentenceEnd + 1)
        }

        if (truncated.length > 50) {
            truncated = truncated.substring(0, 47).trimEnd() + "..."
        }

        return truncated.takeIf { it.isNotBlank() }
    }
    
    // Extension to map remote model to domain ToolExecution
    private fun RemoteToolExecution.toToolExecution(): ToolExecution {
        val normalizedName = AntigravityToolMapper.mapToolName(name)
        val normalizedArgs = AntigravityToolMapper.mapToolArgs(name, arguments)
        return ToolExecution(
            toolCallId = toolCallId,
            name = normalizedName,
            arguments = normalizedArgs,
            result = AntigravityToolMapper.extractToolResult(result),
            status = when (status.uppercase()) {
                "RUNNING" -> ToolStatus.RUNNING
                "SUCCESS" -> ToolStatus.SUCCESS
                "ERROR"   -> ToolStatus.ERROR
                "PENDING", "WAITING", "STANDBY" -> ToolStatus.PENDING
                else      -> ToolStatus.PENDING
            },
            metadata = metadata + mapOf("source" to "remote"),
            uiMetadata = AntigravityToolMapper.getUiMetadata(name, arguments, metadata)
        )
    }
}
