package com.amaya.intelligence.data.repository

import android.content.Context
import com.amaya.intelligence.data.local.files.FileWorkspaceMemoryStore
import com.amaya.intelligence.data.local.files.canonicalWorkspacePath
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.EmbeddingClient
import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.MemoryDeduper
import com.amaya.intelligence.domain.memory.MemoryProposal
import com.amaya.intelligence.domain.memory.MemoryScope
import com.amaya.intelligence.domain.memory.MemoryStatus
import com.amaya.intelligence.domain.memory.MemoryType
import com.amaya.intelligence.domain.memory.MemoryVolatility
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileMemoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val classifier: MemoryClassifier,
    private val deduper: MemoryDeduper,
    private val workspaceStore: FileWorkspaceMemoryStore,
    private val settingsManager: AiSettingsManager,
    private val embeddingClient: EmbeddingClient
) : MemoryRepository {
    private val fileLock = Any()
    /** Small LRU of embedded text vectors, keyed by `endpoint|model|text-hash`. Synchronized because
     * semantic reranking runs outside [fileLock] (a suspend call inside a critical section is an
     * error), so concurrent searches may touch the cache. */
    private val vectorCache: MutableMap<String, List<Float>> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Float>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Float>>?): Boolean = size > VECTOR_CACHE_MAX
        }
    )

    private val memoryDir: File
        get() = File(context.filesDir, "memory").also { it.mkdirs() }
    private val recordsFile: File get() = File(memoryDir, "records.jsonl")
    private val migrationMarker: File get() = File(memoryDir, ".structured-memory-v1")

    override suspend fun applyProposal(proposal: MemoryProposal): Result<String> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                if (proposal.action == MemoryAction.IGNORE) return@runCatching "Ignored: ${proposal.reason}"
                require(proposal.confidence >= MIN_CONFIDENCE) { "Memory confidence is too low." }
                require(!classifier.containsSecret(proposal.content)) { "Rejected because content appears to contain a secret." }
                when (proposal.type) {
                    MemoryType.USER_PROFILE -> applyStructuredMemory(proposal, "User Profile")
                    MemoryType.WORKSPACE_FACT -> {
                        require(!proposal.workspacePath.isNullOrBlank()) { "Workspace memory requires an active workspace." }
                        applyStructuredMemory(proposal, "Project Memory")
                    }
                    MemoryType.DECISION -> {
                        require(!proposal.workspacePath.isNullOrBlank()) { "Project decisions require an active workspace." }
                        applyStructuredMemory(proposal, "Project Decision")
                    }
                }
            }
        }
    }

    override suspend fun readUserProfile(): String = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            ensureMigrated()
            renderMemory(MemoryType.USER_PROFILE, MemoryScope.USER, null, "User Profile", "Learned Preferences")
        }
    }

    override suspend fun readWorkspaceFacts(workspacePath: String?): String = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            ensureMigrated()
            val root = workspacePath?.takeIf(String::isNotBlank)?.let(::canonicalWorkspacePath)
                ?: return@synchronized emptySnapshot("Project Memory", "Workspace Facts")
            renderMemory(MemoryType.WORKSPACE_FACT, MemoryScope.WORKSPACE, root, "Project Memory", "Workspace Facts")
        }
    }

    override suspend fun listWorkspaceBindings(): List<WorkspaceMemoryBinding> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            ensureMigrated()
            workspaceStore.list().map { location ->
                WorkspaceMemoryBinding(
                    id = location.id,
                    root = location.root,
                    recordCount = activeMemoryRecords().count { it.workspaceId == location.id },
                    rootExists = File(location.root).isDirectory
                )
            }
                .sortedBy { it.root }
        }
    }

    override suspend fun remapWorkspace(workspaceId: String, newRoot: String): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                require(UUID_REGEX.matches(workspaceId)) { "Invalid workspace id." }
                val canonicalRoot = canonicalWorkspacePath(newRoot)
                require(File(canonicalRoot).isDirectory) { "New workspace root is not a directory: $newRoot" }
                val location = workspaceStore.remap(workspaceId, canonicalRoot)
                writeRecords(location.recordsFile, readRecords(location.recordsFile).map {
                    it.copy(workspacePath = location.root, workspaceId = location.id)
                })
            }
        }
    }

    override suspend fun compactStoredMemory(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                recordFiles().forEach { file ->
                    val compacted = readRecords(file)
                        .filter { it.type in DURABLE_TYPES }
                        .distinctBy { listOf(it.id, it.version, it.status, it.content, it.updatedAt) }
                        .groupBy { it.id }
                        .flatMap { (_, revisions) -> revisions.sortedWith(compareByDescending<MemoryRecord> { it.version }.thenByDescending { it.updatedAt }).take(MAX_REVISIONS_PER_MEMORY) }
                        .sortedWith(compareBy<MemoryRecord> { it.updatedAt }.thenBy { it.version })
                    writeRecords(file, compacted)
                }
            }
        }
    }

    override suspend fun runHousekeeping(): Result<MemoryHousekeepingReport> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                var archivedCount = 0
                var cappedCount = 0
                var decayedCount = 0
                var expiredCount = 0
                val now = System.currentTimeMillis()
                recordFiles().forEach { file ->
                    val records = readRecords(file)
                    if (records.isEmpty()) return@forEach
                    var changed = false
                    val decayed = records.map { record ->
                        if (record.status != MemoryStatus.ACTIVE) return@map record
                        // Explicit TTL expiration check
                        if (record.expiresAt != null && record.expiresAt <= now) {
                            expiredCount++
                            changed = true
                            return@map record.copy(status = MemoryStatus.SUPERSEDED, updatedAt = now)
                        }
                        val factor = decayFactor(record)
                        when {
                            factor < ARCHIVE_DECAY_FLOOR -> {
                                archivedCount++
                                changed = true
                                record.copy(status = MemoryStatus.SUPERSEDED, updatedAt = now)
                            }
                            factor < 1.0 -> {
                                decayedCount++
                                record
                            }
                            else -> record
                        }
                    }
                    val active = decayed.filter { it.status == MemoryStatus.ACTIVE }
                    val finalized = if (active.size > MEMORY_CAP_PER_SCOPE) {
                        val keepIds = active.sortedByDescending { priorityScore(it, 1.0) }
                            .take(MEMORY_CAP_PER_SCOPE)
                            .map { it.id to it.version }
                            .toSet()
                        decayed.map { record ->
                            if (record.status == MemoryStatus.ACTIVE && (record.id to record.version) !in keepIds) {
                                cappedCount++
                                changed = true
                                record.copy(status = MemoryStatus.SUPERSEDED, updatedAt = now)
                            } else record
                        }
                    } else decayed
                    if (changed) writeRecords(file, finalized)
                }
                MemoryHousekeepingReport(
                    archivedCount = archivedCount,
                    cappedCount = cappedCount,
                    decayedCount = decayedCount,
                    expiredCount = expiredCount
                )
            }
        }
    }

    override suspend fun listMemoryRecords(
        type: MemoryType?,
        query: String?,
        limit: Int,
        workspacePath: String?
    ): List<MemoryRecord> = withContext(Dispatchers.IO) {
        // Records are read under the file lock, but lexical scoring and the optional semantic
        // rerank run outside it — rerankWithEmbeddings is a suspend call (network), which is not
        // allowed inside a critical section.
        val scoped = synchronized(fileLock) {
            ensureMigrated()
            val canonicalWorkspace = workspacePath?.takeIf(String::isNotBlank)?.let(::canonicalWorkspacePath)
            activeMemoryRecords()
                .filter { type == null || it.type == type }
                .filter { it.type !in WORKSPACE_SCOPED_TYPES || it.workspacePath == canonicalWorkspace }
        }
        val ranked = if (query.isNullOrBlank()) {
            scoped.sortedByDescending { it.updatedAt }.map { it to 0.0 }
        } else {
            val lexical = scoped.map { it to scoreMemoryRecord(it, query) }
                .filter { it.second >= MIN_SEARCH_SCORE }
                .sortedByDescending { it.second }
            val blended = rerankWithEmbeddings(lexical, query) ?: lexical
            // Fusion priority (scheme §2): relevance × volatility decay × confidence. Decay is applied
            // after the relevance threshold so stale memories are ranked lower, not filtered out.
            blended.map { (record, relevance) -> record to priorityScore(record, relevance) }
                .sortedByDescending { it.second }
        }
        ranked.map { it.first }.take(limit.coerceIn(1, MAX_LIST_LIMIT))
    }

    override suspend fun updateMemoryById(
        id: String,
        content: String,
        expectedVersion: Int,
        workspacePath: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                val clean = content.trim()
                require(clean.isNotBlank()) { "Content is required." }
                require(!classifier.containsSecret(clean)) { "Rejected because content appears to contain a secret." }
                val record = findActiveMemory(id, workspacePath)
                require(record.version == expectedVersion) {
                    "Memory conflict: expected version $expectedVersion, current version is ${record.version}. Refresh the memory before updating."
                }
                val now = System.currentTimeMillis()
                supersedeMemoryRecord(record, now)
                appendMemoryRecord(record.copy(
                    action = MemoryAction.REPLACE,
                    title = inferTitle(clean, record.type),
                    content = clean,
                    reason = "Updated manually.",
                    updatedAt = now,
                    version = record.version + 1,
                    source = "structured",
                    status = MemoryStatus.ACTIVE
                ))
                "Updated memory ${record.id} to version ${record.version + 1}. This affects the next chat."
            }
        }
    }

    override suspend fun appendEvidence(
        id: String,
        evidenceLine: String,
        workspacePath: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                val clean = evidenceLine.trim()
                require(clean.isNotBlank()) { "Evidence line is required." }
                val record = findActiveMemory(id, workspacePath)
                val now = System.currentTimeMillis()
                supersedeMemoryRecord(record, now)
                appendMemoryRecord(record.copy(
                    action = MemoryAction.REPLACE,
                    reason = "Verification evidence appended.",
                    updatedAt = now,
                    version = record.version + 1,
                    evidence = (record.evidence + clean.take(300)).take(MAX_EVIDENCE_LINES)
                ))
            }
        }
    }

    override suspend fun deleteMemoryById(
        id: String,
        expectedVersion: Int,
        workspacePath: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                val record = findActiveMemory(id, workspacePath)
                require(record.version == expectedVersion) {
                    "Memory conflict: expected version $expectedVersion, current version is ${record.version}. Refresh the memory before deleting."
                }
                supersedeMemoryRecord(record, System.currentTimeMillis())
                "Deleted memory ${record.id}. This affects the next chat."
            }
        }
    }

    override suspend fun confirmMemory(id: String, workspacePath: String?): Result<String> = withContext(Dispatchers.IO) {
        synchronized(fileLock) {
            runCatching {
                ensureMigrated()
                val record = findActiveMemory(id, workspacePath)
                val now = System.currentTimeMillis()
                // Content is unchanged; only the confirmation status advances. Supersede + append so
                // the active record (last per id) carries the new verified state.
                supersedeMemoryRecord(record, now)
                appendMemoryRecord(record.copy(
                    updatedAt = now,
                    verified = true,
                    verifyCount = record.verifyCount + 1,
                    lastConfirmedAt = now
                ))
                "Confirmed memory ${record.id} (verified)."
            }
        }
    }

    private fun findActiveMemory(id: String, workspacePath: String?): MemoryRecord {
        val canonicalWorkspace = workspacePath?.takeIf(String::isNotBlank)?.let(::canonicalWorkspacePath)
        return activeMemoryRecords().firstOrNull {
            it.id == id && (it.type !in WORKSPACE_SCOPED_TYPES || it.workspacePath == canonicalWorkspace)
        } ?: throw IllegalArgumentException("Memory not found: $id")
    }

    private fun applyStructuredMemory(proposal: MemoryProposal, label: String): String {
        val canonicalWorkspace = proposal.workspacePath?.takeIf(String::isNotBlank)?.let(::canonicalWorkspacePath)
        val finalContent = replacementValue(proposal.content)
        require(finalContent.isNotBlank()) { "Memory content is required." }
        val candidate = MemoryRecord(
            id = proposal.id,
            type = proposal.type,
            action = proposal.action,
            scope = proposal.scope,
            target = targetFor(proposal.type, canonicalWorkspace),
            label = label,
            title = proposal.title,
            content = finalContent,
            reason = proposal.reason,
            confidence = proposal.confidence,
            createdAt = proposal.createdAt,
            updatedAt = proposal.createdAt,
            expiresAt = proposal.expiresAt,
            source = "structured",
            workspacePath = canonicalWorkspace,
            workspaceId = proposal.workspaceId ?: canonicalWorkspace?.let(::workspaceId),
            subject = proposal.subject.ifBlank { defaultSubject(proposal.scope) },
            attribute = proposal.attribute.ifBlank { inferAttribute(finalContent, proposal.title, proposal.type) },
            sourceConversationId = proposal.sourceConversationId,
            volatility = proposal.volatility ?: MemoryVolatility.fromType(proposal.type),
            evidence = proposal.evidence
        ).withIdentity()
        val allActive = activeMemoryRecords()
        val existing = allActive.firstOrNull { memoryIdentity(it) == memoryIdentity(candidate) }
            ?: allActive.firstOrNull {
                it.type == candidate.type &&
                it.scope == candidate.scope &&
                (candidate.scope != MemoryScope.WORKSPACE || it.workspacePath == candidate.workspacePath) &&
                (it.attribute.isNotBlank() && it.attribute == candidate.attribute || deduper.isContradictionOrUpdate(candidate.content, it.content))
            }

        if (existing != null && deduper.isDuplicate(candidate.content, existing.content)) {
            if (candidate.confidence > existing.confidence && !existing.verified) {
                val now = System.currentTimeMillis()
                supersedeMemoryRecord(existing, now, supersededById = candidate.id)
                appendMemoryRecord(existing.copy(
                    confidence = candidate.confidence,
                    updatedAt = now,
                    expiresAt = candidate.expiresAt ?: existing.expiresAt,
                    version = existing.version + 1,
                    evidence = (existing.evidence + candidate.evidence).distinct().take(MAX_EVIDENCE_LINES)
                ))
                return "Refreshed confidence for $label."
            }
            return "Skipped duplicate $label."
        }

        val now = System.currentTimeMillis()
        // Phase B temporal validity: the previous revision is marked superseded and points at the
        // replacing record (valid → superseded → archived), instead of silently vanishing.
        if (existing != null) supersedeMemoryRecord(existing, now, supersededById = candidate.id)
        appendMemoryRecord(candidate.copy(
            id = existing?.id ?: candidate.id,
            version = existing?.version?.plus(1) ?: candidate.version,
            createdAt = existing?.createdAt ?: candidate.createdAt,
            updatedAt = now
        ))
        return if (existing == null) "Saved to $label." else "Updated $label (resolved update/contradiction)."
    }

    private fun activeMemoryRecords(): List<MemoryRecord> {
        val byIdentity = linkedMapOf<String, MemoryRecord>()
        val latestById = readAllRecords().groupBy { it.id }.mapNotNull { (_, records) -> records.lastOrNull() }
        latestById.asSequence()
            .filter { it.status == MemoryStatus.ACTIVE }
            .filter { it.expiresAt == null || it.expiresAt > System.currentTimeMillis() }
            .sortedBy { it.updatedAt }
            .forEach { record ->
                val key = memoryIdentity(record)
                val current = byIdentity[key]
                if (current == null || record.version > current.version || record.updatedAt >= current.updatedAt) byIdentity[key] = record
            }
        return byIdentity.values.toList()
    }

    private fun renderMemory(
        type: MemoryType,
        scope: MemoryScope,
        workspacePath: String?,
        title: String,
        section: String
    ): String {
        val records = activeMemoryRecords()
            .filter { it.type == type && it.scope == scope }
            .filter { type != MemoryType.WORKSPACE_FACT || it.workspacePath == workspacePath }
            .sortedByDescending { it.updatedAt }
        if (records.isEmpty()) return emptySnapshot(title, section)
        return buildString {
            appendLine("# $title")
            appendLine()
            appendLine("## $section")
            records.forEach { appendLine("- ${it.content}") }
        }.trimEnd() + "\n"
    }

    private fun emptySnapshot(title: String, section: String): String = "# $title\n\n## $section\n"

    private fun appendMemoryRecord(record: MemoryRecord) {
        val normalized = record.withIdentity()
        val file = recordFile(normalized)
        file.parentFile?.mkdirs()
        file.appendText(normalized.toJson().toString() + "\n")
    }

    private fun supersedeMemoryRecord(record: MemoryRecord, updatedAt: Long, supersededById: String? = null) {
        val file = recordFile(record)
        val records = readRecords(file).toMutableList()
        val index = records.indexOfLast { it.id == record.id && it.version == record.version && it.status == MemoryStatus.ACTIVE }
        val superseded = record.copy(status = MemoryStatus.SUPERSEDED, updatedAt = updatedAt, supersededById = supersededById)
        if (index >= 0) {
            records[index] = records[index].copy(status = MemoryStatus.SUPERSEDED, updatedAt = updatedAt, supersededById = supersededById)
            writeRecords(file, records)
        } else {
            appendMemoryRecord(superseded)
        }
    }

    private fun recordFile(record: MemoryRecord): File = record.workspacePath
        ?.takeIf { record.scope == MemoryScope.WORKSPACE }
        ?.let(::workspaceRecordsFile)
        ?: recordsFile

    private fun readAllRecords(): List<MemoryRecord> = recordFiles().flatMap(::readRecords)

    private fun recordFiles(): List<File> = buildList {
        add(recordsFile)
        val workspaceRoot = File(memoryDir, "workspaces")
        if (workspaceRoot.exists()) addAll(workspaceRoot.walkTopDown().filter { it.isFile && it.name == "records.jsonl" })
    }.distinct()

    private fun readRecords(file: File): List<MemoryRecord> = runCatching {
        if (!file.exists()) return emptyList()
        val parent = file.parentFile
        val metadataRoot = if (parent?.parentFile?.name == "workspaces") workspaceRootFromMetadata(parent) else null
        file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line).toMemoryRecordOrNull() }.getOrNull()?.let { record ->
                when {
                    record.workspacePath == null && metadataRoot != null -> record.copy(workspacePath = metadataRoot, workspaceId = parent?.name)
                    record.workspaceId == null && metadataRoot != null -> record.copy(workspaceId = parent?.name)
                    else -> record
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun writeRecords(file: File, records: List<MemoryRecord>) {
        val content = records.joinToString("\n") { it.withIdentity().toJson().toString() }
        atomicWrite(file, content)
    }

    private fun ensureMigrated() {
        deleteLegacyDailyLogs()
        if (migrationMarker.exists()) {
            purgeRemovedMemoryTypes()
            return
        }
        val existingKeys = readAllRecords().map(::migrationKey).toMutableSet()
        val imports = mutableListOf<MemoryRecord>()
        imports += readLegacyIndex(File(memoryDir, "index.jsonl"))
        imports += buildRecordsFromMarkdown(File(memoryDir, "USER.md"), MemoryType.USER_PROFILE, MemoryScope.USER, "User Profile")
        imports += buildLegacyUserRecords(File(memoryDir, "MEMORY.md"))
        val legacyProjectFiles = listOf(File(memoryDir, "PROJECT.md")).filter(File::isFile)
        if (legacyProjectFiles.isNotEmpty()) {
            val legacyWorkspace = workspaceStore.legacyUnmapped()
            legacyProjectFiles.forEach { file ->
                imports += buildRecordsFromMarkdown(file, MemoryType.WORKSPACE_FACT, MemoryScope.WORKSPACE, "Project Memory", legacyWorkspace.root)
            }
        }
        File(memoryDir, "workspaces").listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
            val root = workspaceRootFromMetadata(directory) ?: return@forEach
            imports += buildRecordsFromMarkdown(File(directory, "MEMORY.md"), MemoryType.WORKSPACE_FACT, MemoryScope.WORKSPACE, "Project Memory", root)
        }
        imports.asSequence()
            .filter { it.type in DURABLE_TYPES }
            .filter { existingKeys.add(migrationKey(it)) }
            .forEach { appendMemoryRecord(it.copy(source = "legacy-migration")) }
        File(memoryDir, "pending-proposals.jsonl").takeIf(File::exists)?.let { file ->
            val kept = file.readLines().filterNot { line -> runCatching { JSONObject(line).optString("type") in setOf("USER_PROFILE", "DAILY_LOG") }.getOrDefault(false) }
            atomicWrite(file, kept.joinToString("\n"))
        }
        atomicWrite(migrationMarker, "completed")
        purgeRemovedMemoryTypes()
    }

    private fun purgeRemovedMemoryTypes() {
        recordFiles().forEach { file ->
            val kept = if (!file.exists()) emptyList() else file.readLines().filter { line ->
                runCatching { JSONObject(line).optString("type") in DURABLE_TYPES.map(MemoryType::name) }.getOrDefault(false)
            }
            if (file.exists()) atomicWrite(file, kept.joinToString("\n"))
        }
        File(memoryDir, "pending-proposals.jsonl").takeIf(File::exists)?.let { file ->
            val kept = file.readLines().filter { line ->
                runCatching { JSONObject(line).optString("type") !in setOf("USER_PROFILE", "LONG_TERM_MEMORY") }.getOrDefault(false)
            }
            atomicWrite(file, kept.joinToString("\n"))
        }
    }

    private fun deleteLegacyDailyLogs() {
        memoryDir.listFiles().orEmpty()
            .filter { it.isFile && LEGACY_DAILY_FILE.matches(it.name) }
            .forEach(File::delete)
    }

    private fun readLegacyIndex(file: File): List<MemoryRecord> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line -> runCatching { JSONObject(line).toMemoryRecordOrNull() }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun buildRecordsFromMarkdown(
        file: File,
        type: MemoryType,
        scope: MemoryScope,
        label: String,
        workspacePath: String? = null
    ): List<MemoryRecord> {
        if (!file.exists() || file.readText().isBlank()) return emptyList()
        val now = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        return file.readLines().asSequence()
            .map(String::trim)
            .filter { it.startsWith("-") || it.startsWith("*") }
            .map { it.trimStart('-', '*').trim() }
            .filter(String::isNotBlank)
            .map { content ->
                MemoryRecord(
                    id = stableMemoryId(file.path, content),
                    type = type,
                    action = MemoryAction.ADD,
                    scope = scope,
                    target = targetFor(type, workspacePath),
                    label = label,
                    title = inferTitle(content, type),
                    content = content,
                    reason = "Migrated from legacy markdown memory.",
                    confidence = 0.8,
                    createdAt = now,
                    updatedAt = now,
                    source = "legacy-migration",
                    workspacePath = workspacePath,
                    workspaceId = workspacePath?.let(::workspaceId),
                    subject = defaultSubject(scope),
                    attribute = inferAttribute(content, inferTitle(content, type), type),
                    volatility = MemoryVolatility.fromType(type)
                )
            }.toList()
    }

    private fun buildLegacyUserRecords(file: File): List<MemoryRecord> =
        buildRecordsFromMarkdown(file, MemoryType.USER_PROFILE, MemoryScope.USER, "User Profile")
            .filter { record -> USER_FACT_TERMS.any { it in record.content.lowercase() } }

    private fun workspaceRecordsFile(workspacePath: String): File =
        requireNotNull(workspaceStore.resolve(workspacePath)).recordsFile

    private fun workspaceId(workspacePath: String): String =
        requireNotNull(workspaceStore.resolve(workspacePath)).id

    private fun workspaceRootFromMetadata(directory: File): String? = File(directory, "workspace.json")
        .takeIf(File::isFile)
        ?.let { runCatching { JSONObject(it.readText()).optString("root") }.getOrNull() }
        ?.takeIf(String::isNotBlank)
        ?.let(::canonicalWorkspacePath)


    private fun targetFor(type: MemoryType, workspacePath: String?): String = when (type) {
        MemoryType.USER_PROFILE -> "records.jsonl#user"
        MemoryType.WORKSPACE_FACT,
        MemoryType.DECISION -> "workspaces/${workspacePath?.let(::workspaceId)}/records.jsonl"
    }

    private fun memoryIdentity(record: MemoryRecord): String {
        val normalized = record.withIdentity()
        return "${normalized.type}:${normalized.scope}:${normalized.workspacePath.orEmpty()}:${normalized.subject}:${normalized.attribute}"
    }

    private fun MemoryRecord.withIdentity(): MemoryRecord = copy(
        subject = subject.ifBlank { defaultSubject(scope) },
        attribute = attribute.ifBlank { inferAttribute(content, title, type) }
    )

    private fun defaultSubject(scope: MemoryScope): String = when (scope) {
        MemoryScope.USER -> "user"
        MemoryScope.WORKSPACE -> "workspace"
    }

    private fun inferAttribute(content: String, title: String, type: MemoryType): String {
        val lower = content.lowercase()
        return when {
            listOf("call me", "panggil", "nama saya", "my name", "nickname").any { it in lower } -> "user_name"
            listOf("concise", "ringkas", "detail", "verbose", "panjang", "singkat").any { it in lower } -> "response_detail"
            listOf("bahasa", "language", "respond in", "jawab saya", "indonesian", "english").any { it in lower } -> "response_language"
            type == MemoryType.WORKSPACE_FACT && listOf("build command", "assemble", "gradlew", "mvn ", "npm run build").any { it in lower } -> "build_command"
            type == MemoryType.DECISION && listOf("decided", "decision", "chose", "memilih", "keputusan", "rather than", "instead of", "opted for").any { it in lower } -> "decision"
            type == MemoryType.WORKSPACE_FACT && listOf("test command", "test task", "npm test", "pytest").any { it in lower } -> "test_command"
            type == MemoryType.WORKSPACE_FACT && listOf("uses gradle", "uses maven", "build system").any { it in lower } -> "build_system"
            title.isNotBlank() && title !in GENERIC_TITLES -> normalizeMemoryText(title).take(80)
            else -> normalizeMemoryText(content).take(80)
        }.ifBlank { stableMemoryId(type.name, content) }
    }

    private fun inferTitle(content: String, type: MemoryType): String {
        val lower = content.lowercase()
        return when {
            "user's name" in lower -> "User name"
            "prefers" in lower && "responses" in lower -> "Response language preference"
            "works at" in lower -> "Workplace context"
            type == MemoryType.USER_PROFILE -> "User profile"
            type == MemoryType.WORKSPACE_FACT -> "Workspace fact"
            type == MemoryType.DECISION -> "Project decision"
            else -> content.removeSuffix(".").take(80).ifBlank { "Memory" }
        }
    }

    private fun conflictFreeStatus(raw: String): MemoryStatus = when (raw.uppercase()) {
        MemoryStatus.ACTIVE.name -> MemoryStatus.ACTIVE
        else -> MemoryStatus.SUPERSEDED
    }

    private fun MemoryRecord.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("action", action.name)
        .put("scope", scope.name)
        .put("target", target)
        .put("label", label)
        .put("title", title)
        .put("content", content)
        .put("reason", reason)
        .put("confidence", confidence)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("expiresAt", expiresAt)
        .put("source", source)
        .put("version", version)
        .put("workspacePath", workspacePath)
        .put("workspaceId", workspaceId)
        .put("subject", subject)
        .put("attribute", attribute)
        .put("status", status.name)
        .put("sourceConversationId", sourceConversationId)
        .put("volatility", volatility.name)
        .put("verified", verified)
        .put("verifyCount", verifyCount)
        .put("lastConfirmedAt", lastConfirmedAt)
        .put("evidence", JSONArray(evidence))
        .put("supersededById", supersededById)

    private fun JSONObject.toMemoryRecordOrNull(): MemoryRecord? {
        val type = runCatching { MemoryType.valueOf(optString("type")) }.getOrNull() ?: return null
        if (type !in DURABLE_TYPES) return null
        val rawAction = optString("action", MemoryAction.ADD.name)
        if (rawAction.equals("IGNORE", true)) return null
        return MemoryRecord(
            id = optString("id").ifBlank { stableMemoryId(optString("target"), optString("content")) },
            type = type,
            action = runCatching { MemoryAction.valueOf(rawAction) }.getOrDefault(MemoryAction.ADD),
            scope = runCatching { MemoryScope.valueOf(optString("scope")) }.getOrElse {
                if (type == MemoryType.WORKSPACE_FACT) MemoryScope.WORKSPACE else MemoryScope.USER
            },
            target = optString("target"),
            label = optString("label"),
            title = optString("title").ifBlank { inferTitle(optString("content"), type) },
            content = optString("content"),
            reason = optString("reason"),
            confidence = optDouble("confidence", 0.8),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", optLong("createdAt", 0L)),
            expiresAt = if (has("expiresAt") && !isNull("expiresAt")) optLong("expiresAt") else null,
            source = optString("source", "legacy"),
            version = optInt("version", 1).coerceAtLeast(1),
            workspacePath = optString("workspacePath").takeIf(String::isNotBlank),
            workspaceId = optString("workspaceId").takeIf(String::isNotBlank),
            subject = optString("subject"),
            attribute = optString("attribute"),
            status = if (rawAction.equals("REMOVE", true)) MemoryStatus.SUPERSEDED
                else conflictFreeStatus(optString("status", MemoryStatus.ACTIVE.name)),
            sourceConversationId = optString("sourceConversationId").takeIf(String::isNotBlank),
            volatility = runCatching { MemoryVolatility.valueOf(optString("volatility")) }
                .getOrDefault(MemoryVolatility.fromType(type)),
            verified = optBoolean("verified", false),
            verifyCount = optInt("verifyCount", 0).coerceAtLeast(0),
            lastConfirmedAt = if (has("lastConfirmedAt") && !isNull("lastConfirmedAt")) optLong("lastConfirmedAt") else null,
            evidence = optJSONArray("evidence")?.let { array ->
                List(array.length()) { array.optString(it) }.filter(String::isNotBlank)
            }.orEmpty(),
            supersededById = optString("supersededById").takeIf(String::isNotBlank)
        ).withIdentity()
    }

    private fun replacementValue(content: String): String {
        listOf("=>", "->", "→").forEach { delimiter ->
            val index = content.indexOf(delimiter)
            if (index > 0 && index < content.lastIndex) return content.substring(index + delimiter.length).trim()
        }
        return content.trim()
    }

    /**
     * Rank a memory record against the query. Field-weighted lexical scoring (title 3x,
     * attribute 2.5x, content 2x), fuzzy substring hits, multi-word bigram co-occurrence, and a
     * recency decay so recently relevant memories outrank stale ones. Purely local (no embedding
     * API) while keeping the same pass threshold as before, so recall improves without noise.
     */
    private fun scoreMemoryRecord(record: MemoryRecord, query: String): Double {
        val queryTerms = expandTerms(query)
        if (queryTerms.isEmpty()) return 0.0
        val titleTerms = expandTerms(record.title)
        val attributeTerms = expandTerms(record.attribute)
        val contentTerms = expandTerms("${record.content} ${record.label} ${record.type.name}")
        val allTerms = titleTerms + attributeTerms + contentTerms
        var score = 0.0
        queryTerms.forEach { term ->
            when {
                term in titleTerms -> score += 3.0
                term in attributeTerms -> score += 2.5
                term in contentTerms -> score += 2.0
                allTerms.any { it.contains(term) || term.contains(it) } -> score += 0.75
            }
        }
        score += bigramOverlap(query, "${record.title} ${record.attribute} ${record.content} ${record.label}") * 1.5
        // Recency was removed here: time-based ranking is handled by priorityScore (volatility decay)
        // so the relevance threshold stays age-independent and decay applies to the final rank.
        return score
    }

    /**
     * Final ranking weight = relevance × volatility decay × confidence. Mirrors the biomimetic
     * priority_score (decay_score × urgency_weight) from the self-improving memory scheme §2,
     * where decay depends on the memory's volatility class and how long ago it was updated.
     */
    private fun priorityScore(record: MemoryRecord, relevance: Double): Double =
        relevance * decayFactor(record) * confidenceFactor(record.confidence)

    /** Decay multiplier^(periods) with a floor so decayed memories still surface at a low priority. */
    private fun decayFactor(record: MemoryRecord): Double {
        val ageDays = (System.currentTimeMillis() - record.updatedAt).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)
        val periods = ageDays / DECAY_PERIOD_DAYS
        return record.volatility.decayMultiplier.pow(periods).coerceIn(MIN_DECAY_FLOOR, 1.0)
    }

    /** Low-confidence memories rank below confirmed ones (0.7–1.0 multiplier). */
    private fun confidenceFactor(confidence: Double): Double = 0.7 + 0.3 * confidence.coerceIn(0.0, 1.0)

    /**
     * Semantic rerank: when the user configured an embedding endpoint, re-score the top lexical
     * candidates by cosine similarity to the query (batched in one API call) and blend the scores.
     * Any failure (offline, bad key, unsupported model) falls back to the lexical ranking, so
     * semantic recall is strictly an improvement, never a regression. Vectors are cached per
     * (endpoint, model, text-hash) so repeated snapshots do not re-hit the API.
     */
    private suspend fun rerankWithEmbeddings(
        lexical: List<Pair<MemoryRecord, Double>>,
        query: String
    ): List<Pair<MemoryRecord, Double>>? {
        if (lexical.isEmpty()) return lexical
        // Settings and credential reads can throw on broken stores; a search must never crash.
        val settings = runCatching { settingsManager.getSettings() }.getOrNull() ?: return null
        val config = settings.memoryEmbedding
        if (!config.enabled || config.endpoint.isBlank() || config.model.isBlank()) return null
        val apiKey = runCatching { settingsManager.getEmbeddingApiKey(config) }.getOrNull()
        if (apiKey.isNullOrBlank()) return null
        val candidates = lexical.take(EMBEDDING_CANDIDATE_LIMIT)
        return runCatching {
            val texts = candidates.map { (record, _) ->
                "${record.title} ${record.attribute} ${record.content} ${record.label}".take(EMBEDDING_TEXT_MAX_CHARS)
            }
            val cacheKey = "${config.format}|${config.model}|${config.endpoint}"
            val queryVector = embeddingClient.embed(listOf(query.take(EMBEDDING_TEXT_MAX_CHARS)), config, apiKey).getOrThrow().first()
            val vectors = embeddingClient.embed(texts, config, apiKey).getOrThrow()
            val blended = candidates.mapIndexedNotNull { index, (record, lexicalScore) ->
                val text = texts[index]
                val cached = vectorCache["$cacheKey|${text.hashCode()}"]
                val vector = cached ?: vectors.getOrNull(index)?.also { vectorCache["$cacheKey|${text.hashCode()}"] = it }
                if (vector == null) return@mapIndexedNotNull null
                val similarity = cosineSimilarity(queryVector, vector)
                val combined = lexicalScore + EMBEDDING_SIMILARITY_WEIGHT * similarity
                if (combined >= MIN_SEARCH_SCORE) record to combined else null
            }
            blended.sortedByDescending { it.second }.ifEmpty { lexical }
        }.getOrNull() ?: lexical
    }

    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    /** Fraction of the query's adjacent token pairs whose tokens both appear in [candidate]. */
    private fun bigramOverlap(query: String, candidate: String): Double {
        val queryTerms = expandTerms(query).toList()
        if (queryTerms.size < 2) return 0.0
        val candidateTerms = expandTerms(candidate).toSet()
        val hits = queryTerms.zipWithNext().count { pair -> pair.first in candidateTerms && pair.second in candidateTerms }
        return hits.toDouble() / (queryTerms.size - 1)
    }

    private fun expandTerms(text: String): Set<String> {
        val terms = text.lowercase()
            .replace(Regex("[^a-z0-9\\p{L}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toMutableSet()
        SYNONYMS.forEach { (key, values) ->
            if (key in terms || values.any { it in terms }) {
                terms += key
                terms += values
            }
        }
        return terms
    }

    private fun migrationKey(record: MemoryRecord): String =
        "${record.type}:${record.scope}:${record.workspacePath.orEmpty()}:${normalizeMemoryText(record.content)}"

    private fun normalizeMemoryText(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9\\p{L}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun stableMemoryId(target: String, content: String): String =
        "mem_${UUID.nameUUIDFromBytes("$target:$content".toByteArray())}"

    private fun atomicWrite(file: File, content: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(content.trimEnd() + if (content.isBlank()) "" else "\n")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.55
        private const val MIN_SEARCH_SCORE = 2.0
        private const val MAX_LIST_LIMIT = 200
        private const val MAX_REVISIONS_PER_MEMORY = 5
        /** Top lexical candidates re-scored semantically; bounds API cost to ~13 texts per recall. */
        private const val EMBEDDING_CANDIDATE_LIMIT = 12
        private const val EMBEDDING_TEXT_MAX_CHARS = 512
        /** Cosine (0..1) weight added on top of the lexical score. */
        private const val EMBEDDING_SIMILARITY_WEIGHT = 3.0
        private const val VECTOR_CACHE_MAX = 200
        /** Decay period for the volatility multiplier (scheme §2 uses 30-day periods). */
        private const val DECAY_PERIOD_DAYS = 30.0
        /** Memories decayed below this are archived during end-of-session housekeeping. */
        private const val ARCHIVE_DECAY_FLOOR = 0.05
        /** Floor for the live decay factor so decayed memories still rank (but last). */
        private const val MIN_DECAY_FLOOR = 0.05
        /** Bounded memory cap per scope (user file and each workspace file). */
        private const val MEMORY_CAP_PER_SCOPE = 200
        /** Cap on appended provenance lines kept per memory record. */
        private const val MAX_EVIDENCE_LINES = 8
        private val UUID_REGEX = Regex("[0-9a-fA-F-]{36}")
        private val DURABLE_TYPES = setOf(MemoryType.USER_PROFILE, MemoryType.WORKSPACE_FACT, MemoryType.DECISION)
        private val WORKSPACE_SCOPED_TYPES = setOf(MemoryType.WORKSPACE_FACT, MemoryType.DECISION)
        private val LEGACY_DAILY_FILE = Regex("\\d{4}-\\d{2}-\\d{2}\\.md")
        private val GENERIC_TITLES = setOf("User profile", "Workspace fact", "Memory")
        private val USER_FACT_TERMS = setOf(
            "the user", "user prefers", "nama saya", "my name", "call me", "panggil", "bahasa", "language",
            "concise", "ringkas", "detailed", "detail", "works at", "bekerja di", "nickname"
        )
        private val SYNONYMS = mapOf(
            "language" to setOf("bahasa", "jawab", "respond", "reply"),
            "tone" to setOf("gaya", "style", "nada", "cara"),
            "concise" to setOf("ringkas", "singkat", "pendek", "brief"),
            "detail" to setOf("rinci", "lengkap", "panjang", "verbose"),
            "name" to setOf("nama", "panggil", "nickname", "call"),
            "project" to setOf("workspace", "repo", "repository", "codebase", "kode"),
            "memory" to setOf("ingat", "remember", "memori"),
            // Site/browser terms so browser-site memory and login/flow facts recall across languages.
            "login" to setOf("sign in", "signin", "log in", "masuk", "authenticate", "auth"),
            "register" to setOf("sign up", "signup", "daftar", "buat akun"),
            "search" to setOf("cari", "find", "lookup"),
            "download" to setOf("unduh", "downloads"),
            "upload" to setOf("unggah"),
            "settings" to setOf("pengaturan", "config", "configuration"),
            "browser" to setOf("web", "website", "site", "halaman", "page")
        )
    }
}
