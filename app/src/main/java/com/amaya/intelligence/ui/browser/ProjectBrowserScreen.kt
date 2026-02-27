package com.amaya.intelligence.ui.browser

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.name

/**
 * Project browser for navigating and selecting workspace directories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectBrowserScreen(
    onWorkspaceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { 
        mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) 
    }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    
    // Load files when path changes
    LaunchedEffect(currentPath, showHidden) {
        isLoading = true
        files = loadFiles(currentPath, showHidden)
        isLoading = false
    }
    
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.extraLarge.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), topEnd = androidx.compose.foundation.shape.CornerSize(0.dp))
            ) {
                Column(
                    modifier = Modifier.statusBarsPadding()
                ) {
                    TopAppBar(
                        title = { 
                            Text(
                                "Select Workspace", 
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showHidden = !showHidden }) {
                                Icon(
                                    if (showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle hidden files",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { 
                            Text("Search files...", style = MaterialTheme.typography.bodyLarge) 
                        },
                        leadingIcon = { 
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) 
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        shape = CircleShape,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                    
                    // Breadcrumb navigation
                    BreadcrumbBar(
                        path = currentPath,
                        onNavigate = { currentPath = it }
                    )
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onWorkspaceSelected(currentPath) },
                icon = { Icon(Icons.Default.Check, "Select") },
                text = { Text("Select This Folder", style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (filteredFiles.isEmpty()) {
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
                        if (searchQuery.isNotEmpty()) "No files match your search" else "This folder is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Parent directory
                    if (currentPath != "/" && currentPath != Environment.getExternalStorageDirectory().absolutePath) {
                        item {
                            FileListItem(
                                item = FileItem(
                                    name = "..",
                                    path = Path.of(currentPath).parent?.toString() ?: "/",
                                    isDirectory = true,
                                    isHidden = false
                                ),
                                onClick = { selected ->
                                    if (selected.isDirectory) {
                                        if (selected.name == "..") {
                                            currentPath = Path.of(currentPath).parent?.toString() ?: "/"
                                        } else {
                                            currentPath = selected.path
                                        }
                                        searchQuery = "" // Reset search on navigation
                                    }
                                }
                            )
                        }
                    }
                    
                    items(filteredFiles, key = { it.path }) { item ->
                        FileListItem(
                            item = item,
                            onClick = { selected ->
                                if (selected.isDirectory) {
                                    currentPath = selected.path
                                    searchQuery = ""
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    onNavigate: (String) -> Unit
) {
    val parts = path.split("/").filter { it.isNotEmpty() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Root icon
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clip(CircleShape).clickable { onNavigate("/storage/emulated/0") }
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
        
        parts.forEachIndexed { index, part ->
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            val isLast = index == parts.size - 1
            
            Surface(
                shape = CircleShape,
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val targetPath = "/" + parts.take(index + 1).joinToString("/")
                        onNavigate(targetPath)
                    }
            ) {
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            
            if (!isLast) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun FileListItem(
    item: FileItem,
    onClick: (FileItem) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick(item) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Boxed Icon for that premium look
            Surface(
                shape = CircleShape,
                color = if (item.isDirectory) MaterialTheme.colorScheme.tertiaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val (icon, tint) = if (item.isDirectory) {
                        Icons.Default.Folder to MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        getFileIcon(item.name) to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Name
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isHidden) 
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else 
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Arrow for directories
            if (item.isDirectory && item.name != "..") {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun getFileIcon(name: String) = when {
    name.endsWith(".kt") || name.endsWith(".java") -> Icons.Default.Code
    name.endsWith(".xml") || name.endsWith(".json") -> Icons.Default.DataObject
    name.endsWith(".md") || name.endsWith(".txt") -> Icons.Default.Description
    name.endsWith(".gradle") || name.endsWith(".kts") -> Icons.Default.Build
    name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") -> Icons.Default.Image
    else -> Icons.Default.InsertDriveFile
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isHidden: Boolean
)

private suspend fun loadFiles(path: String, showHidden: Boolean): List<FileItem> = 
    withContext(Dispatchers.IO) {
        try {
            val dir = Path.of(path)
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return@withContext emptyList()
            }
            
            Files.list(dir).use { stream ->
                stream
                    .filter { showHidden || !it.isHidden() }
                    .map { file ->
                        FileItem(
                            name = file.name,
                            path = file.toString(),
                            isDirectory = file.isDirectory(),
                            isHidden = file.isHidden()
                        )
                    }
                    .toList()
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

