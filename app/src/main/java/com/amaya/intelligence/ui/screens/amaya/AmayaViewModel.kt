package com.amaya.intelligence.ui.screens.amaya

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaya.intelligence.data.repository.BrainSettings
import com.amaya.intelligence.data.repository.BrainSettingsRepository
import com.amaya.intelligence.data.repository.ContextRecallSettings
import com.amaya.intelligence.data.repository.MemoryBehaviorSettings
import com.amaya.intelligence.data.repository.MemoryRecord
import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.data.repository.WorkspaceMemoryBinding
import com.amaya.intelligence.data.repository.PendingProposalRepository
import com.amaya.intelligence.data.repository.ProposalApplyResult
import com.amaya.intelligence.data.repository.SkillBehaviorSettings
import com.amaya.intelligence.data.repository.SkillRepository
import com.amaya.intelligence.domain.memory.MemoryAction
import com.amaya.intelligence.domain.memory.MemoryScope
import com.amaya.intelligence.domain.memory.MemoryType
import com.amaya.intelligence.domain.memory.PendingProposal
import com.amaya.intelligence.domain.memory.PendingProposalType
import com.amaya.intelligence.domain.skills.SkillMetadata
import com.amaya.intelligence.domain.skills.SkillStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmayaViewModel @Inject constructor(
    private val brainSettingsRepository: BrainSettingsRepository,
    private val memoryRepository: MemoryRepository,
    private val memoryClassifier: com.amaya.intelligence.domain.memory.MemoryClassifier,
    private val skillRepository: SkillRepository,
    private val pendingProposalRepository: PendingProposalRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AmayaUiState())
    val uiState: StateFlow<AmayaUiState> = _uiState.asStateFlow()
    private var workspacePath: String? = null

    init { refresh() }

    fun setWorkspace(path: String?) {
        workspacePath = path?.takeIf(String::isNotBlank)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val settings = brainSettingsRepository.getBrainSettings()
            val canonicalWorkspace = workspacePath?.let { runCatching { java.io.File(it).canonicalPath }.getOrDefault(it) }
            val proposals = pendingProposalRepository.listPending(100).filter { proposal ->
                proposal.workspacePath == null || canonicalWorkspace != null &&
                    runCatching { java.io.File(proposal.workspacePath).canonicalPath }.getOrDefault(proposal.workspacePath) == canonicalWorkspace
            }
            val skills = skillRepository.listSkills()
            val userRecords = memoryRepository.listMemoryRecords(type = com.amaya.intelligence.domain.memory.MemoryType.USER_PROFILE, limit = 100)
            val projectRecords = memoryRepository.listMemoryRecords(type = com.amaya.intelligence.domain.memory.MemoryType.WORKSPACE_FACT, limit = 100, workspacePath = workspacePath)
            val rawUserMemory = memoryRepository.readUserProfile()
            val rawProjectMemory = memoryRepository.readWorkspaceFacts(workspacePath)
            val workspaceBindings = memoryRepository.listWorkspaceBindings()
            _uiState.value = _uiState.value.copy(
                settings = settings,
                pendingProposals = proposals,
                skills = skills,
                userMemoryPreview = rawUserMemory.toFriendlyPreview(),
                projectMemoryPreview = rawProjectMemory.toFriendlyPreview(),
                userMemoryRaw = rawUserMemory,
                projectMemoryRaw = rawProjectMemory,
                userMemoryRecords = userRecords,
                projectMemoryRecords = projectRecords,
                workspaceBindings = workspaceBindings,
                workspacePath = workspacePath,
                userMemoryCount = userRecords.size,
                projectMemoryCount = projectRecords.size,
                isLoading = false
            )
        }
    }

    fun updateMemorySettings(transform: (MemoryBehaviorSettings) -> MemoryBehaviorSettings) {
        viewModelScope.launch {
            val current = brainSettingsRepository.getBrainSettings()
            brainSettingsRepository.setMemorySettings(transform(current.memory))
            refresh()
        }
    }

    fun updateSkillSettings(transform: (SkillBehaviorSettings) -> SkillBehaviorSettings) {
        viewModelScope.launch {
            val current = brainSettingsRepository.getBrainSettings()
            brainSettingsRepository.setSkillSettings(transform(current.skills))
            refresh()
        }
    }

    fun setSkillEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            val skill = skillRepository.getSkill(name)
            if (skill == null) {
                _uiState.value = _uiState.value.copy(message = "Skill not found")
                return@launch
            }
            val result = skillRepository.updateSkillMetadata(
                skill.metadata.copy(enabled = enabled, updatedAt = System.currentTimeMillis())
            )
            _uiState.value = _uiState.value.copy(message = result.fold({ if (enabled) "Skill enabled" else "Skill disabled" }, { "Update failed: ${it.message}" }))
            refresh()
        }
    }

    fun updateContextSettings(transform: (ContextRecallSettings) -> ContextRecallSettings) {
        viewModelScope.launch {
            val current = brainSettingsRepository.getBrainSettings()
            brainSettingsRepository.setContextRecallSettings(transform(current.context))
            refresh()
        }
    }

    fun saveSuggestion(id: String) {
        viewModelScope.launch {
            val approve = pendingProposalRepository.approve(id)
            val result = if (approve.isSuccess) pendingProposalRepository.applyApprovedWithResult(id) else Result.failure(approve.exceptionOrNull() ?: IllegalStateException("Approve failed"))
            _uiState.value = _uiState.value.copy(
                lastApplyResults = result.fold({ listOf(it) }, { listOf(ProposalApplyResult(id, false, "Suggestion", it.message ?: "Save failed")) }),
                message = result.fold({ "Suggestion saved" }, { "Save failed: ${it.message}" })
            )
            refresh()
        }
    }

    fun addMemory(area: MemoryArea, content: String) {
        viewModelScope.launch {
            val clean = content.trim()
            if (clean.isBlank()) {
                _uiState.value = _uiState.value.copy(message = "Memory content is empty")
                return@launch
            }
            val proposal = memoryClassifier.classify(
                content = clean,
                requestedType = area.memoryType(),
                requestedAction = MemoryAction.ADD,
                requestedScope = area.memoryScope(),
                reason = "The user manually added this memory in Settings.",
                confidence = 0.95,
                workspacePath = workspacePath
            )
            val result = memoryRepository.applyProposal(proposal)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Saved" }, { "Save failed: ${it.message}" }))
            refresh()
        }
    }

    fun deleteMemory(area: MemoryArea, record: MemoryRecord) {
        viewModelScope.launch {
            val result = memoryRepository.deleteMemoryById(record.id, record.version, if (area == MemoryArea.PROJECT) workspacePath else null)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Deleted" }, { "Delete failed: ${it.message}" }))
            refresh()
        }
    }

    fun reconnectWorkspaceMemory(workspaceId: String) {
        val root = workspacePath
        if (root == null) {
            _uiState.value = _uiState.value.copy(message = "Select the moved workspace first")
            return
        }
        viewModelScope.launch {
            val result = memoryRepository.remapWorkspace(workspaceId, root)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Workspace memory reconnected" }, { "Reconnect failed: ${it.message}" }))
            refresh()
        }
    }

    fun dismissSuggestion(id: String) {
        viewModelScope.launch {
            val result = pendingProposalRepository.reject(id)
            _uiState.value = _uiState.value.copy(message = result.fold({ "Suggestion dismissed" }, { "Dismiss failed: ${it.message}" }))
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

data class AmayaUiState(
    val settings: BrainSettings = BrainSettings(),
    val pendingProposals: List<PendingProposal> = emptyList(),
    val skills: List<SkillMetadata> = emptyList(),
    val userMemoryPreview: String = "",
    val projectMemoryPreview: String = "",
    val userMemoryRaw: String = "",
    val projectMemoryRaw: String = "",
    val userMemoryRecords: List<MemoryRecord> = emptyList(),
    val projectMemoryRecords: List<MemoryRecord> = emptyList(),
    val workspaceBindings: List<WorkspaceMemoryBinding> = emptyList(),
    val workspacePath: String? = null,
    val userMemoryCount: Int = 0,
    val projectMemoryCount: Int = 0,
    val lastApplyResults: List<ProposalApplyResult> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null
) {
    val activeSkills: Int get() = skills.count { it.status == SkillStatus.ACTIVE }
    val enabledSkills: Int get() = skills.count { it.status == SkillStatus.ACTIVE && it.enabled }
    val reviewSkills: Int get() = skills.count { it.needsReview }
    val skillSuggestions: Int get() = pendingProposals.count { it.type.isSkillType() }
    val totalMemoryCount: Int get() = userMemoryCount + projectMemoryCount
}

fun PendingProposalType.isMemoryType(): Boolean =
    this == PendingProposalType.USER_PROFILE || this == PendingProposalType.WORKSPACE_FACT

fun PendingProposalType.isSkillType(): Boolean = this == PendingProposalType.SKILL_CREATE ||
    this == PendingProposalType.SKILL_PATCH || this == PendingProposalType.SKILL_UPDATE

private fun MemoryArea.memoryType(): MemoryType = when (this) {
    MemoryArea.USER -> MemoryType.USER_PROFILE
    MemoryArea.PROJECT -> MemoryType.WORKSPACE_FACT
}

private fun MemoryArea.memoryScope(): MemoryScope = when (this) {
    MemoryArea.USER -> MemoryScope.USER
    MemoryArea.PROJECT -> MemoryScope.WORKSPACE
}

private fun String.toFriendlyPreview(): String = lines()
    .map { it.trim() }
    .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith(">") && !it.startsWith("*(") }
    .take(6)
    .joinToString("\n")
    .ifBlank { "No saved items" }
