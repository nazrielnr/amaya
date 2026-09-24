package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.*

import org.json.JSONObject

internal fun isValidToolCall(
    id: String,
    name: String,
    allowedNames: Set<String>,
    currentBatchIds: Set<String>
): Boolean = id.isNotBlank() && name in allowedNames && id !in currentBatchIds

internal fun AiRepository.validateToolArguments(
        name: String,
        arguments: Map<String, Any?>,
        tools: List<AiToolDefinition>
    ): Result<Map<String, Any?>> = AiToolArgumentValidator().validate(name, arguments, tools)

internal fun AiRepository.normalizeJsonSchemaValue(schema: JSONObject, value: Any?): Any? {
        val types = buildList {
            schema.optString("type").takeIf { it.isNotBlank() }?.let(::add)
            schema.optJSONArray("type")?.let { array ->
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }
        val scalar = if (types.any { it.equals("integer", true) } && types.none { it.equals("string", true) }) {
            normalizeIntegerArgument(value)
        } else value
        return when (scalar) {
            is Map<*, *> -> {
                val properties = schema.optJSONObject("properties") ?: return scalar
                scalar.entries.associate { (key, child) ->
                    val name = key.toString()
                    name to (properties.optJSONObject(name)?.let { normalizeJsonSchemaValue(it, child) } ?: child)
                }
            }
            is List<*> -> schema.optJSONObject("items")?.let { itemSchema ->
                scalar.map { normalizeJsonSchemaValue(itemSchema, it) }
            } ?: scalar
            else -> scalar
        }
    }

internal fun AiRepository.validateJsonSchema(schema: JSONObject, value: Any?, path: String) {
        val types = buildList {
            schema.optString("type").takeIf { it.isNotBlank() }?.let(::add)
            schema.optJSONArray("type")?.let { array ->
                for (index in 0 until array.length()) add(array.optString(index))
            }
        }
        if (value == null) {
            require("null" in types || schema.optBoolean("nullable")) { "$path cannot be null" }
            return
        }
        val matches = types.isEmpty() || types.any { type ->
            when (type.lowercase()) {
                "object" -> value is Map<*, *>
                "array" -> value is List<*>
                "string" -> value is String
                "integer" -> value is Number && value.toDouble() % 1.0 == 0.0
                "number" -> value is Number
                "boolean" -> value is Boolean
                "null" -> false
                else -> true
            }
        }
        require(matches) { "$path must be ${types.joinToString(" or ")}" }
        schema.optJSONArray("enum")?.let { allowed ->
            require((0 until allowed.length()).any { allowed.opt(it) == value }) { "$path is not an allowed value" }
        }
        when (value) {
            is Map<*, *> -> {
                val properties = schema.optJSONObject("properties") ?: JSONObject()
                val required = schema.optJSONArray("required")
                if (required != null) for (index in 0 until required.length()) {
                    val key = required.optString(index)
                    require(value.containsKey(key) && value[key] != null) { "Missing required property: $path.$key" }
                }
                if (schema.has("additionalProperties") && schema.opt("additionalProperties") == false) {
                    require(value.keys.all { properties.has(it.toString()) }) { "Unknown properties in $path" }
                }
                value.forEach { (key, child) ->
                    properties.optJSONObject(key.toString())?.let { validateJsonSchema(it, child, "$path.$key") }
                }
            }
            is List<*> -> schema.optJSONObject("items")?.let { itemSchema ->
                value.forEachIndexed { index, child -> validateJsonSchema(itemSchema, child, "$path[$index]") }
            }
        }
    }

internal fun AiRepository.browserErrorSignature(resultContent: String): String? {
        val root = runCatching { JSONObject(resultContent) }.getOrNull() ?: return null
        val status = root.optString("status")
        if (status != "error" && status != "cancelled" && status != "timeout") return null
        val error = root.optJSONObject("agent")?.optJSONObject("error") ?: root.optJSONObject("error")
        val code = error?.optString("code")?.takeIf { it.isNotBlank() } ?: status
        val message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: root.optJSONObject("agent")?.optString("latest_summary").orEmpty()
        return "$code:${message.take(120)}"
    }

    /**
     * Stable signature for any tool failure, so identical repeated failures can be detected
     * across iterations. Browser failures use the structured JSON signature; every other tool
     * uses the first error-looking line of its result.
     */
    internal fun AiRepository.toolErrorSignature(resultContent: String): String? {
        browserErrorSignature(resultContent)?.let { return it }
        val errorLine = resultContent.lineSequence().firstOrNull { line ->
            val lower = line.lowercase()
            TOOL_ERROR_SIGNAL_WORDS.any { it in lower }
        } ?: return null
        return errorLine.trim().take(120)
    }

    private val TOOL_ERROR_SIGNAL_WORDS = listOf(
        "error", "failed", "failure", "timeout", "cancelled", "exception", "denied", "blocked", "invalid", "not found", "unavailable"
    )



    /**
     * Build tool definitions for AI.
     * Uses cached MCP tools — refresh happens automatically via settingsFlow watcher in init.
     */
