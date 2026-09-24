package com.amaya.intelligence.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class AgentToolRegistryTest {

    private class SimpleTestRegistry : AgentToolRegistry {
        private val registrations = ConcurrentHashMap<String, ToolRegistration>()

        override fun register(registration: ToolRegistration) {
            registrations[registration.name] = registration
        }

        override fun unregister(name: String): Boolean {
            return registrations.remove(name) != null
        }

        override fun getTool(name: String): Tool? {
            return registrations[name]?.tool
        }

        override fun getModelCallableTools(): List<Tool> {
            return registrations.values
                .filter { it.tool.visibility == ToolVisibility.MODEL }
                .map { it.tool }
        }

        override fun getToolDefinitions(
            mode: com.amaya.intelligence.domain.models.AssistantMode,
            agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile?,
            delegationAgentIds: List<Long>
        ): List<ToolDefinition> {
            return registrations.values.mapNotNull { it.definition }
        }

        override fun getReadOnlyToolDefinitions(): List<ToolDefinition> {
            return registrations.values
                .filter { it.isReadOnlyAllowed }
                .mapNotNull { it.definition }
        }

        override fun isAllowedInReadOnlyMode(handlerName: String): Boolean {
            return registrations[handlerName]?.isReadOnlyAllowed ?: false
        }
    }

    private fun createDummyTool(
        toolName: String,
        toolVisibility: ToolVisibility = ToolVisibility.MODEL
    ): Tool {
        return object : Tool {
            override val name: String = toolName
            override val description: String = "Test tool: $toolName"
            override val visibility: ToolVisibility = toolVisibility
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                return ToolResult.Success("executed $toolName")
            }
        }
    }

    @Test
    fun `registers and retrieves tools by name`() {
        val registry = SimpleTestRegistry()
        val tool = createDummyTool("read_file")

        registry.register(
            ToolRegistration(
                name = "read_file",
                tool = tool,
                isReadOnlyAllowed = true
            )
        )

        val retrieved = registry.getTool("read_file")
        assertNotNull(retrieved)
        assertEquals("read_file", retrieved?.name)
    }

    @Test
    fun `unregisters existing tool successfully`() {
        val registry = SimpleTestRegistry()
        val tool = createDummyTool("mcp__github__create_issue")

        registry.register(
            ToolRegistration(
                name = "mcp__github__create_issue",
                tool = tool
            )
        )

        assertNotNull(registry.getTool("mcp__github__create_issue"))
        val removed = registry.unregister("mcp__github__create_issue")
        assertTrue(removed)
        assertNull(registry.getTool("mcp__github__create_issue"))

        val removedAgain = registry.unregister("mcp__github__create_issue")
        assertFalse(removedAgain)
    }

    @Test
    fun `filters model-callable tools by ToolVisibility`() {
        val registry = SimpleTestRegistry()
        val modelTool = createDummyTool("web_search", ToolVisibility.MODEL)
        val internalTool = createDummyTool("internal_sync", ToolVisibility.INTERNAL)

        registry.register(ToolRegistration("web_search", modelTool))
        registry.register(ToolRegistration("internal_sync", internalTool))

        val callable = registry.getModelCallableTools()
        assertEquals(1, callable.size)
        assertEquals("web_search", callable.first().name)
    }

    @Test
    fun `respects read-only mode permissions`() {
        val registry = SimpleTestRegistry()
        val readTool = createDummyTool("read_file")
        val writeTool = createDummyTool("write_file")

        registry.register(
            ToolRegistration(
                name = "read_file",
                tool = readTool,
                isReadOnlyAllowed = true
            )
        )
        registry.register(
            ToolRegistration(
                name = "write_file",
                tool = writeTool,
                isReadOnlyAllowed = false
            )
        )

        assertTrue(registry.isAllowedInReadOnlyMode("read_file"))
        assertFalse(registry.isAllowedInReadOnlyMode("write_file"))
        assertFalse(registry.isAllowedInReadOnlyMode("unknown_tool"))
    }
}
