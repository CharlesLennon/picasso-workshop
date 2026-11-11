package com.picassoworkshop.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.dashboard.model.*
import com.picassoworkshop.app.ui.theme.*
import java.util.UUID

/**
 * Bottom Sheet modal for creating a new custom dashboard or editing an existing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardBuilderSheet(
    initialDashboard: CustomDashboard? = null,
    onDismiss: () -> Unit,
    onSaveDashboard: (title: String, description: String, widgets: List<DashboardWidgetConfig>) -> Unit
) {
    var title by remember { mutableStateOf(initialDashboard?.title ?: "") }
    var description by remember { mutableStateOf(initialDashboard?.description ?: "") }
    var configuredWidgets by remember {
        mutableStateOf(initialDashboard?.widgets ?: listOf(
            DashboardWidgetConfig(
                id = UUID.randomUUID().toString(),
                pidId = "ENGINE_RPM",
                gaugeType = GaugeType.RADIAL_DIAL,
                minVal = 0.0,
                maxVal = 5000.0,
                warnThreshold = 4200.0,
                dangerThreshold = 4600.0
            ),
            DashboardWidgetConfig(
                id = UUID.randomUUID().toString(),
                pidId = "BOOST_PRESSURE",
                gaugeType = GaugeType.TREND_GRAPH,
                minVal = 0.0,
                maxVal = 2.5,
                warnThreshold = 1.4,
                dangerThreshold = 1.6
            )
        ))
    }

    var showAddSensorDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WorkshopSurfaceElevated,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = CyanAccent,
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = DarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = if (initialDashboard != null) "EDIT DASHBOARD" else "CREATE CUSTOM DASHBOARD",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Dashboard Name & Info
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Dashboard Title (e.g. Turbo & Rail Pressure)") },
                        placeholder = { Text("My Custom Cluster") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = WorkshopBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Specific gauges for testing Garrett turbo boost...") },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = WorkshopBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 2. Configured Widgets Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DASHBOARD GAUGES (${configuredWidgets.size})",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        TextButton(
                            onClick = { showAddSensorDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MintGreen)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD GAUGE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }

                // List of Widgets
                itemsIndexed(configuredWidgets, key = { _, w -> w.id }) { index, widget ->
                    val meta = PidCatalog.getById(widget.pidId)
                    Surface(
                        color = WorkshopSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        color = CyanAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            color = CyanAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = widget.customTitle ?: meta?.displayName ?: widget.pidId,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        configuredWidgets = configuredWidgets.filter { it.id != widget.id }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove", tint = CitroenRed, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Gauge Type Selector Chips
                            Text("Display Style:", color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GaugeType.values().forEach { gType ->
                                    val isSelected = widget.gaugeType == gType
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) CyanAccent else WorkshopSurfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(
                                            0.5.dp,
                                            if (isSelected) CyanAccent else WorkshopBorder
                                        ),
                                        modifier = Modifier.clickable {
                                            configuredWidgets = configuredWidgets.map {
                                                if (it.id == widget.id) it.copy(gaugeType = gType) else it
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = gType.displayName,
                                            color = if (isSelected) DarkBackground else TextSecondary,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSaveDashboard(title, description, configuredWidgets)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = title.isNotBlank() && configuredWidgets.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = DarkBackground)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SAVE DASHBOARD",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
    }

    // Add Sensor Picker Dialog
    if (showAddSensorDialog) {
        AlertDialog(
            onDismissRequest = { showAddSensorDialog = false },
            title = {
                Text(
                    text = "SELECT SENSOR / PID",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PidCatalog.ALL_PIDS.size) { idx ->
                        val pid = PidCatalog.ALL_PIDS[idx]
                        Surface(
                            color = WorkshopSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newWidget = DashboardWidgetConfig(
                                        id = UUID.randomUUID().toString(),
                                        pidId = pid.id,
                                        gaugeType = pid.recommendedGauge,
                                        minVal = pid.defaultMin,
                                        maxVal = pid.defaultMax,
                                        warnThreshold = pid.defaultWarn,
                                        dangerThreshold = pid.defaultDanger
                                    )
                                    configuredWidgets = configuredWidgets + newWidget
                                    showAddSensorDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = pid.displayName, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "${pid.category.title} • Standard: ${pid.standardCommand}", color = CyanAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    Text(text = pid.description, color = TextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Surface(
                                    color = MintGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = pid.defaultUnit,
                                        color = MintGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSensorDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = WorkshopSurfaceElevated
        )
    }
}
