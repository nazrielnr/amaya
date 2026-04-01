package com.amaya.intelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = FileEntity::class)
@Entity(tableName = "files_fts")
data class FileFtsEntity(
    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "relative_path")
    val relativePath: String
)
