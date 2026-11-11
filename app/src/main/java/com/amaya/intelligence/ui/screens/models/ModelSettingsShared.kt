package com.amaya.intelligence.ui.screens.models

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaya.intelligence.ui.components.shared.ModelLeadingIcon

import com.amaya.intelligence.ui.screens.amaya.AmayaGroupedSettingsTokens
import com.amaya.intelligence.ui.screens.amaya.IosAmayaColors

@Composable
fun InlineError(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(stringResource(R.string.could_not_complete_action), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}



@Composable
fun ModelSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    colors: IosAmayaColors,
    onClick: (() -> Unit)?,
    modelId: String? = null,
    providerId: String? = null
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = AmayaGroupedSettingsTokens.rowHorizontalPadding,
                vertical = AmayaGroupedSettingsTokens.rowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(AmayaGroupedSettingsTokens.rowIconSize)
                    .clip(CircleShape)
                    .background(colors.iconBackground),
                contentAlignment = Alignment.Center
            ) {
                if (modelId != null) {
                    ModelLeadingIcon(
                        modelId = modelId,
                        providerId = providerId,
                        modifier = Modifier.size(AmayaGroupedSettingsTokens.rowIconGlyphSize),
                        tint = colors.iconTint
                    )
                } else {
                    Icon(
                        icon,
                        null,
                        tint = colors.iconTint,
                        modifier = Modifier.size(AmayaGroupedSettingsTokens.rowIconGlyphSize)
                    )
                }
            }
            Spacer(Modifier.width(AmayaGroupedSettingsTokens.rowIconTextGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 19.sp
                    ),
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(AmayaGroupedSettingsTokens.inlineTextSpacing))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp
                        ),
                        color = colors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(AmayaGroupedSettingsTokens.rowChevronSize)
                )
            }
        }
    }
}

@Composable
fun ModelDivider(colors: IosAmayaColors) {
    HorizontalDivider(
        Modifier.padding(start = AmayaGroupedSettingsTokens.rowDividerStartPadding),
        color = colors.separator,
        thickness = AmayaGroupedSettingsTokens.sectionBorderWidth
    )
}
