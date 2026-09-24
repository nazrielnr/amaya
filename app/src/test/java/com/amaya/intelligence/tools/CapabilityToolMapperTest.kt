package com.amaya.intelligence.tools

import com.amaya.intelligence.domain.models.AgentCapabilityProfile
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.agentMentionMarkdown
import com.amaya.intelligence.domain.models.commandMarkdown
import com.amaya.intelligence.domain.models.parseComposerReferences
import com.amaya.intelligence.domain.models.workspaceMentionMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityToolMapperTest {
    @Test
    fun `workspace patch maps to legacy edit handler`() {
        val call = CapabilityToolMapper.map(
            "workspace_change",
            mapOf("operation" to "patch", "path" to "src/Main.kt", "diff" to "@@ -1 +1 @@")
        )!!
        assertEquals("edit_file", call.handlerName)
        assertEquals("src/Main.kt", call.arguments["path"])
    }

    @Test
    fun `memory update preserves compare and swap version`() {
        val call = CapabilityToolMapper.map(
            "memory",
            mapOf("operation" to "update", "id" to "mem_1", "expected_version" to 3, "content" to "New value")
        )!!
        assertEquals("memory_manage", call.handlerName)
        assertEquals(3, call.arguments["expected_version"])
    }

    @Test
    fun `memory archive and delete are rejected`() {
        assertTrue(CapabilityToolMapper.map("memory", mapOf("operation" to "archive")) == null)
        assertTrue(CapabilityToolMapper.map("memory", mapOf("operation" to "delete")) == null)
    }

    @Test
    fun `mode tool surfaces match product capabilities`() {
        assertTrue(!assistantModeAllowsCapability("run_shell", AssistantMode.CHAT))
        assertTrue(!assistantModeAllowsCapability("browser", AssistantMode.CHAT))
        assertTrue(!assistantModeAllowsCapability("workspace_change", AssistantMode.CHAT))
        assertTrue(!assistantModeAllowsCapability("reminder", AssistantMode.CHAT))
        assertTrue(assistantModeAllowsCapability("run_shell", AssistantMode.PROJECT))
        assertTrue(!assistantModeAllowsCapability("reminder", AssistantMode.PROJECT))
        assertTrue(!assistantModeAllowsCapability("browser", AssistantMode.PROJECT))
        assertTrue(assistantModeAllowsCapability("browser", AssistantMode.AGENT))
        assertTrue(!assistantModeAllowsCapability("delegate_agent", AssistantMode.PROJECT))
        assertTrue(assistantModeAllowsCapability("delegate_agent", AssistantMode.AGENT))
        assertTrue(assistantModeAllowsCapability("reminder", AssistantMode.AGENT))
        assertTrue(!assistantModeAllowsCapability("browser", AssistantMode.AGENT, AgentCapabilityProfile(browser = false)))
        assertTrue(!assistantModeAllowsCapability("run_shell", AssistantMode.AGENT, AgentCapabilityProfile(terminal = false)))
        assertTrue(!assistantModeAllowsCapability("web_search", AssistantMode.AGENT, AgentCapabilityProfile(webSearch = false)))
        assertTrue(!assistantModeAllowsCapability("skill", AssistantMode.AGENT, AgentCapabilityProfile(skills = false)))
        assertTrue(!assistantModeAllowsCapability("write_file", AssistantMode.AGENT, AgentCapabilityProfile(excludedTools = setOf("write_file"))))
        assertTrue(assistantModeAllowsCapability("read_file", AssistantMode.AGENT, AgentCapabilityProfile(excludedTools = setOf("write_file"))))
        assertTrue(!assistantModeAllowsCapability("write_file", AssistantMode.AGENT, AgentCapabilityProfile(workspace = false)))
        assertTrue(!assistantModeAllowsCapability("mcp__github__create_issue", AssistantMode.AGENT, AgentCapabilityProfile(mcp = false)))
        assertTrue(assistantModeAllowsCapability("mcp__github__create_issue", AssistantMode.AGENT, AgentCapabilityProfile()))
        // Round-trip: encode → decode preserves category flags and per-tool exclusions.
        val profile = AgentCapabilityProfile(browser = false, mcp = false, excludedTools = setOf("write_file", "delete_file"))
        val decoded = AgentCapabilityProfile.decode(profile.encode())
        assertTrue(!decoded.browser)
        assertTrue(!decoded.mcp)
        assertTrue(decoded.workspace)
        assertEquals(setOf("delete_file", "write_file"), decoded.excludedTools)
        // Legacy string without the new segments still decodes with defaults.
        assertEquals(AgentCapabilityProfile(), AgentCapabilityProfile.decode("workspace=true;terminal=true;browser=true;subagents=true"))
        assertTrue(assistantModeAllowsCapability("agent_memory", AssistantMode.AGENT, AgentCapabilityProfile(workspace = false, terminal = false, browser = false, subagents = false, webSearch = false, skills = false, reminders = false, todo = false)))
        assertTrue(!assistantModeAllowsCapability("memory", AssistantMode.AGENT, AgentCapabilityProfile()))
        assertEquals(AssistantMode.CHAT, AssistantMode.forWorkspace(null))
        assertEquals(AssistantMode.PROJECT, AssistantMode.forWorkspace("/project"))
    }

    @Test
    fun `agent memory schema advertises only supported operations`() {
        val memory = ToolDefinition(
            name = "memory",
            description = "memory",
            parameters = listOf(
                ToolParameter(
                    name = "operation",
                    type = "string",
                    description = "save, list, search, update, or recall_sessions",
                    enum = listOf("save", "list", "search", "update", "recall_sessions")
                )
            )
        )

        val exposed = exposeToolDefinition(memory, AssistantMode.AGENT)

        assertEquals("agent_memory", exposed.name)
        assertEquals(listOf("save", "list", "search", "update"), exposed.parameters.single().enum)
    }

    @Test
    fun `direct registered tools remain exposable after capability wrappers`() {
        val directTools = listOf(
            "list_files", "write_file", "create_directory", "delete_file", "edit_file", "find_files",
            "update_memory", "memory_manage", "skill_view", "skill_manage", "session_search"
        )

        assertTrue(directTools.all { assistantModeAllowsCapability(it, AssistantMode.PROJECT) })
    }

    @Test
    fun `agent capability profile round trips all configurable tools`() {
        val profile = AgentCapabilityProfile(false, true, false, true, false, true, false, true)
        assertEquals(profile, AgentCapabilityProfile.decode(profile.encode()))
    }

    @Test
    fun `composer markdown keeps stable agent and raw workspace identities`() {
        val text = listOf(
            agentMentionMarkdown(42, "CEO"),
            workspaceMentionMarkdown("docs/quarter one/plan.md"),
            commandMarkdown("/review")
        ).joinToString(" ")

        assertEquals(listOf(42L), parseComposerReferences(text).agentIds)
        assertEquals(listOf("docs/quarter one/plan.md"), parseComposerReferences(text).workspacePaths)
        assertEquals(listOf("review"), parseComposerReferences(text).commands)
        assertTrue(text.contains("[@CEO](agent:42)"))
        assertTrue(text.contains("[@plan.md](workspace:docs/quarter%20one/plan.md)"))
        assertEquals(emptyList<Long>(), parseComposerReferences("@CEO").agentIds)
    }

    @Test
    fun `unknown workspace operation is rejected`() {
        assertTrue(CapabilityToolMapper.map("workspace_change", mapOf("operation" to "restore")) == null)
    }
}
