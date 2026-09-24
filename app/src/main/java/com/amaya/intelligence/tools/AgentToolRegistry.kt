package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.models.AgentCapabilityProfile
import com.amaya.intelligence.domain.models.AssistantMode

/**
 * Metadata descriptor for tools registered in [AgentToolRegistry].
 */
data class ToolRegistration(
    val name: String,
    val tool: Tool,
    val definition: ToolDefinition? = null,
    val capabilities: Set<String> = emptySet(),
    val isWorkspaceRequired: Boolean = false,
    val isReadOnlyAllowed: Boolean = false
)

/**
 * Central registry for tools to enable dynamic tool cataloging,
 * capability discovery, and schema resolution.
 */
interface AgentToolRegistry {
    /** Register or replace a tool in the registry. */
    fun register(registration: ToolRegistration)

    /** Unregister a tool by handler name. */
    fun unregister(name: String): Boolean

    /** Retrieve a registered tool by handler name. */
    fun getTool(name: String): Tool?

    /** List all registered tools with MODEL visibility. */
    fun getModelCallableTools(): List<Tool>

    /** Get definitions for all registered tools, filtered by mode and profile. */
    fun getToolDefinitions(
        mode: AssistantMode = AssistantMode.PROJECT,
        agentCapabilityProfile: AgentCapabilityProfile? = null,
        delegationAgentIds: List<Long> = emptyList()
    ): List<ToolDefinition>

    /** Get definitions specifically permitted in read-only subagent mode. */
    fun getReadOnlyToolDefinitions(): List<ToolDefinition>

    /** Check if a handler is allowed in read-only mode. */
    fun isAllowedInReadOnlyMode(handlerName: String): Boolean
}
