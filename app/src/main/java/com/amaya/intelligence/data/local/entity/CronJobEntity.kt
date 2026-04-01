package com.amaya.intelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CronRecurringType {
    ONCE,
    DAILY,
    WEEKLY
}

enum class CronSessionMode {
    CONTINUE,
    NEW
}

@Entity(tableName = "cron_jobs")
data class CronJobEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "prompt")
    val prompt: String,

    @ColumnInfo(name = "trigger_time_millis")
    val triggerTimeMillis: Long,

    @ColumnInfo(name = "recurring_type")
    val recurringType: CronRecurringType,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "conversation_id")
    val conversationId: Long? = null,

    @ColumnInfo(name = "fire_count")
    val fireCount: Int = 0,

    @ColumnInfo(name = "session_mode")
    val sessionMode: CronSessionMode
)
