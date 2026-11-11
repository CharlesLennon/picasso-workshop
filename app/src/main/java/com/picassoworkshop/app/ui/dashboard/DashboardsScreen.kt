package com.picassoworkshop.app.ui.dashboard

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.dashboard.DashboardManager
import com.picassoworkshop.app.dashboard.RecordingSessionSummary
import com.picassoworkshop.app.dashboard.TelemetryRecorder
import com.picassoworkshop.app.dashboard.model.CustomDashboard
import com.picassoworkshop.app.dashboard.model.GaugeType
import com.picassoworkshop.app.obd.model.ObdConnectionState
import com.picassoworkshop.app.obd.ObdDiagnosticsCoordinator
import com.picassoworkshop.app.ui.dashboard.widgets.*
import com.picassoworkshop.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardsScreen(
    coordinator: ObdDiagnosticsCoordinator,
    dashboardManager: DashboardManager,
    telemetryRecorder: TelemetryRecorder,
    onBackClick: () -> Unit,
    onOpenObdScan: () -> Unit
) {
    val telemetry by coordinator.liveTelemetry.collectAsState()
    val connectionState by coordinator.connectionManager.connectionState.collectAsState()

    val dashboards by dashboardManager.dashboards.collectAsState()
    val activeDashboard by dashboardManager.activeDashboard.collectAsState()

    val isRecording by telemetryRecorder.isRecording.collectAsState()
    val recordedSamplesCount by telemetryRecorder.recordedSamplesCount.collectAsState()

    var showBuilderSheet by remember { mutableStateOf(false) }
    var dashboardToEdit by remember { mutableStateOf<CustomDashboard?>(null) }
    var recordingSummary by remember { mutableStateOf<RecordingSessionSummary?>(null) }

    // Start live telemetry polling on entry if connected, feed samples to recorder
    LaunchedEffect(Unit) {
        coordinator.startLiveTelemetryPolling()
    }

    LaunchedEffect(telemetry) {
        if (isRecording) {
            telemetryRecorder.recordSample(telemetry)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CITROËN",
                                color = CitroenRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CUSTOM DASHBOARDS",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Live Telemetry & Gauge Cluster • ${activeDashboard.title}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    // Record run button (🔴 REC / ⏹ STOP)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isRecording) CitroenRed.copy(alpha = 0.2f) else WorkshopSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRecording) CitroenRed else WorkshopBorder
                        ),
                        modifier = Modifier
                            .clickable {
                                if (isRecording) {
                                    val summary = telemetryRecorder.stopRecording()
                                    if (summary != null) {
                                        recordingSummary = summary
                                    }
                                } else {
                                    telemetryRecorder.startRecording()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isRecording) CitroenRed else TextSecondary,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text(
                                text = if (isRecording) "REC ($recordedSamplesCount)" else "LOG CSV",
                                color = if (isRecording) CitroenRed else TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Live Polling Play/Pause
                    IconButton(
                        onClick = {
                            // Toggle polling
                            coordinator.startLiveTelemetryPolling()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Live Polling",
                            tint = if (connectionState is ObdConnectionState.Connected) MintGreen else TextSecondary
                        )
                    }

                    // Jump to DTC OBD scanner
                    IconButton(onClick = onOpenObdScan) {
                        Icon(
                            imageVector = Icons.Default.Troubleshoot,
                            contentDescription = "OBD DTC Scanner",
                            tint = CyanAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WorkshopSurfaceElevated)
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
            // 1. Connection Status Banner
            item {
                Surface(
                    color = WorkshopSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (connectionState is ObdConnectionState.Connected) MintGreen.copy(alpha = 0.5f) else WorkshopBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = if (connectionState is ObdConnectionState.Connected) MintGreen else TextSecondary,
                                shape = CircleShape,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Column {
                                Text(
                                    text = when (connectionState) {
                                        is ObdConnectionState.Connected -> "ELM327 ADAPTER CONNECTED"
                                        is ObdConnectionState.Connecting -> "CONNECTING TO OBD ADAPTER..."
                                        else -> "DEMO SIMULATOR ACTIVE (CAN 500k)"
                                    },
                                    color = if (connectionState is ObdConnectionState.Connected) MintGreen else CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (connectionState is ObdConnectionState.Connected) {
                                        (connectionState as ObdConnectionState.Connected).deviceName
                                    } else {
                                        "Streaming simulated DV6 diesel sensor readings at 600ms rate"
                                    },
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (connectionState !is ObdConnectionState.Connected) {
                            TextButton(onClick = onOpenObdScan) {
                                Text("CONNECT", color = CyanAccent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // 2. Dashboards Carousel Selector Row + "➕ CREATE" Button
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SELECT CLUSTER DASHBOARD",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )

                        // ➕ CREATE DASHBOARD Action Button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CyanAccent,
                            modifier = Modifier.clickable {
                                dashboardToEdit = null
                                showBuilderSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "CREATE DASHBOARD",
                                    color = DarkBackground,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Scrolling chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dashboards.forEach { d ->
                            val isSelected = d.id == activeDashboard.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) CyanAccent else WorkshopSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) CyanAccent else WorkshopBorder
                                ),
                                modifier = Modifier.clickable {
                                    dashboardManager.selectDashboard(d.id)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (d.isPreset) Icons.Default.Speed else Icons.Default.DashboardCustomize,
                                        contentDescription = null,
                                        tint = if (isSelected) DarkBackground else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = d.title,
                                        color = if (isSelected) DarkBackground else TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    if (d.isPreset) {
                                        Surface(
                                            color = if (isSelected) DarkBackground.copy(alpha = 0.2f) else WorkshopBorder,
                                            shape = RoundedCornerShape(3.dp)
                                        ) {
                                            Text(
                                                text = "PRESET",
                                                color = if (isSelected) DarkBackground else TextSecondary,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Active Dashboard Header Card (Title, description, edit & delete buttons)
            item {
                Surface(
                    color = WorkshopSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeDashboard.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (activeDashboard.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = activeDashboard.description,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (activeDashboard.isEditable) {
                                    IconButton(
                                        onClick = {
                                            dashboardToEdit = activeDashboard
                                            showBuilderSheet = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            dashboardManager.deleteDashboard(activeDashboard.id)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = CitroenRed, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    // Clone Preset as Custom Dashboard
                                    TextButton(
                                        onClick = {
                                            val clone = dashboardManager.createDashboard(
                                                title = "${activeDashboard.title} (Custom)",
                                                description = activeDashboard.description,
                                                widgets = activeDashboard.widgets
                                            )
                                            dashboardToEdit = clone
                                            showBuilderSheet = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CLONE", fontSize = 10.sp, color = CyanAccent, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Render Active Dashboard Gauges Grid
            if (activeDashboard.widgets.isEmpty()) {
                item {
                    Surface(
                        color = WorkshopSurface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No gauges configured for this dashboard", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Tap Edit or Create Dashboard to add speed, RPM, boost or rail pressure gauges.", color = TextSecondary, fontSize = 11.5.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    dashboardToEdit = activeDashboard
                                    showBuilderSheet = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                            ) {
                                Text("CONFIGURE GAUGES", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(activeDashboard.widgets, key = { it.id }) { widget ->
                    val currentValue = telemetry.getValueForPid(widget.pidId)

                    when (widget.gaugeType) {
                        GaugeType.RADIAL_DIAL -> {
                            RadialDialGaugeCard(config = widget, currentValue = currentValue)
                        }
                        GaugeType.TREND_GRAPH -> {
                            TrendGraphCard(config = widget, currentValue = currentValue)
                        }
                        GaugeType.BAR_GAUGE -> {
                            BarGaugeCard(config = widget, currentValue = currentValue)
                        }
                        GaugeType.DIGITAL_CARD -> {
                            DigitalReadoutCard(config = widget, currentValue = currentValue)
                        }
                        GaugeType.STATUS_LIGHT -> {
                            StatusIndicatorCard(config = widget, currentValue = currentValue)
                        }
                    }
                }
            }
        }
    }

    // Dashboard Builder Modal Bottom Sheet
    if (showBuilderSheet) {
        DashboardBuilderSheet(
            initialDashboard = dashboardToEdit,
            onDismiss = { showBuilderSheet = false },
            onSaveDashboard = { title, desc, widgets ->
                if (dashboardToEdit != null) {
                    dashboardManager.saveDashboard(dashboardToEdit!!.copy(title = title, description = desc, widgets = widgets))
                } else {
                    dashboardManager.createDashboard(title = title, description = desc, widgets = widgets)
                }
                showBuilderSheet = false
            }
        )
    }

    // Telemetry Recording Completed Summary Dialog
    if (recordingSummary != null) {
        val summary = recordingSummary!!
        AlertDialog(
            onDismissRequest = { recordingSummary = null },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintGreen, modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    text = "TELEMETRY RUN SAVED",
                    color = MintGreen,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Successfully logged ${summary.sampleCount} live sensor samples over ${summary.durationSeconds} seconds.",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = DarkBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Peak Boost: ${String.format("%.2f", summary.maxBoostBar)} Bar", color = GoldenBolt, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("• Peak Rail: ${summary.maxRailPressureBar} Bar", color = MintGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("• Max RPM: ${summary.maxRpm} RPM", color = CyanAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("• Top Speed: ${summary.maxSpeed} km/h", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        telemetryRecorder.shareCsvFile(summary.file)
                        recordingSummary = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE CSV LOG", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingSummary = null }) {
                    Text("CLOSE", color = TextSecondary)
                }
            },
            containerColor = WorkshopSurfaceElevated
        )
    }
}
