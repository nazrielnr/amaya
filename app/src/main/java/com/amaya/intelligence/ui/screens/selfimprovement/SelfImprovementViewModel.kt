package com.amaya.intelligence.ui.screens.selfimprovement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.repository.MaintenanceScheduler
import com.amaya.intelligence.data.repository.PendingProposalRepository
import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.data.repository.ProposalApplyResult
import com.amaya.intelligence.domain.memory.MemoryClassifier
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PromptPreviewState(
    val userProfile: String = "",
    val agents: String = ""
)

data class SelfImprovementUiState(
    val pendingProposals: List<PendingProposal> = emptyList(),
    val lastMaintenanceRun: String = "Never",
    val promptPreview: PromptPreviewState = PromptPreviewState(),
    val lastApplyResults: List<ProposalApplyResult> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class SelfImprovementViewModel @Inject constructor(
    private val pendingProposalRepository: PendingProposalRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryClassifier: MemoryClassifier,
    private val maintenanceScheduler: MaintenanceScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelfImprovementUiState())
    val uiState: StateFlow<SelfImprovementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val proposals = pendingProposalRepository.listPending().filter { it.type.isSelfImprovementType() }
            _uiState.value = _uiState.value.copy(
                pendingProposals = proposals,
                promptPreview = buildPromptPreview(),
                isLoading = false
            )
        }
    }

    fun approve(id: String) {
        viewModelScope.launch {
            val result = pendingProposalRepository.approve(id)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Proposal approved" }, { "Approve failed: ${it.message}" }))
            refresh()
        }
    }

    fun reject(id: String) {
        viewModelScope.launch {
            val result = pendingProposalRepository.reject(id)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Proposal rejected" }, { "Reject failed: ${it.message}" }))
            refresh()
        }
    }

    fun applyApproved(id: String) {
        viewModelScope.launch {
            val result = pendingProposalRepository.applyApprovedWithResult(id)
            val applyResults = result.fold(
                onSuccess = { listOf(it) },
                onFailure = { error -> listOf(ProposalApplyResult(id, success = false, target = "Unknown", message = error.message ?: "Apply failed")) }
            )
            _uiState.value = _uiState.value.copy(
                lastApplyResults = applyResults,
                message = applyResults.firstOrNull()?.let { "${if (it.success) "Applied" else "Apply failed"}: ${it.target} — ${it.message}" }
            )
            refresh()
        }
    }

    fun applyAllApproved() {
        viewModelScope.launch {
            val result = pendingProposalRepository.applyAllApprovedWithResults()
            val applyResults = result.getOrElse { error ->
                listOf(ProposalApplyResult("all", success = false, target = "Approved proposals", message = error.message ?: "Apply failed"))
            }
            val successCount = applyResults.count { it.success }
            _uiState.value = _uiState.value.copy(
                lastApplyResults = applyResults,
                message = "Applied $successCount approved proposal(s). Memory changes affect the next chat turn."
            )
            refresh()
        }
    }

    fun runMaintenance() {
        viewModelScope.launch {
            val result = runCatching { maintenanceScheduler.runMaintenanceNow() }
            _uiState.value = _uiState.value.copy(
                lastMaintenanceRun = result.getOrNull()?.lastRunAt?.toString() ?: _uiState.value.lastMaintenanceRun,
                message = result.fold({ "Maintenance complete" }, { "Maintenance failed: ${it.message}" })
            )
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun buildPromptPreview(): PromptPreviewState {
        return PromptPreviewState(
            userProfile = redactForPreview(memoryRepository.readUserProfile()),
            agents = "# Project Memory\n\nSelect a workspace in Local Settings to preview project memory."
        )
    }

    private fun redactForPreview(content: String): String {
        return memoryClassifier.checkSafety(content).redactedContent.take(6_000)
    }

    private fun PendingProposalType.isSelfImprovementType(): Boolean =
        this == PendingProposalType.USER_PROFILE ||
            this == PendingProposalType.WORKSPACE_FACT ||
            this == PendingProposalType.SKILL_CREATE || this == PendingProposalType.SKILL_PATCH || this == PendingProposalType.SKILL_UPDATE
}
