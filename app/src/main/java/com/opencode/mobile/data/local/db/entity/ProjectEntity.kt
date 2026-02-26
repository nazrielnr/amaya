package com.opencode.mobile.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a project tracked by the AI Coding Agent.
 * 
 * A project is a root directory containing source code that the agent can
 * index, analyze, and modify. The agent maintains metadata about projects
 * to provide context to the AI without sending entire codebases.
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["root_path"], unique = true)
    ]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Human-readable name of the project.
     * Usually derived from the folder name or user input.
     */
    @ColumnInfo(name = "name")
    val name: String,
    
    /**
     * Absolute path to the project root directory.
     * Example: "/storage/emulated/0/Projects/my-app"
     */
    @ColumnInfo(name = "root_path")
    val rootPath: String,
    
    /**
     * Primary programming language detected in the project.
     * Used for syntax validation and context building.
     */
    @ColumnInfo(name = "primary_language")
    val primaryLanguage: String? = null,
    
    /**
     * Timestamp of the last full scan of the project.
     * Used to determine if re-indexing is needed.
     */
    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,
    
    /**
     * Total number of files indexed in the project.
     * Useful for progress indicators and context management.
     */
    @ColumnInfo(name = "file_count")
    val fileCount: Int = 0,
    
    /**
     * Timestamp when the project was added.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    /**
     * Whether the project is currently active/selected.
     */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
)
