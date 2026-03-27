package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders a unified diff with red/green line highlighting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerScreen(
    diff: String,
    onBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val lines = remember(diff) { diff.lines() }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Diff colors
    val addedBg = if (isDark) Color(0xFF0D2818) else Color(0xFFE6FFEC)
    val removedBg = if (isDark) Color(0xFF2D0A0A) else Color(0xFFFFEBE9)
    val headerBg = if (isDark) Color(0xFF0D1B2A) else Color(0xFFDDF4FF)
    val addedText = if (isDark) Color(0xFF56D364) else Color(0xFF116329)
    val removedText = if (isDark) Color(0xFFF85149) else Color(0xFF82071E)
    val headerText = if (isDark) Color(0xFF79C0FF) else Color(0xFF0550AE)
    val normalText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Changes", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(diff))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (diff.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No changes", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(lines, key = { idx, _ -> idx }) { _, line ->
                    val (bg, textColor) = when {
                        line.startsWith("+++") || line.startsWith("---") -> headerBg to headerText
                        line.startsWith("@@") -> headerBg to headerText
                        line.startsWith("diff --git") -> headerBg to headerText
                        line.startsWith("+") -> addedBg to addedText
                        line.startsWith("-") -> removedBg to removedText
                        else -> Color.Transparent to normalText
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = textColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
