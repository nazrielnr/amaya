package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@androidx.compose.runtime.Immutable
data class PermissionSheetSpec(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val detail: String,
    val systemFlow: String,
    val fallback: String,
    val actionLabel: String
)

object StandardModalSheetDefaults {
    val MaxHeight: Dp = 720.dp
    val ContentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
    val HeaderHorizontalPadding: Dp = 24.dp
    val HeaderTopPadding: Dp = 14.dp
    val HeaderBottomPadding: Dp = 20.dp
    val DragHandleTopPadding: Dp = 14.dp
    val DragHandleBottomPadding: Dp = 8.dp
    val DragHandleWidth: Dp = 32.dp
    val DragHandleHeight: Dp = 4.dp
    val DragDismissVelocityThreshold: Dp = 125.dp
    const val DragDismissFraction: Float = 0.2f
}

class StandardModalSheetScope internal constructor(
    columnScope: ColumnScope,
    private val state: StandardModalSheetState
) : ColumnScope by columnScope {
    fun dismiss(afterDismiss: (() -> Unit)? = null) = state.dismiss(afterDismiss)
}

class StandardModalHeaderScope internal constructor(
    rowScope: androidx.compose.foundation.layout.RowScope,
    private val state: StandardModalSheetState
) : androidx.compose.foundation.layout.RowScope by rowScope {
    fun dismiss(afterDismiss: (() -> Unit)? = null) = state.dismiss(afterDismiss)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StandardModalBottomSheet(
    onDismissRequest: () -> Unit,
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    showCloseButton: Boolean = true,
    dismissible: Boolean = true,
    scrollable: Boolean = true,
    actions: @Composable StandardModalHeaderScope.() -> Unit = {},
    content: @Composable StandardModalSheetScope.() -> Unit
) {
    val state = rememberStandardModalSheetState(onDismissRequest, dismissible)
    val dragState = rememberDraggableState { delta -> state.dragBy(delta) }
    val dragModifier = Modifier.draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        enabled = dismissible,
        onDragStopped = { velocity -> state.settleDrag(velocity) }
    )

    ModalBottomSheet(
        onDismissRequest = state::finishDismiss,
        modifier = Modifier
            .onSizeChanged { state.updateSheetHeight(it.height) }
            .graphicsLayer { translationY = state.dragOffset },
        sheetState = state.sheetState,
        properties = standardModalBottomSheetProperties(dismissible),
        containerColor = containerColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier)
                    .padding(
                        top = StandardModalSheetDefaults.DragHandleTopPadding,
                        bottom = StandardModalSheetDefaults.DragHandleBottomPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(StandardModalSheetDefaults.DragHandleWidth)
                        .height(StandardModalSheetDefaults.DragHandleHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        },
        shape = responsiveBottomSheetShape(state.sheetState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = StandardModalSheetDefaults.MaxHeight)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(dragModifier)
                    .background(com.amaya.intelligence.ui.theme.LocalAmayaGradients.current.modalTopScrim)
                    .padding(horizontal = StandardModalSheetDefaults.HeaderHorizontalPadding)
                    .padding(
                        top = StandardModalSheetDefaults.HeaderTopPadding,
                        bottom = StandardModalSheetDefaults.HeaderBottomPadding
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StandardModalHeaderScope(this, state).actions()
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (showCloseButton) {
                    com.amaya.intelligence.ui.components.shared.AmayaTopBarButton(
                        icon = Icons.Default.Close,
                        onClick = state::dismiss,
                        contentDescription = stringResource(R.string.dismiss),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            if (scrollable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .blockBottomSheetBodyDrag()
                        .verticalScroll(rememberScrollState())
                        .padding(StandardModalSheetDefaults.ContentPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StandardModalSheetScope(this, state).content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .blockBottomSheetBodyDrag()
                ) {
                    StandardModalSheetScope(this, state).content()
                }
            }
        }
    }
}

@Composable
fun PermissionSheetBody(
    spec: PermissionSheetSpec,
    granted: Boolean,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    primaryLabel: String? = null,
    secondaryLabel: String = stringResource(R.string.skip)
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = spec.detail,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
            )

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.system_flow),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = spec.systemFlow,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = spec.fallback,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                    )
                }
            }

            if (granted) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.granted),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(primaryLabel ?: if (granted) stringResource(R.string.next) else spec.actionLabel, fontWeight = FontWeight.SemiBold)
            }

            if (onSecondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

