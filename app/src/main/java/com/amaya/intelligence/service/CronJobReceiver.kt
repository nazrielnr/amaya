package com.amaya.intelligence.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Receives the AlarmManager broadcast when a cron job fires.
 * Immediately delegates to [ReminderWorker] via WorkManager so the
 * AI call (which can take seconds) runs safely in the background.
 */
class CronJobReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CronJobReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.amaya.intelligence.CRON_JOB_FIRE") {
            debugLog(TAG) { "onReceive: ignoring action=${intent.action}" }
            return
        }
        val jobId          = intent.getLongExtra("job_id", -1L)
        val conversationId = intent.getLongExtra("conversation_id", -1L)
        val title          = intent.getStringExtra("title") ?: "Reminder"
        val prompt         = intent.getStringExtra("prompt") ?: title

        debugLog(TAG) { "onReceive: jobId=$jobId, convId=$conversationId, title=$title" }

        if (jobId < 0) {
            errorLog(TAG, "onReceive: invalid jobId=$jobId, aborting")
            return
        }

        val inputData = Data.Builder()
            .putLong(ReminderWorker.KEY_JOB_ID, jobId)
            .putLong(ReminderWorker.KEY_CONVERSATION_ID, conversationId)
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_PROMPT, prompt)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(inputData)
            // FIX 6: Add exponential backoff so transient failures (network, AI rate-limit)
            // are retried gracefully — without this, Result.retry() uses default 10s linear backoff.
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueue(request)

        debugLog(TAG) { "onReceive: ReminderWorker enqueued for jobId=$jobId" }
    }
}
