package com.amaya.intelligence.ui.components.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable

enum class PermissionType {
    STORAGE,
    CAMERA,
    NOTIFICATIONS,
    EXACT_ALARM,
    BATTERY_OPTIMIZATION
}

private fun PermissionType.toSpec(): PermissionSheetSpec = when (this) {
    PermissionType.STORAGE -> PermissionSheetSpec(
        icon = Icons.Default.FolderOpen,
        title = "File Access",
        subtitle = "Index local projects.",
        detail = "Amaya reads your workspace so it can build context and search files locally.",
        systemFlow = "Android will show all-files access or storage permission dialog.",
        fallback = "You can always change this later in system app settings.",
        actionLabel = "Grant"
    )
    PermissionType.CAMERA -> PermissionSheetSpec(
        icon = Icons.Default.CameraAlt,
        title = "Camera Access",
        subtitle = "Scan remote pairing QR codes.",
        detail = "Used only when you connect to the IDE remotely.",
        systemFlow = "Android asks camera permission once when scan is requested.",
        fallback = "You can disable camera any time if you stop using remote QR.",
        actionLabel = "Grant"
    )
    PermissionType.NOTIFICATIONS -> PermissionSheetSpec(
        icon = Icons.Default.Notifications,
        title = "Notifications",
        subtitle = "Keep task updates visible.",
        detail = "Used for background jobs, reminders, and completion alerts.",
        systemFlow = "Android requests notification permission so alerts can appear.",
        fallback = "You can mute channels later without disabling everything.",
        actionLabel = "Enable"
    )
    PermissionType.EXACT_ALARM -> PermissionSheetSpec(
        icon = Icons.Default.Alarm,
        title = "Precise Alarms",
        subtitle = "Run time-based actions on time.",
        detail = "Needed for scheduled jobs that should not drift.",
        systemFlow = "Android opens alarm settings to allow exact scheduling.",
        fallback = "You can keep it off if you do not use scheduled automations.",
        actionLabel = "Enable"
    )
    PermissionType.BATTERY_OPTIMIZATION -> PermissionSheetSpec(
        icon = Icons.Default.BatteryChargingFull,
        title = "Battery Optimization",
        subtitle = "Keep background work alive.",
        detail = "Helps Amaya stay stable during long syncs and local tasks.",
        systemFlow = "Android opens battery optimization page for this app.",
        fallback = "You can re-enable optimization later if needed.",
        actionLabel = "Allow"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRequirementSheet(
    permissionType: PermissionType,
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    val spec = permissionType.toSpec()
    val sheetState = rememberLockedModalBottomSheetState()

    StandardModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        title = spec.title,
        icon = spec.icon,
        subtitle = spec.subtitle,
        showCloseButton = true,
        dismissible = true
    ) {
        PermissionSheetBody(
            spec = spec,
            granted = false,
            onPrimary = {
                onDismiss()
                onGrant()
            },
            onSecondary = onDismiss,
            primaryLabel = spec.actionLabel,
            secondaryLabel = "Maybe later"
        )
    }
}
