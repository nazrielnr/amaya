package com.amaya.intelligence.ui.activities.agent.local

import com.amaya.intelligence.R

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.amaya.intelligence.data.repository.AgentMemoryRepository
import com.amaya.intelligence.ui.screens.agent.local.LocalAgentMemoryScreen
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocalAgentMemoryActivity : AppCompatActivity() {
    @Inject lateinit var repository: AgentMemoryRepository
    private val agentId by lazy { intent.getLongExtra(EXTRA_AGENT_ID, -1L) }
    private val records = MutableStateFlow(emptyList<com.amaya.intelligence.data.repository.AgentMemoryRecord>())
    private val message = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refresh()
        setContent {
            AmayaTheme {
                val items by records.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(message.value) { message.value?.let { snackbarHostState.showSnackbar(it); message.value = null } }
                LocalAgentMemoryScreen(
                    records = items,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = ::finish,
                    onAdd = { content -> lifecycleScope.launch {
                        repository.save(agentId, null, content).onFailure { message.value = getString(R.string.save_failed_detail, it.message.orEmpty()) }
                        refresh()
                    } },
                    onDelete = { record -> lifecycleScope.launch {
                        repository.delete(agentId, record.id, record.version).onSuccess { message.value = getString(R.string.deleted) }.onFailure { message.value = getString(R.string.delete_failed_detail, it.message.orEmpty()) }
                        refresh()
                    } }
                )
            }
        }
    }

    private fun refresh() { lifecycleScope.launch { records.value = repository.list(agentId, limit = 100) } }

    companion object {
        private const val EXTRA_AGENT_ID = "agent_id"
        fun start(activity: Activity, agentId: Long) {
            activity.startActivity(Intent(activity, LocalAgentMemoryActivity::class.java).putExtra(EXTRA_AGENT_ID, agentId))
        }
    }
}
