package com.amaya.intelligence.ui.screens.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.ui.components.shared.PermissionSheetBody
import com.amaya.intelligence.ui.components.shared.PermissionSheetSpec
import com.amaya.intelligence.ui.components.shared.StandardModalBottomSheet
import com.amaya.intelligence.ui.components.shared.rememberLockedModalBottomSheetState

private val onboardingSpecs = listOf(
    PermissionSheetSpec(
        icon = Icons.Default.FolderOpen,
        title = "File Access",
        subtitle = "Index local projects.",
        detail = "Amaya reads your workspace so it can build context and search files locally.",
        systemFlow = "Android will show all-files access or storage permission dialog.",
        fallback = "You can always change this later in system app settings.",
        actionLabel = "Grant"
    ),
    PermissionSheetSpec(
        icon = Icons.Default.CameraAlt,
        title = "Camera Access",
        subtitle = "Scan remote pairing QR codes.",
        detail = "Used only when you connect to the IDE remotely.",
        systemFlow = "Android asks camera permission once when scan is requested.",
        fallback = "You can disable camera any time if you stop using remote QR.",
        actionLabel = "Grant"
    ),
    PermissionSheetSpec(
        icon = Icons.Default.Notifications,
        title = "Notifications",
        subtitle = "Keep task updates visible.",
        detail = "Used for background jobs, reminders, and completion alerts.",
        systemFlow = "Android requests notification permission so alerts can appear.",
        fallback = "You can mute channels later without disabling everything.",
        actionLabel = "Enable"
    ),
    PermissionSheetSpec(
        icon = Icons.Default.Alarm,
        title = "Precise Alarms",
        subtitle = "Run time-based actions on time.",
        detail = "Needed for scheduled jobs that should not drift.",
        systemFlow = "Android opens alarm settings to allow exact scheduling.",
        fallback = "You can keep it off if you do not use scheduled automations.",
        actionLabel = "Enable"
    ),
    PermissionSheetSpec(
        icon = Icons.Default.BatteryChargingFull,
        title = "Battery Optimization",
        subtitle = "Keep background work alive.",
        detail = "Helps Amaya stay stable during long syncs and local tasks.",
        systemFlow = "Android opens battery optimization page for this app.",
        fallback = "You can re-enable optimization later if needed.",
        actionLabel = "Allow"
    )
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    hasStoragePermission: Boolean,
    hasCameraPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasExactAlarmPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestStorage: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onFinish: () -> Unit
) {
    val sheetState = rememberLockedModalBottomSheetState()
    val totalSteps = onboardingSpecs.size + 1
    var currentStep by remember { mutableIntStateOf(0) }
    val isFinalStep = currentStep == totalSteps - 1
    val currentSpec = onboardingSpecs.getOrNull(currentStep)
    val currentGranted = when (currentStep) {
        0 -> hasStoragePermission
        1 -> hasCameraPermission
        2 -> hasNotificationPermission
        3 -> hasExactAlarmPermission
        4 -> isIgnoringBatteryOptimizations
        else -> false
    }

    LaunchedEffect(Unit) {
        sheetState.show()
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && currentStep == 0) {
            currentStep = 1
        }
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && currentStep == 1) {
            currentStep = 2
        }
    }
    LaunchedEffect(hasNotificationPermission) {
        if (hasNotificationPermission && currentStep == 2) {
            currentStep = 3
        }
    }
    LaunchedEffect(hasExactAlarmPermission) {
        if (hasExactAlarmPermission && currentStep == 3) {
            currentStep = 4
        }
    }
    LaunchedEffect(isIgnoringBatteryOptimizations) {
        if (isIgnoringBatteryOptimizations && currentStep == 4) {
            currentStep = 5
        }
    }

    StandardModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {},
        onCloseRequested = null,
        title = if (isFinalStep) "Start using Amaya" else currentSpec?.title ?: "Permission Request",
        icon = currentSpec?.icon ?: Icons.Default.FolderOpen,
        subtitle = if (isFinalStep) "Everything is ready." else currentSpec?.subtitle ?: "Grant each permission once.",
        showCloseButton = false,
        dismissible = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = "${currentStep + 1} / $totalSteps",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isFinalStep) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "All required permissions are already in place. Start using Amaya.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No extra checklist, no swipe gesture, no modal variation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                PermissionSheetBody(
                    spec = currentSpec!!,
                    granted = currentGranted,
                    onPrimary = {
                        if (currentGranted) {
                            currentStep = (currentStep + 1).coerceAtMost(totalSteps - 1)
                        } else {
                            when (currentStep) {
                                0 -> onRequestStorage()
                                1 -> onRequestCamera()
                                2 -> onRequestNotifications()
                                3 -> onRequestExactAlarm()
                                4 -> onRequestBatteryOptimization()
                            }
                        }
                    },
                    onSecondary = {
                        currentStep = (currentStep + 1).coerceAtMost(totalSteps - 1)
                    },
                    primaryLabel = if (currentGranted) "Next" else currentSpec.actionLabel,
                    secondaryLabel = "Skip"
                )
            }

            if (isFinalStep) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Setup complete.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "You can change any permission later from system settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = "Start using Amaya",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
