package com.amaya.intelligence.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amaya.intelligence.R
import com.amaya.intelligence.data.local.db.dao.ConversationDao
import com.amaya.intelligence.data.local.db.entity.ConversationEntity
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.remote.api.ChatMessage
import com.amaya.intelligence.data.remote.api.MessageRole
import com.amaya.intelligence.data.repository.AiRepository
import com.amaya.intelligence.data.repository.AgentEvent
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.toList

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val conversationDao: ConversationDao,
    private val cronJobRepository: CronJobRepository,
    private val aiRepository: AiRepository,
    private val aiSettingsManager: AiSettingsManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG              = "ReminderWorker"
        const val KEY_JOB_ID          = "job_id"
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_TITLE           = "title"
        const val KEY_PROMPT          = "prompt"
        private const val CHANNEL_ID  = "amaya_reminders"
        private const val CHANNEL_NAME = "Amaya Reminders"
    }

    override suspend fun doWork(): Result {
        val jobId          = inputData.getLong(KEY_JOB_ID, -1L)
        val conversationId = inputData.getLong(KEY_CONVERSATION_ID, -1L).takeIf { it > 0 }
        val title          = inputData.getString(KEY_TITLE) ?: "Reminder"
        val prompt         = inputData.getString(KEY_PROMPT) ?: title

        Log.d(TAG, "doWork: START jobId=$jobId, convId=$conversationId, title=$title")

        try {
            // ── Build AI reply ────────────────────────────────────────────
            val history: List<ChatMessage> = if (conversationId != null) {
                conversationDao.getConversationById(conversationId)
                    ?.let { parseHistory(it) }
                    ?: emptyList()
            } else {
                emptyList()
            }
            Log.d(TAG, "doWork: loaded ${history.size} history messages")

            val reminderTriggerMsg = "⏰ [REMINDER FIRED] Your scheduled reminder has arrived: \"$title\". " +
                    "Please acknowledge this reminder and respond to the user naturally as Amaya, " +
                    "referencing the context of the original request if visible in history."

            val aiReply = StringBuilder()
            aiRepository.chat(
                message             = reminderTriggerMsg,
                conversationHistory = history,
                projectId           = null,
                workspacePath       = null,
                onConfirmation      = { false }   // auto-deny confirmations from background
            ).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> aiReply.append(event.text)
                    is AgentEvent.Error -> Log.e(TAG, "doWork: AI error: ${event.message}")
                    else -> Unit
                }
            }

            val replyText = aiReply.toString().trim().ifBlank { "⏰ Reminder: $title" }
            Log.d(TAG, "doWork: AI reply length=${replyText.length}")

            // ── Append AI reply to conversation in Room ───────────────────
            if (conversationId != null) {
                appendReplyToConversation(conversationId, replyText)
                Log.d(TAG, "doWork: reply appended to conversation $conversationId")
            }

            // ── Mark job fired ────────────────────────────────────────────
            if (jobId > 0) cronJobRepository.onJobFired(jobId)

            // ── Show notification ─────────────────────────────────────────
            showNotification(title, replyText, conversationId)
            Log.d(TAG, "doWork: DONE successfully")

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "doWork: FAILED", e)
            // Still show a basic notification even if AI call fails
            showNotification(title, "⏰ $title", conversationId)
            if (jobId > 0) runCatching { cronJobRepository.onJobFired(jobId) }
            return Result.failure()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun parseHistory(entity: ConversationEntity): List<ChatMessage> {
        if (entity.messagesJson.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(entity.messagesJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val role = when (obj.getString("role")) {
                    "USER"      -> MessageRole.USER
                    "ASSISTANT" -> MessageRole.ASSISTANT
                    "SYSTEM"    -> MessageRole.SYSTEM
                    else        -> return@mapNotNull null
                }
                ChatMessage(role = role, content = obj.getString("content"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun appendReplyToConversation(conversationId: Long, replyText: String) {
        val existing = conversationDao.getConversationById(conversationId) ?: return
        val messagesJson = try {
            val arr = if (existing.messagesJson.isBlank()) org.json.JSONArray()
                      else org.json.JSONArray(existing.messagesJson)
            val obj = org.json.JSONObject()
            obj.put("role", "ASSISTANT")
            obj.put("content", replyText)
            arr.put(obj)
            arr.toString()
        } catch (_: Exception) { existing.messagesJson }

        conversationDao.updateConversation(
            existing.copy(messagesJson = messagesJson, updatedAt = System.currentTimeMillis())
        )
    }

    private fun showNotification(title: String, body: String, conversationId: Long?) {
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = "Scheduled reminders from Amaya"
                    enableVibration(true)
                }
            notifManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            conversationId?.let { putExtra("open_conversation_id", it) }
        }
        val pendingTap = PendingIntent.getActivity(
            context, conversationId?.toInt() ?: 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Amaya: $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingTap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notifManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
