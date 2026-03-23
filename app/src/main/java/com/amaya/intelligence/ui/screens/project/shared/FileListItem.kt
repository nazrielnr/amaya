package com.amaya.intelligence.ui.screens.project.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.domain.models.ProjectFileEntry
import com.amaya.intelligence.ui.components.shared.getFileIcon
import com.amaya.intelligence.ui.theme.SectionShape

@Composable
fun FileListItem(
    item: ProjectFileEntry,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: (ProjectFileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val isDirectory = item.type == "directory"
    val itemShape = when {
        isFirst && isLast -> SectionShape
        isFirst           -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        isLast            -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
        else              -> RoundedCornerShape(0.dp)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(itemShape)
            .clickable { onClick(item) },
        shape = itemShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDirectory) MaterialTheme.colorScheme.tertiaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val iconTint = if (isDirectory) {
                        Icons.Default.Folder to MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        getFileIcon(item.name) to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val icon = iconTint.first
                    val tint = iconTint.second
                    
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            if (isDirectory && item.name != "..") {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
