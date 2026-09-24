package com.amaya.intelligence.ui.activities.amaya.local

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
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.ui.activities.recommendations.local.LocalRecommendationsActivity
import com.amaya.intelligence.ui.screens.amaya.AmayaHomeScreen
import com.amaya.intelligence.ui.screens.amaya.AmayaViewModel
import com.amaya.intelligence.ui.theme.AmayaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocalAmayaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AmayaTheme {
                val viewModel: AmayaViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(Unit) { viewModel.setWorkspace(intent.getStringExtra(EXTRA_WORKSPACE)) }
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(state.message) {
                    state.message?.takeIf { it.isNotBlank() }?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearMessage()
                    }
                }
                AmayaHomeScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = { finish() },
                    onMemory = { LocalMemoryActivity.start(this, state.workspacePath) },
                    onReview = { LocalReviewActivity.start(this, state.workspacePath) },
                    onSkills = { LocalSkillsActivity.start(this) },
                    onRecommendations = { LocalRecommendationsActivity.start(this) },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_WORKSPACE = "workspace_path"
        fun start(activity: Activity, workspacePath: String? = null) {
            activity.startActivity(Intent(activity, LocalAmayaActivity::class.java).putExtra(EXTRA_WORKSPACE, workspacePath))
        }
    }
}
