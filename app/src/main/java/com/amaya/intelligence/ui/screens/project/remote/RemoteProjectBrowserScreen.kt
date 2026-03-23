package com.amaya.intelligence.ui.screens.project.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.domain.models.RemoteWorkspace
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.screens.project.shared.FileListItem
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import com.amaya.intelligence.ui.theme.SectionShape
import com.amaya.intelligence.ui.viewmodels.ChatViewModel
import java.nio.file.Path

private fun normalizeRemotePath(path: String?): String? {
    return path
        ?.replace("\\", "/")
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
}

private fun remoteParentPath(path: String): String? {
    return runCatching { Path.of(path).parent?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun remoteChildPath(basePath: String, segment: String): String {
    val cleanBase = basePath.trimEnd('/')
    return if (cleanBase.isBlank()) segment else "$cleanBase/$segment"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteProjectBrowserScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val projectFiles by viewModel.projectFiles.collectAsState()
    val projectPath by viewModel.projectPath.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val gradients = LocalAmayaGradients.current

    var searchQuery by remember { mutableStateOf("") }

    val currentWorkspace = remember(workspaces) {
        workspaces.firstOrNull { it.isCurrent } ?: workspaces.firstOrNull()
    }
    val normalizedWorkspacePath = remember(currentWorkspace?.path) {
        normalizeRemotePath(currentWorkspace?.path)
    }
    val normalizedProjectPath = remember(projectPath, normalizedWorkspacePath) {
        normalizeRemotePath(projectPath) ?: normalizedWorkspacePath.orEmpty()
    }
    val filteredFiles = remember(projectFiles, searchQuery) {
        if (searchQuery.isBlank()) projectFiles
        else projectFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val workspaceRootFiles = remember(filteredFiles) { filteredFiles }

    LaunchedEffect(Unit) {
        viewModel.resync()
    }

    LaunchedEffect(normalizedWorkspacePath, projectPath) {
        if (projectPath.isBlank()) {
            normalizedWorkspacePath?.let { viewModel.setWorkspace(it) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Workspace",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onNavigateBack)
                },
                actions = {
                    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    val backgroundColor = MaterialTheme.colorScheme.background
                    val solidColor = remember(baseColor, backgroundColor) {
                        baseColor.compositeOver(backgroundColor)
                    }

                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(solidColor)
                            .clickable { viewModel.resync() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    placeholder = {
                        Text("Search files...", style = MaterialTheme.typography.bodyLarge)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    shape = SectionShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                RemoteBreadcrumbBar(
                    workspace = currentWorkspace,
                    currentPath = normalizedProjectPath,
                    onNavigate = { viewModel.setWorkspace(it) }
                )

                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        uiState.connectionState != ConnectionState.CONNECTED && workspaceRootFiles.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    "Remote session not connected",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.resync() }) {
                                    Text("Refresh")
                                }
                            }
                        }

                        workspaceRootFiles.isEmpty() -> Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(
                                if (searchQuery.isNotEmpty()) "No files match your search" else "This workspace is empty",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchQuery.isEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { viewModel.resync() }) {
                                    Text("Refresh data")
                                }
                            }
                        }

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val parentPath = if (normalizedProjectPath.isBlank()) null else remoteParentPath(normalizedProjectPath)
                            if (parentPath != null && parentPath != normalizedProjectPath) {
                                item {
                                    FileListItem(
                                        item = ProjectFileEntry(
                                            name = "..",
                                            path = parentPath,
                                            type = "directory",
                                            size = 0
                                        ),
                                        onClick = { selected ->
                                            viewModel.setWorkspace(selected.path)
                                        },
                                        isFirst = true,
                                        isLast = workspaceRootFiles.isEmpty()
                                    )
                                }
                            }

                            items(workspaceRootFiles, key = { it.path }) { item ->
                                FileListItem(
                                    item = item,
                                    onClick = { selected ->
                                        if (selected.type == "directory") {
                                            viewModel.setWorkspace(selected.path)
                                        }
                                    },
                                    isFirst = false,
                                    isLast = false
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.TopCenter)
                    .background(gradients.topScrim)
            )
        }
    }
}

@Composable
private fun RemoteBreadcrumbBar(
    workspace: RemoteWorkspace?,
    currentPath: String,
    onNavigate: (String) -> Unit
) {
    val rootPath = normalizeRemotePath(workspace?.path)
    val rootLabel = workspace?.name?.takeIf { it.isNotBlank() } ?: "Workspace"
    val normalizedCurrentPath = normalizeRemotePath(currentPath)
    val relativePath = when {
        rootPath.isNullOrBlank() -> normalizedCurrentPath.orEmpty().trim('/')
        normalizedCurrentPath.isNullOrBlank() -> ""
        normalizedCurrentPath.startsWith(rootPath) -> normalizedCurrentPath.removePrefix(rootPath).trim('/')
        else -> normalizedCurrentPath.trim('/')
    }
    val segments = relativePath.split('/').filter { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = SectionShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clip(SectionShape).clickable { rootPath?.let(onNavigate) }
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                Icon(
                    Icons.Default.Home,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        segments.forEachIndexed { index, part ->
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            val isLast = index == segments.size - 1
            val targetPath = buildString {
                if (!rootPath.isNullOrBlank()) append(rootPath)
                if (isNotEmpty()) append('/')
                append(segments.take(index + 1).joinToString("/"))
            }

            Surface(
                shape = SectionShape,
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .clip(SectionShape)
                    .clickable { onNavigate(targetPath) }
            ) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isLast) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
