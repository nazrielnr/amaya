package com.amaya.intelligence.data.remote.mcp

import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.tools.AgentToolRegistry
import com.amaya.intelligence.tools.Tool
import com.amaya.intelligence.tools.ToolDefinition
import com.amaya.intelligence.tools.ToolRegistration
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class McpServerTestConnectivityTest {

    private class NoopToolRegistry : AgentToolRegistry {
        val registered = ConcurrentHashMap<String, ToolRegistration>()
        override fun register(registration: ToolRegistration) { registered[registration.name] = registration }
        override fun unregister(name: String): Boolean = registered.remove(name) != null
        override fun getTool(name: String): Tool? = registered[name]?.tool
        override fun getModelCallableTools(): List<Tool> = emptyList()
        override fun getToolDefinitions(
            mode: com.amaya.intelligence.domain.models.AssistantMode,
            agentCapabilityProfile: com.amaya.intelligence.domain.models.AgentCapabilityProfile?,
            delegationAgentIds: List<Long>
        ): List<ToolDefinition> = emptyList()
        override fun getReadOnlyToolDefinitions(): List<ToolDefinition> = emptyList()
        override fun isAllowedInReadOnlyMode(handlerName: String): Boolean = false
    }

    private val client = OkHttpClient()
    private val toolRegistry = NoopToolRegistry()

    private val manager = McpClientManager(
        httpClient = client,
        settingsManager = null,
        toolRegistry = toolRegistry
    )

    @Test
    fun `rejects empty server URL immediately`() = runTest {
        val config = McpServerConfig(
            name = "empty_server",
            serverUrl = "",
            headers = emptyMap(),
            enabled = true
        )

        val result = manager.testServer(config)
        assertFalse(result.isSuccess)
        assertEquals("Server URL is empty", result.message)
    }

    @Test
    fun `rejects invalid URL schemes`() = runTest {
        val config = McpServerConfig(
            name = "ftp_server",
            serverUrl = "ftp://127.0.0.1:8080/mcp",
            headers = emptyMap(),
            enabled = true
        )

        val result = manager.testServer(config)
        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("http:// or https://", ignoreCase = true))
    }

    @Test
    fun `rejects header with missing or blank value`() = runTest {
        val config = McpServerConfig(
            name = "bad_headers",
            serverUrl = "https://example.com/mcp",
            headers = mapOf("Authorization" to "Bearer valid-token", "X-Custom-Key" to ""),
            enabled = true
        )

        val result = manager.testServer(config)
        assertFalse(result.isSuccess)
        assertEquals("Missing value for header(s): X-Custom-Key", result.message)
    }

    @Test
    fun `handles unreachable host gracefully`() = runTest {
        val config = McpServerConfig(
            name = "unreachable",
            serverUrl = "http://127.0.0.1:59999/mcp",
            headers = emptyMap(),
            enabled = true
        )

        val result = manager.testServer(config)
        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Connection failed"))
    }

    @Test
    fun `parses multi-server json configuration correctly for testing`() {
        val json = """
            {
              "mcpServers": {
                "weather": {
                  "serverUrl": "https://api.weather.com/mcp",
                  "headers": {
                    "X-Api-Key": "secret123"
                  },
                  "enabled": true
                },
                "github": {
                  "serverUrl": "https://mcp.github.com/v1",
                  "headers": {},
                  "enabled": false
                }
              }
            }
        """.trimIndent()

        val parsed = McpConfig.fromJson(json)
        assertEquals(2, parsed.servers.size)

        val weatherServer = parsed.servers.find { it.name == "weather" }
        assertEquals("https://api.weather.com/mcp", weatherServer?.serverUrl)
        assertEquals("secret123", weatherServer?.headers?.get("X-Api-Key"))
        assertTrue(weatherServer?.enabled == true)

        val githubServer = parsed.servers.find { it.name == "github" }
        assertEquals("https://mcp.github.com/v1", githubServer?.serverUrl)
        assertFalse(githubServer?.enabled == true)
    }
}
