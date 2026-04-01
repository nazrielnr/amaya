package com.amaya.intelligence.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.repository.CronJobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Application-scoped ViewModel for global state that must persist
 * across conversation switches (e.g. active reminder count).
 *
 * Injected at [MainActivity] level so it's tied to the process lifetime,
 * not to a single chat screen.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    cronJobRepository: CronJobRepository
) : ViewModel() {

    /** Count of currently active/pending cron jobs — shown as badge in header. */
    val activeReminderCount: StateFlow<Int> = cronJobRepository.activeJobCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Global permission states for File Access, Camera, and Notifications. */
    private val _hasStoragePermission = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission

    private val _hasCameraPermission = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission

    private val _hasNotificationPermission = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission

    private val _hasExactAlarmPermission = kotlinx.coroutines.flow.MutableStateFlow(false)
    val hasExactAlarmPermission: StateFlow<Boolean> = _hasExactAlarmPermission

    private val _isIgnoringBatteryOptimizations = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations

    fun setStoragePermission(granted: Boolean) { _hasStoragePermission.value = granted }
    fun setCameraPermission(granted: Boolean) { _hasCameraPermission.value = granted }
    fun setNotificationPermission(granted: Boolean) { _hasNotificationPermission.value = granted }
    fun setExactAlarmPermission(granted: Boolean) { _hasExactAlarmPermission.value = granted }
    fun setBatteryOptimizationIgnored(ignored: Boolean) { _isIgnoringBatteryOptimizations.value = ignored }
}
