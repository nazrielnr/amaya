package com.amaya.intelligence.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CronRecurringType { ONCE, DAILY, WEEKLY }

/**
 * How the reminder reply is handled:
 * - CONTINUE: append AI reply into the same conversation where the reminder was created.
 * - NEW: always create a brand new conversation for each reminder firing.
 */
enum class CronSessionMode { CONTINUE, NEW }

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
    /** The conversation where this reminder was created — used when sessionMode=CONTINUE. */
    val conversationId: Long? = null,
    /** Times this job has fired (for recurring jobs). */
    val fireCount: Int = 0,
    /**
     * Session mode:
     * CONTINUE = append reply to [conversationId] (or create new if not found).
     * NEW      = always create a fresh conversation for each firing.
     */
    val sessionMode: CronSessionMode = CronSessionMode.CONTINUE
)
