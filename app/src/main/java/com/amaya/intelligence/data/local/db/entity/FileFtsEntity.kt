package com.amaya.intelligence.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 virtual table for full-text search on files.
 * 
 * WHY FTS4 OVER LIKE QUERIES:
 * - FTS4 is 10-100x faster for text search on large datasets
 * - Supports stemming, prefix matching, and relevance ranking
 * - Essential when searching across thousands of files
 * 
 * This table is synchronized with the main `files` table.
 * When a file is indexed, its searchable text is also added here.
 */
@Entity(tableName = "files_fts")
@Fts4(contentEntity = FileEntity::class)
data class FileFtsEntity(
    /**
     * File name for search.
     * Uses FTS4 for fast prefix and full-text matching.
     */
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    /**
     * Relative path for path-based search.
     */
    @ColumnInfo(name = "relative_path")
    val relativePath: String
)
