package com.amaya.intelligence.ui.screens.settings.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.repository.UpdateRepository
import com.amaya.intelligence.domain.models.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun checkForUpdate(manual: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            val info = updateRepository.getLatestUpdate()
            if (info != null) {
                if (info.isNewer) {
                    _uiState.value = UpdateUiState.UpdateAvailable(info)
                } else {
                    _uiState.value = if (manual) UpdateUiState.UpToDate else UpdateUiState.Idle
                }
            } else {
                _uiState.value = if (manual) UpdateUiState.Error("Failed to check for updates") else UpdateUiState.Idle
            }
        }
    }

    fun dismiss() {
        _uiState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState()
    object UpToDate : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
