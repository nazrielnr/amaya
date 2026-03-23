package com.amaya.intelligence.ui.screens.settings.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.ui.theme.SectionShape

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSystemInDarkTheme()) Color(0xFF98989D) else Color(0xFF8E8E93),
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        Surface(
            shape = SectionShape,
            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}
