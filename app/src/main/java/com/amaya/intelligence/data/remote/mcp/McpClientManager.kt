package com.amaya.intelligence.data.remote.mcp

import com.amaya.intelligence.data.remote.api.AiToolDefinition
import com.amaya.intelligence.data.remote.api.AiToolParameters
import com.amaya.intelligence.data.remote.api.AiToolProperty
import com.amaya.intelligence.data.remote.api.AiToolPropertyItems
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.McpConfig
import com.amaya.intelligence.data.remote.api.McpServerConfig
import com.amaya.intelligence.tools.ToolResult
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpClientManager @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsManager: AiSettingsManager
) {
    companion object {
        const val TOOL_PREFIX = "mcp__"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private var toolCache: Map<String, McpToolHandle> = emptyMap()
    private var toolDefinitionsCache: List<AiToolDefinition> = emptyList()

    suspend fun refreshTools(): List<AiToolDefinition> {
        val settings = settingsManager.getSettings()
        val config = McpConfig.fromJson(settings.mcpConfigJson)
        val tools = mutableListOf<AiToolDefinition>()
        val handles = mutableMapOf<String, McpToolHandle>()

        debugLog("MCP") { "Refreshing MCP tools: servers=${config.servers.size}" }

        for (server in config.servers.filter { it.enabled && it.serverUrl.isNotBlank() }) {
            val serverTools = fetchTools(server)
            debugLog("MCP") { "Server ${server.name} tools=${serverTools.size}" }
            for (tool in serverTools) {
                val fullName = "${TOOL_PREFIX}${server.name}__${tool.name}"
                tools.add(tool.toAiToolDefinition(fullName))
                handles[fullName] = McpToolHandle(server, tool.name)
                debugLog("MCP") { "Registered tool: $fullName" }
            }
        }

        toolCache = handles
        toolDefinitionsCache = tools
        debugLog("MCP") { "MCP tool cache size=${toolDefinitionsCache.size}" }
        return tools
    }

    fun getCachedToolDefinitions(): List<AiToolDefinition> = toolDefinitionsCache

    suspend fun callTool(toolName: String, arguments: Map<String, Any?>): ToolResult {
        val handle = toolCache[toolName]
            ?: return ToolResult.Error("Unknown MCP tool: $toolName")

        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", "tools/call")
            put(
                "params",
                JSONObject().apply {
                    put("name", handle.toolName)
                    put("arguments", toJsonObject(arguments))
                }
            )
        }

        val response = executeRequest(handle.server, payload)
            ?: return ToolResult.Error("MCP server did not return a response")

        if (response.has("error")) {
            return ToolResult.Error(response.optJSONObject("error")?.optString("message") ?: "MCP error")
        }

        val result = response.optJSONObject("result") ?: return ToolResult.Error("Missing MCP result")
        val isError = result.optBoolean("isError", false)
        val content = buildContentText(result)
        val structuredContent = result.opt("structuredContent")?.toString()

        return if (isError) {
            ToolResult.Error(content.ifBlank { "MCP tool error" })
        } else {
            val output = if (content.isNotBlank()) content else structuredContent.orEmpty()
            ToolResult.Success(output.ifBlank { "OK" })
        }
    }

    private suspend fun fetchTools(server: McpServerConfig): List<McpTool> {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val response = executeRequest(server, payload) ?: return emptyList()
        if (response.has("error")) {
            errorLog("MCP", "tools/list error: ${response.optJSONObject("error")?.optString("message")}")
            return emptyList()
        }
        val result = response.optJSONObject("result") ?: return emptyList()
        val toolsArray = result.optJSONArray("tools") ?: JSONArray()
        val tools = mutableListOf<McpTool>()
        for (i in 0 until toolsArray.length()) {
            val toolObj = toolsArray.optJSONObject(i) ?: continue
            val name = toolObj.optString("name")
            if (name.isBlank()) continue
            tools.add(
                McpTool(
                    name = name,
                    description = toolObj.optString("description"),
                    inputSchema = toolObj.optJSONObject("inputSchema") ?: JSONObject()
                )
            )
        }
        return tools
    }

    private suspend fun executeRequest(server: McpServerConfig, payload: JSONObject): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestBody = payload.toString().toRequestBody(JSON_MEDIA)
            val requestBuilder = Request.Builder()
                .url(server.serverUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json, text/event-stream")
                .addHeader("Content-Type", "application/json")

            server.headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    requestBuilder.addHeader(key, value)
                }
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    errorLog("MCP", "HTTP ${it.code} from ${server.serverUrl}")
                    return@withContext null
                }
                val body = it.body?.string() ?: return@withContext null
                debugLog("MCP") { "Response from ${server.serverUrl}: ${body.take(500)}" }
                return@withContext parseMcpResponse(body)
            }
        }
    }

    private fun buildContentText(result: JSONObject): String {
        val contentArr = result.optJSONArray("content") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until contentArr.length()) {
            val obj = contentArr.optJSONObject(i) ?: continue
            if (obj.optString("type") == "text") {
                parts.add(obj.optString("text"))
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun parseMcpResponse(body: String): JSONObject? {
        // First try plain JSON (non-streaming servers)
        runCatching { return JSONObject(body) }

        // SSE format: scan all "data: {...}" lines, return the last result-bearing one
        val lines = body.lineSequence().filter { it.isNotBlank() }
        var best: JSONObject? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) continue
            val jsonPart = trimmed.removePrefix("data:").trim()
            if (jsonPart.isBlank() || jsonPart == "[DONE]") continue
            val parsed = runCatching { JSONObject(jsonPart) }.getOrNull() ?: continue
            // Prefer the event that has "result"
            if (parsed.has("result")) {
                best = parsed
            } else if (best == null) {
                best = parsed
            }
        }
        if (best == null) errorLog("MCP", "Failed to parse MCP response: ${body.take(300)}")
        return best
    }

    private fun toJsonObject(arguments: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        arguments.forEach { (key, value) ->
            obj.put(key, toJsonValue(value))
        }
        return obj
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                value.forEach { (k, v) ->
                    if (k != null) obj.put(k.toString(), toJsonValue(v))
                }
                obj
            }
            is Iterable<*> -> JSONArray().apply { value.forEach { put(toJsonValue(it)) } }
            else -> value
        }
    }

    private data class McpToolHandle(
        val server: McpServerConfig,
        val toolName: String
    )

    private data class McpTool(
        val name: String,
        val description: String,
        val inputSchema: JSONObject
    ) {
        fun toAiToolDefinition(fullName: String): AiToolDefinition {
            val schemaType = inputSchema.optString("type", "object")
            val propertiesObj = inputSchema.optJSONObject("properties") ?: JSONObject()
            val requiredArr = inputSchema.optJSONArray("required") ?: JSONArray()
            val required = mutableListOf<String>()
            for (i in 0 until requiredArr.length()) {
                required.add(requiredArr.optString(i))
            }
            val properties = mutableMapOf<String, AiToolProperty>()
            val keys = propertiesObj.keys()
            while (keys.hasNext()) {
                val propName = keys.next()
                val propObj = propertiesObj.optJSONObject(propName) ?: JSONObject()
                val propType = propObj.optString("type", "string")
                val propDesc = propObj.optString("description", "")
                val enumArr = propObj.optJSONArray("enum")
                val enumValues = enumArr?.let {
                    (0 until it.length()).mapNotNull { idx -> it.optString(idx) }
                }
                val itemsObj = propObj.optJSONObject("items")
                val itemsType = itemsObj?.optString("type")
                properties[propName] = AiToolProperty(
                    type = propType,
                    description = propDesc,
                    enum = enumValues,
                    items = itemsType?.let { AiToolPropertyItems(it) }
                )
            }
            return AiToolDefinition(
                name = fullName,
                description = description.ifBlank { "MCP tool" },
                parameters = AiToolParameters(
                    type = schemaType,
                    properties = properties,
                    required = required
                )
            )
        }
    }
}
