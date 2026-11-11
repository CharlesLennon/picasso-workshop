package com.picassoworkshop.app.ui.obd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.obd.BluetoothConnectionManager
import com.picassoworkshop.app.obd.ObdDiagnosticsCoordinator
import com.picassoworkshop.app.obd.model.*
import com.picassoworkshop.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdDiagnosticsScreen(
    coordinator: ObdDiagnosticsCoordinator,
    onBackClick: () -> Unit,
    onOpenProcedure: (String) -> Unit
) {
    val connectionState by coordinator.connectionManager.connectionState.collectAsState()
    val availableDevices by coordinator.connectionManager.availableDevices.collectAsState()
    val detectedFaults by coordinator.detectedFaults.collectAsState()
    val liveTelemetry by coordinator.liveTelemetry.collectAsState()
    val isScanning by coordinator.isScanning.collectAsState()

    var selectedScanMode by remember { mutableStateOf(ScanMode.DEEP_PSA_MULTI_ECU) }
    var selectedEcuFilter by remember { mutableStateOf<EcuTarget?>(null) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isLiveTelemetryActive by remember { mutableStateOf(false) }

    val isConnected = connectionState is ObdConnectionState.Connected

    // Start/stop telemetry when toggle changes
    LaunchedEffect(isLiveTelemetryActive, isConnected) {
        if (isLiveTelemetryActive && isConnected) {
            coordinator.startLiveTelemetryPolling()
        } else {
            coordinator.stopLiveTelemetryPolling()
        }
    }

    val filteredFaults = remember(detectedFaults, selectedEcuFilter) {
        if (selectedEcuFilter == null) detectedFaults else detectedFaults.filter { it.ecu == selectedEcuFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OBD-II DIAGNOSTICS",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = when (connectionState) {
                                    is ObdConnectionState.Connected -> MintGreen.copy(alpha = 0.2f)
                                    is ObdConnectionState.Connecting, is ObdConnectionState.Initializing -> GoldenBolt.copy(alpha = 0.2f)
                                    is ObdConnectionState.Error -> CitroenRed.copy(alpha = 0.2f)
                                    else -> WorkshopBorder.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (connectionState) {
                                        is ObdConnectionState.Connected -> MintGreen
                                        is ObdConnectionState.Connecting, is ObdConnectionState.Initializing -> GoldenBolt
                                        is ObdConnectionState.Error -> CitroenRed
                                        else -> WorkshopBorder
                                    }
                                )
                            ) {
                                Text(
                                    text = when (connectionState) {
                                        is ObdConnectionState.Connected -> if (coordinator.connectionManager.isSimulationMode) "SIMULATOR" else "ONLINE"
                                        is ObdConnectionState.Connecting -> "CONNECTING"
                                        is ObdConnectionState.Initializing -> "INITIALIZING"
                                        is ObdConnectionState.Error -> "ERROR"
                                        else -> "DISCONNECTED"
                                    },
                                    color = when (connectionState) {
                                        is ObdConnectionState.Connected -> MintGreen
                                        is ObdConnectionState.Connecting, is ObdConnectionState.Initializing -> GoldenBolt
                                        is ObdConnectionState.Error -> CitroenRed
                                        else -> TextSecondary
                                    },
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "ELM327 Bluetooth • PSA DiagOnCan Multi-ECU",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { coordinator.connectionManager.refreshPairedDevices(); showDevicePicker = true }) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Select Bluetooth Device", tint = CyanAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WorkshopSurface)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Connection Card
            item {
                DeviceConnectionCard(
                    connectionState = connectionState,
                    isSimulationMode = coordinator.connectionManager.isSimulationMode,
                    onSelectDeviceClick = {
                        coordinator.connectionManager.refreshPairedDevices()
                        showDevicePicker = true
                    },
                    onDisconnectClick = { coordinator.connectionManager.disconnect() }
                )
            }

            // 2. Live Sensor Gauges (When Connected)
            if (isConnected) {
                item {
                    LiveGaugesStrip(
                        telemetry = liveTelemetry,
                        isActive = isLiveTelemetryActive,
                        onToggleActive = { isLiveTelemetryActive = !isLiveTelemetryActive }
                    )
                }

                // 3. Scan Controller Card
                item {
                    ScanControllerCard(
                        selectedMode = selectedScanMode,
                        isScanning = isScanning,
                        faultCount = detectedFaults.size,
                        onModeChange = { selectedScanMode = it },
                        onScanClick = { coordinator.scanForFaults(selectedScanMode) },
                        onClearClick = { showClearConfirmDialog = true }
                    )
                }

                // 4. ECU Filter Chips
                if (detectedFaults.isNotEmpty()) {
                    item {
                        EcuFilterChipsRow(
                            faults = detectedFaults,
                            selectedFilter = selectedEcuFilter,
                            onFilterSelect = { selectedEcuFilter = it }
                        )
                    }
                }
            }

            // 5. Faults List or Empty State
            if (filteredFaults.isEmpty()) {
                item {
                    EmptyFaultsCard(
                        isConnected = isConnected,
                        isScanning = isScanning,
                        onConnectClick = {
                            coordinator.connectionManager.refreshPairedDevices()
                            showDevicePicker = true
                        },
                        onScanClick = { coordinator.scanForFaults(selectedScanMode) }
                    )
                }
            } else {
                items(filteredFaults, key = { it.fullCodeDisplay }) { fault ->
                    DiagnosticFaultCard(
                        fault = fault,
                        onOpenProcedure = onOpenProcedure
                    )
                }
            }
        }
    }

    // Bluetooth Device Picker Bottom Sheet
    if (showDevicePicker) {
        BluetoothDeviceSheet(
            devices = availableDevices,
            onDismiss = { showDevicePicker = false },
            onSelectDevice = { device ->
                showDevicePicker = false
                coordinator.connectionManager.connectToDevice(device)
            }
        )
    }

    // Clear Faults Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("Clear Diagnostic Trouble Codes?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will send Mode 04 and PSA UDS 14 FF 00 commands to erase DTCs across the Engine ECU, BSI, Air Suspension, and EGS6 Gearbox. Make sure ignition is ON with engine stopped.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        coordinator.clearAllFaults { _ ->
                            // Cleared
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CitroenRed)
                ) {
                    Text("CLEAR ALL DTCS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = WorkshopSurfaceElevated
        )
    }
}

