package com.amaya.intelligence.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.domain.terminal.TerminalLine
import com.amaya.intelligence.domain.terminal.TerminalLineType
import com.amaya.intelligence.domain.terminal.TerminalMode
import com.amaya.intelligence.domain.terminal.TerminalSessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionManager: TerminalSessionManager,
    sandboxManager: LinuxSandboxManager,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val lines by sessionManager.lines.collectAsState()
    val isRunning by sessionManager.isRunning.collectAsState()
    val terminalMode by sessionManager.terminalMode.collectAsState()
    val prompt by sessionManager.currentPrompt.collectAsState()
    val isSandboxReady = remember(sandboxManager) { sandboxManager.isReady() }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new output
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (terminalMode == TerminalMode.PYTHON_REPL) "Python 3 REPL" else "Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (terminalMode == TerminalMode.PYTHON_REPL) {
                                Color(0xFF3572A5)
                            } else if (isSandboxReady) {
                                Color(0xFF0D74CE)
                            } else {
                                Color(0xFF6E7681)
                            }
                        ) {
                            Text(
                                text = if (terminalMode == TerminalMode.PYTHON_REPL) "Python" else if (isSandboxReady) "Alpine PRoot" else "Host Toybox",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isRunning) {
                        IconButton(onClick = { sessionManager.interrupt() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Interrupt", tint = Color(0xFFF85149))
                        }
                    }
                    IconButton(onClick = { sessionManager.clearScreen() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Screen")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Terminal Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .imePadding()
            ) {
                // Accessory Keys Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccessoryKey(text = "Ctrl+C", isAction = true) {
                        sessionManager.interrupt()
                    }
                    AccessoryKey(text = "▲") {
                        sessionManager.getPreviousHistory()?.let { inputText = it }
                    }
                    AccessoryKey(text = "▼") {
                        sessionManager.getNextHistory()?.let { inputText = it }
                    }
                    AccessoryKey(text = "Tab") {
                        inputText += "    "
                    }
                    if (terminalMode == TerminalMode.SHELL) {
                        AccessoryKey(text = "python3") {
                            inputText = "python3"
                            sessionManager.executeInput("python3")
                            inputText = ""
                        }
                        AccessoryKey(text = "gh") {
                            inputText = "gh "
                        }
                        AccessoryKey(text = "ls -la") {
                            sessionManager.executeInput("ls -la")
                        }
                        AccessoryKey(text = "pwd") {
                            sessionManager.executeInput("pwd")
                        }
                        AccessoryKey(text = "apk") {
                            inputText = "apk add "
                        }
                    } else {
                        AccessoryKey(text = "exit()", isAction = true) {
                            sessionManager.executeInput("exit()")
                            inputText = ""
                        }
                        AccessoryKey(text = "print()") {
                            inputText = "print()"
                        }
                        AccessoryKey(text = "help()") {
                            inputText = "help()"
                        }
                    }
                }

                // Command Input Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 8.dp, bottom = 8.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prompt,
                        fontFamily = FontFamily.Monospace,
                        color = if (terminalMode == TerminalMode.PYTHON_REPL) Color(0xFF79C0FF) else Color(0xFF7EE787),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(Modifier.width(6.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        placeholder = {
                            Text(
                                if (terminalMode == TerminalMode.PYTHON_REPL) "Ekspresi Python..." else "Perintah terminal...",
                                color = Color(0xFF484F58),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank() || terminalMode == TerminalMode.PYTHON_REPL) {
                                sessionManager.executeInput(inputText)
                                inputText = ""
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0D1117),
                            unfocusedContainerColor = Color(0xFF0D1117),
                            focusedBorderColor = Color(0xFF388BFD),
                            unfocusedBorderColor = Color(0xFF30363D)
                        )
                    )

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || terminalMode == TerminalMode.PYTHON_REPL) {
                                sessionManager.executeInput(inputText)
                                inputText = ""
                            }
                        },
                        enabled = !isRunning || terminalMode == TerminalMode.PYTHON_REPL
                    ) {
                        if (isRunning && terminalMode != TerminalMode.PYTHON_REPL) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF58A6FF)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color(0xFF58A6FF)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(lines) { line ->
                    TerminalLineRow(line)
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    val (color, fontWeight) = when (line.type) {
        TerminalLineType.PROMPT -> Pair(Color(0xFF7EE787), FontWeight.Bold)
        TerminalLineType.INPUT -> Pair(Color(0xFF58A6FF), FontWeight.SemiBold)
        TerminalLineType.OUTPUT -> Pair(Color(0xFFC9D1D9), FontWeight.Normal)
        TerminalLineType.ERROR -> Pair(Color(0xFFFFA198), FontWeight.Normal)
        TerminalLineType.SYSTEM_INFO -> Pair(Color(0xFF8B949E), FontWeight.Medium)
    }

    Text(
        text = line.text,
        color = color,
        fontWeight = fontWeight,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp
    )
}

@Composable
private fun AccessoryKey(
    text: String,
    isAction: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isAction) Color(0xFF30363D) else Color(0xFF21262D)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = if (isAction) Color(0xFFFFA198) else Color(0xFFC9D1D9),
            fontWeight = FontWeight.Bold
        )
    }
}
