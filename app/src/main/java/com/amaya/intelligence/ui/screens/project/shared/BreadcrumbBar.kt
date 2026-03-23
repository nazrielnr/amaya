package com.amaya.intelligence.ui.screens.project.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.ui.theme.SectionShape

@Composable
fun BreadcrumbBar(
    path: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val parts = path.split("/").filter { it.isNotEmpty() }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = SectionShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clip(SectionShape).clickable { onNavigate("/storage/emulated/0") }
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
                shape = SectionShape,
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier
                    .clip(SectionShape)
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
