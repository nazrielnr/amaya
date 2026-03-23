package com.amaya.intelligence.ui.components.shared

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object TimeUtils {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatRelativeTime(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val resetDate = isoFormat.parse(isoString) ?: return null
            val now = Date()
            val diffMs = resetDate.time - now.time

            if (diffMs <= 0) return "soon"

            val diffMins = diffMs / (60 * 1000)
            val diffHours = diffMins / 60
            val diffDays = diffHours / 24

            when {
                diffDays > 0 -> {
                    val remHours = diffHours % 24
                    if (remHours > 0) "$diffDays days, $remHours hours" else "$diffDays days"
                }
                diffHours > 0 -> {
                    val remMins = diffMins % 60
                    if (remMins > 0) "$diffHours hours, $remMins minutes" else "$diffHours hours"
                }
                else -> "$diffMins minutes"
            }
        } catch (e: Exception) {
            isoString // Fallback to raw string if parsing fails
        }
    }

    fun parseResetTime(resetTime: String): String {
        // Parse ISO 8601 format like "2026-03-21T15:33:13Z" to dd/mm/yyyy HH:mm
        return try {
            val isoFormat = resetTime.replace("Z", "+00:00")
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            val date = formatter.parse(isoFormat)
            if (date != null) {
                val outputFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                outputFormatter.timeZone = TimeZone.getDefault()
                outputFormatter.format(date)
            } else {
                resetTime
            }
        } catch (e: Exception) {
            resetTime
        }
    }
}
