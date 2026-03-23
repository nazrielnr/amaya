package com.amaya.intelligence.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.amaya.intelligence.data.local.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Project operations.
 * 
 * Provides CRUD operations and queries for project management.
 * All queries use suspend functions for Coroutine support,
 * or Flow for reactive data observation.
 */
@Dao
interface ProjectDao {
    
    // ========================================================================
    // INSERT OPERATIONS
    // ========================================================================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>)
    
    // ========================================================================
    // UPDATE OPERATIONS
    // ========================================================================
    
    @Update
    suspend fun update(project: ProjectEntity)
    
    /**
     * Update the last scanned timestamp and file count after indexing.
     */
    @Query("""
        UPDATE projects 
        SET last_scanned_at = :scannedAt, file_count = :fileCount 
        WHERE id = :projectId
    """)
    suspend fun updateScanStatus(projectId: Long, scannedAt: Long, fileCount: Int)
    
    /**
     * Set a project as active and deactivate all others.
     */
    @Query("UPDATE projects SET is_active = (id = :projectId)")
    suspend fun setActiveProject(projectId: Long)
    
    // ========================================================================
    // DELETE OPERATIONS
    // ========================================================================
    
    @Delete
    suspend fun delete(project: ProjectEntity)
    
    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)
    
    @Query("DELETE FROM projects")
    suspend fun deleteAll()
    
    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================
    
    @Query("SELECT * FROM projects ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ProjectEntity>>
    
    @Query("SELECT * FROM projects ORDER BY created_at DESC")
    suspend fun getAll(): List<ProjectEntity>
    
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: Long): ProjectEntity?
    
    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeById(projectId: Long): Flow<ProjectEntity?>
    
    @Query("SELECT * FROM projects WHERE root_path = :path")
    suspend fun getByPath(path: String): ProjectEntity?
    
    @Query("SELECT * FROM projects WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveProject(): ProjectEntity?
    
    @Query("SELECT * FROM projects WHERE is_active = 1 LIMIT 1")
    fun observeActiveProject(): Flow<ProjectEntity?>
    
    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getCount(): Int
    
    /**
     * Check if a project path is already indexed.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE root_path = :path)")
    suspend fun existsByPath(path: String): Boolean
}
