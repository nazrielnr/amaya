package com.amaya.intelligence.ui.activities.settings.local

import com.amaya.intelligence.R

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.dao.ProjectDao
import com.amaya.intelligence.data.repository.AppUpdateInstaller
import com.amaya.intelligence.data.repository.UpdateRepository
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.data.local.entity.ProjectEntity
import com.amaya.intelligence.data.local.entity.AgentGroupEntity
import com.amaya.intelligence.domain.models.AssistantMode
import com.amaya.intelligence.domain.models.UpdateInfo
import com.amaya.intelligence.ui.activities.agent.local.LocalAgentDetailActivity
import com.amaya.intelligence.ui.activities.amaya.local.LocalMemoryAreaActivity
import com.amaya.intelligence.ui.activities.amaya.local.LocalSkillsActivity
import com.amaya.intelligence.ui.activities.mcp.local.LocalMcpActivity
import com.amaya.intelligence.ui.activities.models.ManageModelsActivity
import com.amaya.intelligence.ui.activities.project.local.LocalProjectActivity
import com.amaya.intelligence.ui.activities.project.local.LocalProjectDetailActivity
import com.amaya.intelligence.ui.screens.amaya.MemoryArea
import com.amaya.intelligence.ui.screens.settings.local.LocalSettingsScreen
import com.amaya.intelligence.ui.screens.settings.local.SettingsScope
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocalSettingsActivity : AppCompatActivity() {
    @Inject lateinit var aiSettingsManager: AiSettingsManager
    @Inject lateinit var projectDao: ProjectDao
    @Inject lateinit var agentDao: AgentDao
    @Inject lateinit var updateRepository: UpdateRepository
    @Inject lateinit var appUpdateInstaller: AppUpdateInstaller
    private var updateStatus by mutableStateOf<String?>(null)
    private var updateInfo by mutableStateOf<UpdateInfo?>(null)
    private var updateUrl by mutableStateOf<String?>(null)
    private var isDownloadingUpdate by mutableStateOf(false)
    private var pendingWorkspaceTarget by mutableStateOf<SettingsScope?>(null)
    private var projectWorkspace by mutableStateOf<String?>(null)
    private var agentWorkspace by mutableStateOf<String?>(null)
    private val workspacePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val path = result.data?.getStringExtra(LocalProjectActivity.RESULT_KEY) ?: return@registerForActivityResult
        if (pendingWorkspaceTarget == SettingsScope.PROJECT) projectWorkspace = path else agentWorkspace = path
    }

    private val mode by lazy {
        runCatching { AssistantMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty()) }.getOrDefault(AssistantMode.CHAT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                val projects by projectDao.observeAll().collectAsState(initial = emptyList())
                val groups by agentDao.observeGroups().collectAsState(initial = emptyList())
                val agents by agentDao.observeAll().collectAsState(initial = emptyList())
                LocalSettingsScreen(
                    onNavigateBack = { finish() },
                    initialScope = when (mode) {
                        AssistantMode.PROJECT -> SettingsScope.PROJECT
                        AssistantMode.AGENT -> SettingsScope.AGENT
                        AssistantMode.CHAT -> SettingsScope.GLOBAL
                    },
                    projects = projects,
                    agentGroups = groups,
                    agents = agents,
                    aiSettingsManager = aiSettingsManager,
                    onNavigateToModels = { ManageModelsActivity.start(this) },
                    onNavigateToMcp = { LocalMcpActivity.start(this) },
                    onNavigateToTerminal = { LocalTerminalSettingsActivity.start(this) },
                    onNavigateToAboutYou = { LocalMemoryAreaActivity.start(this, MemoryArea.USER) },
                    onNavigateToSkills = { LocalSkillsActivity.start(this) },
                    updateStatus = updateStatus,
                    isDownloadingUpdate = isDownloadingUpdate,
                    updateInfo = updateInfo,
                    onCheckForUpdate = ::checkForUpdate,
                    onInstallUpdate = ::installUpdate,
                    onOpenProject = { LocalProjectDetailActivity.startForResult(this, it.id) },
                    onCreateProject = { name, instructions, workspace ->
                        lifecycleScope.launch {
                            val existing = projectDao.getByRootPath(workspace)
                            if (existing == null) projectDao.insert(ProjectEntity(name = name, instructions = instructions, rootPath = workspace))
                        }
                        projectWorkspace = null
                    },
                    onSelectProjectWorkspace = {
                        pendingWorkspaceTarget = SettingsScope.PROJECT
                        workspacePicker.launch(LocalProjectActivity.workspacePickerIntent(this))
                    },
                    selectedProjectWorkspace = projectWorkspace,
                    onOpenAgentGroup = { LocalAgentDetailActivity.startForResult(this, it.id) },
                    onCreateAgentGroup = { name, instructions, workspace ->
                        lifecycleScope.launch { agentDao.insertGroup(AgentGroupEntity(name = name, instructions = instructions, workspacePath = workspace)) }
                        agentWorkspace = null
                    },
                    onSelectAgentWorkspace = {
                        pendingWorkspaceTarget = SettingsScope.AGENT
                        workspacePicker.launch(LocalProjectActivity.workspacePickerIntent(this))
                    },
                    selectedAgentWorkspace = agentWorkspace
                )
            }
        }
    }

    private fun checkForUpdate() {
        updateStatus = getString(R.string.checking_for_updates)
        updateInfo = null
        updateUrl = null
        lifecycleScope.launch {
            val update = updateRepository.getLatestUpdate()
            updateInfo = update
            updateUrl = update?.takeIf { it.isNewer }?.downloadUrl
            updateStatus = when {
                update == null -> getString(R.string.could_not_check_for_updates)
                update.isNewer -> getString(R.string.version_available, update.versionName)
                else -> getString(R.string.app_is_up_to_date)
            }
        }
    }

    private fun installUpdate() {
        if (isDownloadingUpdate) return
        val url = updateUrl ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        isDownloadingUpdate = true
        updateStatus = getString(R.string.downloading_update)
        lifecycleScope.launch {
            appUpdateInstaller.download(url) { downloaded, total ->
                runOnUiThread {
                    updateStatus = if (total != null) {
                        getString(R.string.downloading_update_percent, downloaded * 100 / total)
                    } else {
                        getString(R.string.downloading_update_mb, downloaded / 1024 / 1024)
                    }
                }
            }.onSuccess {
                updateStatus = getString(R.string.downloaded_opening_android_installer)
                appUpdateInstaller.install(it)
            }.onFailure {
                updateStatus = it.message ?: getString(R.string.could_not_download_update)
            }
            isDownloadingUpdate = false
        }
    }

    companion object {
        private const val EXTRA_MODE = "assistant_mode"
        private const val EXTRA_OWNER_ID = "owner_id"
        private const val EXTRA_WORKSPACE = "current_workspace"
        const val EXTRA_DELETED_OWNER = "deleted_owner"
        const val REQUEST_CODE = 1002

        fun start(
            activity: Activity,
            mode: AssistantMode = AssistantMode.CHAT,
            ownerId: String? = null,
            workspacePath: String? = null
        ) {
            activity.startActivityForResult(
                Intent(activity, LocalSettingsActivity::class.java)
                    .putExtra(EXTRA_MODE, mode.name)
                    .putExtra(EXTRA_OWNER_ID, ownerId)
                    .putExtra(EXTRA_WORKSPACE, workspacePath),
                REQUEST_CODE
            )
        }
    }
}
