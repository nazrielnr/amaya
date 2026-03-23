package com.amaya.intelligence.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amaya.intelligence.data.repository.CronJobRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives BOOT_COMPLETED broadcast and reschedules all active cron job alarms.
 * This is necessary because AlarmManager alarms are lost when the device reboots.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var cronJobRepository: CronJobRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        val pendingResult = goAsync()
        // FIX 7: Use SupervisorJob so a failure in rescheduleAll() doesn't silently kill the scope.
        // The scope is created locally and tied to pendingResult.finish() — no leak.
        val job = kotlinx.coroutines.SupervisorJob()
        CoroutineScope(Dispatchers.IO + job).launch {
            try {
                cronJobRepository.rescheduleAll()
            } catch (e: Exception) {
                com.amaya.intelligence.util.errorLog("BootReceiver", "rescheduleAll failed: ${e.message}")
            } finally {
                pendingResult.finish()
                job.cancel()
            }
        }
    }
}
