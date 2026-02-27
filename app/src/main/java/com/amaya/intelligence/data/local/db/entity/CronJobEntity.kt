package com.amaya.intelligence.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CronRecurringType { ONCE, DAILY, WEEKLY }

@Entity(tableName = "cron_jobs")
data class CronJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    /** The prompt/message to send to AI when this job fires. */
    val prompt: String,
    /** Epoch millis for the first (or next) trigger time. */
    val triggerTimeMillis: Long,
    val recurringType: CronRecurringType = CronRecurringType.ONCE,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** The conversation where this reminder was created — reply goes here. */
    val conversationId: Long? = null,
    /** Times this job has fired (for recurring jobs). */
    val fireCount: Int = 0
)
