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