package com.amaya.intelligence.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing conversation history.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val workspacePath: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messagesJson: String // JSON array of messages
)
