package com.amaya.intelligence.ui.activities.agent.local

import com.amaya.intelligence.R

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.amaya.intelligence.data.local.dao.AgentDao
import com.amaya.intelligence.data.local.entity.AgentGroupEntity
import com.amaya.intelligence.ui.activities.project.local.LocalProjectActivity
import com.amaya.intelligence.ui.screens.agent.local.LocalAgentListScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocalAgentListActivity : AppCompatActivity() {
    @Inject lateinit var agentDao: AgentDao
    private var selectedWorkspace by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                val groups by agentDao.observeGroups().collectAsState(initial = emptyList())
                val agents by agentDao.observeAll().collectAsState(initial = emptyList())
                val snackbar = remember { SnackbarHostState() }
                LocalAgentListScreen(
                    groups = groups,
                    agents = agents,
                    snackbarHostState = snackbar,
                    selectedWorkspace = selectedWorkspace,
                    onNavigateBack = { finish() },
                    onOpenGroup = { group -> LocalAgentDetailActivity.startForResult(this, group.id) },
                    onSelectWorkspace = { LocalProjectActivity.startWorkspacePickerForResult(this) },
                    onCreateGroup = { name, instructions, workspacePath ->
                        lifecycleScope.launch {
                            runCatching {
                                val id = agentDao.insertGroup(
                                    AgentGroupEntity(name = name, instructions = instructions, workspacePath = workspacePath)
                                )
                                LocalAgentDetailActivity.startForResult(this@LocalAgentListActivity, id)
                            }.onFailure { snackbar.showSnackbar(getString(R.string.agent_group_name_already_exists)) }
                        }
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LocalProjectActivity.REQUEST_CODE && resultCode == RESULT_OK) {
            selectedWorkspace = data?.getStringExtra(LocalProjectActivity.RESULT_KEY)
        }
    }

    companion object {
        const val REQUEST_CODE = 1011
        const val RESULT_GROUP_ID = "agent_group_id"
        const val RESULT_GROUP_NAME = "agent_group_name"
        const val RESULT_WORKSPACE_PATH = "workspace_path"
        const val RESULT_AGENT_ID = "agent_id"

        fun startForResult(activity: Activity) {
            activity.startActivityForResult(Intent(activity, LocalAgentListActivity::class.java), REQUEST_CODE)
        }
    }
}
