package com.amaya.intelligence.ui.screens.cronjob.shared

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.CronRecurringType
import com.amaya.intelligence.data.local.db.entity.CronSessionMode
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.components.shared.rememberLockedModalBottomSheetState
import com.amaya.intelligence.ui.components.shared.ignoreNestedScrollForBottomSheet
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import com.amaya.intelligence.ui.res.UiStrings
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronJobEditSheet(
    onDismiss: () -> Unit,
    onAdd: (CronJobEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxSheetHeight = (0.75f * LocalConfiguration.current.screenHeightDp).dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberLockedModalBottomSheetState()
    val scrollState = rememberScrollState()

    val dismissAction = {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
            }
        }
        Unit
    }

    BackHandler {
        dismissAction()
    }

    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var recurringType by remember { mutableStateOf(CronRecurringType.ONCE) }
    var sessionMode by remember { mutableStateOf(CronSessionMode.CONTINUE) }
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }) }

    val fmtDisplay = remember { java.text.SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.getDefault()) }

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
        properties = com.amaya.intelligence.ui.components.shared.lockedModalBottomSheetProperties(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = com.amaya.intelligence.ui.components.shared.responsiveBottomSheetShape()
    ) {
        val gradients = LocalAmayaGradients.current
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .weight(1f, fill = false)
        ) {
            // Bottom Layer: Scrolling Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ignoreNestedScrollForBottomSheet()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(90.dp)) // Reserve space for the header
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(UiStrings.Labels.TITLE) },
                    placeholder = { Text(UiStrings.Placeholders.TITLE_EXAMPLE) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(UiStrings.CronJob.MESSAGE_REMINDER) },
                    placeholder = { Text(UiStrings.Placeholders.REMINDER_MESSAGE_EXAMPLE) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(18.dp)) }
                )

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

                Text("When reminder fires", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = sessionMode == CronSessionMode.CONTINUE,
                        onClick = { sessionMode = CronSessionMode.CONTINUE },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Default.Forum, null, modifier = Modifier.size(14.dp)) }
                    ) {
                        Text("Continue session")
                    }
                    SegmentedButton(
                        selected = sessionMode == CronSessionMode.NEW,
                        onClick = { sessionMode = CronSessionMode.NEW },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Default.AddComment, null, modifier = Modifier.size(14.dp)) }
                    ) {
                        Text("New session")
                    }
                }
                Text(
                    if (sessionMode == CronSessionMode.CONTINUE)
                        "AI reply will be added to the current chat session"
                    else
                        "AI reply will open a fresh conversation each time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onAdd(
                                    CronJobEntity(
                                        title = title.trim().ifBlank { "Reminder" },
                                        prompt = prompt.trim(),
                                        triggerTimeMillis = selectedCalendar.timeInMillis,
                                        recurringType = recurringType,
                                        isActive = true,
                                        sessionMode = sessionMode
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = selectedCalendar.timeInMillis > System.currentTimeMillis()
                ) {
                    Icon(Icons.Default.AlarmAdd, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Set Reminder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        // Top Layer: Blurred Header Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(gradients.modalTopScrim)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = com.amaya.intelligence.ui.components.shared.responsiveDragHandleAlpha()))
                )
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "New Reminder Plan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                .compositeOver(MaterialTheme.colorScheme.background)
                        )
                        .clickable(onClick = dismissAction),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    }
}
