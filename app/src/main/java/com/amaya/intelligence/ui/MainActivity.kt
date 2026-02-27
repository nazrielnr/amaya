package com.amaya.intelligence.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.amaya.intelligence.data.remote.api.AiSettings
import com.amaya.intelligence.data.remote.api.AiSettingsManager
import com.amaya.intelligence.ui.chat.ChatScreen
import com.amaya.intelligence.ui.chat.ChatViewModel
import com.amaya.intelligence.ui.settings.SettingsActivity
import com.amaya.intelligence.ui.theme.AmayaTheme
import com.amaya.intelligence.ui.workspace.WorkspaceActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main entry point for the Amaya app.
 *
 * Hosts the root NavHost and provides global state (AppViewModel) that
 * survives conversation switches — e.g. active reminder count badge.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var aiSettingsManager: AiSettingsManager

    /** Scoped to Activity process — survives all conversation switches. */
    private val appViewModel: AppViewModel by viewModels()

    private var hasStoragePermission by mutableStateOf(false)
    private var chatViewModel: ChatViewModel? = null

    /** Runtime POST_NOTIFICATIONS launcher (Android 13 / API 33+). */
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkStoragePermission()
        requestNotificationPermissionIfNeeded()

        setContent {
            val settings by aiSettingsManager.settingsFlow.collectAsState(initial = AiSettings())
            val isDark = when (settings.theme) {
                "light" -> false
                "dark"  -> true
                else    -> isSystemInDarkTheme()
            }

            AmayaTheme(darkTheme = isDark, accentColor = settings.accentColor) {
                AppContent(
                    hasStoragePermission = hasStoragePermission,
                    appViewModel = appViewModel,
                    initialIntent = intent,
                    onStoragePermissionRequest = { requestStoragePermission() },
                    onChatViewModelReady = { vm -> chatViewModel = vm },
                    onNavigateToSettings = { workspacePath ->
                        // Activity context available here — no cast needed
                        SettingsActivity.start(this@MainActivity, workspacePath)
                    },
                    onNavigateToWorkspace = {
                        @Suppress("DEPRECATION")
                        WorkspaceActivity.startForResult(this@MainActivity)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getLongExtra("open_conversation_id", -1L)
        if (id > 0) chatViewModel?.loadConversation(id)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WorkspaceActivity.REQUEST_CODE && resultCode == RESULT_OK) {
            data?.getStringExtra(WorkspaceActivity.RESULT_KEY)?.let { path ->
                chatViewModel?.setWorkspace(path)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun checkStoragePermission() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// ── Root composable ──────────────────────────────────────────────────────────

@Composable
private fun AppContent(
    hasStoragePermission: Boolean,
    appViewModel: AppViewModel,
    initialIntent: Intent?,
    onStoragePermissionRequest: () -> Unit,
    onChatViewModelReady: (ChatViewModel) -> Unit,
    onNavigateToSettings: (workspacePath: String?) -> Unit,
    onNavigateToWorkspace: () -> Unit
) {
    val navController = rememberNavController()
    val viewModel: ChatViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val activeReminderCount by appViewModel.activeReminderCount.collectAsState()

    // Expose ViewModel reference to Activity
    LaunchedEffect(viewModel) { onChatViewModelReady(viewModel) }

    // Handle notification deep link
    LaunchedEffect(initialIntent) {
        val id = initialIntent?.getLongExtra("open_conversation_id", -1L) ?: -1L
        if (id > 0) viewModel.loadConversation(id)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = if (hasStoragePermission) "chat" else "permission"
        ) {
            composable("permission") {
                PermissionRequestScreen(onRequestPermission = onStoragePermissionRequest)
            }
            composable("chat") {
                ChatScreen(
                    viewModel = viewModel,
                    activeReminderCount = activeReminderCount,
                    onNavigateToSettings = { onNavigateToSettings(uiState.workspacePath) },
                    onNavigateToWorkspace = onNavigateToWorkspace
                )
            }
        }

        LaunchedEffect(hasStoragePermission) {
            if (hasStoragePermission && navController.currentDestination?.route == "permission") {
                navController.navigate("chat") { popUpTo("permission") { inclusive = true } }
            }
        }
    }
}

// ── Permission screen ────────────────────────────────────────────────────────

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "File Access Needed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Amaya is your personal AI workspace. To help you manage tasks and files seamlessly, we need access to your local files.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text("Enable All Files Access", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "This permission is strictly required on Android 11+ to manage projects locally on your device.",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
