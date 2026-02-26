package com.opencode.mobile.ui.settings

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
import com.opencode.mobile.data.repository.PersonaMode
import com.opencode.mobile.data.repository.PersonaRepository
import com.opencode.mobile.data.repository.SimplePersona
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
    var mode by remember { mutableStateOf(personaRepository.getMode()) }
    val snackbarHostState = remember { SnackbarHostState() }

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
    var persona by remember { mutableStateOf(personaRepository.getSimplePersona()) }

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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            PersonaTextField(
                label = "Characteristic",
                value = persona.characteristic,
                onValueChange = { persona = persona.copy(characteristic = it) },
                placeholder = "e.g. Analytical, patient, thorough",
                pills = listOf("Analytical", "Patient", "Creative", "Thorough", "Direct", "Helpful")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            PersonaTextField(
                label = "Custom Instruction",
                value = persona.customInstruction,
                onValueChange = { persona = persona.copy(customInstruction = it) },
                placeholder = "Any extra rules or preferences",
                maxLines = 4
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            PersonaTextField(
                label = "Your Nickname",
                value = persona.nickname,
                onValueChange = { persona = persona.copy(nickname = it) },
                placeholder = "How should Amaya call you?",
                pills = listOf("Boss", "Friend", "User")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

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
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val fileDescriptions = mapOf(
        "AGENTS.md" to "Operating instructions, behavior rules, memory usage, safety",
        "SOUL.md" to "Personality, language style, principles, values, boundaries",
        "IDENTITY.md" to "Name, role, capability description",
        "USER.md" to "Your preferences, timezone, communication style",
        "MEMORY.md" to "Long-term memory: important things to remember"
    )

    val tabs = PersonaRepository.PRO_FILES
    var selectedTabIndex by remember { mutableStateOf(0) }
    val selectedFile = tabs[selectedTabIndex]
    var content by remember(selectedFile) { mutableStateOf(personaRepository.readFile(selectedFile)) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Pro Mode Workspace",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Edit .md files that define your AI's personality and memory. Amaya reads these files at the start of every session.",
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
                    Button(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    personaRepository.writeFile(selectedFile, content)
                                }
                                onSaved(selectedFile)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save $selectedFile")
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
