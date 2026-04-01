package com.amaya.intelligence.data.local.dao

import androidx.room.*
import com.amaya.intelligence.data.local.entity.FileMetadataEntity

@Dao
interface FileMetadataDao {
    @Query("SELECT * FROM file_metadata WHERE file_id = :fileId")
    suspend fun getMetadataByFileId(fileId: Long): FileMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: FileMetadataEntity): Long

    @Query("DELETE FROM file_metadata WHERE file_id = :fileId")
    suspend fun deleteMetadataByFileId(fileId: Long)
}
