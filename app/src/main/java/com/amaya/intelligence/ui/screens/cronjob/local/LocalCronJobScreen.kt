package com.amaya.intelligence.ui.screens.cronjob.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.CronJobRepository
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.cronjob.shared.CronJobEditSheet
import com.amaya.intelligence.ui.screens.cronjob.shared.CronJobList
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalCronJobScreen(
    onNavigateBack: () -> Unit,
    cronJobRepository: CronJobRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val jobs by cronJobRepository.allJobs.collectAsState(initial = emptyList())
    val gradients = LocalAmayaGradients.current

    var showAlarmPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!cronJobRepository.canScheduleExact()) {
            showAlarmPermissionDialog = true
        }
    }
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CronJobList(
                jobs = jobs,
                iconPalettes = gradients.iconPalettes,
                onToggle = { job, active ->
                    scope.launch { cronJobRepository.setActive(job.id, active) }
                },
                onDelete = { job ->
                    scope.launch {
                        cronJobRepository.deleteJob(job.id)
                        snackbarHostState.showSnackbar("Reminder deleted")
                    }
                },
                topPadding = topPadding
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.TopCenter)
                    .background(gradients.topScrim)
            )

            TopAppBar(
                title = { 
                    Text(
                        "Reminders", 
                        style = MaterialTheme.typography.titleLarge, 
                        modifier = Modifier.padding(start = 12.dp)
                    ) 
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onNavigateBack)
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            .clickable { showAddSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AddAlarm, 
                            "Add Reminder",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp),
                windowInsets = WindowInsets(0.dp)
            )
        }
    }

    if (showAlarmPermissionDialog) {
        com.amaya.intelligence.ui.components.shared.PermissionRequirementSheet(
            permissionType = com.amaya.intelligence.ui.components.shared.PermissionType.EXACT_ALARM,
            onGrant = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            },
            onDismiss = { showAlarmPermissionDialog = false }
        )
    }

    if (showAddSheet) {
        CronJobEditSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { job ->
                showAddSheet = false
                scope.launch {
                    cronJobRepository.addJob(job)
                    snackbarHostState.showSnackbar("Reminder set ✓")
                }
            }
        )
    }
}
