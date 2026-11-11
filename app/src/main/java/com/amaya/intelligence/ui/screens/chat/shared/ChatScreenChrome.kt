package com.amaya.intelligence.ui.screens.chat.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource



import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@Composable
internal fun ChatFloatingTopBar(
    title: String,
    subtitle: String?,
    isRemoteMode: Boolean,
    isBridgeMode: Boolean,
    onMenuClick: () -> Unit,
    onTitleClick: () -> Unit,
    showNewChat: Boolean,
    onNewChatClick: () -> Unit,
    showAgentMenu: Boolean,
    onAgentMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    agentMenu: @Composable () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val orbColor = if (isDark) Color(0xFF202228).copy(alpha = 0.92f) else Color(0xFFFAFAFC).copy(alpha = 0.96f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)


    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidOrbButton(
            icon = Icons.Default.Menu,
            contentDescription = stringResource(R.string.menu),
            color = orbColor,
            borderColor = borderColor,
            onClick = onMenuClick
        )

        Spacer(modifier = Modifier.width(12.dp))

        if (title.isNotEmpty() || !subtitle.isNullOrBlank()) {
            FadingTitleLayout(
                title = title,
                subtitle = subtitle,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTitleClick
                    )
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(18.dp))

        when {
            // The menu is composed inside the button's own Box so the popup anchors to the orb.
            // It used to be emitted at screen level with a hardcoded DpOffset(180.dp, 48.dp),
            // which put it wherever that offset happened to land on a given screen size.
            showAgentMenu -> Box {
                LiquidOrbButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.agent_menu),
                    color = orbColor,
                    borderColor = borderColor,
                    onClick = onAgentMenuClick
                )
                agentMenu()
            }
            showNewChat -> LiquidOrbButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.new_chat),
                color = orbColor,
                borderColor = borderColor,
                onClick = onNewChatClick
            )
            else -> Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
internal fun AgentChatMenu(
    expanded: Boolean,
    onOpenBrowser: () -> Unit,
    onConfigure: () -> Unit,
    onDeleteChat: () -> Unit,
    onDismiss: () -> Unit
) {
    com.amaya.intelligence.ui.components.shared.AmayaDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        alignment = com.amaya.intelligence.ui.components.shared.AmayaMenuAlignment.End,
        placement = com.amaya.intelligence.ui.components.shared.AmayaMenuPlacement.Below
    ) {
        com.amaya.intelligence.ui.components.shared.AmayaDropdownMenuItem(
            text = stringResource(R.string.open_browser),
            icon = Icons.Default.OpenInBrowser,
            onClick = onOpenBrowser
        )
        com.amaya.intelligence.ui.components.shared.AmayaDropdownMenuItem(
            text = stringResource(R.string.configure_agent),
            icon = Icons.Default.Settings,
            onClick = onConfigure
        )
        com.amaya.intelligence.ui.components.shared.AmayaDropdownMenuItem(
            text = stringResource(R.string.delete_chat),
            icon = Icons.Default.Delete,
            destructive = true,
            onClick = onDeleteChat
        )
    }
}

@Composable
private fun FadingTitleLayout(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    var isOverflowing by remember { mutableStateOf(false) }
    val renderedTitle = rememberHyperText(title)

    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (isOverflowing) {
                            val fadeStartPx = 64.dp.toPx()
                            val fadeEndPx = 24.dp.toPx()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black,
                                        0.2f to Color.Black.copy(alpha = 0.9f),
                                        0.4f to Color.Black.copy(alpha = 0.7f),
                                        0.6f to Color.Black.copy(alpha = 0.4f),
                                        0.8f to Color.Black.copy(alpha = 0.15f),
                                        1.0f to Color.Transparent
                                    ),
                                    startX = size.width - fadeStartPx,
                                    endX = size.width - fadeEndPx
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = renderedTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.session_info),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    ) { measurables, constraints ->
        val textMeasurable = measurables[0]
        val iconMeasurable = measurables[1]

        val iconPlaceable = iconMeasurable.measure(constraints.copy(minWidth = 0))
        val iconWidth = iconPlaceable.width

        val textIntrinsicWidth = textMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val totalIntrinsicWidth = textIntrinsicWidth + iconWidth

        val overflowing = totalIntrinsicWidth > constraints.maxWidth

        val textPlaceable = textMeasurable.measure(
            if (overflowing) {
                constraints.copy(maxWidth = constraints.maxWidth, minWidth = 0)
            } else {
                constraints.copy(maxWidth = textIntrinsicWidth, minWidth = 0)
            }
        )

        if (isOverflowing != overflowing) {
            isOverflowing = overflowing
        }

        val width = if (constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth) {
            constraints.maxWidth
        } else if (overflowing) {
            constraints.maxWidth
        } else {
            totalIntrinsicWidth
        }

        val height = maxOf(textPlaceable.height, iconPlaceable.height)

        layout(width, height) {
            textPlaceable.placeRelative(0, (height - textPlaceable.height) / 2)
            if (overflowing) {
                iconPlaceable.placeRelative(width - iconWidth, (height - iconPlaceable.height) / 2)
            } else {
                iconPlaceable.placeRelative(textPlaceable.width, (height - iconPlaceable.height) / 2)
            }
        }
    }
}

@Composable
private fun LiquidOrbButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    color: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = BorderStroke(0.7.dp, borderColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
