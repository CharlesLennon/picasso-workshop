package com.picassoworkshop.app.ui.home

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
import com.picassoworkshop.app.data.DataUpdateState
import com.picassoworkshop.app.data.model.DifficultyLevel
import com.picassoworkshop.app.data.model.ProcedureCategory
import com.picassoworkshop.app.data.model.RepairProcedure
import com.picassoworkshop.app.data.model.VehicleSelection
import com.picassoworkshop.app.ui.theme.*
import com.picassoworkshop.app.update.AppUpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProceduresListScreen(
    currentSelection: VehicleSelection,
    procedures: List<RepairProcedure>,
    dataVersion: String,
    appVersion: String,
    dataUpdateState: DataUpdateState,
    appUpdateState: AppUpdateState,
    onSwitchVehicleClick: () -> Unit,
    onProcedureClick: (RepairProcedure) -> Unit,
    onCheckDataUpdatesClick: () -> Unit,
    onCheckAppUpdatesClick: () -> Unit,
    onApplyDataUpdateClick: (String) -> Unit,
    onDownloadAppUpdateClick: () -> Unit,
    onInstallAppUpdateClick: () -> Unit,
    onDismissDataUpdate: () -> Unit,
    onDismissAppUpdate: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = remember(procedures) {
        listOf("ALL") + procedures.map { it.category.title }.distinct()
    }

    val filteredProcedures = remember(procedures, searchQuery, selectedCategory) {
        procedures.filter { proc ->
            val matchesCategory = selectedCategory == "ALL" || proc.category.title.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                proc.title.contains(searchQuery, ignoreCase = true) ||
                        proc.summary.contains(searchQuery, ignoreCase = true) ||
                        proc.category.title.contains(searchQuery, ignoreCase = true) ||
                        proc.explodedParts.any { it.name.contains(searchQuery, ignoreCase = true) }
            }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
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
                                text = "C4 GRAND PICASSO",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Interactive Workshop Companion • v$appVersion (Data $dataVersion)",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                actions = {
                    // Data update button / badge
                    IconButton(onClick = onCheckDataUpdatesClick) {
                        BadgedBox(
                            badge = {
                                if (dataUpdateState is DataUpdateState.UpdateAvailable) {
                                    Badge(containerColor = CyanAccent) {
                                        Text("NEW", fontSize = 8.sp, color = DarkBackground)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Check Data Updates",
                                tint = if (dataUpdateState is DataUpdateState.Checking) CyanAccent else TextPrimary
                            )
                        }
                    }

                    // App APK update button / badge
                    IconButton(onClick = onCheckAppUpdatesClick) {
                        BadgedBox(
                            badge = {
                                if (appUpdateState is AppUpdateState.UpdateAvailable || appUpdateState is AppUpdateState.ReadyToInstall) {
                                    Badge(containerColor = CitroenRed) {
                                        Text("!", fontSize = 8.sp, color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Check App Updates",
                                tint = if (appUpdateState is AppUpdateState.Checking) CitroenRed else TextPrimary
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Update Alerts Banner (if available)
            if (dataUpdateState is DataUpdateState.UpdateAvailable) {
                item {
                    DataUpdateBanner(
                        state = dataUpdateState,
                        onUpdate = { onApplyDataUpdateClick(dataUpdateState.downloadUrl) },
                        onDismiss = onDismissDataUpdate
                    )
                }
            }

            if (appUpdateState is AppUpdateState.UpdateAvailable) {
                item {
                    AppUpdateBanner(
                        state = appUpdateState,
                        onDownload = onDownloadAppUpdateClick,
                        onDismiss = onDismissAppUpdate
                    )
                }
            } else if (appUpdateState is AppUpdateState.ReadyToInstall) {
                item {
                    AppInstallBanner(
                        state = appUpdateState,
                        onInstall = onInstallAppUpdateClick,
                        onDismiss = onDismissAppUpdate
                    )
                }
            }

            // Active Vehicle Selector Card
            item {
                ActiveVehicleCard(
                    selection = currentSelection,
                    onSwitchClick = onSwitchVehicleClick
                )
            }

            // Search Bar & Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Search alternator, belt, filter, torque...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = WorkshopSurface,
                            unfocusedContainerColor = WorkshopSurface,
                            focusedBorderColor = CitroenRed,
                            unfocusedBorderColor = WorkshopBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // Category chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { categoryName ->
                            val isSelected = selectedCategory.equals(categoryName, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CitroenRed else WorkshopSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) CitroenRed else WorkshopBorder
                                ),
                                modifier = Modifier.clickable { selectedCategory = categoryName }
                            ) {
                                Text(
                                    text = categoryName.uppercase(),
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Header: Available Procedures count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WORKSHOP PROCEDURES (${filteredProcedures.size})",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "BLOWN-OUT & 3D READY",
                        color = MintGreen,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Procedures items
            if (filteredProcedures.isEmpty()) {
                item {
                    EmptyProceduresCard(
                        searchQuery = searchQuery,
                        onReset = {
                            searchQuery = ""
                            selectedCategory = "ALL"
                        }
                    )
                }
            } else {
                items(filteredProcedures, key = { it.id }) { procedure ->
                    ProcedureCard(
                        procedure = procedure,
                        onClick = { onProcedureClick(procedure) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveVehicleCard(
    selection: VehicleSelection,
    onSwitchClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CitroenRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = CitroenRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CitroenRed)
                        ) {
                            Text(
                                text = "ACTIVE VEHICLE",
                                color = CitroenRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (selection.isPrimaryFocus) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MintGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen)
                            ) {
                                Text(
                                    text = "MK1 1.6 DIESEL TARGET",
                                    color = MintGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Citroën C4 Grand Picasso",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selection.generation.displayTitle} (${selection.generation.years})",
                        color = CyanAccent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Engine: ${selection.engine.displayTitle} (${selection.engine.engineFamily})",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                    Text(
                        text = "Gearbox: ${selection.transmission.title}",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                Button(
                    onClick = onSwitchClick,
                    colors = ButtonDefaults.buttonColors(containerColor = WorkshopSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch",
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SWITCH",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ProcedureCard(
    procedure: RepairProcedure,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Category & Difficulty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BlueprintGrid.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = procedure.category.title.uppercase(),
                        color = CyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                val diffColor = when (procedure.difficulty) {
                    DifficultyLevel.BEGINNER -> MintGreen
                    DifficultyLevel.INTERMEDIATE -> GoldenBolt
                    DifficultyLevel.ADVANCED -> WarningOrange
                    DifficultyLevel.EXPERT -> CitroenRed
                }

                Surface(
                    color = diffColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, diffColor)
                ) {
                    Text(
                        text = procedure.difficulty.label.uppercase(),
                        color = diffColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = procedure.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Summary
            Text(
                text = procedure.summary,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Badges row: duration, steps count, exploded parts count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${procedure.estimatedMinutes} min",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text("•", color = WorkshopBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatListNumbered,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${procedure.steps.size} steps",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text("•", color = WorkshopBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = GoldenBolt,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${procedure.explodedParts.size} parts",
                        color = GoldenBolt,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 3D Tag
                Surface(
                    color = BlueprintGrid.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "3D / 2D",
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataUpdateBanner(
    state: DataUpdateState.UpdateAvailable,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "DATA UPDATE AVAILABLE",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "New content version: ${state.newVersion}",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }

            if (state.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.releaseNotes.take(160),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DOWNLOAD & HOT-RELOAD WORKSHOP DATA",
                    color = DarkBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AppUpdateBanner(
    state: AppUpdateState.UpdateAvailable,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CitroenRed)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CitroenRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "APP UPDATE: ${state.release.tagName}",
                            color = CitroenRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Installed: v${state.currentVersion} • Size: ${state.release.apkSizeBytes / (1024 * 1024)} MB",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = CitroenRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DOWNLOAD APK UPDATE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AppInstallBanner(
    state: AppUpdateState.ReadyToInstall,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WorkshopSurfaceElevated),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MintGreen)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "UPDATE READY TO INSTALL",
                            color = MintGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = state.apkFile.name,
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "INSTALL APK NOW",
                    color = DarkBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun EmptyProceduresCard(
    searchQuery: String,
    onReset: () -> Unit
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No matching procedures found",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "for \"$searchQuery\"",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = WorkshopSurfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder)
            ) {
                Text("RESET SEARCH & FILTERS", color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
