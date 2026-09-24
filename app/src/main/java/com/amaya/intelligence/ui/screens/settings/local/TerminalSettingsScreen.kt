package com.amaya.intelligence.ui.screens.settings.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.TerminalSettings
import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.domain.sandbox.ALPINE_VERSION
import com.amaya.intelligence.domain.sandbox.LinuxArchitecture
import com.amaya.intelligence.domain.sandbox.LinuxSandboxManager
import com.amaya.intelligence.domain.sandbox.SandboxStatus
import com.amaya.intelligence.ui.screens.amaya.AmayaScaffold
import com.amaya.intelligence.ui.screens.amaya.AmayaSection
import com.amaya.intelligence.ui.screens.amaya.AmayaSwitchRow
import androidx.compose.ui.platform.LocalContext
import com.amaya.intelligence.ui.activities.terminal.TerminalActivity
import kotlinx.coroutines.launch

@Composable
fun TerminalSettingsScreen(
    repository: TerminalSettingsRepository,
    sandboxManager: LinuxSandboxManager? = null,
    onNavigateBack: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var trusted by remember { mutableStateOf("") }
    var declined by remember { mutableStateOf("") }
    var autoApproveNonDestructive by remember { mutableStateOf(true) }
    var autoApproveAll by remember { mutableStateOf(false) }
    var useLinuxSandbox by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var packageActionStatus by remember { mutableStateOf<String?>(null) }
    // Last-loaded settings snapshot so a single-field change (sandbox toggle) can be
    // persisted immediately without clobbering unrelated fields the user has not saved.
    var loadedSettings by remember { mutableStateOf<TerminalSettings?>(null) }
    val context = LocalContext.current

    val detectedArch = remember { LinuxArchitecture.detect() }
    val sandboxStatus by (sandboxManager?.status?.collectAsState()
        ?: remember { mutableStateOf(SandboxStatus.NotInstalled) })

    val isSandboxReady = sandboxStatus is SandboxStatus.Ready
    val isInstalling = sandboxStatus is SandboxStatus.Installing

    // Persist the sandbox preference right away so the choice survives leaving the
    // screen (previously it only applied after pressing Save, so re-opening the
    // settings — or the app — showed the sandbox as disabled even when enabled).
    val persistSandbox = { enabled: Boolean ->
        useLinuxSandbox = enabled
        scope.launch {
            loadedSettings?.let { base ->
                val updated = base.copy(useLinuxSandbox = enabled)
                loadedSettings = updated
                repository.setSettings(updated)
            }
        }
    }

    LaunchedEffect(Unit) {
        val settings = repository.getSettings()
        loadedSettings = settings
        trusted = settings.trustedCommands.joinToString("\n")
        declined = settings.declinedCommands.joinToString("\n")
        autoApproveNonDestructive = settings.autoApproveNonDestructive
        autoApproveAll = settings.autoApproveAll
        useLinuxSandbox = settings.useLinuxSandbox
        loaded = true
        sandboxManager?.checkStatus()
    }

    AmayaScaffold("Terminal", snackbar, onNavigateBack) {
        AmayaSection("Linux Sandbox (Alpine + PRoot)") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Target ABI: ${detectedArch.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val statusBadge = when (sandboxStatus) {
                                is SandboxStatus.Ready -> "Siap / Ready"
                                is SandboxStatus.Installing -> "Memasang..."
                                is SandboxStatus.Error -> "Gagal"
                                is SandboxStatus.NotInstalled -> "Belum Dipasang"
                            }
                            Text(
                                statusBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = when (sandboxStatus) {
                                    is SandboxStatus.Ready -> MaterialTheme.colorScheme.primary
                                    is SandboxStatus.Installing -> MaterialTheme.colorScheme.tertiary
                                    is SandboxStatus.Error -> MaterialTheme.colorScheme.error
                                    is SandboxStatus.NotInstalled -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }

                        when (val status = sandboxStatus) {
                            is SandboxStatus.Installing -> {
                                Text(
                                    status.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { status.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp)
                                )
                            }
                            is SandboxStatus.Ready -> {
                                Text(
                                    "Alpine Linux $ALPINE_VERSION aktif di internal storage. Mendukung pemasangan paket (apk), runtime Python, Node.js, Git, GCC, dan toolchain lainnya.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            is SandboxStatus.Error -> {
                                Text(
                                    "Error: ${status.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is SandboxStatus.NotInstalled -> {
                                Text(
                                    "Minirootfs (~4MB unduhan) memungkinkan eksekusi perintah Linux lengkap terisolasi tanpa memerlukan root di perangkat 32-bit & 64-bit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        TerminalActivity.start(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Buka Terminal Interaktif")
                }

                AmayaSwitchRow(
                    title = "Gunakan Linux Sandbox untuk Terminal",
                    subtitle = "Arahkan perintah terminal dan eksekusi AI ke dalam container Alpine Linux (PRoot) terisolasi.",
                    checked = useLinuxSandbox && isSandboxReady,
                    onCheckedChange = { persistSandbox(it) },
                    enabled = loaded && isSandboxReady && !isInstalling
                )

                if (!isSandboxReady && !isInstalling) {
                    Button(
                        onClick = {
                            scope.launch {
                                snackbar.showSnackbar("Memulai unduhan Alpine Linux...")
                                val result = sandboxManager?.install { stage, _ ->
                                    // Progress handled via StateFlow
                                }
                                if (result?.isSuccess == true) {
                                    persistSandbox(true)
                                    snackbar.showSnackbar("Alpine Linux Sandbox berhasil dipasang!")
                                } else {
                                    snackbar.showSnackbar("Pemasangan gagal: ${result?.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pasang Alpine Linux Sandbox (~4 MB)")
                    }
                }

                if (isSandboxReady) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Paket Cepat (Quick Packages):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (packageActionStatus != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    packageActionStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        packageActionStatus = "Memasang Python 3 & Pip..."
                                        val res = sandboxManager?.runApkAdd("python3 py3-pip")
                                        packageActionStatus = null
                                        if (res?.isSuccess == true) {
                                            snackbar.showSnackbar("Python 3 & Pip siap digunakan!")
                                        } else {
                                            val err = res?.exceptionOrNull()?.message ?: "Gagal memasang Python 3"
                                            snackbar.showSnackbar("Gagal: $err", duration = SnackbarDuration.Long)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = packageActionStatus == null
                            ) {
                                Text("Python 3")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        packageActionStatus = "Memasang Node.js & NPM..."
                                        val res = sandboxManager?.runApkAdd("nodejs npm")
                                        packageActionStatus = null
                                        if (res?.isSuccess == true) {
                                            snackbar.showSnackbar("Node.js & NPM siap digunakan!")
                                        } else {
                                            val err = res?.exceptionOrNull()?.message ?: "Gagal memasang Node.js"
                                            snackbar.showSnackbar("Gagal: $err", duration = SnackbarDuration.Long)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = packageActionStatus == null
                            ) {
                                Text("Node.js")
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        packageActionStatus = "Memasang GitHub CLI (gh)..."
                                        val res = sandboxManager?.runApkAdd("github-cli git")
                                        packageActionStatus = null
                                        if (res?.isSuccess == true) {
                                            snackbar.showSnackbar("GitHub CLI (gh) siap digunakan!")
                                        } else {
                                            val err = res?.exceptionOrNull()?.message ?: "Gagal memasang GitHub CLI"
                                            snackbar.showSnackbar("Gagal: $err", duration = SnackbarDuration.Long)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = packageActionStatus == null
                            ) {
                                Text("GitHub CLI (gh)")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        packageActionStatus = "Memasang Git & Curl..."
                                        val res = sandboxManager?.runApkAdd("git curl")
                                        packageActionStatus = null
                                        if (res?.isSuccess == true) {
                                            snackbar.showSnackbar("Git & Curl siap digunakan!")
                                        } else {
                                            val err = res?.exceptionOrNull()?.message ?: "Gagal memasang Git & Curl"
                                            snackbar.showSnackbar("Gagal: $err", duration = SnackbarDuration.Long)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = packageActionStatus == null
                            ) {
                                Text("Git / Curl")
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        sandboxManager?.uninstall()
                                        persistSandbox(false)
                                        snackbar.showSnackbar("Alpine Linux Sandbox dihapus.")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = packageActionStatus == null
                            ) {
                                Text("Hapus Sandbox")
                            }
                        }
                    }
                }
            }
        }

        AmayaSection("Command Policy") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "One wildcard pattern per line. Trusted commands run automatically without approval. Declined commands are blocked. Toggle Auto-Approve below to run commands without manual confirmation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = trusted,
                    onValueChange = { trusted = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Trusted Commands") },
                    supportingText = { Text("Examples: npm * or python * or * (for all)") },
                    minLines = 5,
                    enabled = loaded,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                OutlinedTextField(
                    value = declined,
                    onValueChange = { declined = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Declined Commands") },
                    supportingText = { Text("Matched commands are rejected without review") },
                    minLines = 3,
                    enabled = loaded,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                AmayaSwitchRow(
                    title = "Auto-approve all commands (Full Auto)",
                    subtitle = "Automatically run all workspace commands and tool actions without confirmation dialogs. Critical system boundaries (e.g. root/system format) and declined patterns are still enforced.",
                    checked = autoApproveAll,
                    onCheckedChange = {
                        autoApproveAll = it
                        if (it) autoApproveNonDestructive = true
                    },
                    enabled = loaded
                )
                AmayaSwitchRow(
                    title = "Auto-approve safe commands",
                    subtitle = "Auto-approve non-destructive commands (MCP, python, gradle, git status, reads); destructive commands (deletion, overwrite, chmod, sudo, git push) still require manual confirmation.",
                    checked = autoApproveNonDestructive || autoApproveAll,
                    onCheckedChange = { autoApproveNonDestructive = it },
                    enabled = loaded && !autoApproveAll
                )
                Button(
                    onClick = {
                        scope.launch {
                            val cleanTrusted = trusted.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val cleanDeclined = declined.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val saved = TerminalSettings(
                                trustedCommands = cleanTrusted,
                                declinedCommands = cleanDeclined,
                                autoApproveNonDestructive = autoApproveNonDestructive,
                                autoApproveAll = autoApproveAll,
                                useLinuxSandbox = useLinuxSandbox
                            )
                            loadedSettings = saved
                            repository.setSettings(saved)
                            snackbar.showSnackbar("Terminal settings saved")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loaded
                ) { Text("Save") }
            }
        }
    }
}

