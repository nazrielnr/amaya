package com.opencode.mobile.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.opencode.mobile.data.local.db.entity.FileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for File operations.
 * 
 * Provides efficient file indexing and querying with:
 * - Batch insert/update for fast project scanning
 * - FTS4 full-text search for file discovery
 * - Stale file detection for incremental updates
 */
@Dao
interface FileDao {
    
    // ========================================================================
    // INSERT OPERATIONS
    // ========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity): Long
    
    /**
     * Batch insert for efficient project scanning.
     * Uses REPLACE strategy to update existing files.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)
    
    // ========================================================================
    // UPDATE OPERATIONS
    // ========================================================================
    
    @Update
    suspend fun update(file: FileEntity)
    
    /**
     * Update content hash and metadata when file content changes.
     */
    @Query("""
        UPDATE files 
        SET content_hash = :hash, 
            size_bytes = :size, 
            last_modified = :lastModified,
            indexed_at = :indexedAt
        WHERE id = :fileId
    """)
    suspend fun updateFileHash(
        fileId: Long,
        hash: String,
        size: Long,
        lastModified: Long,
        indexedAt: Long = System.currentTimeMillis()
    )
    
    // ========================================================================
    // DELETE OPERATIONS
    // ========================================================================
    
    @Delete
    suspend fun delete(file: FileEntity)
    
    @Query("DELETE FROM files WHERE id = :fileId")
    suspend fun deleteById(fileId: Long)
    
    @Query("DELETE FROM files WHERE project_id = :projectId")
    suspend fun deleteByProject(projectId: Long)
    
    /**
     * Delete stale files that weren't updated in the last scan.
     * 
     * WHY THIS IS NEEDED:
     * When scanning a project, we update the indexed_at timestamp.
     * Files that don't exist anymore won't be updated, so their
     * indexed_at will be older than the scan start time.
     */
    @Query("DELETE FROM files WHERE project_id = :projectId AND indexed_at < :scanStartTime")
    suspend fun deleteStaleFiles(projectId: Long, scanStartTime: Long): Int
    
    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================
    
    @Query("SELECT * FROM files WHERE project_id = :projectId ORDER BY relative_path")
    fun observeByProject(projectId: Long): Flow<List<FileEntity>>
    
    @Query("SELECT * FROM files WHERE project_id = :projectId ORDER BY relative_path")
    suspend fun getByProject(projectId: Long): List<FileEntity>
    
    @Query("SELECT * FROM files WHERE id = :fileId")
    suspend fun getById(fileId: Long): FileEntity?
    
    @Query("SELECT * FROM files WHERE project_id = :projectId AND relative_path = :path")
    suspend fun getByPath(projectId: Long, path: String): FileEntity?
    
    /**
     * Get files by extension for filtering (e.g., only Kotlin files).
     */
    @Query("SELECT * FROM files WHERE project_id = :projectId AND extension = :ext ORDER BY relative_path")
    suspend fun getByExtension(projectId: Long, ext: String): List<FileEntity>
    
    /**
     * Get directories only for building file tree.
     */
    @Query("SELECT * FROM files WHERE project_id = :projectId AND is_directory = 1 ORDER BY relative_path")
    suspend fun getDirectories(projectId: Long): List<FileEntity>
    
    @Query("SELECT COUNT(*) FROM files WHERE project_id = :projectId")
    suspend fun getCountByProject(projectId: Long): Int
    
    @Query("SELECT SUM(size_bytes) FROM files WHERE project_id = :projectId")
    suspend fun getTotalSizeByProject(projectId: Long): Long?
    
    // ========================================================================
    // FTS SEARCH OPERATIONS
    // ========================================================================
    
    /**
     * Full-text search on file names.
     * 
     * WHY FTS OVER LIKE:
     * - 10-100x faster for large projects
     * - Supports prefix matching (e.g., "Main*")
     * - Relevance ranking for better results
     */
    @Query("""
        SELECT f.* FROM files f
        INNER JOIN files_fts fts ON f.rowid = fts.rowid
        WHERE files_fts MATCH :query
        AND f.project_id = :projectId
        ORDER BY f.file_name
        LIMIT :limit
    """)
    suspend fun searchByName(projectId: Long, query: String, limit: Int = 50): List<FileEntity>
    
    /**
     * Search files by path pattern.
     */
    @Query("""
        SELECT f.* FROM files f
        INNER JOIN files_fts fts ON f.rowid = fts.rowid
        WHERE files_fts MATCH :query
        AND f.project_id = :projectId
        ORDER BY f.relative_path
        LIMIT :limit
    """)
    suspend fun searchByPath(projectId: Long, query: String, limit: Int = 50): List<FileEntity>
    
    // ========================================================================
    // UTILITY QUERIES
    // ========================================================================
    
    /**
     * Check if a file exists and get its hash for change detection.
     */
    @Query("SELECT content_hash FROM files WHERE project_id = :projectId AND relative_path = :path")
    suspend fun getHash(projectId: Long, path: String): String?
    
    /**
     * Get all file hashes for efficient batch comparison.
     */
    @Query("SELECT relative_path, content_hash FROM files WHERE project_id = :projectId")
    suspend fun getAllHashes(projectId: Long): List<FileHashPair>
    
    /**
     * Get recently modified files.
     */
    @Query("""
        SELECT * FROM files 
        WHERE project_id = :projectId 
        ORDER BY last_modified DESC 
        LIMIT :limit
    """)
    suspend fun getRecentlyModified(projectId: Long, limit: Int = 20): List<FileEntity>
}

/**
 * Simple data class for batch hash comparison.
 */
data class FileHashPair(
    @androidx.room.ColumnInfo(name = "relative_path")
    val relativePath: String,
    @androidx.room.ColumnInfo(name = "content_hash")
    val contentHash: String
)
