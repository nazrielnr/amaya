package com.amaya.intelligence.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.amaya.intelligence.data.local.db.entity.FileMetadataEntity

/**
 * Data Access Object for FileMetadata operations.
 * 
 * Manages extended code metadata (symbols, imports) for files.
 */
@Dao
interface FileMetadataDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: FileMetadataEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<FileMetadataEntity>)
    
    @Update
    suspend fun update(metadata: FileMetadataEntity)
    
    @Query("DELETE FROM file_metadata WHERE file_id = :fileId")
    suspend fun deleteByFileId(fileId: Long)
    
    @Query("SELECT * FROM file_metadata WHERE file_id = :fileId")
    suspend fun getByFileId(fileId: Long): FileMetadataEntity?
    
    /**
     * Get all files of a specific language in a project.
     */
    @Query("""
        SELECT fm.* FROM file_metadata fm
        INNER JOIN files f ON fm.file_id = f.id
        WHERE f.project_id = :projectId AND fm.language = :language
    """)
    suspend fun getByLanguage(projectId: Long, language: String): List<FileMetadataEntity>
    
    /**
     * Search for files containing a specific class name.
     */
    @Query("""
        SELECT fm.* FROM file_metadata fm
        INNER JOIN files f ON fm.file_id = f.id
        WHERE f.project_id = :projectId 
        AND fm.class_names LIKE '%' || :className || '%'
    """)
    suspend fun searchByClassName(projectId: Long, className: String): List<FileMetadataEntity>
    
    /**
     * Search for files containing a specific function name.
     */
    @Query("""
        SELECT fm.* FROM file_metadata fm
        INNER JOIN files f ON fm.file_id = f.id
        WHERE f.project_id = :projectId 
        AND fm.function_names LIKE '%' || :functionName || '%'
    """)
    suspend fun searchByFunctionName(projectId: Long, functionName: String): List<FileMetadataEntity>
}
