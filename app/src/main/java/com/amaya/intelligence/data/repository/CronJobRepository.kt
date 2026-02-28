package com.amaya.intelligence.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import com.amaya.intelligence.data.local.db.dao.CronJobDao
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.CronRecurringType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CronJobRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CronJobDao
) {
    companion object {
        private const val TAG = "CronJobRepo"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val allJobs: Flow<List<CronJobEntity>> = dao.getAllJobs()
    val activeJobCount: Flow<Int> = dao.getActiveJobCount()

    /** Add a new cron job and schedule its alarm. */
    suspend fun addJob(job: CronJobEntity): Long {
        val id = dao.insertJob(job)
        debugLog(TAG) { "addJob: inserted id=$id, title=${job.title}, trigger=${job.triggerTimeMillis}, active=${job.isActive}" }
        if (job.isActive) scheduleAlarm(job.copy(id = id))
        return id
    }

    /** Update an existing job and reschedule. */
    suspend fun updateJob(job: CronJobEntity) {
        cancelAlarm(job.id)
        dao.updateJob(job)
        if (job.isActive) scheduleAlarm(job)
    }

    /** Delete a job and cancel its alarm. */
    suspend fun deleteJob(id: Long) {
        cancelAlarm(id)
        dao.deleteJobById(id)
    }

    /** Toggle a job on/off. */
    suspend fun setActive(id: Long, active: Boolean) {
        dao.setActive(id, active)
        val job = dao.getJobById(id) ?: return
        if (active) scheduleAlarm(job) else cancelAlarm(id)
    }

    /** Called by ReminderWorker after firing. Updates trigger for recurring jobs. */
    suspend fun onJobFired(id: Long) {
        val job = dao.getJobById(id) ?: return
        debugLog(TAG) { "onJobFired: id=$id, type=${job.recurringType}, fireCount=${job.fireCount}" }
        // Always increment fire count
        dao.incrementFireCount(id)
        when (job.recurringType) {
            CronRecurringType.ONCE -> {
                dao.setActive(id, false)
                debugLog(TAG) { "onJobFired: ONCE job $id marked inactive" }
            }
            CronRecurringType.DAILY -> {
                val nextTime = job.triggerTimeMillis + 24 * 60 * 60 * 1000L
                dao.updateTriggerTime(id, nextTime)
                scheduleAlarm(job.copy(triggerTimeMillis = nextTime))
                debugLog(TAG) { "onJobFired: DAILY job $id rescheduled to $nextTime" }
            }
            CronRecurringType.WEEKLY -> {
                val nextTime = job.triggerTimeMillis + 7 * 24 * 60 * 60 * 1000L
                dao.updateTriggerTime(id, nextTime)
                scheduleAlarm(job.copy(triggerTimeMillis = nextTime))
                debugLog(TAG) { "onJobFired: WEEKLY job $id rescheduled to $nextTime" }
            }
        }
    }

    /** Reschedule all active alarms (called on boot). */
    suspend fun rescheduleAll() {
        val jobs = dao.getActiveJobs().first()
        jobs.forEach { job ->
            // If trigger time is in the past for ONCE jobs, mark inactive
            if (job.recurringType == CronRecurringType.ONCE &&
                job.triggerTimeMillis < System.currentTimeMillis()
            ) {
                dao.setActive(job.id, false)
            } else {
                scheduleAlarm(job)
            }
        }
    }

    private fun scheduleAlarm(job: CronJobEntity) {
        val intent = makePendingIntent(job) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                debugLog(TAG, "scheduleAlarm: exact alarms not permitted, using inexact for job ${job.id}")
                alarmManager.set(AlarmManager.RTC_WAKEUP, job.triggerTimeMillis, intent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    job.triggerTimeMillis,
                    intent
                )
                debugLog(TAG) { "scheduleAlarm: exact alarm set for job ${job.id} at ${job.triggerTimeMillis} (in ${(job.triggerTimeMillis - System.currentTimeMillis()) / 1000}s)" }
            }
        } catch (e: SecurityException) {
            errorLog(TAG, "scheduleAlarm: SecurityException for job ${job.id}, falling back to inexact: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, job.triggerTimeMillis, intent)
        }
    }

    private fun cancelAlarm(id: Long) {
        // Build a minimal stub PendingIntent to match by requestCode (job id)
        val stubJob = CronJobEntity(id = id, title = "", prompt = "", triggerTimeMillis = 0)
        makePendingIntent(stubJob)?.let { alarmManager.cancel(it) }
    }

    private fun makePendingIntent(job: CronJobEntity): PendingIntent? {
        val intent = Intent(context, Class.forName("com.amaya.intelligence.service.CronJobReceiver")).apply {
            action = "com.amaya.intelligence.CRON_JOB_FIRE"
            putExtra("job_id", job.id)
            putExtra("conversation_id", job.conversationId ?: -1L)
            putExtra("title", job.title)
            putExtra("prompt", job.prompt)
        }
        // FIX 1: job.id.toInt() overflows for large IDs (Long > Int.MAX_VALUE).
        // Use stable hash that preserves uniqueness within Int range.
        val requestCode = (job.id and 0x7FFFFFFF).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    /** Returns true if exact alarms can be scheduled (Android 12+ permission check). */
    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
