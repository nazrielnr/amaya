package com.amaya.intelligence.data.local.dao

import androidx.room.*
import com.amaya.intelligence.data.local.entity.FileEntity
import com.amaya.intelligence.data.local.entity.FileFtsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE project_id = :projectId")
    fun getByProject(projectId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE project_id = :projectId")
    fun getFilesByProject(projectId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE project_id = :projectId")
    fun observeByProject(projectId: Long): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE project_id = :projectId AND relative_path = :relativePath")
    suspend fun getFileByPath(projectId: Long, relativePath: String): FileEntity?

    @Query("SELECT * FROM files WHERE project_id = :projectId AND relative_path = :relativePath")
    suspend fun getByPath(projectId: Long, relativePath: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Query("DELETE FROM files WHERE project_id = :projectId")
    suspend fun deleteFilesByProject(projectId: Long)

    @Query("DELETE FROM files WHERE project_id = :projectId AND indexed_at < :indexedAt")
    suspend fun deleteStaleFiles(projectId: Long, indexedAt: Long): Int

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("""
        SELECT files.* FROM files 
        JOIN files_fts ON files.id = files_fts.docid 
        WHERE files_fts MATCH :query
    """)
    suspend fun searchFiles(query: String): List<FileEntity>

    @Query("""
        SELECT files.* FROM files
        JOIN files_fts ON files.id = files_fts.docid
        WHERE files.project_id = :projectId AND files_fts MATCH :query
    """)
    suspend fun searchByName(projectId: Long, query: String): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files WHERE project_id = :projectId")
    suspend fun getFileCount(projectId: Long): Int

    @Query("SELECT content_hash AS contentHash, relative_path AS relativePath FROM files WHERE project_id = :projectId")
    suspend fun getAllHashes(projectId: Long): List<FileHashProjection>
}

data class FileHashProjection(
    val contentHash: String,
    val relativePath: String
)
