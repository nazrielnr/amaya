package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsBackButton(onClick: () -> Unit) {
    AmayaTopBarButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        onClick = onClick,
        contentDescription = stringResource(R.string.back),
        modifier = Modifier.padding(start = 8.dp)
    )
}
