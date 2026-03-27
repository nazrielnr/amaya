package com.amaya.intelligence.ui.components.shared

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.compositeOver
import com.amaya.intelligence.ui.components.shared.ignoreNestedScrollForBottomSheet
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class LocalhostLinkInfo(
    val originalText: String,
    val fullUrl: String,
    val displayText: String,
    val port: String?,
    val path: String?
)

private const val LOCALHOST_URL_DELIMITER = "\u0000LOCALHOST\u0000"

private val LOCALHOST_HOSTS = listOf(
    "localhost", "127\\.0\\.0\\.1", "0\\.0\\.0\\.0", "::1"
).joinToString("|")

object LocalhostPatterns {
    val URL_PATTERN = Regex(
        "(https?)://($LOCALHOST_HOSTS)(:\\d{1,5})?(/[^\\s]*)?",
        RegexOption.IGNORE_CASE
    )

    val PLAIN_LOCALHOST_PATTERN = Regex(
        "($LOCALHOST_HOSTS)(:\\d{1,5})?(/[^\\s]*)?",
        RegexOption.IGNORE_CASE
    )

    fun Regex.findAt(s: String, i: Int): Boolean {
        return this.find(s, i)?.let { it.range.first == i } ?: false
    }

    fun parseLocalhostLink(text: String, localIp: String): LocalhostLinkInfo? {
        val urlMatch = URL_PATTERN.find(text) ?: PLAIN_LOCALHOST_PATTERN.find(text)
        return urlMatch?.let { match ->
            val fullMatch = match.value
            val isHttpScheme = fullMatch.startsWith("http://") || fullMatch.startsWith("https://")

            val groupValues = match.groupValues
            val host = if (groupValues.size > 2) groupValues[2].lowercase() else match.value
            val portGroup = if (groupValues.size > 3) groupValues[3].ifEmpty { null } else null
            val path = if (groupValues.size > 4) groupValues[4].ifEmpty { "/" } else "/"

            val actualHost = when (host) {
                "localhost", "127.0.0.1", "0.0.0.0", "::1" -> localIp
                else -> host
            }

            val actualPort = portGroup?.removePrefix(":") ?: "3000"
            val protocol = if (isHttpScheme && fullMatch.startsWith("https")) "https" else "http"
            val fullUrl = "$protocol://$actualHost:$actualPort$path"
            val displayText = "$actualHost:$actualPort$path"

            LocalhostLinkInfo(
                originalText = fullMatch,
                fullUrl = fullUrl,
                displayText = displayText,
                port = actualPort,
                path = path
            )
        }
    }

    fun hasLocalhostLink(text: String): Boolean {
        return URL_PATTERN.containsMatchIn(text) || PLAIN_LOCALHOST_PATTERN.containsMatchIn(text)
    }

    fun serializeLinkInfo(info: LocalhostLinkInfo): String {
        return "$LOCALHOST_URL_DELIMITER${info.fullUrl}|$LOCALHOST_URL_DELIMITER"
    }
}

object LocalhostLinkInfoParser {
    private const val IP_PLACEHOLDER = "LOCALHOST_IP_PLACEHOLDER"

    fun parse(annotationItem: String, actualIp: String): LocalhostLinkInfo {
        val cleanItem = annotationItem
            .removePrefix(LOCALHOST_URL_DELIMITER)
            .removeSuffix(LOCALHOST_URL_DELIMITER)

        val fullUrl = cleanItem.replace(IP_PLACEHOLDER, actualIp)

        val portMatch = Regex(":(\\d{1,5})").find(fullUrl)
        val port = portMatch?.groupValues?.get(1)
        val pathMatch = Regex("(/[^?#]*).*").find(fullUrl)
        val path = pathMatch?.groupValues?.get(1)

        val hostWithPort = fullUrl.substringAfter("://").substringBefore("/").substringBefore("?")
        val displayText = if (port != null) "$actualIp:$port$path" else "$actualIp$path"

        return LocalhostLinkInfo(
            originalText = fullUrl,
            fullUrl = fullUrl,
            displayText = displayText,
            port = port,
            path = path
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalhostLinkBottomSheet(
    linkInfo: LocalhostLinkInfo,
    localIp: String,
    onDismiss: () -> Unit,
    onCopyLink: ((String) -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null
) {
    val sheetState = rememberLockedModalBottomSheetState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    var copySuccess by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = {
            copyError = null
            onDismiss()
        },
        sheetState = sheetState,
        properties = com.amaya.intelligence.ui.components.shared.lockedModalBottomSheetProperties(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = com.amaya.intelligence.ui.components.shared.responsiveBottomSheetShape(sheetState)
    ) {
        val gradients = LocalAmayaGradients.current
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .weight(1f, fill = false)
        ) {
            // Bottom Layer: Scrolling Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ignoreNestedScrollForBottomSheet()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(90.dp)) // Reserve space for the header

                LocalhostWarningSection(linkInfo = linkInfo, localIp = localIp)

                LocalhostUrlDisplay(url = linkInfo.fullUrl)

                LocalhostActionButtons(
                    url = linkInfo.fullUrl,
                    onCopy = { url ->
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Localhost URL", url)
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip)
                                copySuccess = true
                                copyError = null
                                onCopyLink?.invoke(url)
                                scope.launch {
                                    kotlinx.coroutines.delay(2000)
                                    copySuccess = false
                                }
                            } else {
                                copyError = "Clipboard not available"
                            }
                        } catch (e: Exception) {
                            copyError = "Failed to copy: ${e.message}"
                            copySuccess = false
                        }
                    },
                    onOpen = { url ->
                        try {
                            scope.launch {
                                sheetState.hide()
                            }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    uriHandler.openUri(url)
                                    onOpenLink?.invoke(url)
                                    onDismiss()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    copySuccess = copySuccess,
                    copyError = copyError
                )
            }

            // Top Layer: Blurred Header Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(gradients.modalTopScrim)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = com.amaya.intelligence.ui.components.shared.responsiveDragHandleAlpha(sheetState)))
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Localhost Redirect",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    .compositeOver(MaterialTheme.colorScheme.background)
                            )
                            .clickable {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalhostWarningSection(linkInfo: LocalhostLinkInfo, localIp: String) {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "External Connection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This link points to a service running on your local network. You will be redirected to ${linkInfo.displayText} via ${localIp}. Please ensure you trust the source before proceeding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun LocalhostUrlDisplay(url: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Full Address",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalhostActionButtons(
    url: String,
    onCopy: (String) -> Unit,
    onOpen: (String) -> Unit,
    copySuccess: Boolean,
    copyError: String?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { onOpen(url) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Open in Browser",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = { onCopy(url) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (copySuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (copySuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = copySuccess,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn()).togetherWith(
                        androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
                    )
                },
                label = "CopyButtonContent"
            ) { success ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (success) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (success) "Link Copied!" else "Copy to Clipboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        copyError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
