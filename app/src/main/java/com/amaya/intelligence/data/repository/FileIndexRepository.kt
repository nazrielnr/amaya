package com.amaya.intelligence.data.repository

import com.amaya.intelligence.data.local.db.dao.FileDao
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao
import com.amaya.intelligence.data.local.db.dao.ProjectDao
import com.amaya.intelligence.data.local.db.entity.FileEntity
import com.amaya.intelligence.data.local.db.entity.FileMetadataEntity
import com.amaya.intelligence.data.local.db.entity.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime

/**
 * Repository for managing file indexing and synchronization.
 * 
 * This repository handles the synchronization between the file system
 * and the local database. It's the core of the "Memory" layer.
 * 
 * KEY OPERATIONS:
 * 1. Scan: Walk the file tree and index all files
 * 2. Sync: Update database with changed/new/deleted files
 * 3. Search: Query files by name, path, or content
 */
@Singleton
class FileIndexRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val fileDao: FileDao,
    private val fileMetadataDao: FileMetadataDao
) {
    
    companion object {
        // Skip these directories during indexing
        private val EXCLUDED_DIRS = setOf(
            ".git", ".svn", ".hg",
            "node_modules", "build", "out", ".gradle",
            "__pycache__", ".pytest_cache",
            "target", ".idea", ".vscode"
        )
        
        // Skip these file patterns
        private val EXCLUDED_PATTERNS = listOf(
            Regex(""".*\.(class|jar|war|ear|zip|tar|gz|rar)$"""),
            Regex(""".*\.(png|jpg|jpeg|gif|ico|svg|webp)$"""),
            Regex(""".*\.(mp3|mp4|avi|mov|wav)$"""),
            Regex(""".*\.(pdf|doc|docx|xls|xlsx)$""")
        )
        
        // Maximum file size to index (1MB)
        private const val MAX_FILE_SIZE = 1024 * 1024L
    }
    
    // ========================================================================
    // PROJECT OPERATIONS
    // ========================================================================
    
    suspend fun addProject(name: String, rootPath: String): Long {
        val project = ProjectEntity(
            name = name,
            rootPath = rootPath
        )
        return projectDao.insert(project)
    }
    
    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeAll()
    
    fun observeActiveProject(): Flow<ProjectEntity?> = projectDao.observeActiveProject()
    
    suspend fun setActiveProject(projectId: Long) {
        projectDao.setActiveProject(projectId)
    }
    
    suspend fun deleteProject(projectId: Long) {
        projectDao.deleteById(projectId)
    }
    
    // ========================================================================
    // FILE INDEXING
    // ========================================================================
    
    /**
     * Scan and index a project directory.
     * 
     * WHY NATIVE API (java.nio.file):
     * Using Files.walk() is 10-100x faster than spawning shell processes
     * like `find` or `ls -R` for each directory. On Android, process forking
     * is especially expensive due to Zygote overhead.
     * 
     * @param projectId ID of the project to scan
     * @param rootPath Root directory path
     * @param onProgress Callback for progress updates (0.0 to 1.0)
     */
    suspend fun scanProject(
        projectId: Long,
        rootPath: String,
        onProgress: ((Float) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.IO) {
        val scanStartTime = System.currentTimeMillis()
        val root = Path.of(rootPath)
        
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return@withContext ScanResult.Error("Path does not exist or is not a directory")
        }
        
        // Collect all files first to calculate progress
        val filesToIndex = mutableListOf<Path>()
        
        try {
            Files.walk(root)
                .filter { path ->
                    // Skip excluded directories
                    val pathStr = path.toString()
                    EXCLUDED_DIRS.none { pathStr.contains("/$it/") || pathStr.endsWith("/$it") }
                }
                .filter { path ->
                    // Skip excluded file patterns
                    if (path.isRegularFile()) {
                        EXCLUDED_PATTERNS.none { it.matches(path.name) }
                    } else true
                }
                .forEach { filesToIndex.add(it) }
        } catch (e: Exception) {
            return@withContext ScanResult.Error("Failed to walk directory: ${e.message}")
        }
        
        // Get existing hashes for change detection
        val existingHashes = fileDao.getAllHashes(projectId).associate { 
            it.relativePath to it.contentHash 
        }
        
        val entities = mutableListOf<FileEntity>()
        var processed = 0
        var newFiles = 0
        var updatedFiles = 0
        var skippedFiles = 0
        
        for (path in filesToIndex) {
            try {
                val relativePath = root.relativize(path).toString()
                val isDir = path.isDirectory()
                
                // Calculate hash for files (not directories)
                val hash = if (!isDir && path.fileSize() <= MAX_FILE_SIZE) {
                    calculateMd5(path)
                } else {
                    ""
                }
                
                // Check if file changed
                val existingHash = existingHashes[relativePath]
                when {
                    existingHash == null -> newFiles++
                    existingHash != hash -> updatedFiles++
                    else -> skippedFiles++
                }
                
                val entity = FileEntity(
                    projectId = projectId,
                    relativePath = relativePath,
                    fileName = path.name,
                    extension = if (isDir) null else path.extension.takeIf { it.isNotEmpty() },
                    contentHash = hash,
                    sizeBytes = if (isDir) 0 else path.fileSize(),
                    lastModified = path.getLastModifiedTime().toMillis(),
                    indexedAt = scanStartTime,
                    isDirectory = isDir
                )
                entities.add(entity)
                
                processed++
                if (processed % 100 == 0) {
                    onProgress?.invoke(processed.toFloat() / filesToIndex.size)
                }
            } catch (e: Exception) {
                // Skip files we can't read
                skippedFiles++
            }
        }
        
        // Batch insert all files
        fileDao.insertAll(entities)
        
        // Delete files that no longer exist
        val deletedCount = fileDao.deleteStaleFiles(projectId, scanStartTime)
        
        // Update project metadata
        projectDao.updateScanStatus(projectId, scanStartTime, entities.size)
        
        onProgress?.invoke(1.0f)
        
        ScanResult.Success(
            totalFiles = entities.size,
            newFiles = newFiles,
            updatedFiles = updatedFiles,
            deletedFiles = deletedCount,
            skippedFiles = skippedFiles
        )
    }
    
    /**
     * Calculate MD5 hash of a file.
     * 
     * WHY MD5:
     * - Fast enough for file change detection
     * - We're not using it for security, just change detection
     * - SHA-256 would be overkill and slower
     */
    private fun calculateMd5(path: Path): String {
        val digest = MessageDigest.getInstance("MD5")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    // ========================================================================
    // FILE QUERIES
    // ========================================================================
    
    suspend fun getFiles(projectId: Long): List<FileEntity> {
        return fileDao.getByProject(projectId)
    }
    
    fun observeFiles(projectId: Long): Flow<List<FileEntity>> {
        return fileDao.observeByProject(projectId)
    }
    
    suspend fun searchFiles(projectId: Long, query: String): List<FileEntity> {
        // Format query for FTS (add * for prefix matching)
        val ftsQuery = "$query*"
        return fileDao.searchByName(projectId, ftsQuery)
    }
    
    suspend fun getFileByPath(projectId: Long, relativePath: String): FileEntity? {
        return fileDao.getByPath(projectId, relativePath)
    }
    
    /**
     * Build a project tree structure for AI context.
     * 
     * This creates a compact representation of the project structure
     * that can be sent to the AI without including file contents.
     */
    suspend fun buildProjectTree(projectId: Long): ProjectTree {
        val files = fileDao.getByProject(projectId)
        val project = projectDao.getById(projectId)
        
        return ProjectTree(
            projectName = project?.name ?: "Unknown",
            rootPath = project?.rootPath ?: "",
            files = files.map { file ->
                ProjectTree.Node(
                    path = file.relativePath,
                    isDirectory = file.isDirectory,
                    extension = file.extension,
                    sizeBytes = file.sizeBytes
                )
            }
        )
    }
}

/**
 * Result of a project scan operation.
 */
sealed class ScanResult {
    data class Success(
        val totalFiles: Int,
        val newFiles: Int,
        val updatedFiles: Int,
        val deletedFiles: Int,
        val skippedFiles: Int
    ) : ScanResult()
    
    data class Error(val message: String) : ScanResult()
}

/**
 * Compact project tree structure for AI context.
 */
data class ProjectTree(
    val projectName: String,
    val rootPath: String,
    val files: List<Node>
) {
    data class Node(
        val path: String,
        val isDirectory: Boolean,
        val extension: String?,
        val sizeBytes: Long
    )
    
    /**
     * Convert to a string representation for AI prompts.
     */
    fun toTreeString(): String {
        val sb = StringBuilder()
        sb.appendLine("Project: $projectName")
        sb.appendLine("Root: $rootPath")
        sb.appendLine("Files:")
        
        files.sortedBy { it.path }.forEach { node ->
            val prefix = if (node.isDirectory) "📁" else "📄"
            val size = if (node.isDirectory) "" else " (${formatSize(node.sizeBytes)})"
            sb.appendLine("  $prefix ${node.path}$size")
        }
        
        return sb.toString()
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
