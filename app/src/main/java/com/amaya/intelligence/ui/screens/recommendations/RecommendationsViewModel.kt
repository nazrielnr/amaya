package com.amaya.intelligence.ui.screens.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.repository.RecommendationRepository
import com.amaya.intelligence.domain.memory.Recommendation
import com.amaya.intelligence.domain.memory.RecommendationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationsUiState(
    val recommendations: List<Recommendation> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
)

/**
 * Drives the recommendations screen: lists every implementation recommendation (optionally scoped
 * to a workspace) and exposes the guarded lifecycle actions (accept / start / verify / complete /
 * archive). VERIFY takes evidence text, which the repository checks against the recommendation's
 * verification rule before promoting it — "user said done" (COMPLETED) and "system proved done"
 * (VERIFIED) stay distinct here just like in the memory confidence breaker.
 */
@HiltViewModel
class RecommendationsViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecommendationsUiState())
    val uiState: StateFlow<RecommendationsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val items = recommendationRepository.list(workspacePath = null, limit = 200)
            _uiState.value = RecommendationsUiState(recommendations = items, isLoading = false)
        }
    }

    fun accept(id: String) = transition(id, RecommendationStatus.ACCEPTED, "Recommendation accepted")
    fun start(id: String) = transition(id, RecommendationStatus.IN_PROGRESS, "Work started")
    fun complete(id: String) = transition(id, RecommendationStatus.COMPLETED, "Marked completed (user claim)")
    fun archive(id: String) = transition(id, RecommendationStatus.ARCHIVED, "Recommendation archived")

    fun verify(id: String, evidence: String) {
        viewModelScope.launch {
            val result = recommendationRepository.verify(id, evidence)
            _uiState.value = _uiState.value.copy(
                message = result.fold(
                    { "Verified by evidence — provenance linked to related memories" },
                    { "Verification failed: ${it.message}" }
                )
            )
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun transition(id: String, target: RecommendationStatus, successMessage: String) {
        viewModelScope.launch {
            val result = recommendationRepository.transition(id, target)
            _uiState.value = _uiState.value.copy(
                message = result.fold({ successMessage }, { "Action failed: ${it.message}" })
            )
            refresh()
        }
    }
}
