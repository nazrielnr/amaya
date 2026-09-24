package com.amaya.intelligence.ui.screens.chat.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class IosDrawerColors(
    val groupedBackground: Color,
    val groupSurface: Color,
    val border: Color,
    val separator: Color,
    val iconBackground: Color,
    val iconTint: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val headerText: Color,
    val activeIndicator: Color,
    val activeBackground: Color,
    val chevronTint: Color
)

@Composable
internal fun iosDrawerColors(isDark: Boolean): IosDrawerColors {
    return if (isDark) {
        IosDrawerColors(
            groupedBackground = Color(0xFF0B0B0F),
            groupSurface = Color(0xFF1C1C1E),
            border = Color.White.copy(alpha = 0.10f),
            separator = Color.White.copy(alpha = 0.10f),
            iconBackground = Color(0xFF2C2C2E),
            iconTint = Color(0xFFC7C7CC),
            primaryText = Color(0xFFF2F2F7),
            secondaryText = Color(0xFFEBEBF5).copy(alpha = 0.60f),
            headerText = Color(0xFFEBEBF5).copy(alpha = 0.48f),
            activeIndicator = Color(0xFF0A84FF),
            activeBackground = Color.White.copy(alpha = 0.10f),
            chevronTint = Color(0xFFC7C7CC).copy(alpha = 0.5f)
        )
    } else {
        IosDrawerColors(
            groupedBackground = Color(0xFFF2F2F7),
            groupSurface = Color.White,
            border = Color.Black.copy(alpha = 0.08f),
            separator = Color(0xFF3C3C43).copy(alpha = 0.13f),
            iconBackground = Color(0xFFE9E9EE),
            iconTint = Color(0xFF5F6368),
            primaryText = Color(0xFF1C1C1E),
            secondaryText = Color(0xFF3C3C43).copy(alpha = 0.62f),
            headerText = Color(0xFF3C3C43).copy(alpha = 0.52f),
            activeIndicator = Color(0xFF0A84FF),
            activeBackground = Color.Black.copy(alpha = 0.06f),
            chevronTint = Color(0xFFC7C7CC)
        )
    }
}

@Composable
internal fun Modifier.shimmeringText(
    shimmering: Boolean,
    baseColor: Color,
    shimmerColor: Color = if (isSystemInDarkTheme()) Color.White else Color.Black
): Modifier {
    if (!shimmering) return this
    val transition = rememberInfiniteTransition(label = "text_shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = size.width
            val peakX = (shimmerProgress * (w * 3f)) - w
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to baseColor.copy(alpha = 0.5f),
                    0.5f to shimmerColor,
                    1f to baseColor.copy(alpha = 0.5f),
                    startX = peakX - (w * 0.5f),
                    endX = peakX + (w * 0.5f)
                ),
                blendMode = BlendMode.SrcIn
            )
        }
}

// =============================================================================
// Grouped Surface Container
// =============================================================================

@Composable
internal fun IosGroupSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.groupSurface,
        border = BorderStroke(0.7.dp, colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        Column(content = content)
    }
}

// =============================================================================
// Row Icon (Filled Style)
// =============================================================================

@Composable
internal fun IosRowIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.iconBackground),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.iconTint,
            modifier = Modifier.size(17.dp)
        )
    }
}

// =============================================================================
// Row with Chevron
// =============================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IosRowWithChevron(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    title: String,
    subtitle: String? = null,
    showChevron: Boolean = true,
    expanded: Boolean? = null,
    selected: Boolean = false,
    trailingProgress: Boolean = false,
    unread: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Surface(
        color = if (selected) colors.activeBackground else Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    else Modifier.clickable(onClick = onClick)
                )
                .heightIn(min = 48.dp)
                .drawBehind {
                    if (expanded == true) {
                        val strokeWidth = 1.dp.toPx()
                        val color = colors.border
                        val iconCenterX = 32.dp.toPx()
                        val centerY = size.height / 2
                        drawLine(
                            color = color,
                            start = androidx.compose.ui.geometry.Offset(iconCenterX, centerY),
                            end = androidx.compose.ui.geometry.Offset(iconCenterX, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                IosRowIcon(icon = icon)
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f).fadingEdge()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.shimmeringText(trailingProgress, colors.primaryText)
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingProgress) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            if (unread) {
                UnreadDot()
                Spacer(Modifier.width(8.dp))
            }
            if (showChevron) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded == true) 90f else 0f,
                    animationSpec = spring(),
                    label = "chevron_rotation"
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.chevronTint,
                    modifier = Modifier.size(18.dp).rotate(rotation)
                )
            }
        }
    }
}

// =============================================================================
// Row Separator - Full Width
// =============================================================================

@Composable
internal fun IosRowSeparator(
    modifier: Modifier = Modifier,
    startIndent: androidx.compose.ui.unit.Dp = 0.dp
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(colors.separator)
    )
}

@Composable
internal fun IosChildRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    streaming: Boolean = false,
    unread: Boolean = false,
    isLastChild: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Surface(
        onClick = onClick,
        color = if (selected) colors.activeBackground else Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .drawBehind {
                    drawLine(
                        color = colors.separator,
                        start = androidx.compose.ui.geometry.Offset(60.dp.toPx(), 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 0.5.dp.toPx()
                    )

                    val strokeWidth = 1.dp.toPx()
                    val color = colors.border
                    val parentIconCenterX = 32.dp.toPx()
                    val childIconLeftEdge = 44.dp.toPx()
                    val centerY = size.height / 2
                    val cornerRadius = 12.dp.toPx()

                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (isLastChild) {
                            moveTo(parentIconCenterX, 0f)
                            lineTo(parentIconCenterX, centerY - cornerRadius)
                            quadraticTo(
                                parentIconCenterX, centerY,
                                parentIconCenterX + cornerRadius, centerY
                            )
                            lineTo(childIconLeftEdge, centerY)
                        } else {
                            moveTo(parentIconCenterX, 0f)
                            lineTo(parentIconCenterX, size.height)
                            moveTo(parentIconCenterX, centerY)
                            lineTo(childIconLeftEdge, centerY)
                        }
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
                .padding(start = 44.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                IosRowIcon(icon = icon)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fadingEdge()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.shimmeringText(streaming, colors.primaryText)
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (streaming) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            if (unread) {
                UnreadDot()
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

// =============================================================================
// Unread Indicator Dot
// =============================================================================

@Composable
internal fun UnreadDot(
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colors = iosDrawerColors(isDark)

    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(colors.activeIndicator)
    )
}
