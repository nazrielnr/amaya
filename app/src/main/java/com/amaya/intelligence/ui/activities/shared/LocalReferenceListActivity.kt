package com.amaya.intelligence.ui.activities.shared

import com.amaya.intelligence.R

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.ProjectDao
import com.amaya.intelligence.data.repository.ReferenceDocumentRepository
import com.amaya.intelligence.ui.screens.shared.ReferenceListScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocalReferenceListActivity : AppCompatActivity() {
    @Inject lateinit var projectDao: ProjectDao
    @Inject lateinit var agentDao: AgentDao
    @Inject lateinit var repository: ReferenceDocumentRepository
    private val ownerType by lazy { intent.getStringExtra(EXTRA_OWNER_TYPE).orEmpty() }
    private val ownerId by lazy { intent.getLongExtra(EXTRA_OWNER_ID, -1L) }
    private val paths = mutableStateOf<List<String>>(emptyList())
    private val message = mutableStateOf<String?>(null)
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            repository.import(ownerType, ownerId, uri).onSuccess { path -> append(path); refresh() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(ownerType, ownerId) { refresh() }
                LaunchedEffect(message.value) { message.value?.let { snackbarHostState.showSnackbar(it); message.value = null } }
                val items by paths
                ReferenceListScreen(
                    title = when (ownerType) { "project" -> getString(R.string.project_references); "agent_group" -> getString(R.string.shared_references); else -> getString(R.string.agent_references) },
                    paths = items,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = { finish() },
                    onAdd = { picker.launch(arrayOf("text/*")) },
                    onAddManual = { name, content -> lifecycleScope.launch {
                        repository.saveManual(ownerType, ownerId, name, content)
                            .onSuccess { append(it); refresh(); message.value = getString(R.string.saved) }
                            .onFailure { message.value = getString(R.string.save_failed_detail, it.message.orEmpty()) }
                    } },
                    onDelete = { path -> lifecycleScope.launch {
                        val json = currentPathsJson()
                        repository.remove(ownerType, ownerId, json, path)
                            .onSuccess { replacePaths(it); refresh(); message.value = getString(R.string.deleted) }
                            .onFailure { message.value = getString(R.string.delete_failed_detail, it.message.orEmpty()) }
                    } }
                )
            }
        }
    }

    private suspend fun refresh() {
        val json = when (ownerType) {
            "project" -> projectDao.getById(ownerId)?.referencePathsJson
            "agent_group" -> agentDao.getGroupById(ownerId)?.referencePathsJson
            else -> agentDao.getById(ownerId)?.referencePathsJson
        }.orEmpty()
        paths.value = repository.parsePaths(json)
    }

    private suspend fun currentPathsJson(): String = when (ownerType) {
        "project" -> projectDao.getById(ownerId)?.referencePathsJson
        "agent_group" -> agentDao.getGroupById(ownerId)?.referencePathsJson
        else -> agentDao.getById(ownerId)?.referencePathsJson
    }.orEmpty()

    private suspend fun append(path: String) {
        when (ownerType) {
            "project" -> projectDao.getById(ownerId)?.let { projectDao.updateProject(it.copy(referencePathsJson = repository.appendPath(it.referencePathsJson, path))) }
            "agent_group" -> agentDao.getGroupById(ownerId)?.let { agentDao.updateGroup(it.copy(referencePathsJson = repository.appendPath(it.referencePathsJson, path), updatedAt = System.currentTimeMillis())) }
            else -> agentDao.getById(ownerId)?.let { agentDao.update(it.copy(referencePathsJson = repository.appendPath(it.referencePathsJson, path), updatedAt = System.currentTimeMillis())) }
        }
    }

    private suspend fun replacePaths(pathsJson: String) {
        when (ownerType) {
            "project" -> projectDao.getById(ownerId)?.let { projectDao.updateProject(it.copy(referencePathsJson = pathsJson)) }
            "agent_group" -> agentDao.getGroupById(ownerId)?.let { agentDao.updateGroup(it.copy(referencePathsJson = pathsJson, updatedAt = System.currentTimeMillis())) }
            else -> agentDao.getById(ownerId)?.let { agentDao.update(it.copy(referencePathsJson = pathsJson, updatedAt = System.currentTimeMillis())) }
        }
    }

    companion object {
        private const val EXTRA_OWNER_TYPE = "owner_type"
        private const val EXTRA_OWNER_ID = "owner_id"
        fun start(activity: android.app.Activity, ownerType: String, ownerId: Long) {
            activity.startActivity(Intent(activity, LocalReferenceListActivity::class.java).putExtra(EXTRA_OWNER_TYPE, ownerType).putExtra(EXTRA_OWNER_ID, ownerId))
        }
    }
}
