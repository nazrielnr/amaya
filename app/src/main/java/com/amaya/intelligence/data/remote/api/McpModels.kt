package com.amaya.intelligence.data.remote.api

import org.json.JSONObject

/**
 * MCP configuration models stored as JSON in settings.
 */
data class McpServerConfig(
    val name: String,
    val serverUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

data class McpConfig(
    val servers: List<McpServerConfig> = emptyList()
) {
    fun toJson(pretty: Boolean = true): String {
        val root = JSONObject()
        val serversObj = JSONObject()
        servers.forEach { server ->
            val serverObj = JSONObject()
            serverObj.put("serverUrl", server.serverUrl)
            if (server.headers.isNotEmpty()) {
                val headerObj = JSONObject()
                server.headers.forEach { (key, value) ->
                    headerObj.put(key, value)
                }
                serverObj.put("headers", headerObj)
            }
            serverObj.put("enabled", server.enabled)
            serversObj.put(server.name, serverObj)
        }
        root.put("mcpServers", serversObj)
        return if (pretty) root.toString(2) else root.toString()
    }

    companion object {
        fun fromJson(json: String): McpConfig {
            val root = try {
                JSONObject(json)
            } catch (_: Exception) {
                return McpConfig()
            }
            val serversObj = root.optJSONObject("mcpServers") ?: JSONObject()
            val servers = mutableListOf<McpServerConfig>()
            val keys = serversObj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val serverObj = serversObj.optJSONObject(name) ?: continue
                val headers = mutableMapOf<String, String>()
                serverObj.optJSONObject("headers")?.let { headersObj ->
                    val headerKeys = headersObj.keys()
                    while (headerKeys.hasNext()) {
                        val key = headerKeys.next()
                        headers[key] = headersObj.optString(key, "")
                    }
                }
                servers.add(
                    McpServerConfig(
                        name = name,
                        serverUrl = serverObj.optString("serverUrl", ""),
                        headers = headers,
                        enabled = serverObj.optBoolean("enabled", true)
                    )
                )
            }
            return McpConfig(servers)
        }
    }
}
