package com.amaya.intelligence.tools

import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.domain.memory.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManageTool @Inject constructor(
    private val memoryRepository: MemoryRepository
) : Tool, ContextAwareTool {
    override val name = "memory_manage"
    override val description = "List, search, or update active saved memory by id. Update requires the current version returned by list/search. Never use for secrets."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        when (val action = (arguments["action"] as? String)?.lowercase()) {
            "list", "search" -> listOrSearch(arguments, context.workspacePath)
            "update", "replace" -> update(arguments, context.confirmed, context.workspacePath)
            null -> ToolResult.Error("Missing required: action", ErrorType.VALIDATION_ERROR)
            else -> ToolResult.Error("Unsupported action: $action", ErrorType.VALIDATION_ERROR)
        }
    }

    private suspend fun listOrSearch(arguments: Map<String, Any?>, workspacePath: String?): ToolResult {
        val records = memoryRepository.listMemoryRecords(
            type = parseType(arguments["type"] as? String),
            query = arguments["query"] as? String,
            limit = ((arguments["limit"] as? Number)?.toInt() ?: 20).coerceIn(1, 100),
            workspacePath = workspacePath
        )
        return ToolResult.Success(
            output = JSONObject().put("results", JSONArray(records.map { record ->
                JSONObject()
                    .put("id", record.id)
                    .put("content", record.content)
                    .put("type", record.type.name.lowercase())
                    .put("version", record.version)
            })).toString(),
            metadata = mapOf("count" to records.size)
        )
    }

    private suspend fun update(arguments: Map<String, Any?>, confirmed: Boolean, workspacePath: String?): ToolResult {
        val id = arguments["id"] as? String
            ?: return ToolResult.Error("Missing required: id", ErrorType.VALIDATION_ERROR)
        val content = arguments["content"] as? String
            ?: return ToolResult.Error("Missing required: content", ErrorType.VALIDATION_ERROR)
        val expectedVersion = (arguments["expected_version"] as? Number)?.toInt()
            ?: return ToolResult.Error("Missing required: expected_version", ErrorType.VALIDATION_ERROR)
        val record = memoryRepository.listMemoryRecords(limit = 100, workspacePath = workspacePath).firstOrNull { it.id == id }
        if (record == null) return ToolResult.Error("Memory not found: $id", ErrorType.NOT_FOUND)
        if (!confirmed) {
            return ToolResult.RequiresConfirmation(
                reason = "Update saved memory '$id'?",
                details = "Current: ${record.content}\nNew: ${content.take(500)}"
            )
        }
        return memoryRepository.updateMemoryById(id, content, expectedVersion, workspacePath).fold(
            onSuccess = {
                // Scheme §4 confidence breaker: an explicit user-confirmed edit is independent
                // validation, so the memory may rise to verified. Best-effort and non-fatal.
                runCatching { memoryRepository.confirmMemory(id, workspacePath) }
                ToolResult.Success(JSONObject()
                    .put("id", id)
                    .put("content", content.trim())
                    .put("type", record.type.name.lowercase())
                    .put("version", expectedVersion + 1)
                    .toString())
            },
            onFailure = { ToolResult.Error("Update memory failed: ${it.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private fun parseType(raw: String?): MemoryType? = when (raw?.lowercase()) {
        "user_profile", "user" -> MemoryType.USER_PROFILE
        "workspace_fact", "workspace", "project" -> MemoryType.WORKSPACE_FACT
        "decision" -> MemoryType.DECISION
        else -> null
    }
}
