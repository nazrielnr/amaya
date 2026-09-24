package com.amaya.intelligence.domain.terminal

import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager.Companion.HOST_LEAK_ENV_VARS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT
)

enum class TerminalLineType {
    PROMPT,
    INPUT,
    OUTPUT,
    ERROR,
    SYSTEM_INFO
}

enum class TerminalMode {
    SHELL,
    PYTHON_REPL
}

@Singleton
class TerminalSessionManager @Inject constructor(
    private val sandboxManager: LinuxSandboxManager,
    private val terminalSettingsRepository: TerminalSettingsRepository
) {
    companion object {
        private const val MAX_BUFFER_LINES = 1500
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _terminalMode = MutableStateFlow(TerminalMode.SHELL)
    val terminalMode: StateFlow<TerminalMode> = _terminalMode.asStateFlow()

    private val _currentPrompt = MutableStateFlow("~ # ")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    private var activeProcess: Process? = null
    private var activeWriter: BufferedWriter? = null
    private var readerJob: Job? = null
    private var activeWorkspaceDir: String? = null

    init {
        appendSystemInfo("Amaya Built-in Terminal Ready.")
        updatePrompt()
    }

    fun setWorkspace(workspaceDir: String?) {
        activeWorkspaceDir = workspaceDir
        updatePrompt()
    }

    private fun updatePrompt() {
        val mode = _terminalMode.value
        if (mode == TerminalMode.PYTHON_REPL) {
            _currentPrompt.value = ">>> "
        } else {
            val isReady = sandboxManager.isReady()
            _currentPrompt.value = if (isReady) "alpine # " else "sh $ "
        }
    }

    fun appendSystemInfo(msg: String) {
        appendLine(TerminalLine(msg, TerminalLineType.SYSTEM_INFO))
    }

    private fun appendLine(line: TerminalLine) {
        val current = _lines.value.toMutableList()
        current.add(line)
        if (current.size > MAX_BUFFER_LINES) {
            _lines.value = current.takeLast(MAX_BUFFER_LINES)
        } else {
            _lines.value = current
        }
    }

    fun clearScreen() {
        _lines.value = emptyList()
    }

    fun getPreviousHistory(): String? {
        if (_commandHistory.isEmpty()) return null
        if (historyIndex == -1) {
            historyIndex = _commandHistory.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        }
        return _commandHistory.getOrNull(historyIndex)
    }

    fun getNextHistory(): String? {
        if (_commandHistory.isEmpty() || historyIndex == -1) return null
        if (historyIndex < _commandHistory.size - 1) {
            historyIndex++
            return _commandHistory[historyIndex]
        } else {
            historyIndex = -1
            return ""
        }
    }

    fun executeInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            appendLine(TerminalLine(_currentPrompt.value, TerminalLineType.PROMPT))
            return
        }

        // Add to history
        if (_commandHistory.isEmpty() || _commandHistory.last() != trimmed) {
            _commandHistory.add(trimmed)
        }
        historyIndex = -1

        // Display user input line
        appendLine(TerminalLine("${_currentPrompt.value}$trimmed", TerminalLineType.INPUT))

        // Check if currently inside interactive Python REPL
        if (_terminalMode.value == TerminalMode.PYTHON_REPL) {
            sendToActiveProcess(trimmed)
            return
        }

        // Built-in Shell commands
        when (trimmed) {
            "clear" -> {
                clearScreen()
                return
            }
            "help" -> {
                appendSystemInfo("Amaya Terminal Commands:")
                appendSystemInfo("  clear        : Bersihkan layar")
                appendSystemInfo("  python3      : Membuka Interactive Python Shell (REPL)")
                appendSystemInfo("  gh           : GitHub CLI (jika terpasang via Quick Package)")
                appendSystemInfo("  apk add <pkg>: Pasang paket Linux (Alpine PRoot)")
                appendSystemInfo("  status       : Cek status lingkungan Linux Sandbox")
                return
            }
            "status" -> {
                val isReady = sandboxManager.isReady()
                if (isReady) {
                    appendSystemInfo("Linux Sandbox: AKTIF (Alpine Linux v3.22 PRoot)")
                    appendSystemInfo("Rootfs path  : ${sandboxManager.rootfsDir.absolutePath}")
                } else {
                    appendSystemInfo("Linux Sandbox: Belum dipasang / Menggunakan Host /system/bin/sh")
                }
                return
            }
            "python", "python3", "python3 -i" -> {
                startPythonRepl()
                return
            }
        }

        // Execute one-off command
        runShellCommand(trimmed)
    }

    private fun startPythonRepl() {
        scope.launch {
            killActiveProcess()
            _isRunning.value = true
            _terminalMode.value = TerminalMode.PYTHON_REPL
            updatePrompt()

            appendSystemInfo("Memulai Python 3 Interactive Shell (REPL)...")
            appendSystemInfo("Ketik 'exit()' atau tekan Ctrl+C untuk keluar.")

            val isSandbox = sandboxManager.isReady()
            val (cmdList, envMap) = if (isSandbox) {
                // Run python3 -i inside Alpine PRoot
                sandboxManager.buildExecution("python3 -i", activeWorkspaceDir)
            } else {
                Pair(listOf("/system/bin/sh", "-c", "python3 -i"), emptyMap())
            }

            try {
                val pb = ProcessBuilder(cmdList)
                pb.environment().apply {
                    putAll(envMap)
                    put("PYTHONUNBUFFERED", "1")
                    HOST_LEAK_ENV_VARS.forEach { remove(it) }
                }
                pb.redirectErrorStream(true)

                val proc = withContext(Dispatchers.IO) { pb.start() }
                activeProcess = proc
                activeWriter = BufferedWriter(OutputStreamWriter(proc.outputStream))

                readerJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.inputStream))
                        val buffer = CharArray(1024)
                        var read = reader.read(buffer)
                        val lineBuilder = StringBuilder()
                        while (read != -1) {
                            for (i in 0 until read) {
                                val ch = buffer[i]
                                if (ch == '\n') {
                                    appendLine(TerminalLine(lineBuilder.toString(), TerminalLineType.OUTPUT))
                                    lineBuilder.clear()
                                } else {
                                    lineBuilder.append(ch)
                                }
                            }
                            // Flush incomplete prompt characters like ">>> "
                            if (lineBuilder.isNotEmpty() && (lineBuilder.endsWith(">>> ") || lineBuilder.endsWith("... "))) {
                                appendLine(TerminalLine(lineBuilder.toString(), TerminalLineType.OUTPUT))
                                lineBuilder.clear()
                            }
                            read = reader.read(buffer)
                        }
                        if (lineBuilder.isNotEmpty()) {
                            appendLine(TerminalLine(lineBuilder.toString(), TerminalLineType.OUTPUT))
                        }
                    } catch (_: Exception) {} finally {
                        _isRunning.value = false
                        _terminalMode.value = TerminalMode.SHELL
                        updatePrompt()
                        appendSystemInfo("Python REPL selesai.")
                    }
                }
            } catch (e: Exception) {
                _isRunning.value = false
                _terminalMode.value = TerminalMode.SHELL
                updatePrompt()
                appendLine(TerminalLine("Gagal memulai Python: ${e.message}", TerminalLineType.ERROR))
                appendSystemInfo("Pastikan paket 'python3' telah dipasang melalui menu Terminal Settings.")
            }
        }
    }

    private fun sendToActiveProcess(text: String) {
        if (text == "exit" || text == "exit()") {
            killActiveProcess()
            _terminalMode.value = TerminalMode.SHELL
            updatePrompt()
            appendSystemInfo("Keluar dari mode interaktif.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                activeWriter?.let { writer ->
                    writer.write(text)
                    writer.newLine()
                    writer.flush()
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("Error menulis ke proses: ${e.message}", TerminalLineType.ERROR))
            }
        }
    }

    private fun runShellCommand(command: String) {
        scope.launch {
            killActiveProcess()
            _isRunning.value = true

            val isSandbox = sandboxManager.isReady()
            val (cmdList, envMap) = if (isSandbox) {
                sandboxManager.buildExecution(command, activeWorkspaceDir)
            } else {
                Pair(listOf("/system/bin/sh", "-c", command), emptyMap())
            }

            try {
                val pb = ProcessBuilder(cmdList)
                pb.environment().apply {
                    putAll(envMap)
                    HOST_LEAK_ENV_VARS.forEach { remove(it) }
                }
                pb.redirectErrorStream(true)

                val proc = withContext(Dispatchers.IO) { pb.start() }
                activeProcess = proc

                readerJob = scope.launch(Dispatchers.IO) {
                    try {
                        val reader = BufferedReader(InputStreamReader(proc.inputStream))
                        var line = reader.readLine()
                        while (line != null) {
                            appendLine(TerminalLine(line, TerminalLineType.OUTPUT))
                            line = reader.readLine()
                        }
                        val exitCode = proc.waitFor()
                        if (exitCode != 0) {
                            appendLine(TerminalLine("Proses selesai dengan kode exit: $exitCode", TerminalLineType.ERROR))
                        }
                    } catch (_: Exception) {} finally {
                        _isRunning.value = false
                    }
                }
            } catch (e: Exception) {
                _isRunning.value = false
                appendLine(TerminalLine("Eksekusi gagal: ${e.message}", TerminalLineType.ERROR))
            }
        }
    }

    fun interrupt() {
        if (_isRunning.value) {
            killActiveProcess()
            appendLine(TerminalLine("^C", TerminalLineType.ERROR))
            _isRunning.value = false
            _terminalMode.value = TerminalMode.SHELL
            updatePrompt()
        }
    }

    private fun killActiveProcess() {
        try {
            readerJob?.cancel()
            readerJob = null
            activeWriter?.close()
            activeWriter = null
            activeProcess?.destroyForcibly()
            activeProcess = null
        } catch (_: Exception) {}
    }
}
