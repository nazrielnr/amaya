package com.amaya.intelligence.ui.screens.cronjob.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import android.app.DatePickerDialog
import android.app.TimePickerDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.local.entity.CronJobEntity
import com.amaya.intelligence.data.local.entity.CronRecurringType
import com.amaya.intelligence.data.local.entity.CronSessionMode


import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronJobEditSheet(
    ownerAgentId: Long? = null,
    onDismiss: () -> Unit,
    onAdd: (CronJobEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current


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

    com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "New Reminder Plan"
    ) {
        com.amaya.intelligence.ui.screens.amaya.AmayaSection("Reminder Details") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    placeholder = { Text(stringResource(R.string.e_g_buy_groceries)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.message_reminder)) },
                    placeholder = { Text(stringResource(R.string.what_should_amaya_remind_you_of)) },
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
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
        if (ownerAgentId != null) {
            Text(
                "AI reply continues this agent's persistent conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
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
        }

        Button(
            onClick = {
                val job = CronJobEntity(
                    title = title.trim().ifBlank { "Reminder" },
                    prompt = prompt.trim(),
                    triggerTimeMillis = selectedCalendar.timeInMillis,
                    recurringType = recurringType,
                    isActive = true,
                    sessionMode = sessionMode,
                    agentId = ownerAgentId
                )
                dismiss { onAdd(job) }
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
}
