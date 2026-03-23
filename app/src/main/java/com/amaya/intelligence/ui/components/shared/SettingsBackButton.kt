package com.amaya.intelligence.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

@Composable
fun SettingsBackButton(onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val backgroundColor = MaterialTheme.colorScheme.background
    
    val solidColor = remember(baseColor, backgroundColor) {
        baseColor.compositeOver(backgroundColor)
    }

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(solidColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
