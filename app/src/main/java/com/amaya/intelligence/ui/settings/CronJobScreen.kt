package com.amaya.intelligence.ui.settings

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.CronRecurringType
import com.amaya.intelligence.data.repository.CronJobRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronJobScreen(
    onNavigateBack: () -> Unit,
    cronJobRepository: CronJobRepository
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val jobs by cronJobRepository.allJobs.collectAsState(initial = emptyList())

    // Check alarm permission on screen entry (Android 12+)
    var showAlarmPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!cronJobRepository.canScheduleExact()) {
            showAlarmPermissionDialog = true
        }
    }

    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reminders", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, "Add Reminder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (jobs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No reminders yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                "Tap + to schedule a reminder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }

            items(jobs, key = { it.id }) { job ->
                CronJobCard(
                    job = job,
                    onToggle = { active ->
                        scope.launch { cronJobRepository.setActive(job.id, active) }
                    },
                    onDelete = {
                        scope.launch {
                            cronJobRepository.deleteJob(job.id)
                            snackbarHostState.showSnackbar("Reminder deleted")
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // Alarm permission onboarding dialog
    if (showAlarmPermissionDialog) {
        AlarmPermissionDialog(onDismiss = { showAlarmPermissionDialog = false })
    }

    if (showAddSheet) {
        AddCronJobSheet(
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

@Composable
private fun CronJobCard(
    job: CronJobEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.getDefault()) }
    val timeStr = fmt.format(Date(job.triggerTimeMillis))
    val isPast = job.triggerTimeMillis < System.currentTimeMillis() && job.recurringType == CronRecurringType.ONCE

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (job.isActive && !isPast)
            MaterialTheme.colorScheme.surface
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (job.isActive) 1.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when (job.recurringType) {
                    CronRecurringType.ONCE -> Icons.Default.Alarm
                    CronRecurringType.DAILY -> Icons.Default.Repeat
                    CronRecurringType.WEEKLY -> Icons.Default.DateRange
                },
                contentDescription = null,
                tint = if (job.isActive && !isPast)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    job.title.ifBlank { "Reminder" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (job.isActive && !isPast)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (job.prompt.isNotBlank()) {
                    Text(
                        job.prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 2
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            job.recurringType.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (isPast) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Expired",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Switch(
                checked = job.isActive && !isPast,
                onCheckedChange = { if (!isPast) onToggle(it) },
                enabled = !isPast
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCronJobSheet(
    onDismiss: () -> Unit,
    onAdd: (CronJobEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )
    // Predictive back: animate sheet down, then dismiss
    BackHandler {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var recurringType by remember { mutableStateOf(CronRecurringType.ONCE) }
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }) }

    val fmtDisplay = remember { SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.getDefault()) }

    fun pickDateTime() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val cal = Calendar.getInstance().apply { set(y, m, d) }
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0)
                        selectedCalendar = cal
                    },
                    selectedCalendar.get(Calendar.HOUR_OF_DAY),
                    selectedCalendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
        ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("New Reminder", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("e.g. Buy groceries") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Message / Reminder") },
                placeholder = { Text("What should Amaya remind you of?") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp)) }
            )

            // Date & Time picker
            OutlinedCard(
                onClick = { pickDateTime() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        fmtDisplay.format(selectedCalendar.time),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Recurrence type
            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CronRecurringType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = recurringType == type,
                        onClick = { recurringType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, CronRecurringType.entries.size)
                    ) {
                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Button(
                onClick = {
                    onAdd(
                        CronJobEntity(
                            title = title.trim().ifBlank { "Reminder" },
                            prompt = prompt.trim(),
                            triggerTimeMillis = selectedCalendar.timeInMillis,
                            recurringType = recurringType,
                            isActive = true
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCalendar.timeInMillis > System.currentTimeMillis()
            ) {
                Icon(Icons.Default.AlarmAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Set Reminder")
            }
        }
        } // Surface
    }
}
