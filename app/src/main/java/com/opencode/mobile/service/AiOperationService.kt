package com.opencode.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opencode.mobile.R
import com.opencode.mobile.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service for long-running AI operations.
 * 
 * This service ensures that AI operations continue even when the app is in the background,
 * and prevents the system from killing the process during heavy operations.
 */
@AndroidEntryPoint
class AiOperationService : Service() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState
    
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_OPERATION -> {
                val operationName = intent.getStringExtra(EXTRA_OPERATION_NAME) ?: "AI Operation"
                startForegroundOperation(operationName)
            }
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getStringExtra(EXTRA_PROGRESS) ?: ""
                updateProgress(progress)
            }
            ACTION_STOP_OPERATION -> {
                stopForegroundOperation()
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when AI is performing long operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundOperation(operationName: String) {
        _operationState.value = OperationState.Running(operationName)
        
        val notification = buildNotification(
            title = "OpenCode AI Working",
            content = operationName
        )
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun updateProgress(progress: String) {
        val currentState = _operationState.value
        if (currentState is OperationState.Running) {
            val notification = buildNotification(
                title = "OpenCode AI Working",
                content = progress
            )
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    private fun stopForegroundOperation() {
        _operationState.value = OperationState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    sealed class OperationState {
        data object Idle : OperationState()
        data class Running(val operation: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }
    
    companion object {
        private const val CHANNEL_ID = "ai_operation_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START_OPERATION = "com.opencode.mobile.START_OPERATION"
        const val ACTION_UPDATE_PROGRESS = "com.opencode.mobile.UPDATE_PROGRESS"
        const val ACTION_STOP_OPERATION = "com.opencode.mobile.STOP_OPERATION"
        
        const val EXTRA_OPERATION_NAME = "operation_name"
        const val EXTRA_PROGRESS = "progress"
        
        /**
         * Start a foreground AI operation.
         */
        fun startOperation(context: Context, operationName: String) {
            val intent = Intent(context, AiOperationService::class.java).apply {
                action = ACTION_START_OPERATION
                putExtra(EXTRA_OPERATION_NAME, operationName)
            }
            context.startForegroundService(intent)
        }
        
        /**
         * Update the progress of the current operation.
         */
        fun updateProgress(context: Context, progress: String) {
            val intent = Intent(context, AiOperationService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_PROGRESS, progress)
            }
            context.startService(intent)
        }
        
        /**
         * Stop the foreground operation.
         */
        fun stopOperation(context: Context) {
            val intent = Intent(context, AiOperationService::class.java).apply {
                action = ACTION_STOP_OPERATION
            }
            context.startService(intent)
        }
    }
}
