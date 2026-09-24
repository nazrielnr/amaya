package com.amaya.intelligence.tools

import com.amaya.intelligence.data.repository.RecommendationRepository
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationPriority
import com.amaya.intelligence.domain.memory.RecommendationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Intelligence System: recommendation_manage tool.
 *
 * Lifecycle: suggested → accepted → in_progress → verified → completed (or archived at any point
 * before completion). VERIFIED is evidence-gated: [verify] only succeeds when the supplied evidence
 * text satisfies the recommendation's verification rule, so "the user says done" (complete) and
 * "the system proved done" (verify) remain distinct.
 */
@Singleton
class RecommendationManageTool @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) : Tool, ContextAwareTool {
    override val name = "recommendation_manage"
    override val description = "List, create, or advance implementation recommendations for the current workspace. " +
        "Lifecycle: suggested -> accepted -> in_progress -> verified -> completed; archive drops one. " +
        "verified requires evidence text that matches the recommendation's verification rule (independent system confirmation); " +
        "completed is a user claim. Actions: list, suggest, accept, start, verify, complete, archive. Never use for secrets."

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        when (val action = (arguments["action"] as? String)?.lowercase()) {
            "list", "search" -> list(arguments, context.workspacePath)
            "suggest" -> suggest(arguments, context.workspacePath)
            "accept" -> transition(arguments, RecommendationStatus.ACCEPTED, "accept")
            "start" -> transition(arguments, RecommendationStatus.IN_PROGRESS, "start")
            "verify" -> verify(arguments)
            "complete" -> transition(arguments, RecommendationStatus.COMPLETED, "complete")
            "archive" -> transition(arguments, RecommendationStatus.ARCHIVED, "archive")
            null -> ToolResult.Error("Missing required: action", ErrorType.VALIDATION_ERROR)
            else -> ToolResult.Error("Unsupported action: $action", ErrorType.VALIDATION_ERROR)
        }
    }

    private suspend fun list(arguments: Map<String, Any?>, workspacePath: String?): ToolResult {
        val statuses = parseStatuses(arguments["status"] as? String)
        val records = recommendationRepository.list(
            workspacePath = workspacePath,
            statuses = statuses,
            limit = ((arguments["limit"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)
        )
        return ToolResult.Success(
            output = JSONObject().put("results", JSONArray(records.map { it.toJson() })).toString(),
            metadata = mapOf("count" to records.size)
        )
    }

    private suspend fun suggest(arguments: Map<String, Any?>, workspacePath: String?): ToolResult {
        val title = arguments["title"] as? String
            ?: return ToolResult.Error("Missing required: title", ErrorType.VALIDATION_ERROR)
        if (workspacePath.isNullOrBlank()) {
            return ToolResult.Error("Suggesting recommendations requires an active workspace", ErrorType.VALIDATION_ERROR)
        }
        val relatedMemoryIds = splitIds(arguments["related_memory_ids"] as? String)
        val relatedSkillIds = splitIds(arguments["related_skill_ids"] as? String)
        return recommendationRepository.suggest(
            workspacePath = workspacePath,
            title = title,
            rationale = arguments["rationale"] as? String ?: "",
            priority = RecommendationPriority.fromString(arguments["priority"] as? String),
            verificationRule = arguments["verification_rule"] as? String ?: "",
            relatedMemoryIds = relatedMemoryIds,
            relatedSkillIds = relatedSkillIds
        ).fold(
            onSuccess = { id ->
                ToolResult.Success(
                    output = JSONObject()
                        .put("id", id)
                        .put("title", title.trim())
                        .put("status", RecommendationStatus.SUGGESTED.name.lowercase())
                        .toString()
                )
            },
            onFailure = { ToolResult.Error("Suggest failed: ${it.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private suspend fun transition(arguments: Map<String, Any?>, target: RecommendationStatus, verb: String): ToolResult {
        val id = arguments["id"] as? String
            ?: return ToolResult.Error("Missing required: id", ErrorType.VALIDATION_ERROR)
        return recommendationRepository.transition(id, target).fold(
            onSuccess = { recommendation ->
                ToolResult.Success(
                    output = JSONObject()
                        .put("id", recommendation.id)
                        .put("status", recommendation.status.name.lowercase())
                        .toString()
                )
            },
            onFailure = { ToolResult.Error("$verb failed: ${it.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private suspend fun verify(arguments: Map<String, Any?>): ToolResult {
        val id = arguments["id"] as? String
            ?: return ToolResult.Error("Missing required: id", ErrorType.VALIDATION_ERROR)
        val evidence = arguments["evidence"] as? String
            ?: return ToolResult.Error("Missing required: evidence", ErrorType.VALIDATION_ERROR)
        return recommendationRepository.verify(id, evidence).fold(
            onSuccess = { recommendation ->
                ToolResult.Success(
                    output = JSONObject()
                        .put("id", recommendation.id)
                        .put("status", recommendation.status.name.lowercase())
                        .put("evidence_count", recommendation.evidence.size)
                        .toString()
                )
            },
            onFailure = { ToolResult.Error("verify failed: ${it.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private fun Recommendation.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("rationale", rationale)
        .put("priority", priority.name.lowercase())
        .put("status", status.name.lowercase())
        .put("verification_rule", verificationRule)
        .put("evidence", JSONArray(evidence))
        .put("updated_at", updatedAt)

    private fun parseStatuses(raw: String?): Set<RecommendationStatus>? {
        if (raw.isNullOrBlank()) return null
        val parsed = raw.split(',', ';').mapNotNull { RecommendationStatus.fromString(it.trim()) }.toSet()
        return parsed.ifEmpty { null }
    }

    private fun splitIds(raw: String?): List<String> =
        raw?.split(',', ';')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
}
