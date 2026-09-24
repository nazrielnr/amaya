package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.remote.api.AiToolDefinition
import org.json.JSONArray
import org.json.JSONObject

/** Validates and normalizes provider-emitted tool arguments before execution. */
internal class AiToolArgumentValidator {
    companion object {
        private val PARAMETER_ALIASES = mapOf(
            "file_path" to "path",
            "filepath" to "path",
            "target_path" to "path",
            "text" to "content",
            "body" to "content",
            "code" to "content",
            "q" to "query",
            "search" to "query",
            "search_term" to "query",
            "glob" to "pattern",
            "regex" to "pattern"
        )
    }

    fun validate(
        name: String,
        arguments: Map<String, Any?>,
        tools: List<AiToolDefinition>
    ): Result<Map<String, Any?>> = runCatching {
        val definition = tools.firstOrNull { it.name == name } ?: error("Tool '$name' was not advertised")
        
        // Coerce aliases first
        val resolvedArguments = resolveAliases(arguments, definition)

        definition.rawParametersJson?.let { schema ->
            @Suppress("UNCHECKED_CAST")
            val normalized = normalizeJsonSchemaValue(JSONObject(schema), resolvedArguments) as Map<String, Any?>
            validateJsonSchema(JSONObject(schema), normalized, "arguments")
            return@runCatching normalized
        }

        val normalized = resolvedArguments.toMutableMap()
        definition.parameters.properties.forEach { (key, property) ->
            if (key in normalized) {
                normalized[key] = coerceType(normalized[key], property.type)
            }
        }
        val missing = definition.parameters.required.filter { it !in normalized || normalized[it] == null }
        require(missing.isEmpty()) { "Missing required properties for $name: ${missing.joinToString()}" }

        if (!definition.parameters.additionalProperties) {
            val allowedKeys = definition.parameters.properties.keys
            val extraKeys = normalized.keys - allowedKeys
            require(extraKeys.isEmpty()) { "Unknown properties for $name: ${extraKeys.joinToString()}" }
        }
        
        definition.parameters.properties.forEach { (key, property) ->
            val value = normalized[key] ?: return@forEach
            val validType = when (property.type.lowercase()) {
                "string" -> value is String
                "integer" -> value is Number && value.toDouble() % 1.0 == 0.0
                "number" -> value is Number
                "boolean" -> value is Boolean
                "array" -> value is List<*>
                "object" -> value is Map<*, *>
                else -> true
            }
            require(validType) { "$key must be ${property.type} (got ${value::class.java.simpleName})" }
            property.enum?.let { allowed -> require(value.toString() in allowed) { "$key must be one of: ${allowed.joinToString()}" } }
        }
        normalized
    }

    private fun resolveAliases(arguments: Map<String, Any?>, definition: AiToolDefinition): Map<String, Any?> {
        val result = arguments.toMutableMap()
        val acceptedKeys = definition.parameters.properties.keys
        for ((alias, canonical) in PARAMETER_ALIASES) {
            if (alias in result && canonical !in result && (canonical in acceptedKeys || acceptedKeys.isEmpty())) {
                result[canonical] = result.remove(alias)
            }
        }
        return result
    }

    private fun coerceType(value: Any?, targetType: String): Any? {
        if (value == null) return null
        return when (targetType.lowercase()) {
            "integer" -> when (value) {
                is Number -> value.toLong()
                is String -> value.trim().toLongOrNull() ?: value.trim().toDoubleOrNull()?.toLong() ?: value
                else -> value
            }
            "number" -> when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull() ?: value
                else -> value
            }
            "boolean" -> when (value) {
                is Boolean -> value
                is String -> when (value.trim().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> value
                }
                is Number -> value.toInt() != 0
                else -> value
            }
            "string" -> value.toString()
            "array" -> when (value) {
                is List<*> -> value
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        runCatching {
                            val jsonArr = JSONArray(trimmed)
                            (0 until jsonArr.length()).map { jsonArr.get(it) }
                        }.getOrDefault(listOf(value))
                    } else listOf(value)
                }
                else -> listOf(value)
            }
            else -> value
        }
    }

    private fun normalizeJsonSchemaValue(schema: JSONObject, value: Any?): Any? {
        val types = schemaTypes(schema)
        val scalar = if (types.any { it.equals("integer", true) } && types.none { it.equals("string", true) }) {
            coerceType(value, "integer")
        } else if (types.any { it.equals("boolean", true) } && types.none { it.equals("string", true) }) {
            coerceType(value, "boolean")
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

    private fun validateJsonSchema(schema: JSONObject, value: Any?, path: String) {
        val types = schemaTypes(schema)
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
            require((0 until allowed.length()).any { allowed.opt(it) == value }) { "$path must be one of allowed values" }
        }
        when (value) {
            is Map<*, *> -> {
                val properties = schema.optJSONObject("properties") ?: JSONObject()
                val required = schema.optJSONArray("required")
                if (required != null) for (index in 0 until required.length()) {
                    val key = required.optString(index)
                    require(value.containsKey(key) && value[key] != null) { "Missing required property: $path.$key" }
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

    private fun schemaTypes(schema: JSONObject): List<String> = buildList {
        schema.optString("type").takeIf(String::isNotBlank)?.let(::add)
        schema.optJSONArray("type")?.let { array ->
            for (index in 0 until array.length()) add(array.optString(index))
        }
    }
}
