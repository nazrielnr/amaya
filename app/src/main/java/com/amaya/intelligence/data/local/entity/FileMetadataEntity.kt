package com.amaya.intelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "file_id")
    val fileId: Long,

    @ColumnInfo(name = "language")
    val language: String,

    @ColumnInfo(name = "class_names")
    val classNames: String? = null,

    @ColumnInfo(name = "function_names")
    val functionNames: String? = null,

    @ColumnInfo(name = "imports")
    val imports: String? = null,

    @ColumnInfo(name = "loc")
    val linesOfCode: Int? = null,

    @ColumnInfo(name = "extracted_at")
    val extractedAt: Long = System.currentTimeMillis()
)
