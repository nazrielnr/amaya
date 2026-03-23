package com.amaya.intelligence.ui.components.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val gradientWidth = 600f
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 1f),
            Color.White.copy(alpha = 0.7f),
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0f),
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.7f),
            Color.White.copy(alpha = 1f)
        ),
        start  = Offset(translateAnim - gradientWidth / 2, 0f),
        end    = Offset(translateAnim + gradientWidth / 2, 0f)
    )

    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(
            "Thinking..",
            style      = MaterialTheme.typography.bodyLarge,
            fontStyle  = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            color      = MaterialTheme.colorScheme.onSurface,
            modifier   = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = shimmerBrush, blendMode = BlendMode.DstIn)
                }
        )
    }
}

@Composable
fun ConversationSkeleton() {
    val transition = rememberInfiniteTransition(label = "conversation_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(4) { index ->
            val alignEnd = index % 2 == 1
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                    modifier = Modifier
                        .fillMaxWidth(if (alignEnd) 0.62f else 0.78f)
                        .height(if (index == 2) 90.dp else 56.dp)
                ) {}
            }
        }
    }
}
