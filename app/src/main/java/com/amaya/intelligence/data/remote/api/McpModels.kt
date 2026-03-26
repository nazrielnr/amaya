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
) {
    fun toRawJson(pretty: Boolean = true): String {
        val serverObj = JSONObject().apply {
            put("serverUrl", serverUrl)
            if (headers.isNotEmpty()) {
                put("headers", JSONObject(headers))
            }
            put("enabled", enabled)
        }
        return if (pretty) serverObj.toString(2) else serverObj.toString()
    }

    companion object {
        fun fromRawJson(name: String, rawJson: String): McpServerConfig? {
            val root = try {
                JSONObject(rawJson)
            } catch (_: Exception) {
                return null
            }

            val resolvedName: String
            val serverObj = if (root.has("mcpServers")) {
                val serversObj = root.optJSONObject("mcpServers") ?: return null
                if (name.isNotBlank()) {
                    resolvedName = name
                    serversObj.optJSONObject(name) ?: return null
                } else {
                    if (serversObj.length() != 1) return null
                    val key = serversObj.keys().next()
                    resolvedName = key
                    serversObj.optJSONObject(key) ?: return null
                }
            } else {
                if (name.isBlank()) return null
                resolvedName = name
                root
            }

            val headers = mutableMapOf<String, String>()
            serverObj.optJSONObject("headers")?.let { headersObj ->
                val headerKeys = headersObj.keys()
                while (headerKeys.hasNext()) {
                    val key = headerKeys.next()
                    headers[key] = headersObj.optString(key, "")
                }
            }

            return McpServerConfig(
                name = resolvedName,
                serverUrl = serverObj.optString("serverUrl", ""),
                headers = headers,
                enabled = serverObj.optBoolean("enabled", true)
            )
        }
    }
}

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
