package com.amaya.intelligence.ui.components.local

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.amaya.intelligence.ui.components.shared.rememberLockedModalBottomSheetState
import com.amaya.intelligence.ui.components.shared.ignoreNestedScrollForBottomSheet
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import com.amaya.intelligence.tools.TodoItem
import com.amaya.intelligence.tools.TodoStatus

// ── TodoPill ─────────────────────────────────────────────────────────────────

@Composable
fun TodoPill(
    items: List<TodoItem>,
    onClick: () -> Unit
) {
    val completed = items.count { it.status == TodoStatus.COMPLETED }
    val total     = items.size
    val isRunning = items.any { it.status == TodoStatus.IN_PROGRESS }

    val transition = rememberInfiniteTransition(label = "todo_pill_shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "todo_pill_shimmer_x"
    )

    val pillColor   = if (isRunning) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant
    val labelColor  = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick  = onClick,
        shape    = CircleShape,
        color    = pillColor,
        modifier = Modifier.padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text       = "$completed/$total",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = labelColor,
                modifier   = Modifier
                    .then(if (isRunning)
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val w = size.width
                                val peakX = (shimmerProgress * (w * 3f)) - w
                                val hw = w * 0.6f
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 1f),
                                            Color.White.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 0.3f),
                                            Color.White.copy(alpha = 0f),
                                            Color.White.copy(alpha = 0.3f),
                                            Color.White.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 1f)
                                        ),
                                        start = Offset(peakX - hw, 0f),
                                        end   = Offset(peakX + hw, 0f)
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    else Modifier)
            )
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                modifier = Modifier.size(14.dp),
                tint = labelColor.copy(alpha = 0.7f)
            )
        }
    }
}

// ── TodoSheet ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoSheet(
    items: List<TodoItem>,
    onDismiss: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "todo_sheet_shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "todo_sheet_shimmer_x"
    )

    val scope = rememberCoroutineScope()
    val sheetState = rememberLockedModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        properties = com.amaya.intelligence.ui.components.shared.lockedModalBottomSheetProperties(),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = null,
        shape = com.amaya.intelligence.ui.components.shared.responsiveBottomSheetShape(sheetState)
    ) {
        val gradients = LocalAmayaGradients.current
        val done = items.count { it.status == TodoStatus.COMPLETED }
        val total = items.size

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) {
            // Bottom Layer: Scrolling Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ignoreNestedScrollForBottomSheet()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
            ) {
                Spacer(Modifier.height(90.dp)) // Reserve space for the header overlay
                
                items.forEach { item ->
                    TodoItemRow(item = item, shimmerProgress = shimmerProgress)
                    if (item != items.last()) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                            modifier = Modifier.padding(start = 44.dp)
                        )
                    }
                }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Task Plan",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "$done of $total completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

// ── TodoItemRow ──────────────────────────────────────────────────────────────

@Composable
internal fun TodoItemRow(item: TodoItem, shimmerProgress: Float) {
    val isActive    = item.status == TodoStatus.IN_PROGRESS
    val isCompleted = item.status == TodoStatus.COMPLETED
    val isPending   = item.status == TodoStatus.PENDING

    val label = if (isActive) item.activeForm ?: item.content ?: "Task ${item.id}"
                else item.content ?: "Task ${item.id}"

    val iosGreen  = Color(0xFF34C759)
    val iosBlue   = Color(0xFF007AFF)

    val iconTint = when (item.status) {
        TodoStatus.COMPLETED   -> iosGreen
        TodoStatus.IN_PROGRESS -> iosBlue
        TodoStatus.PENDING     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(
                    when (item.status) {
                        TodoStatus.COMPLETED   -> iosGreen.copy(alpha = 0.12f)
                        TodoStatus.IN_PROGRESS -> iosBlue.copy(alpha = 0.12f)
                        TodoStatus.PENDING     -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    }
                )
        ) {
            Icon(
                imageVector = when (item.status) {
                    TodoStatus.COMPLETED   -> Icons.Default.Check
                    TodoStatus.IN_PROGRESS -> Icons.Default.PlayArrow
                    TodoStatus.PENDING     -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (isActive) {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val w = size.width
                            val peakX = (shimmerProgress * (w * 3f)) - w
                            val hw = w * 0.6f
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 1f),
                                        Color.White.copy(alpha = 0.7f),
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = 0.3f),
                                        Color.White.copy(alpha = 0.7f),
                                        Color.White.copy(alpha = 1f)
                                    ),
                                    start = Offset(peakX - hw, 0f),
                                    end   = Offset(peakX + hw, 0f)
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            } else {
                Text(
                    text       = label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.Medium,
                    color      = when {
                        isCompleted -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        isPending   -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else        -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough
                                     else null
                )
            }
            Text(
                text  = when (item.status) {
                    TodoStatus.COMPLETED   -> "Completed"
                    TodoStatus.IN_PROGRESS -> "In progress"
                    TodoStatus.PENDING     -> "Pending"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when (item.status) {
                    TodoStatus.COMPLETED   -> iosGreen.copy(alpha = 0.8f)
                    TodoStatus.IN_PROGRESS -> iosBlue.copy(alpha = 0.9f)
                    TodoStatus.PENDING     -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            text  = "${item.id}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
