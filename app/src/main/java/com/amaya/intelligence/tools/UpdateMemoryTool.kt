package com.amaya.intelligence.tools

import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proposal-based memory tool. It classifies and deduplicates before writing structured records.
 * Markdown is imported only for legacy migration. Skills and reminders use their own domains.
 */
@Singleton
class UpdateMemoryTool @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memoryClassifier: MemoryClassifier,
    private val workspaceMemoryStore: FileWorkspaceMemoryStore
) : Tool, ContextAwareTool {

    override val name = "update_memory"

    override val description = """
        Store explicit durable memory requested by the user. This tool classifies, safety-checks, and deduplicates before writing through MemoryRepository.

        Use only when the user explicitly asks Amaya to remember an important preference, stable fact, or workspace fact. Do not use for inferred guesses.
        Do not store passwords, API keys, access tokens, refresh tokens, OTPs, session cookies, private credentials, payment data, or temporary guesses.
        Use create_reminder for reminders instead of writing reminders to memory.

        Arguments:
        - title (string, optional): Short professional header, 2-7 words. Good: "Response language preference".
        - content (string, required): Final durable memory text, written like a concise summary. Do not copy the user's command. Do not include phrases such as "remember", "tolong ingat", "user asked/discussed", or "user preference/profile". Good: "The user prefers English for responses." / "The user works at an office." Bad: "tolong ingat pakai bahasa Inggris".
        - type (string, optional): user_profile or workspace_fact.
        - action (string, optional): add, replace, ignore. Default add.
        - reason (string, optional): Specific reason this memory is durable, e.g. "The user explicitly asked Amaya to remember their response-language preference." Do not use a generic reason.
        - confidence (number, optional): 0.0-1.0. Low-confidence proposals are ignored.
        - ttl_days (integer, optional): Explicit duration in days before memory expires.
        - volatility (string, optional): stable, moderate, or perishable.

    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext): ToolResult = withContext(Dispatchers.IO) {
        val content = arguments["content"] as? String
            ?: return@withContext ToolResult.Error("Missing required: content", ErrorType.VALIDATION_ERROR)
        val workspaceId = context.workspacePath?.let { workspaceMemoryStore.resolve(it)?.id }
        val ttlDays = (arguments["ttl_days"] as? Number)?.toLong()
        val ttlMillis = ttlDays?.takeIf { it > 0L }?.let { it * 24L * 60L * 60L * 1000L }
        val volatility = com.amaya.intelligence.domain.memory.MemoryVolatility.parse(arguments["volatility"] as? String)
        val proposal = memoryClassifier.classify(
            content = content,
            requestedType = parseType(arguments),
            requestedAction = parseAction(arguments["action"] as? String),
            requestedTitle = arguments["title"] as? String,
            reason = arguments["reason"] as? String ?: "Agent requested memory update",
            confidence = (arguments["confidence"] as? Number)?.toDouble() ?: 0.8,
            workspacePath = context.workspacePath,
            workspaceId = workspaceId,
            sourceConversationId = context.conversationId,
            ttlMillis = ttlMillis,
            requestedVolatility = volatility
        )

        if (proposal.type == MemoryType.WORKSPACE_FACT && context.workspacePath.isNullOrBlank()) {
            return@withContext ToolResult.Error("Select a workspace before saving workspace memory.", ErrorType.VALIDATION_ERROR)
        }
        val ignored = proposal.action == MemoryAction.IGNORE
        val result = if (ignored) Result.success(proposal.reason) else memoryRepository.applyProposal(proposal)
        result.fold(
            onSuccess = { message -> successOutput(proposal, ignored, message) },
            onFailure = { error -> ToolResult.Error("Failed to update memory: ${error.message}", ErrorType.EXECUTION_ERROR) }
        )
    }

    private fun successOutput(
        proposal: com.amaya.intelligence.domain.memory.MemoryProposal,
        ignored: Boolean,
        message: String
    ): ToolResult.Success {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val status = if (ignored) "ignored" else "applied"
        return ToolResult.Success(
            output = JSONObject()
                .put("id", proposal.id)
                .put("status", status)
                .put("message", message)
                .put("type", proposal.type.name.lowercase())
                .put("action", proposal.action.name.lowercase())
                .put("scope", proposal.scope.name.lowercase())
                .put("date", today)
                .put("title", proposal.title)
                .put("content", proposal.content)
                .put("reason", proposal.reason)
                .toString(),
            metadata = mapOf(
                "status" to status,
                "message" to message,
                "type" to proposal.type.name.lowercase(),
                "action" to proposal.action.name.lowercase(),
                "title" to proposal.title,
                "content" to proposal.content,
                "reason" to proposal.reason
            )
        )
    }

    private fun parseType(arguments: Map<String, Any?>): MemoryType? {
        val raw = (arguments["type"] as? String)?.lowercase()
        return when (raw) {
            "user_profile", "user" -> MemoryType.USER_PROFILE
            "workspace_fact", "workspace" -> MemoryType.WORKSPACE_FACT
            else -> null
        }
    }

    private fun parseAction(raw: String?): MemoryAction = when (raw?.lowercase()) {
        "replace" -> MemoryAction.REPLACE
        "ignore" -> MemoryAction.IGNORE
        else -> MemoryAction.ADD
    }

}
