package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.domain.security.CommandValidator
import com.amaya.intelligence.domain.security.ValidationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Safe file deletion using a trash system.
 * Uses java.io.File API for Android compatibility.
 */
@Singleton
class DeleteFileTool @Inject constructor(
    private val commandValidator: CommandValidator,
    @ApplicationContext private val context: Context
) : Tool, ContextAwareTool {

    companion object {
        const val TRASH_DIR_NAME = ".trash"
        const val TRASH_RETENTION_DAYS = 7L
    }

    override val name = "delete_file"

    override val description = """
        Safely delete a file or directory by moving it to trash.
        Can be recovered from the .trash directory if needed.

        Arguments:
        - path (string, required): Absolute path to file or directory
        - permanent (bool, optional): Permanently delete (dangerous, default: false)
    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        execute(arguments, ToolExecutionContext())

    override suspend fun execute(
        arguments: Map<String, Any?>,
        executionContext: ToolExecutionContext
    ): ToolResult = withContext(Dispatchers.IO) {

        val pathStr = (arguments["path"] ?: arguments["file_path"] ?: arguments["filePath"] ?: arguments["file"]) as? String
            ?: return@withContext ToolResult.Error(
                "Missing required argument: path",
                ErrorType.VALIDATION_ERROR
            )

        val permanent = arguments["permanent"] as? Boolean ?: false

        // Validate path access
        when (val validation = commandValidator.validatePath(pathStr, isWrite = true)) {
            is ValidationResult.Denied -> return@withContext ToolResult.Error(
                validation.reason,
                ErrorType.SECURITY_VIOLATION
            )
            is ValidationResult.RequiresConfirmation -> if (!executionContext.confirmed) {
                return@withContext ToolResult.RequiresConfirmation(validation.reason, "Path: $pathStr")
            }
            is ValidationResult.Allowed -> { /* proceed */ }
        }

        val file = File(pathStr)

        if (!file.exists()) {
            return@withContext ToolResult.Error(
                "File does not exist: $pathStr",
                ErrorType.NOT_FOUND
            )
        }

        // FIX #8: Permanent delete — execute directly (ToolExecutor already got confirmation
        // from user before re-calling execute). Returning RequiresConfirmation again would
        // cause an infinite confirmation loop since ToolExecutor retries with same args.
        if (permanent) {
            return@withContext try {
                deleteNoFollow(file)
                if (Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    return@withContext ToolResult.Error("Permanent delete failed: $pathStr", ErrorType.EXECUTION_ERROR)
                }
                ToolResult.Success(
                    output = "Permanently deleted: $pathStr",
                    metadata = mapOf("path" to pathStr, "permanent" to true)
                )
            } catch (e: Exception) {
                ToolResult.Error("Permanent delete failed: ${e.message}", ErrorType.EXECUTION_ERROR)
            }
        }

        try {
            // Move to trash instead of deleting
            val trashFile = moveToTrash(file)

            ToolResult.Success(
                output = "Moved to trash: $pathStr\nRecover from: ${trashFile.absolutePath}",
                metadata = mapOf(
                    "original_path" to pathStr,
                    "trash_path" to trashFile.absolutePath
                )
            )

        } catch (e: SecurityException) {
            ToolResult.Error(
                "Permission denied: ${e.message}",
                ErrorType.PERMISSION_ERROR
            )
        } catch (e: Exception) {
            ToolResult.Error(
                "Failed to delete file: ${e.message}",
                ErrorType.EXECUTION_ERROR
            )
        }
    }

    private fun moveToTrash(sourceFile: File): File {
        val parent = sourceFile.parentFile ?: File(".")
        val trashDir = File(parent, TRASH_DIR_NAME)

        if (!trashDir.exists() && !trashDir.mkdirs()) {
            error("Could not create trash directory: ${trashDir.absolutePath}")
        }

        val timestamp = System.currentTimeMillis()
        val itemName = "${sourceFile.name}.$timestamp"
        val trashFile = File(trashDir, itemName)

        try {
            Files.move(sourceFile.toPath(), trashFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(sourceFile.toPath(), trashFile.toPath())
        }
        require(Files.exists(trashFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(sourceFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            "Trash move verification failed"
        }

        // Run cleanup in background
        cleanupOldTrash(trashDir)

        return trashFile
    }

    private fun deleteNoFollow(file: File) {
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(path: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(path)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(path: java.nio.file.Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(path)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun cleanupOldTrash(trashDir: File) {
        if (!trashDir.exists()) return

        val cutoffTime = System.currentTimeMillis() - (TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000)

        runCatching {
            trashDir.listFiles()?.forEach { item ->
                val timestamp = item.name.substringAfterLast(".").toLongOrNull()

                if (timestamp != null && timestamp < cutoffTime) deleteNoFollow(item)
            }
        }
    }
}
