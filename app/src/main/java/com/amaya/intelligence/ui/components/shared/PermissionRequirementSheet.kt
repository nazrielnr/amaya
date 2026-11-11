package com.amaya.intelligence.ui.components.shared

import com.amaya.intelligence.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
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

@Composable
private fun PermissionType.toSpec(): PermissionSheetSpec = when (this) {
    PermissionType.STORAGE -> PermissionSheetSpec(
        icon = Icons.Default.FolderOpen,
        title = stringResource(R.string.file_access),
        subtitle = stringResource(R.string.index_local_projects),
        detail = stringResource(R.string.amaya_reads_your_workspace_so_it_can_build_context_and_search_fil),
        systemFlow = stringResource(R.string.android_will_show_all_files_access_or_storage_permission_dialog),
        fallback = stringResource(R.string.you_can_always_change_this_later_in_system_app_settings),
        actionLabel = stringResource(R.string.grant)
    )
    PermissionType.CAMERA -> PermissionSheetSpec(
        icon = Icons.Default.CameraAlt,
        title = stringResource(R.string.camera_access),
        subtitle = stringResource(R.string.scan_remote_pairing_qr_codes),
        detail = stringResource(R.string.used_only_when_you_connect_to_the_ide_remotely),
        systemFlow = stringResource(R.string.android_asks_camera_permission_once_when_scan_is_requested),
        fallback = stringResource(R.string.you_can_disable_camera_any_time_if_you_stop_using_remote_qr),
        actionLabel = stringResource(R.string.grant)
    )
    PermissionType.NOTIFICATIONS -> PermissionSheetSpec(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.notifications),
        subtitle = stringResource(R.string.keep_task_updates_visible),
        detail = stringResource(R.string.used_for_background_jobs_reminders_and_completion_alerts),
        systemFlow = stringResource(R.string.android_requests_notification_permission_so_alerts_can_appear),
        fallback = stringResource(R.string.you_can_mute_channels_later_without_disabling_everything),
        actionLabel = stringResource(R.string.enable)
    )
    PermissionType.EXACT_ALARM -> PermissionSheetSpec(
        icon = Icons.Default.Alarm,
        title = stringResource(R.string.precise_alarms),
        subtitle = stringResource(R.string.run_time_based_actions_on_time),
        detail = stringResource(R.string.needed_for_scheduled_jobs_that_should_not_drift),
        systemFlow = stringResource(R.string.android_opens_alarm_settings_to_allow_exact_scheduling),
        fallback = stringResource(R.string.you_can_keep_it_off_if_you_do_not_use_scheduled_automations),
        actionLabel = stringResource(R.string.enable)
    )
    PermissionType.BATTERY_OPTIMIZATION -> PermissionSheetSpec(
        icon = Icons.Default.BatteryChargingFull,
        title = stringResource(R.string.battery_optimization),
        subtitle = stringResource(R.string.keep_background_work_alive),
        detail = stringResource(R.string.helps_amaya_stay_stable_during_long_syncs_and_local_tasks),
        systemFlow = stringResource(R.string.android_opens_battery_optimization_page_for_this_app),
        fallback = stringResource(R.string.you_can_re_enable_optimization_later_if_needed),
        actionLabel = stringResource(R.string.allow)
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
    StandardModalBottomSheet(
        onDismissRequest = onDismiss,
        title = spec.title
    ) {
        PermissionSheetBody(
            spec = spec,
            granted = false,
            onPrimary = { dismiss(onGrant) },
            onSecondary = { dismiss() },
            primaryLabel = spec.actionLabel,
            secondaryLabel = stringResource(R.string.maybe_later)
        )
    }
}
