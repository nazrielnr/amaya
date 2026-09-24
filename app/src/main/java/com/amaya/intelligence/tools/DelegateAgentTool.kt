package com.amaya.intelligence.tools

import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.DelegationTaskDao
import com.amaya.intelligence.data.local.entity.DelegationTaskEntity
import com.amaya.intelligence.data.repository.AgentConversationRepository
import com.amaya.intelligence.di.ApplicationScope
import com.amaya.intelligence.impl.local.LocalIntelligenceService
import com.amaya.intelligence.util.StreamDebugLog
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class DelegateAgentTool @Inject constructor(
    private val agentDao: AgentDao,
    private val delegationTaskDao: DelegationTaskDao,
    private val agentConversationRepository: AgentConversationRepository,
    private val localIntelligenceService: Provider<LocalIntelligenceService>,
    @ApplicationScope private val appScope: CoroutineScope
) : Tool, ContextAwareTool {
    companion object {
        private val inFlightDelegations = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }

    override val name = "delegate_agent"
    override val description = "Dispatch one explicit task to another named member of the active agent group. The result is delivered automatically when that Agent finishes; never poll or call this tool to fetch output. Use the group-local agent_id; do not use a name or database ID."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        ToolResult.Error("Agent group context is required", ErrorType.PERMISSION_ERROR)

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult {
        val groupId = context.ownerId?.toLongOrNull()
            ?: return ToolResult.Error("Active agent group is required", ErrorType.PERMISSION_ERROR)
        val title = (arguments["title"] as? String)?.trim().orEmpty()
        val request = (arguments["task"] as? String)?.trim().orEmpty()
        if (title.isBlank() || request.isBlank()) return ToolResult.Error("title, agent_id, and task are required", ErrorType.VALIDATION_ERROR)

        val members = agentDao.getByGroup(groupId)
        val source = context.agentId?.let { id -> members.firstOrNull { it.id == id } }
            ?: return ToolResult.Error("Active source agent is required", ErrorType.PERMISSION_ERROR)

        val targetLocalId = when (val raw = arguments["agent_id"]) {
            is Number -> raw.toLong()
            is String -> {
                val parsed = raw.trim().toLongOrNull()
                if (parsed != null) {
                    parsed
                } else {
                    val clean = raw.trim().removePrefix("@").trim()
                    members.firstOrNull {
                        it.name.equals(clean, ignoreCase = true) ||
                        it.role?.contains(clean, ignoreCase = true) == true
                    }?.localId
                }
            }
            else -> null
        } ?: return ToolResult.Error(
            "Target agent could not be resolved from agent_id '${arguments["agent_id"]}'. Available members: ${members.filter { it.id != source.id }.joinToString { "${it.name} (agent_id=${it.localId})" }}",
            ErrorType.VALIDATION_ERROR
        )

        if (targetLocalId == source.localId) {
            return ToolResult.Error("An agent cannot delegate to itself", ErrorType.VALIDATION_ERROR)
        }
        val agent = members.firstOrNull { it.localId == targetLocalId }
            ?: return ToolResult.Error(
                "Agent ID '$targetLocalId' is not in the active group. Available members: ${members.filter { it.id != source.id }.joinToString { "${it.name} (agent_id=${it.localId})" }}",
                ErrorType.PERMISSION_ERROR
            )
        val group = agentDao.getGroupById(groupId)
            ?: return ToolResult.Error("Active agent group no longer exists", ErrorType.PERMISSION_ERROR)
        val targetContext = runCatching {
            agentConversationRepository.appendDelegationRequest(groupId, source, agent, group.workspacePath, request)
        }.getOrElse {
            return ToolResult.Error("Could not append delegation to ${agent.name}: ${it.message}", ErrorType.EXECUTION_ERROR)
        }
        val sourceConversationId = context.conversationId?.toLongOrNull()
            ?: return ToolResult.Error("Source conversation ID is required", ErrorType.VALIDATION_ERROR)
        val deduplicationKey = "$groupId:$targetLocalId:${request.hashCode()}"
        val existingTaskId = inFlightDelegations[deduplicationKey]
        if (existingTaskId != null) {
            StreamDebugLog.event(sourceConversationId, null, "DELEGATE_DEDUP", "task=$existingTaskId target=${agent.name}")
            return ToolResult.Deferred(
                output = "Delegation already in progress for ${agent.name} (task_id=$existingTaskId). The result will be delivered automatically when ${agent.name} finishes. Do not call delegate_agent to poll for output.",
                taskId = existingTaskId
            )
        }
        val taskId = delegationTaskDao.insert(DelegationTaskEntity(groupId = groupId, agentId = agent.id, request = request, status = "RUNNING"))
        inFlightDelegations[deduplicationKey] = taskId
        appScope.launch {
            try {
                val completed = runCatching {
                    localIntelligenceService.get().runDelegatedAgentTurn(targetContext.conversationId, targetContext.incomingMessage)
                }.getOrElse { error ->
                    SubagentResult(agent.name, "Delegation failed: ${error.message.orEmpty().ifBlank { "unknown error" }}")
                }
                val failed = completed.summary.startsWith("[ERROR]") || completed.summary.startsWith("[RATE LIMITED]") ||
                    completed.summary.startsWith("[INCOMPLETE]") || completed.summary.startsWith("Delegation failed:")
                delegationTaskDao.complete(taskId, if (failed) "FAILED" else "COMPLETED", completed.summary)
                localIntelligenceService.get().completeDelegationEvent(
                    conversationId = sourceConversationId,
                    taskId = taskId,
                    title = title,
                    sourceAgentName = source.name,
                    targetAgentName = agent.name,
                    result = completed.summary,
                    failed = failed
                )
                StreamDebugLog.event(sourceConversationId, null, "DELEGATE_DELIVERED", "task=$taskId failed=$failed chars=${completed.summary.length}")
            } finally {
                inFlightDelegations.remove(deduplicationKey, taskId)
            }
        }
        return ToolResult.Deferred(
            output = "Delegation started: task_id=$taskId target=${agent.name}. The result will be delivered automatically when ${agent.name} finishes. Do not call delegate_agent to poll for output; use the latest conversation context while waiting.",
            taskId = taskId
        )
    }
}