@Composable
fun DeviceConnectionCard(
    connectionState: ObdConnectionState,
    isSimulationMode: Boolean,
    onSelectDeviceClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (connectionState is ObdConnectionState.Connected) MintGreen.copy(alpha = 0.6f) else WorkshopBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connectionState is ObdConnectionState.Connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (connectionState is ObdConnectionState.Connected) MintGreen else CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (connectionState) {
                                    is ObdConnectionState.Connected -> connectionState.deviceName
                                    is ObdConnectionState.Connecting -> "Connecting to ${connectionState.deviceName}..."
                                    is ObdConnectionState.Initializing -> connectionState.step
                                    is ObdConnectionState.Error -> "Connection Failed"
                                    else -> "No OBD Adapter Connected"
                                },
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSimulationMode && connectionState is ObdConnectionState.Connected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MintGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "DEMO SIMULATOR",
                                        color = MintGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (connectionState) {
                                is ObdConnectionState.Connected -> "${connectionState.elmVersion} • Protocol: CAN 500k"
                                is ObdConnectionState.Error -> connectionState.message
                                else -> "Tap to choose paired ELM327 adapter or Demo Simulator"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (connectionState is ObdConnectionState.Connected) {
                    Button(
                        onClick = onDisconnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = WorkshopSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("DISCONNECT", color = CitroenRed, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSelectDeviceClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CONNECT", color = DarkBackground, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveGaugesStrip(
    telemetry: LiveTelemetry,
    isActive: Boolean,
    onToggleActive: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE SENSOR TELEMETRY",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isActive) "STREAMING" else "PAUSED",
                        color = if (isActive) MintGreen else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MintGreen,
                            checkedTrackColor = MintGreen.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5 Gauges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GaugeMiniCard(
                    title = "BATTERY",
                    value = "${telemetry.batteryVoltage} V",
                    color = if (telemetry.batteryVoltage in 13.5f..14.8f) MintGreen else if (telemetry.batteryVoltage > 0f) GoldenBolt else TextSecondary
                )

                GaugeMiniCard(
                    title = "COOLANT",
                    value = "${telemetry.coolantTempC} °C",
                    color = if (telemetry.coolantTempC in 80..95) MintGreen else GoldenBolt
                )

                GaugeMiniCard(
                    title = "ENGINE RPM",
                    value = "${telemetry.rpm}",
                    color = CyanAccent
                )

                GaugeMiniCard(
                    title = "BOOST",
                    value = "${String.format("%.2f", telemetry.boostPressureBar)} Bar",
                    color = GoldenBolt
                )

                GaugeMiniCard(
                    title = "RAIL PRESSURE",
                    value = "${telemetry.railPressureBar} Bar",
                    color = MintGreen
                )
            }
        }
    }
}

@Composable
fun GaugeMiniCard(title: String, value: String, color: Color) {
    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, color = TextSecondary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ScanControllerCard(
    selectedMode: ScanMode,
    isScanning: Boolean,
    faultCount: Int,
    onModeChange: (ScanMode) -> Unit,
    onScanClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CitroenRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Mode Selectors
            Text(
                text = "SCAN TARGET ARCHITECTURE",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = if (selectedMode == ScanMode.DEEP_PSA_MULTI_ECU) CitroenRed.copy(alpha = 0.2f) else WorkshopSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedMode == ScanMode.DEEP_PSA_MULTI_ECU) CitroenRed else WorkshopBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeChange(ScanMode.DEEP_PSA_MULTI_ECU) }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "🦁 DEEP PSA MULTI-ECU",
                            color = if (selectedMode == ScanMode.DEEP_PSA_MULTI_ECU) CitroenRed else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Engine (7E0) + BSI (752) + Air Suspension (7A0) + EGS6",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                Surface(
                    color = if (selectedMode == ScanMode.GENERIC_POWERTRAIN) CyanAccent.copy(alpha = 0.2f) else WorkshopSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selectedMode == ScanMode.GENERIC_POWERTRAIN) CyanAccent else WorkshopBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeChange(ScanMode.GENERIC_POWERTRAIN) }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "🚀 QUICK GENERIC",
                            color = if (selectedMode == ScanMode.GENERIC_POWERTRAIN) CyanAccent else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Standard Mode 03 / 07 powertrain broadcast (7DF)",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onScanClick,
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = CitroenRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SCANNING ECUS...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCAN FAULT CODES", fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }

                if (faultCount > 0) {
                    Button(
                        onClick = onClearClick,
                        colors = ButtonDefaults.buttonColors(containerColor = WorkshopSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR DTCS", color = WarningOrange, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EcuFilterChipsRow(
    faults: List<DiagnosticFault>,
    selectedFilter: EcuTarget?,
    onFilterSelect: (EcuTarget?) -> Unit
) {
    val ecusPresent = remember(faults) {
        faults.map { it.ecu }.distinct()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selectedFilter == null) CyanAccent else WorkshopSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedFilter == null) CyanAccent else WorkshopBorder),
            modifier = Modifier.clickable { onFilterSelect(null) }
        ) {
            Text(
                text = "ALL FAULTS (${faults.size})",
                color = if (selectedFilter == null) DarkBackground else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        ecusPresent.forEach { ecu ->
            val isSelected = selectedFilter == ecu
            val count = faults.count { it.ecu == ecu }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) CyanAccent else WorkshopSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) CyanAccent else WorkshopBorder),
                modifier = Modifier.clickable { onFilterSelect(ecu) }
            ) {
                Text(
                    text = "${ecu.displayName.take(12)} ($count)",
                    color = if (isSelected) DarkBackground else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun DiagnosticFaultCard(
    fault: DiagnosticFault,
    onOpenProcedure: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (fault.severity) {
                DtcSeverity.CRITICAL -> CitroenRed.copy(alpha = 0.8f)
                DtcSeverity.WARNING -> GoldenBolt.copy(alpha = 0.7f)
                DtcSeverity.INFO -> CyanAccent.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Code, Severity badge, ECU badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = fault.fullCodeDisplay,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )

                    Surface(
                        color = BlueprintGrid.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                    ) {
                        Text(
                            text = fault.ecu.code,
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val sevColor = when (fault.severity) {
                    DtcSeverity.CRITICAL -> CitroenRed
                    DtcSeverity.WARNING -> GoldenBolt
                    DtcSeverity.INFO -> CyanAccent
                }

                Surface(
                    color = sevColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, sevColor)
                ) {
                    Text(
                        text = fault.severity.label,
                        color = sevColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = fault.title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Technical Description
            Text(
                text = fault.technicalDescription,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Common Causes
            if (fault.commonCauses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = WorkshopSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "COMMON PSA CAUSES:",
                            color = GoldenBolt,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        fault.commonCauses.forEach { cause ->
                            Text(
                                text = "• $cause",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Direct link to PicassoWorkshop repair procedure!
            fault.procedureIdLink?.let { procId ->
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onOpenProcedure(procId) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIEW STEP-BY-STEP REPAIR GUIDE & 3D MODEL",
                        color = DarkBackground,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyFaultsCard(
    isConnected: Boolean,
    isScanning: Boolean,
    onConnectClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (!isConnected) Icons.Default.BluetoothDisabled else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (!isConnected) TextSecondary else MintGreen,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (!isConnected) "No OBD Adapter Connected" else if (isScanning) "Scanning Vehicle ECUs..." else "No Active Fault Codes Found",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!isConnected) {
                    "Connect your ELM327 Bluetooth OBD-II adapter plugged into the 16-pin socket behind the lower glovebox."
                } else if (isScanning) {
                    "Querying Engine Management (7E0), BSI Body (752), and Air Suspension (7A0)..."
                } else {
                    "Vehicle systems report healthy status. Run a Deep PSA Multi-ECU scan to query secondary comfort & suspension controllers."
                },
                color = TextSecondary,
                fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))
            if (!isConnected) {
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("CONNECT ELM327 ADAPTER", color = DarkBackground, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            } else if (!isScanning) {
                Button(
                    onClick = onScanClick,
                    colors = ButtonDefaults.buttonColors(containerColor = WorkshopSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("START DIAGNOSTIC SCAN", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceSheet(
    devices: List<BluetoothDeviceInfo>,
    onDismiss: () -> Unit,
    onSelectDevice: (BluetoothDeviceInfo) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WorkshopSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "SELECT OBD-II ADAPTER",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select your paired ELM327 Bluetooth adapter or test with the built-in C4 Picasso simulator.",
                color = TextSecondary,
                fontSize = 11.5.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            devices.forEach { device ->
                val isDemo = device.address == BluetoothConnectionManager.DEMO_DEVICE_ADDRESS
                Surface(
                    color = if (isDemo) MintGreen.copy(alpha = 0.12f) else WorkshopSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDemo) MintGreen else WorkshopBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectDevice(device) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = device.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isDemo) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MintGreen.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "BUILT-IN DEMO",
                                            color = MintGreen,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = device.address,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (isDemo) MintGreen else TextSecondary
                        )
                    }
                }
            }
        }
    }
}
