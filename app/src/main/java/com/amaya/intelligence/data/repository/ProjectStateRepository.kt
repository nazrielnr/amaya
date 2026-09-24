package com.amaya.intelligence.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Intelligence System phase B: live per-workspace project state.
 *
 * State is deliberately NOT memory. Memory records durable facts ("the project uses Kotlin");
 * state records what is true RIGHT NOW ("currently fixing an armv7a build crash"). Mixing the two
 * makes facts go stale whenever state changes. State is rewritten at every turn boundary from the
 * interaction evidence, kept at a bounded size, and injected into the prompt as its own section.
 */
data class ProjectState(
    val workspacePath: String,
    val currentGoal: String = "",
    val activeTasks: List<String> = emptyList(),
    val blockers: List<String> = emptyList(),
    val recentChanges: List<String> = emptyList(),
    val lastSuccessfulBuild: Long? = null,
    val lastFailedBuild: Long? = null,
    val stateVersion: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

interface ProjectStateRepository {
    suspend fun read(workspacePath: String?): ProjectState?
    suspend fun update(workspacePath: String, transform: (ProjectState?) -> ProjectState): Result<Unit>
    /** Record one completed turn: goal, blockers, file changes, and build outcome from evidence. */
    suspend fun recordTurn(context: CompletedInteractionContext): Result<Unit>
    suspend fun renderForContext(workspacePath: String?): String
}

@Singleton
class FileProjectStateRepository @Inject constructor(
    @ApplicationContext context: Context
) : ProjectStateRepository {
    private val file = File(context.filesDir, "memory/project-states.jsonl")
    private val fileLock = Any()

    override suspend fun read(workspacePath: String?): ProjectState? = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            readAll().firstOrNull { it.workspacePath == workspacePath }
        }
    }

    override suspend fun update(workspacePath: String, transform: (ProjectState?) -> ProjectState): Result<Unit> =
        withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                runCatching {
                    val records = readAll().toMutableList()
                    val index = records.indexOfFirst { it.workspacePath == workspacePath }
                    val current = if (index >= 0) records[index] else null
                    val next = transform(current).copy(workspacePath = workspacePath)
                    if (index >= 0) records[index] = next else records.add(next)
                    writeAll(records)
                }
            }
        }

    override suspend fun recordTurn(context: CompletedInteractionContext): Result<Unit> {
        if (context.workspacePath.isNullOrBlank()) return Result.success(Unit)
        val goal = context.userMessages.firstOrNull()?.let(::sanitizeLine)?.take(160).orEmpty()
        val blockers = context.toolResults
            .mapNotNull { result -> extractFailure(result)?.let(::sanitizeLine)?.take(140) }
            .distinct()
            .take(MAX_LIST_ITEMS)
        val changes = context.toolCalls
            .mapNotNull(::extractFileChange)
            .distinct()
            .take(MAX_LIST_ITEMS)
        val hasSuccessfulBuild = context.toolResults.any { BUILD_SUCCESS_PATTERNS.any { it in it.lowercase() } }
        val hasFailedBuild = context.toolResults.any { BUILD_FAILURE_PATTERNS.any { it in it.lowercase() } }
        val now = System.currentTimeMillis()
        return update(context.workspacePath) { current ->
            ProjectState(
                workspacePath = context.workspacePath,
                currentGoal = goal.ifBlank { current?.currentGoal.orEmpty() },
                activeTasks = (current?.activeTasks.orEmpty() + context.toolCalls
                    .mapNotNull(::extractTaskHint)).distinct().take(MAX_LIST_ITEMS),
                blockers = blockers.ifEmpty { current?.blockers.orEmpty() },
                recentChanges = changes.ifEmpty { current?.recentChanges.orEmpty() },
                lastSuccessfulBuild = if (hasSuccessfulBuild) now else current?.lastSuccessfulBuild,
                lastFailedBuild = if (hasFailedBuild) now else current?.lastFailedBuild,
                stateVersion = (current?.stateVersion ?: 0) + 1,
                updatedAt = now
            )
        }
    }

    override suspend fun renderForContext(workspacePath: String?): String = withContext(Dispatchers.IO) {
        if (workspacePath.isNullOrBlank()) return@withContext ""
        synchronized(fileLock) {
            val state = readAll().firstOrNull { it.workspacePath == workspacePath } ?: return@withContext ""
            buildString {
                if (state.currentGoal.isNotBlank()) appendLine("- Current goal: ${state.currentGoal}")
                if (state.activeTasks.isNotEmpty()) appendLine("- Active tasks: ${state.activeTasks.joinToString("; ")}")
                if (state.blockers.isNotEmpty()) appendLine("- Blockers: ${state.blockers.joinToString("; ")}")
                if (state.recentChanges.isNotEmpty()) appendLine("- Recent changes: ${state.recentChanges.joinToString("; ")}")
                state.lastSuccessfulBuild?.let { appendLine("- Last successful build: ${formatTime(it)}") }
                state.lastFailedBuild?.let { appendLine("- Last failed build: ${formatTime(it)}") }
            }.trim().ifBlank { "No recorded project state yet for this workspace." }
        }
    }

    private fun extractFailure(result: String): String? {
        val lower = result.lowercase()
        if (FAILURE_TERMS.none { it in lower }) return null
        val line = result.lineSequence().firstOrNull { line ->
            line.isNotBlank() && FAILURE_TERMS.any { it in line.lowercase() }
        } ?: return null
        return line.trim().take(160)
    }

    private fun extractFileChange(call: String): String? {
        val lower = call.lowercase()
        val tool = call.substringBefore(':').trim()
        if (tool !in FILE_CHANGE_TOOLS) return null
        return Regex("path=([^,}]+)").find(call)?.groupValues?.getOrNull(1)?.trim()
            ?.take(120)
            ?: "edited a workspace file"
    }

    private fun extractTaskHint(call: String): String? {
        if (!call.startsWith("update_todo")) return null
        return Regex("(?:task|title)=([^,}]+)").find(call)?.groupValues?.getOrNull(1)?.trim()?.take(100)
    }

    private fun sanitizeLine(text: String): String = text
        .replace(Regex("\\\\s+"), " ")
        .trim()

    private fun formatTime(millis: Long): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(formatter)
    }

    private fun readAll(): List<ProjectState> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line).toProjectState() }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun writeAll(records: List<ProjectState>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(records.joinToString("\n") { it.toJson().toString() } + if (records.isEmpty()) "" else "\n")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun ProjectState.toJson(): JSONObject = JSONObject()
        .put("workspacePath", workspacePath)
        .put("currentGoal", currentGoal)
        .put("activeTasks", JSONArray(activeTasks))
        .put("blockers", JSONArray(blockers))
        .put("recentChanges", JSONArray(recentChanges))
        .put("lastSuccessfulBuild", lastSuccessfulBuild)
        .put("lastFailedBuild", lastFailedBuild)
        .put("stateVersion", stateVersion)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toProjectState(): ProjectState = ProjectState(
        workspacePath = optString("workspacePath"),
        currentGoal = optString("currentGoal"),
        activeTasks = stringList("activeTasks"),
        blockers = stringList("blockers"),
        recentChanges = stringList("recentChanges"),
        lastSuccessfulBuild = if (has("lastSuccessfulBuild") && !isNull("lastSuccessfulBuild")) optLong("lastSuccessfulBuild") else null,
        lastFailedBuild = if (has("lastFailedBuild") && !isNull("lastFailedBuild")) optLong("lastFailedBuild") else null,
        stateVersion = optInt("stateVersion", 1).coerceAtLeast(1),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

    private fun JSONObject.stringList(key: String): List<String> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) } }
            ?.filter(String::isNotBlank).orEmpty()

    companion object {
        private const val MAX_LIST_ITEMS = 5
        private val FAILURE_TERMS = listOf("error", "failed", "failure", "exception", "crash", "gagal")
        private val FILE_CHANGE_TOOLS = setOf("write_file", "edit_file", "workspace_change", "patch", "file_edit", "apply_patch")
        private val BUILD_SUCCESS_PATTERNS = listOf("build successful", "assemble debug", "build succeeded", "compilation succeeded", "task :app:assemble")
        private val BUILD_FAILURE_PATTERNS = listOf("build failed", "assemble failed", "execution failed", "compilation failed", "failed to build", "error: failed")
    }
}
