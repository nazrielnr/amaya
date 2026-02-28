package com.amaya.intelligence.tools

import android.content.Context
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.CronRecurringType
import com.amaya.intelligence.data.local.db.entity.CronSessionMode
import com.amaya.intelligence.data.repository.CronJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI tool for scheduling reminders.
 *
 * Creates a CronJob entry + schedules an AlarmManager alarm.
 * Also appends an entry to today's daily memory log so AI remembers
 * it set a reminder in future sessions.
 */
@Singleton
class CreateReminderTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cronJobRepository: CronJobRepository
) : Tool {

    override val name = "create_reminder"

    override val description = """
        Schedule a reminder at a specific date and time. The user will receive an Android
        notification when the time arrives. Use this when the user asks to be reminded about
        something at a specific time (e.g., "remind me to buy milk at 5pm").
        
        Arguments:
        - title (string, required): Short title for the reminder (e.g., "Buy milk")
        - message (string, required): The reminder message shown in the notification
        - datetime (string, required): Date and time in ISO format "YYYY-MM-DDTHH:MM" 
          (e.g., "2026-02-27T17:00")
        - repeat (string, optional): "once" (default), "daily", or "weekly"
        - conversation_id (long, optional): ID of the current conversation so Amaya can reply there when the reminder fires
        - session_mode (string, optional): "continue" (default) = reply appended to the existing conversation; "new" = always start a fresh conversation when the reminder fires
    """.trimIndent()

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val title = arguments["title"] as? String
                ?: return@withContext ToolResult.Error("Missing required: title", ErrorType.VALIDATION_ERROR)
            val message = arguments["message"] as? String
                ?: return@withContext ToolResult.Error("Missing required: message", ErrorType.VALIDATION_ERROR)
            val datetimeStr = arguments["datetime"] as? String
                ?: return@withContext ToolResult.Error("Missing required: datetime (use YYYY-MM-DDTHH:MM)", ErrorType.VALIDATION_ERROR)
            val repeatStr      = (arguments["repeat"] as? String)?.lowercase() ?: "once"
            val sessionModeStr = (arguments["session_mode"] as? String)?.lowercase() ?: "continue"
            val conversationId = (arguments["conversation_id"] as? Number)?.toLong()
                ?: (arguments["conversation_id"] as? String)?.toLongOrNull()

            // Parse datetime
            val triggerMillis = parseDateTime(datetimeStr)
                ?: return@withContext ToolResult.Error(
                    "Cannot parse datetime: \"$datetimeStr\". Use format YYYY-MM-DDTHH:MM (e.g., 2026-02-27T17:00)",
                    ErrorType.VALIDATION_ERROR
                )

            if (triggerMillis <= System.currentTimeMillis()) {
                return@withContext ToolResult.Error(
                    "Datetime is in the past. Please provide a future time.",
                    ErrorType.VALIDATION_ERROR
                )
            }

            val recurringType = when (repeatStr) {
                "daily"  -> CronRecurringType.DAILY
                "weekly" -> CronRecurringType.WEEKLY
                else     -> CronRecurringType.ONCE
            }

            val sessionMode = if (sessionModeStr == "new") CronSessionMode.NEW else CronSessionMode.CONTINUE

            val job = CronJobEntity(
                title             = title.trim(),
                prompt            = message.trim(),
                triggerTimeMillis = triggerMillis,
                recurringType     = recurringType,
                isActive          = true,
                conversationId    = conversationId,
                sessionMode       = sessionMode
            )

            val id = try {
                cronJobRepository.addJob(job)
            } catch (e: Exception) {
                return@withContext ToolResult.Error(
                    "Failed to schedule reminder: ${e.message}",
                    ErrorType.EXECUTION_ERROR
                )
            }

            val fmtDisplay = SimpleDateFormat("EEE, dd MMM yyyy 'at' HH:mm", Locale.getDefault())
            val timeDisplay = fmtDisplay.format(Date(triggerMillis))

            ToolResult.Success(
                output = "✓ Reminder scheduled! \"$title\" — $timeDisplay" +
                        (if (recurringType != CronRecurringType.ONCE) " (repeats ${repeatStr})" else "") +
                        "\nYou'll receive a notification when the time comes.",
                metadata = mapOf(
                    "reminder_id" to id,
                    "title" to title,
                    "trigger_time_millis" to triggerMillis,
                    "recurring" to repeatStr
                )
            )
        }

    /**
     * Parse ISO datetime string "YYYY-MM-DDTHH:MM" or "YYYY-MM-DD HH:MM".
     * Returns epoch millis or null if unparseable.
     */
    private fun parseDateTime(input: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        )
        for (fmt in formats) {
            try {
                fmt.isLenient = false
                return fmt.parse(input.trim())?.time
            } catch (_: Exception) { }
        }
        return null
    }
}
