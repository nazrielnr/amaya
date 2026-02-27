package com.amaya.intelligence.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Extended metadata for code files.
 * 
 * This entity stores additional information extracted from source files:
 * - Detected programming language
 * - Symbols (class names, function names) for quick navigation
 * - Import statements for dependency analysis
 * 
 * WHY SEPARATE FROM FileEntity:
 * - Not all files need this metadata (images, configs, etc.)
 * - Parsing symbols is expensive, so we cache results
 * - Enables richer AI context (e.g., "find the class named X")
 */
@Entity(
    tableName = "file_metadata",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"], unique = true),
        Index(value = ["language"])
    ]
)
data class FileMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Foreign key to the parent file.
     */
    @ColumnInfo(name = "file_id")
    val fileId: Long,
    
    /**
     * Detected programming language.
     * Examples: "kotlin", "java", "python", "javascript"
     */
    @ColumnInfo(name = "language")
    val language: String,
    
    /**
     * JSON array of class/interface names defined in this file.
     * Example: ["MainActivity", "MainViewModel"]
     */
    @ColumnInfo(name = "class_names")
    val classNames: String? = null,
    
    /**
     * JSON array of top-level function names.
     * Example: ["onCreate", "setupUI", "fetchData"]
     */
    @ColumnInfo(name = "function_names")
    val functionNames: String? = null,
    
    /**
     * JSON array of import statements.
     * Used for dependency analysis and context building.
     */
    @ColumnInfo(name = "imports")
    val imports: String? = null,
    
    /**
     * Number of lines of code (excluding blanks and comments).
     * Useful for estimating file complexity.
     */
    @ColumnInfo(name = "loc")
    val linesOfCode: Int? = null,
    
    /**
     * Timestamp of when metadata was last extracted.
     */
    @ColumnInfo(name = "extracted_at")
    val extractedAt: Long = System.currentTimeMillis()
)
