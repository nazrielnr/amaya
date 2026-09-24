package com.amaya.intelligence.tools

import com.amaya.intelligence.data.repository.SkillRepository
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.skills.Skill
import com.amaya.intelligence.domain.skills.SkillMetadata
import com.amaya.intelligence.domain.skills.SkillStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillManageTool @Inject constructor(
    private val skillRepository: SkillRepository,
    private val memoryClassifier: MemoryClassifier
) : Tool, ContextAwareTool {
    override val name = "skill_manage"
    override val description = "Create, update, patch, merge, archive, or delete reusable procedural skills when the user explicitly asks to manage skills. Never store credentials or trivial one-off notes."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val action = (arguments["action"] as? String)?.lowercase()
            ?: return@withContext ToolResult.Error("Missing required: action", ErrorType.VALIDATION_ERROR)
        val name = arguments["name"] as? String
            ?: return@withContext ToolResult.Error("Missing required: name", ErrorType.VALIDATION_ERROR)
        if (action in setOf("create", "update", "patch", "merge") && (arguments["content"] as? String).isNullOrBlank()) {
            return@withContext ToolResult.Error("Missing required: content", ErrorType.VALIDATION_ERROR)
        }
        if (action == "delete" && !context.confirmed) {
            return@withContext ToolResult.RequiresConfirmation(
                reason = "Delete reusable skill '$name'?",
                details = "This permanently removes the local skill folder and SKILL.md content."
            )
        }

        val result = when (action) {
            "create" -> createSkill(name, arguments).map { skillPayload("create", it, arguments) }
            "update" -> updateSkill(name, arguments, patch = false)
            "patch" -> updateSkill(name, arguments, patch = true)
            "merge" -> mergeSkills(name, arguments).map { skillPayload("merge", it, arguments) }
            "archive" -> archiveSkill(name).map { basicPayload("archive", name) }
            "delete" -> skillRepository.deleteSkill(name).map { basicPayload("delete", name) }
            else -> Result.failure(IllegalArgumentException("Unsupported action: $action"))
        }

        result.fold(
            onSuccess = { payload ->
                ToolResult.Success(
                    output = payload.toString(),
                    metadata = mapOf("action" to action, "skill" to name)
                )
            },
            onFailure = { ToolResult.Error("Skill operation failed: ${it.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private suspend fun createSkill(name: String, arguments: Map<String, Any?>): Result<Skill> {
        val content = requiredContent(arguments)
        if (content.length < 120) return Result.failure(IllegalArgumentException("Skill content is too short/trivial."))
        if (memoryClassifier.containsSecret(content)) return Result.failure(IllegalArgumentException("Skill content appears to contain a secret."))
        if (skillRepository.getSkill(name) != null) return Result.failure(IllegalArgumentException("Skill already exists: $name"))
        val now = System.currentTimeMillis()
        val description = (arguments["description"] as? String)?.take(180)
            ?: content.lineSequence().firstOrNull { it.isNotBlank() }?.take(180)
            ?: "Reusable Amaya skill"
        val tags = (arguments["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val metadata = SkillMetadata(
            name = name,
            description = description,
            status = SkillStatus.ACTIVE,
            usageCount = 0,
            successCount = 0,
            failureCount = 0,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = null,
            createdBy = "agent",
            version = "1.0.0",
            tags = tags,
            enabled = true
        )
        val result = skillRepository.createSkill(Skill(metadata = metadata, content = content))
        if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Create failed"))
        val created = skillRepository.getSkill(name) ?: Skill(metadata, content)
        return Result.success(created)
    }

    private suspend fun updateSkill(name: String, arguments: Map<String, Any?>, patch: Boolean): Result<JSONObject> {
        val before = skillRepository.getSkill(name)
            ?: return Result.failure(IllegalArgumentException("Skill not found: $name"))
        val content = requiredContent(arguments)
        val operation = if (patch) skillRepository.patchSkill(name, content) else skillRepository.updateSkill(name, content)
        if (operation.isFailure) return Result.failure(operation.exceptionOrNull() ?: IllegalStateException("Update failed"))
        val after = skillRepository.getSkill(name) ?: before
        return Result.success(
            skillPayload(if (patch) "patch" else "update", after, arguments)
                .put("reason", arguments["reason"]?.toString().orEmpty())
                .put("summary", arguments["summary"]?.toString().orEmpty())
                .put("diff", buildCoarseDiff(before.content, after.content))
        )
    }

    private suspend fun mergeSkills(targetName: String, arguments: Map<String, Any?>): Result<Skill> {
        val content = requiredContent(arguments)
        if (content.length < 120) return Result.failure(IllegalArgumentException("Merged skill content is too short/trivial."))
        if (memoryClassifier.containsSecret(content)) return Result.failure(IllegalArgumentException("Skill content appears to contain a secret."))
        val sourceNames = (arguments["source_names"] as? List<*>)?.mapNotNull { it as? String }?.filter(String::isNotBlank)
            ?: return Result.failure(IllegalArgumentException("Missing required: source_names (array of skill names to merge)"))
        if (sourceNames.isEmpty()) return Result.failure(IllegalArgumentException("source_names cannot be empty."))

        val now = System.currentTimeMillis()
        val description = (arguments["description"] as? String)?.take(180)
            ?: content.lineSequence().firstOrNull { it.isNotBlank() }?.take(180)
            ?: "Consolidated Amaya skill"
        val tags = (arguments["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val targetMetadata = SkillMetadata(
            name = targetName,
            description = description,
            status = SkillStatus.ACTIVE,
            usageCount = 0,
            successCount = 0,
            failureCount = 0,
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now,
            createdBy = "agent-merge",
            version = "1.0.0",
            tags = tags,
            enabled = true
        )

        val mergeResult = skillRepository.mergeSkills(sourceNames, Skill(metadata = targetMetadata, content = content))
        if (mergeResult.isFailure) return Result.failure(mergeResult.exceptionOrNull() ?: IllegalStateException("Merge failed"))
        val target = skillRepository.getSkill(targetName) ?: Skill(targetMetadata, content)
        return Result.success(target)
    }

    private suspend fun archiveSkill(name: String): Result<Unit> {
        val skill = skillRepository.getSkill(name) ?: return Result.failure(IllegalArgumentException("Skill not found: $name"))
        return skillRepository.updateSkillMetadata(skill.metadata.copy(status = SkillStatus.ARCHIVED, updatedAt = System.currentTimeMillis()))
    }

    private fun skillPayload(action: String, skill: Skill, arguments: Map<String, Any?>): JSONObject {
        val metadata = skill.metadata
        return JSONObject()
            .put("action", action)
            .put("name", metadata.name)
            .put("description", metadata.description)
            .put("reason", arguments["reason"]?.toString().orEmpty())
            .put("summary", arguments["summary"]?.toString().orEmpty())
            .put("content", skill.content)
            .put("tags", JSONArray(metadata.tags))
            .put("enabled", metadata.enabled)
    }

    private fun basicPayload(action: String, name: String): JSONObject = JSONObject()
        .put("action", action)
        .put("name", name)

    private fun buildCoarseDiff(before: String, after: String): String {
        if (before == after) return ""
        val oldLines = before.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val newLines = after.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val oldSet = oldLines.toSet()
        val newSet = newLines.toSet()
        val removed = oldLines.filter { it !in newSet }.take(30)
        val added = newLines.filter { it !in oldSet }.take(40)
        return buildString {
            removed.forEach { appendLine("- $it") }
            added.forEach { appendLine("+ $it") }
            if (removed.size + added.size >= 70) appendLine("… truncated")
        }.trim().ifBlank {
            "- ${oldLines.firstOrNull().orEmpty().take(240)}\n+ ${newLines.firstOrNull().orEmpty().take(240)}"
        }
    }

    private fun requiredContent(arguments: Map<String, Any?>): String {
        return (arguments["content"] as? String)?.trim()
            ?: throw IllegalArgumentException("Missing required: content")
    }
}
