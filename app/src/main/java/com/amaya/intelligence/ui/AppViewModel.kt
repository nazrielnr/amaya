package com.amaya.intelligence.ui

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
}
