package com.picassoworkshop.app.ui.procedure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.data.model.DisassemblyStep
import com.picassoworkshop.app.data.model.ExplodedPart
import com.picassoworkshop.app.data.model.RepairProcedure
import com.picassoworkshop.app.ui.theme.*
import com.picassoworkshop.app.ui.views.BlownOutDiagramView
import com.picassoworkshop.app.ui.views.Spatial3DModelView

enum class VisualizationMode {
    BLOWN_OUT_SCHEMATIC,
    SPATIAL_3D_MODEL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcedureDetailScreen(
    procedure: RepairProcedure,
    onBackClick: () -> Unit
) {
    var viewMode by remember { mutableStateOf(VisualizationMode.BLOWN_OUT_SCHEMATIC) }
    var explodeProgress by remember { mutableFloatStateOf(0.5f) }
    var selectedPartId by remember { mutableStateOf<String?>(procedure.explodedParts.firstOrNull()?.id) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = procedure.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${procedure.targetGeneration.title} • ${procedure.targetEngine.title}",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WorkshopSurface)
            )
        },
        containerColor = WorkshopBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Interactive Visualization Area (Height ~300dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
                    .background(WorkshopBackground)
            ) {
                if (procedure.explodedParts.isNotEmpty()) {
                    if (viewMode == VisualizationMode.BLOWN_OUT_SCHEMATIC) {
                        BlownOutDiagramView(
                            parts = procedure.explodedParts,
                            explodeProgress = explodeProgress,
                            selectedPartId = selectedPartId,
                            onPartSelected = { part -> selectedPartId = part.id }
                        )
                    } else {
                        Spatial3DModelView(
                            parts = procedure.explodedParts,
                            explodeProgress = explodeProgress,
                            selectedPartId = selectedPartId,
                            onPartSelected = { part -> selectedPartId = part.id }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Diagram coming in next data package update",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Floating Top Controls inside visualization viewport
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Switcher Pill
                    Surface(
                        color = WorkshopSurface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder)
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            // Blown Out Button
                            Surface(
                                color = if (viewMode == VisualizationMode.BLOWN_OUT_SCHEMATIC) CitroenRed else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { viewMode = VisualizationMode.BLOWN_OUT_SCHEMATIC }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.AspectRatio, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "BLOWN-OUT",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // 3D Model Button
                            Surface(
                                color = if (viewMode == VisualizationMode.SPATIAL_3D_MODEL) CyanAccent else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable { viewMode = VisualizationMode.SPATIAL_3D_MODEL }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ViewInAr, contentDescription = null, tint = if (viewMode == VisualizationMode.SPATIAL_3D_MODEL) WorkshopBackground else TextSecondary, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "3D MODEL",
                                        color = if (viewMode == VisualizationMode.SPATIAL_3D_MODEL) WorkshopBackground else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Explode Progress Slider Pill
                    Surface(
                        color = WorkshopSurface.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "EXPLODE:",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Slider(
                                value = explodeProgress,
                                onValueChange = { explodeProgress = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyanAccent,
                                    activeTrackColor = CyanAccent,
                                    inactiveTrackColor = WorkshopBorder
                                ),
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                text = "${(explodeProgress * 100).toInt()}%",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // 2. Navigation Tabs (Steps / Parts & Fasteners / Torque & Tools)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = WorkshopSurface,
                contentColor = CyanAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CitroenRed,
                        height = 2.5.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "STEPS (${procedure.steps.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "PARTS & CALLOUTS",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "TORQUE & TOOLS",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
            }

            // 3. Tab Contents
            when (selectedTab) {
                0 -> {
                    // Steps List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(procedure.steps) { step ->
                            StepCard(
                                step = step,
                                isHighlighted = step.calloutNumber != null &&
                                        procedure.explodedParts.find { it.id == selectedPartId }?.calloutNumber == step.calloutNumber,
                                onCalloutClick = { callout ->
                                    val matchedPart = procedure.explodedParts.find { it.calloutNumber == callout }
                                    if (matchedPart != null) selectedPartId = matchedPart.id
                                }
                            )
                        }
                    }
                }
                1 -> {
                    // Parts & Callouts Table
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(procedure.explodedParts) { part ->
                            PartCard(
                                part = part,
                                isSelected = part.id == selectedPartId,
                                onClick = { selectedPartId = part.id }
                            )
                        }
                    }
                }
                2 -> {
                    // Torque Specs & Tools Summary
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "CRITICAL FASTENER TORQUE SPECIFICATIONS",
                                color = GoldenBolt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        items(procedure.torqueSpecs) { torque ->
                            Surface(
                                color = WorkshopSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = torque.componentName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "Fastener: ${torque.fastenerSize} • ${torque.notes}", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Surface(
                                        color = GoldenBolt.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt)
                                    ) {
                                        Text(
                                            text = "${torque.torqueNm} Nm",
                                            color = GoldenBolt,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "WORKSHOP TOOLING REQUIRED",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        items(procedure.toolsNeeded) { tool ->
                            Surface(
                                color = WorkshopSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(text = tool.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "Specification / Size: ${tool.size}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    step: DisassemblyStep,
    isHighlighted: Boolean,
    onCalloutClick: (Int) -> Unit
) {
    Surface(
        color = if (isHighlighted) CyanAccent.copy(alpha = 0.12f) else WorkshopSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) CyanAccent else WorkshopBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Step number, Title, Callout Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = CitroenRed,
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${step.stepNumber}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = step.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                step.calloutNumber?.let { callout ->
                    Surface(
                        color = GoldenBolt.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt),
                        modifier = Modifier.clickable { onCalloutClick(callout) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "PIN #$callout", color = GoldenBolt, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = GoldenBolt, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Instructions Text
            Text(
                text = step.instructions,
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            // Warning or Caution Banners
            step.warning?.let { warn ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CoralAlert.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralAlert)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
                        Text(text = warn, color = CoralAlert, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            step.caution?.let { caut ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AmberWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                        Text(text = caut, color = AmberWarning, fontSize = 11.sp)
                    }
                }
            }

            // Tool Requirement Chip
            step.tool?.let { t ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = WorkshopSurfaceElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(12.dp))
                        Text(text = "${t.name} (${t.size})", color = TextPrimary, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun PartCard(
    part: ExplodedPart,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) CyanAccent.copy(alpha = 0.15f) else WorkshopSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) CyanAccent else WorkshopBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = GoldenBolt,
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${part.calloutNumber}",
                            color = WorkshopBackground,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Column {
                    Text(text = part.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "OEM: ${part.oemPartNumber} • Tool: ${part.fastenerType}", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            if (part.torqueNm != null) {
                Surface(
                    color = GoldenBolt.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt)
                ) {
                    Text(
                        text = "${part.torqueNm} Nm",
                        color = GoldenBolt,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
