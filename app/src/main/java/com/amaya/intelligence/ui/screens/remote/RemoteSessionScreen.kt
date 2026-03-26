package com.amaya.intelligence.ui.screens.remote

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import com.amaya.intelligence.domain.models.ConnectionState
import com.amaya.intelligence.impl.ide.antigravity.client.RemoteSessionClient
import com.amaya.intelligence.impl.ide.IdeProviderFactory

import androidx.compose.ui.graphics.Brush
import com.amaya.intelligence.ui.theme.LocalAmayaGradients
import com.amaya.intelligence.ui.components.shared.SettingsBackButton
import com.amaya.intelligence.ui.res.UiStrings
import com.amaya.intelligence.ui.res.UiDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.amaya.intelligence.impl.common.mappers.RemoteIdeIcon

/**
 * Remote Session screen — IDE selector + IP/Port input.
 *
 * If already connected: shows connected status with disconnect button (no IP/Port).
 * If disconnected: shows IDE list → IP + Port → Connect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSessionScreen(
    client: RemoteSessionClient,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    val connectionState by client.connectionState.collectAsState()
    val errorMessage by client.errorMessage.collectAsState()
    val serverInfo by client.serverInfo.collectAsState()
    val gradients = LocalAmayaGradients.current
    val isDark = isSystemInDarkTheme()

    var ipAddress by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("8765") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Currently only Antigravity — expand in the future
    var selectedIde by remember { mutableStateOf<String?>(null) }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING

    // Auto-navigate to chat screen when connected
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }
    var showConnectionSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Standard spacing for the transparent header
                Spacer(Modifier.statusBarsPadding().height(56.dp))

                // ── Connected state: minimal view ────────────────────
                if (isConnected) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF34C759).copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color(0xFF34C759).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                val providerName = selectedIde?.let { IdeProviderFactory.getIdeInfo(it)?.displayName }
                                    ?: UiStrings.App.REMOTE_NAME
                                Text(
                                    "${UiStrings.Connection.CONNECTED_TO} $providerName",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ws://${serverInfo ?: ""}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraLight),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onConnected() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Chat, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(UiStrings.Connection.OPEN_CHAT)
                            }
                        }
                    }
                } else {
                    // ── Disconnected state: IDE selector + IP/Port ───────

                    // Error message
                    errorMessage?.let { error ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    val sectionTitleColor = if (isSystemInDarkTheme()) Color(0xFF98989D) else Color(0xFF8E8E93)
                    // 1. IDE Selection
                    Text(
                        UiStrings.Connection.REMOTE_CONNECTION,
                        style = MaterialTheme.typography.labelLarge,
                        color = sectionTitleColor,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    val allIdes = remember { IdeProviderFactory.getAll().filter { it.info.capabilities.requiresConnection } }
                    allIdes.forEachIndexed { index, provider ->
                        val info = provider.info
                        val iconSpec = RemoteIdeIcon.resolve(info.id, isDark)
                        IdeCard(
                            name = info.displayName,
                            description = info.description,
                            iconSpec = iconSpec,
                            isSelected = selectedIde == info.id,
                            enabled = provider.isEnabled,
                            onClick = { 
                                selectedIde = info.id
                                ipAddress = info.defaultIpPrefix
                                port = info.defaultPort.toString()
                                showConnectionSheet = true
                            }
                        )
                    }

                    Spacer(Modifier.height(100.dp))
                }
            }

            // Scrims
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .align(Alignment.TopCenter)
                    .background(gradients.topScrim)
            )

            // Header Overlay
            TopAppBar(
                title = { 
                    Text(
                        UiStrings.Connection.REMOTE_SESSION, 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    ) 
                },
                navigationIcon = {
                    SettingsBackButton(onClick = onBack)
                },
                actions = {
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                .clickable { client.disconnect() }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LinkOff, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(6.dp))
                                Text(UiStrings.Connection.DISCONNECT, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        // Empty box to maintain symmetry/spacing if needed, 
                        // but usually there's no right-side button in disconnected state now
                        Spacer(Modifier.width(48.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp),
                windowInsets = WindowInsets(0.dp)
            )

            if (showConnectionSheet) {
                ConnectionSetupSheet(
                    ipAddress = ipAddress,
                    onIpChange = { ipAddress = it },
                    port = port,
                    onPortChange = { port = it },
                    isConnecting = isConnecting,
                    onConnect = {
                        showConnectionSheet = false
                        client.connect(ipAddress, port.toIntOrNull() ?: 8765)
                    },
                    onDismiss = { showConnectionSheet = false }
                )
            }
        }
    }
}

@Composable
private fun IdeCard(
    name: String,
    description: String,
    iconSpec: RemoteIdeIcon.Spec?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.85f else 0.72f)
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ) else null,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.85f else 1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    iconSpec?.tintable == true -> MaterialTheme.colorScheme.onSurface
                    else -> Color.Unspecified
                }
                when {
                    iconSpec?.resId != null -> Icon(
                        painter = painterResource(id = iconSpec.resId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                    iconSpec?.imageVector != null -> Icon(
                        imageVector = iconSpec.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                    else -> Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraLight),
                    color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!enabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ) {
                    Text(
                        "Soon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun QrScannerOverlay(
    onScan: (String) -> Unit,
    onClose: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            QrScannerView(onScan = onScan)

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }

            // Instructions
            Text(
                "Scan the QR code in VS Code",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
            
            // Scanner frame simulation
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.Center)
                    .background(Color.Transparent, RoundedCornerShape(12.dp))
                    .then(Modifier.border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp)))
            )
        }
    }
}

@Composable
fun QrScannerView(onScan: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let { code ->
                                    if (code.startsWith("amaya://")) {
                                        onScan(code)
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    android.util.Log.e("QrScanner", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSetupSheet(
    ipAddress: String,
    onIpChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var showScanner by remember { mutableStateOf(false) }
    val sheetScrollState = rememberScrollState()
    val maxSheetHeight = 0.75f * androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            target != SheetValue.Hidden
        }
    )
    val isDark = isSystemInDarkTheme()
    val statusBarPx = WindowInsets.statusBars.getTop(androidx.compose.ui.platform.LocalDensity.current)
    val cornerSize by remember {
        derivedStateOf {
            val offset = try { sheetState.requireOffset() } catch (e: Exception) { 1000f }
            if (offset <= statusBarPx + 1f) 0.dp else 28.dp
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .verticalScroll(sheetScrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(UiStrings.Connection.CONNECTION_SETUP, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                .compositeOver(MaterialTheme.colorScheme.background)
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(20.dp))
                }
            }

            if (showScanner) {
                QrScannerOverlay(
                    onScan = { data ->
                        val uri = android.net.Uri.parse(data)
                        uri.getQueryParameter("ip")?.let { onIpChange(it) }
                        uri.getQueryParameter("port")?.let { onPortChange(it) }
                        showScanner = false
                    },
                    onClose = { showScanner = false }
                )
            }

            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpChange, // Wait, user code has onIpChange here. I'll fix.
                label = { Text(UiStrings.Connection.IP_ADDRESS) },
                placeholder = { Text(UiStrings.Placeholders.IP_EXAMPLE) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Wifi, null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    }
                },
                enabled = !isConnecting
            )

            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text(UiStrings.Connection.PORT) },
                placeholder = { Text(UiStrings.Placeholders.PORT_EXAMPLE) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Adjust, null, modifier = Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isConnecting
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = ipAddress.isNotBlank() && port.isNotBlank() && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(UiStrings.Connection.CONNECT, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

