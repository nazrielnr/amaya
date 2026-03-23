package com.amaya.intelligence.ui.screens.persona.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.ui.theme.SectionShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProPersonaEditor(
    personaRepository: PersonaRepository,
    onSaved: (String) -> Unit,
    onReset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val fileDescriptions = mapOf(
        "AGENTS.md"   to "Operating instructions, behavior rules, memory usage, safety",
        "SOUL.md"     to "Personality, language style, principles, values, boundaries",
        "IDENTITY.md" to "Name, role, capability description",
        "USER.md"     to "Your preferences, timezone, communication style",
        "MEMORY.md"   to "Long-term memory: important things to remember"
    )

    val tabs = PersonaRepository.PRO_FILES
    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedFile = tabs[selectedTabIndex]
    var content by remember(selectedFile) { mutableStateOf(personaRepository.readFile(selectedFile)) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                "Pro Mode Workspace",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Edit .md files that define Amaya's personality and memory. These files are read at the start of every session.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraLight),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = SectionShape,
            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 20.dp,
                    containerColor = Color.Transparent,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) },
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, filename ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    filename, 
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedTabIndex == index) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                ) 
                            }
                        )
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        fileDescriptions[selectedFile] ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraLight),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 500.dp),
                        placeholder = { Text("Write your $selectedFile content here...") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = if (isDark) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedContainerColor = if (isDark) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Default", style = MaterialTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        personaRepository.writeFile(selectedFile, content)
                                    }
                                    onSaved(selectedFile)
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        val logs = remember { personaRepository.listDailyLogs() }
        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Daily Logs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            logs.take(5).forEach { logName ->
                Surface(
                    shape = SectionShape,
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(logName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        val logContent = remember(logName) { personaRepository.readFile("memory/$logName") }
                        Text(
                            logContent.take(300) + if (logContent.length > 300) "..." else "",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraLight),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = {
                Icon(Icons.Default.RestartAlt, null, tint = MaterialTheme.colorScheme.primary)
            },
            title = { Text("Reset to Default?") },
            text = {
                Text(
                    "\"$selectedFile\" will be reset to its default content. Any changes you made will be lost."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                personaRepository.resetFile(selectedFile)
                            }
                            content = withContext(Dispatchers.IO) {
                                personaRepository.readFile(selectedFile)
                            }
                            onReset(selectedFile)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
