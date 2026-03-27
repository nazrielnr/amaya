package com.amaya.intelligence.ui.components.shared

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLockedModalBottomSheetState() = rememberModalBottomSheetState(
    skipPartiallyExpanded = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun lockedModalBottomSheetProperties() = ModalBottomSheetProperties(
    shouldDismissOnBackPress = true
)

private val ConsumeAllNestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return available
    }
}

fun Modifier.ignoreNestedScrollForBottomSheet() = this.nestedScroll(ConsumeAllNestedScrollConnection)

@Composable
fun isImeVisible(): Boolean {
    val density = LocalDensity.current
    return WindowInsets.ime.getBottom(density) > 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun responsiveBottomSheetShape(): Shape {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val cornerSize by animateDpAsState(
        targetValue = if (imeVisible) 0.dp else 28.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sheet_corner_animation"
    )
    return RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
}

@Composable
fun responsiveDragHandleAlpha(): Float {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val alpha by animateFloatAsState(
        targetValue = if (imeVisible) 0f else 0.4f,
        animationSpec = tween(durationMillis = 250),
        label = "drag_handle_alpha_animation"
    )
    return alpha
}