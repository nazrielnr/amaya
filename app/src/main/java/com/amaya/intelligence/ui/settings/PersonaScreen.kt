package com.amaya.intelligence.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.PersonaMode
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.data.repository.SimplePersona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onNavigateBack: () -> Unit,
    personaRepository: PersonaRepository
) {
    val scope = rememberCoroutineScope()
    // FIX 5.7: getMode() is now suspend — use produceState to collect asynchronously
    val modeState by produceState(initialValue = PersonaMode.SIMPLE) {
        value = personaRepository.getMode()
    }
    var mode by remember(modeState) { mutableStateOf(modeState) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Initialize empty files with default content on first launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            personaRepository.initializeIfEmpty()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Persona", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Mode toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == PersonaMode.SIMPLE,
                    onClick = {
                        mode = PersonaMode.SIMPLE
                        scope.launch { personaRepository.setMode(PersonaMode.SIMPLE) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Simple") }
                SegmentedButton(
                    selected = mode == PersonaMode.PRO,
                    onClick = {
                        mode = PersonaMode.PRO
                        scope.launch { personaRepository.setMode(PersonaMode.PRO) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Pro") }
            }

            if (mode == PersonaMode.SIMPLE) {
                SimplePersonaEditor(
                    personaRepository = personaRepository,
                    onSaved = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Persona saved successfully")
                        }
                    }
                )
            } else {
                ProPersonaEditor(
                    personaRepository = personaRepository,
                    onSaved = { filename ->
                        scope.launch {
                            snackbarHostState.showSnackbar("$filename saved successfully")
                        }
                    },
                    onReset = { filename ->
                        scope.launch {
                            snackbarHostState.showSnackbar("$filename reset to default")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SimplePersonaEditor(
    personaRepository: PersonaRepository,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // FIX 5.7: getSimplePersona() is now suspend — use produceState
    val personaState by produceState(initialValue = SimplePersona()) {
        value = personaRepository.getSimplePersona()
    }
    var persona by remember(personaState) { mutableStateOf(personaState) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PersonaTextField(
                label = "Style & Tone",
                value = persona.tone,
                onValueChange = { persona = persona.copy(tone = it) },
                placeholder = "e.g. Friendly, concise, professional",
                pills = listOf("Friendly", "Concise", "Professional", "Academic", "Humorous")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PersonaTextField(
                label = "Characteristic",
                value = persona.characteristic,
                onValueChange = { persona = persona.copy(characteristic = it) },
                placeholder = "e.g. Analytical, patient, thorough",
                pills = listOf("Analytical", "Patient", "Creative", "Thorough", "Direct", "Helpful")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PersonaTextField(
                label = "Custom Instruction",
                value = persona.customInstruction,
                onValueChange = { persona = persona.copy(customInstruction = it) },
                placeholder = "Any extra rules or preferences",
                maxLines = 4
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PersonaTextField(
                label = "Your Nickname",
                value = persona.nickname,
                onValueChange = { persona = persona.copy(nickname = it) },
                placeholder = "How should Amaya call you?",
                pills = listOf("Boss", "Friend", "User")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            PersonaTextField(
                label = "More About You",
                value = persona.aboutYou,
                onValueChange = { persona = persona.copy(aboutYou = it) },
                placeholder = "Context about you, preferences, timezone...",
                maxLines = 4
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        personaRepository.saveSimplePersona(persona)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Persona")
            }
        }
    }
}

@Composable
private fun ProPersonaEditor(
    personaRepository: PersonaRepository,
    onSaved: (String) -> Unit,
    onReset: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
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

    // Reset confirmation dialog state
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Pro Mode Workspace",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Edit .md files that define Amaya's personality and memory. These files are read at the start of every session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = { HorizontalDivider() }
                ) {
                    tabs.forEachIndexed { index, filename ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(filename, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        fileDescriptions[selectedFile] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        placeholder = { Text("Write your $selectedFile content here...") },
                        shape = MaterialTheme.shapes.medium,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                    Spacer(Modifier.height(16.dp))

                    // Action row: Reset + Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reset to Default button
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Default")
                        }

                        // Save button
                        Button(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        personaRepository.writeFile(selectedFile, content)
                                    }
                                    onSaved(selectedFile)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }

        // Daily logs (read-only view)
        val logs = remember { personaRepository.listDailyLogs() }
        if (logs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Daily Logs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            logs.take(5).forEach { logName ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(logName, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        val logContent = remember(logName) { personaRepository.readFile("memory/$logName") }
                        Text(
                            logContent.take(500),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
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
                            // Reload content from file
                            content = withContext(Dispatchers.IO) {
                                personaRepository.readFile(selectedFile)
                            }
                            onReset(selectedFile)
                        }
                    }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    pills: List<String> = emptyList(),
    maxLines: Int = 1
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = maxLines == 1,
            maxLines = maxLines,
            shape = MaterialTheme.shapes.medium
        )
        if (pills.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                pills.forEach { pill ->
                    AssistChip(
                        onClick = {
                            val activeText = value.trim()
                            if (!activeText.contains(pill)) {
                                val newText = if (activeText.isEmpty()) pill else "$activeText, $pill"
                                onValueChange(newText)
                            }
                        },
                        label = { Text(pill, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}
