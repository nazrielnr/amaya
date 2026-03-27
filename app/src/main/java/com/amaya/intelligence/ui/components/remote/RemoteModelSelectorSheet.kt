package com.amaya.intelligence.ui.components.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.amaya.intelligence.domain.models.AgentSelectorItem
import com.amaya.intelligence.ui.components.shared.AgentIcon
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import com.amaya.intelligence.ui.components.shared.rememberLockedModalBottomSheetState
import com.amaya.intelligence.ui.components.shared.ignoreNestedScrollForBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteModelSelectorSheet(
    agentItems: List<AgentSelectorItem>,
    activeAgentId: String,
    serverName: String?,
    onSelect: (AgentSelectorItem) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberLockedModalBottomSheetState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val maxSheetHeight = (0.75f * LocalConfiguration.current.screenHeightDp).dp
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = com.amaya.intelligence.ui.components.shared.lockedModalBottomSheetProperties(),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = com.amaya.intelligence.ui.components.shared.responsiveBottomSheetShape()
    ) {
        val gradients = LocalAmayaGradients.current
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Spacer(Modifier.height(90.dp))

                if (agentItems.isEmpty()) {
                    Text(
                        text = "No agents available on this server",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    agentItems.forEach { agent ->
                        RemoteAgentItem(
                            agent = agent,
                            isSelected = agent.id == activeAgentId,
                            onClick = { 
                                scope.launch {
                                    sheetState.hide()
                                }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        onSelect(agent)
                                    }
                                }
                            }
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
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = com.amaya.intelligence.ui.components.shared.responsiveDragHandleAlpha()))
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Remote Select Agent",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
private fun RemoteAgentItem(
    agent: AgentSelectorItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else 
            Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSpec = AgentIcon.resolveByType(agent.iconType, isSystemInDarkTheme())

            if (iconSpec != null) {
                Icon(
                    painter = painterResource(id = iconSpec.resId),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (iconSpec.tintable) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (agent.tagTitle != null) {
                        Text(
                            text = agent.tagTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
