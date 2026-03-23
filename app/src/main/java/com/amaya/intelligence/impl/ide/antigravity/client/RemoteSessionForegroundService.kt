package com.amaya.intelligence.impl.ide.antigravity.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.amaya.intelligence.R

class RemoteSessionForegroundService : Service() {

    private var lastUpdateAtMs: Long = 0L
    private var lastState: String = ""
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        // Satisfy the requirement to call startForeground immediately on Android 12+
        // This avoids the ForegroundServiceDidNotStartInTimeException if onStartCommand is delayed.
        val title = "Remote session"
        val text = "Memulai koneksi..."
        promoteToForeground(title, text)
    }

    private fun promoteToForeground(title: String, text: String) {
        val notification = buildNotification(title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                isForeground = true
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                    android.util.Log.e("RemoteSessionService", "Foreground start not allowed: ${e.message}")
                    // On Android 12+, we can't do anything if not allowed, but we shouldn't crash.
                    // The service will continue as a background service (until killed)
                } else {
                    android.util.Log.e("RemoteSessionService", "Failed to start foreground: ${e.message}")
                    try {
                        startForeground(NOTIFICATION_ID, notification)
                        isForeground = true
                    } catch (e2: Exception) {
                        android.util.Log.e("RemoteSessionService", "Fallback startForeground failed: ${e2.message}")
                    }
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
            } catch (e: Exception) {
                android.util.Log.e("RemoteSessionService", "startForeground failed: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        if (intent?.action == ACTION_STOP) {
            isForeground = false
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_UPDATE) {
            val state = intent.getStringExtra(EXTRA_STATE) ?: STATE_CONNECTED
            val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
            updateNotification(state, detail)
            return START_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Remote session aktif"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Menjaga koneksi WebSocket tetap hidup"
        
        promoteToForeground(title, text)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, text: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(state: String, detail: String) {
        val now = SystemClock.elapsedRealtime()
        val signature = "$state|$detail"
        // Removed internal rate limit because we MUST call startForeground here 
        // to ensure every delivery of startForegroundService is matched by a call 
        // to startForeground(). The caller (RemoteSessionClient) already rate-limits 
        // its calls to updateStatus.

        val (title, text) = when (state) {
            STATE_CONNECTED -> "Remote connected" to "Koneksi WebSocket aktif"
            STATE_THINKING -> "Streaming" to "Thinking: ${detail.take(80)}"
            STATE_TOOL -> "Streaming" to detail.take(80) 
            STATE_TEXT -> "Streaming" to "Text: ${detail.take(80)}"
            STATE_DONE -> "Remote connected" to "Selesai"
            else -> "Remote session" to "Aktif"
        }

        promoteToForeground(title, text)
        lastState = signature
        lastUpdateAtMs = now
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Remote Session",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Menjaga sesi remote tetap aktif di background"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "remote_session_channel"
        private const val NOTIFICATION_ID = 44021
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"
        private var isServiceRunning = false

        private const val ACTION_UPDATE = "com.amaya.intelligence.remote.UPDATE_STATUS"
        private const val ACTION_STOP = "com.amaya.intelligence.remote.STOP"
        private const val EXTRA_STATE = "extra_state"
        private const val EXTRA_DETAIL = "extra_detail"

        const val STATE_CONNECTED = "connected"
        const val STATE_THINKING = "thinking"
        const val STATE_TOOL = "tool"
        const val STATE_TEXT = "text"
        const val STATE_DONE = "done"

        fun start(context: Context) {
            val intent = Intent(context, RemoteSessionForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, "Remote session berjalan")
                putExtra(EXTRA_TEXT, "Koneksi tetap dijaga saat aplikasi di background")
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Background start restriction
                android.util.Log.e("RemoteSessionService", "Failed to start service: ${e.message}")
            }
        }

        fun updateStatus(context: Context, state: String, detail: String = "") {
            val intent = Intent(context, RemoteSessionForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_DETAIL, detail)
            }
            try {
                if (isServiceRunning) {
                    // If already running, just startService is enough (updates notification via onStartCommand)
                    context.startService(intent)
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("RemoteSessionService", "Failed to update status: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RemoteSessionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
