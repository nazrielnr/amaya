package com.opencode.mobile.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a file tracked within a project.
 * 
 * This entity stores essential metadata about each file to enable:
 * 1. Fast file lookup by path or name
 * 2. Change detection via content hash comparison
 * 3. Smart context building (file tree) for AI prompts
 * 
 * WHY WE STORE FILE METADATA LOCALLY:
 * Instead of sending the entire project to the AI, we index files locally
 * and send only a "Project Map" (tree structure with file names) to the AI.
 * The AI then requests specific files it needs using the read_file tool.
 * This prevents context window bloat and reduces API costs.
 */
@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["relative_path"], unique = false),
        Index(value = ["content_hash"]),
        Index(value = ["extension"])
    ]
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Foreign key to the parent project.
     */
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    
    /**
     * Path relative to the project root.
     * Example: "src/main/java/com/example/MainActivity.kt"
     */
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    
    /**
     * File name without path.
     * Indexed separately for fast name-based search.
     */
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    /**
     * File extension without dot (e.g., "kt", "java", "xml").
     * Used for language detection and filtering.
     */
    @ColumnInfo(name = "extension")
    val extension: String?,
    
    /**
     * MD5 hash of file content.
     * 
     * WHY MD5 HASH:
     * - Used to detect file changes without reading full content
     * - When hash changes, we know the file was modified
     * - Faster than timestamp for detecting actual content changes
     */
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    
    /**
     * File size in bytes.
     * Used to prevent reading extremely large files that could cause OOM.
     */
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    
    /**
     * Last modified timestamp from the file system.
     */
    @ColumnInfo(name = "last_modified")
    val lastModified: Long,
    
    /**
     * Timestamp of last index update.
     * Used to detect stale entries that no longer exist on disk.
     */
    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long = System.currentTimeMillis(),
    
    /**
     * Whether this file is a directory.
     */
    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean = false
)
