package com.amaya.intelligence.data.local.dao

import androidx.room.*
import com.amaya.intelligence.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE is_active = 1 LIMIT 1")
    fun observeActiveProject(): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE root_path = :rootPath")
    suspend fun getProjectByRootPath(rootPath: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE root_path = :rootPath")
    suspend fun getByRootPath(rootPath: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET is_active = 1 WHERE id = :id")
    suspend fun setActiveProject(id: Long)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE projects SET is_active = 0")
    suspend fun deactivateAllProjects()

    @Query("UPDATE projects SET is_active = 1 WHERE id = :id")
    suspend fun activateProject(id: Long)

    @Query("UPDATE projects SET last_scanned_at = :lastScannedAt, file_count = :fileCount WHERE id = :id")
    suspend fun updateScanStatus(id: Long, lastScannedAt: Long, fileCount: Int)
}
