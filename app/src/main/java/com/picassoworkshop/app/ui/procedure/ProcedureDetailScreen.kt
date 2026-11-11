package com.picassoworkshop.app.ui.procedure

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picassoworkshop.app.data.model.DifficultyLevel
import com.picassoworkshop.app.data.model.DisassemblyStep
import com.picassoworkshop.app.data.model.RepairProcedure
import com.picassoworkshop.app.data.model.TorqueSpec
import com.picassoworkshop.app.ui.theme.*

/**
 * Unified model representing either a physical workshop tool (spanner, socket, torx, pin)
 * or a mechanical fastener/bit (bolts, nuts, clips, fuses, replacement parts)
 * needed to perform the tutorial.
 */
data class ToolOrBit(
    val id: String,
    val name: String,
    val spec: String,
    val category: ToolOrBitType,
    val isCrucial: Boolean = true,
    val stepNumbers: List<Int> = emptyList(),
    val torqueNm: Double? = null,
    val oemPartNumber: String? = null
)

enum class ToolOrBitType(val label: String, val icon: ImageVector) {
    ALL("All Items", Icons.Default.AllInclusive),
    WRENCH_SOCKET("Spanners & Sockets", Icons.Default.Build),
    TORX_DRIVER("Torx & Drivers", Icons.Default.Handyman),
    PINS_SPECIAL("Pins & Special", Icons.Default.PushPin),
    FASTENERS_BITS("Fasteners & Bits", Icons.Default.Hardware),
    TEST_ELECTRICAL("Test & Safety", Icons.Default.Speed)
}

/**
 * Automatically extracts and correlates all required workshop tools and mechanical
 * fasteners/bits from the tutorial's procedure definition.
 */
fun extractToolsAndBits(procedure: RepairProcedure): List<ToolOrBit> {
    val items = mutableListOf<ToolOrBit>()

    // 1. Process toolsNeeded
    procedure.toolsNeeded.forEachIndexed { idx, tool ->
        val cat = when {
            tool.category.contains("TORX", ignoreCase = true) ||
                    tool.name.contains("Torx", ignoreCase = true) ||
                    tool.size.contains("T20", ignoreCase = true) ||
                    tool.size.contains("T30", ignoreCase = true) -> ToolOrBitType.TORX_DRIVER

            tool.category.contains("PIN", ignoreCase = true) ||
                    tool.name.contains("Pin", ignoreCase = true) ||
                    tool.name.contains("Pry", ignoreCase = true) ||
                    tool.name.contains("Clip", ignoreCase = true) ||
                    tool.name.contains("Puller", ignoreCase = true) -> ToolOrBitType.PINS_SPECIAL

            tool.category.contains("TEST", ignoreCase = true) ||
                    tool.name.contains("Multimeter", ignoreCase = true) ||
                    tool.name.contains("Test", ignoreCase = true) -> ToolOrBitType.TEST_ELECTRICAL

            tool.category.contains("WRENCH", ignoreCase = true) ||
                    tool.category.contains("SOCKET", ignoreCase = true) ||
                    tool.name.contains("Spanner", ignoreCase = true) ||
                    tool.name.contains("Socket", ignoreCase = true) ||
                    tool.name.contains("Torque Wrench", ignoreCase = true) -> ToolOrBitType.WRENCH_SOCKET

            else -> ToolOrBitType.WRENCH_SOCKET
        }

        // Correlate with disassembly steps
        val matchingSteps = procedure.steps.filter { step ->
            (step.tool != null && (step.tool.name.contains(tool.name, ignoreCase = true) || tool.name.contains(step.tool.name, ignoreCase = true))) ||
                    (tool.size.isNotBlank() && step.instructions.contains(tool.size, ignoreCase = true)) ||
                    (tool.name.isNotBlank() && step.instructions.contains(tool.name.split(" ").firstOrNull() ?: "", ignoreCase = true))
        }.map { it.stepNumber }.distinct()

        val matchedTorque = procedure.torqueSpecs.firstOrNull { spec ->
            spec.fastenerSize.contains(tool.size, ignoreCase = true) ||
                    tool.name.contains(spec.componentName, ignoreCase = true)
        }?.torqueNm

        items.add(
            ToolOrBit(
                id = "tool_$idx",
                name = tool.name,
                spec = tool.size,
                category = cat,
                isCrucial = tool.isCrucial,
                stepNumbers = matchingSteps,
                torqueNm = matchedTorque
            )
        )
    }

    // 2. Process explodedParts (fasteners, bolts, nuts, clips, replacement bits)
    procedure.explodedParts.forEach { part ->
        if (part.fastenerType.isNotBlank() || part.torqueNm != null) {
            val matchingSteps = procedure.steps.filter { step ->
                step.associatedPartId == part.id ||
                        step.calloutNumber == part.calloutNumber ||
                        step.instructions.contains(part.name, ignoreCase = true) ||
                        (part.fastenerType.isNotBlank() && step.instructions.contains(part.fastenerType.split(" ").firstOrNull() ?: "", ignoreCase = true))
            }.map { it.stepNumber }.distinct()

            items.add(
                ToolOrBit(
                    id = "part_${part.id}",
                    name = part.name,
                    spec = part.fastenerType.ifBlank { "OEM ${part.oemPartNumber}" },
                    category = ToolOrBitType.FASTENERS_BITS,
                    isCrucial = true,
                    stepNumbers = matchingSteps,
                    torqueNm = part.torqueNm,
                    oemPartNumber = part.oemPartNumber.ifBlank { null }
                )
            )
        }
    }

    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcedureDetailScreen(
    procedure: RepairProcedure,
    onBackClick: () -> Unit
) {
    var completedSteps by remember { mutableStateOf(setOf<Int>()) }
    var selectedBottomTab by remember { mutableIntStateOf(0) } // 0: Steps, 1: Tools & Bits, 2: Safety & Torque

    // Tools & Bits state
    val allToolsAndBits = remember(procedure) { extractToolsAndBits(procedure) }
    var activeToolOrBitFilter by remember { mutableStateOf<ToolOrBit?>(null) }
    var toolsSearchQuery by remember { mutableStateOf("") }
    var selectedToolCategory by remember { mutableStateOf(ToolOrBitType.ALL) }
    var trayCheckedIds by remember { mutableStateOf(setOf<String>()) }

    // Compute steps filtered by the selected tool or bit
    val displayedSteps = remember(procedure.steps, activeToolOrBitFilter) {
        if (activeToolOrBitFilter == null) {
            procedure.steps
        } else {
            val filter = activeToolOrBitFilter!!
            val directMatches = procedure.steps.filter { step ->
                filter.stepNumbers.contains(step.stepNumber) ||
                        (filter.spec.isNotBlank() && step.instructions.contains(filter.spec, ignoreCase = true)) ||
                        (filter.name.isNotBlank() && step.instructions.contains(filter.name.split(" ").firstOrNull() ?: "", ignoreCase = true)) ||
                        (step.tool != null && (step.tool.name.contains(filter.name, ignoreCase = true) || filter.name.contains(step.tool.name, ignoreCase = true)))
            }
            if (directMatches.isNotEmpty()) directMatches else procedure.steps
        }
    }

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
                            text = "${procedure.targetGeneration.displayTitle} • ${procedure.targetEngine.displayTitle}",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    Surface(
                        color = BlueprintGrid.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${completedSteps.size}/${procedure.steps.size} DONE",
                            color = if (completedSteps.size == procedure.steps.size && procedure.steps.isNotEmpty()) MintGreen else CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WorkshopSurface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = WorkshopSurfaceElevated,
                contentColor = TextPrimary,
                tonalElevation = 8.dp
            ) {
                // Tab 0: STEPS
                NavigationBarItem(
                    selected = selectedBottomTab == 0,
                    onClick = { selectedBottomTab = 0 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (completedSteps.isNotEmpty()) {
                                    Badge(containerColor = MintGreen) {
                                        Text("${completedSteps.size}/${procedure.steps.size}", fontSize = 9.sp, color = DarkBackground)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListNumbered,
                                contentDescription = "Steps"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "STEPS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CitroenRed,
                        selectedTextColor = CitroenRed,
                        indicatorColor = CitroenRed.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                // Tab 1: TOOLS & BITS
                NavigationBarItem(
                    selected = selectedBottomTab == 1,
                    onClick = { selectedBottomTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (allToolsAndBits.isNotEmpty()) {
                                    Badge(
                                        containerColor = if (trayCheckedIds.size == allToolsAndBits.size) MintGreen else CyanAccent
                                    ) {
                                        Text("${trayCheckedIds.size}/${allToolsAndBits.size}", fontSize = 9.sp, color = DarkBackground)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handyman,
                                contentDescription = "Tools & Bits"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "TOOLS & BITS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanAccent,
                        selectedTextColor = CyanAccent,
                        indicatorColor = CyanAccent.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                // Tab 2: SAFETY & TORQUE
                NavigationBarItem(
                    selected = selectedBottomTab == 2,
                    onClick = { selectedBottomTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (procedure.torqueSpecs.isNotEmpty()) {
                                    Badge(containerColor = GoldenBolt) {
                                        Text("${procedure.torqueSpecs.size}", fontSize = 9.sp, color = DarkBackground)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Safety & Torque"
                            )
                        }
                    },
                    label = {
                        Text(
                            text = "SAFETY & TORQUE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldenBolt,
                        selectedTextColor = GoldenBolt,
                        indicatorColor = GoldenBolt.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        },
        containerColor = WorkshopBackground
    ) { paddingValues ->
        when (selectedBottomTab) {
            0 -> {
                // Tab 0: STEPS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Procedure Overview Card
                    item {
                        ProcedureOverviewHeader(
                            procedure = procedure,
                            completedCount = completedSteps.size,
                            totalCount = procedure.steps.size,
                            toolsCount = allToolsAndBits.size,
                            onNavigateToTools = { selectedBottomTab = 1 },
                            onNavigateToSafetyTorque = { selectedBottomTab = 2 }
                        )
                    }

                    // 2. Active Tool Filter Banner (when filtering steps by a specific tool/bit)
                    if (activeToolOrBitFilter != null) {
                        item {
                            ActiveToolFilterBanner(
                                activeTool = activeToolOrBitFilter!!,
                                displayedCount = displayedSteps.size,
                                totalCount = procedure.steps.size,
                                onClearFilter = { activeToolOrBitFilter = null },
                                onViewToolsTab = { selectedBottomTab = 1 }
                            )
                        }
                    }

                    // 3. Section Title: Step-by-Step Infographs
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = CitroenRed,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.size(width = 4.dp, height = 16.dp)
                                ) {}
                                Text(
                                    text = if (activeToolOrBitFilter != null) {
                                        "FILTERED REPAIR STEPS (${displayedSteps.size})"
                                    } else {
                                        "STEP-BY-STEP REPAIR INFOGRAPHS"
                                    },
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }

                            if (completedSteps.isNotEmpty()) {
                                Text(
                                    text = "Reset All",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable { completedSteps = emptySet() }
                                )
                            }
                        }
                    }

                    // 4. Individual Step-by-Step Infograph Cards
                    itemsIndexed(displayedSteps, key = { _, step -> step.stepNumber }) { _, step ->
                        val isCompleted = completedSteps.contains(step.stepNumber)
                        val relevantTorque = procedure.torqueSpecs.firstOrNull { spec ->
                            step.instructions.contains(spec.componentName, ignoreCase = true) ||
                                    (step.tool != null && spec.fastenerSize.contains(step.tool.size, ignoreCase = true))
                        }

                        StepInfographCard(
                            step = step,
                            stepIndex = step.stepNumber,
                            totalSteps = procedure.steps.size,
                            isCompleted = isCompleted,
                            matchedTorqueSpec = relevantTorque,
                            onToggleComplete = {
                                completedSteps = if (isCompleted) {
                                    completedSteps - step.stepNumber
                                } else {
                                    completedSteps + step.stepNumber
                                }
                            },
                            onToolChipClick = { toolName ->
                                val matched = allToolsAndBits.firstOrNull {
                                    it.name.contains(toolName, ignoreCase = true) || toolName.contains(it.name, ignoreCase = true)
                                }
                                if (matched != null) {
                                    activeToolOrBitFilter = if (activeToolOrBitFilter?.id == matched.id) null else matched
                                }
                            }
                        )
                    }

                    // 5. Final Completion Card
                    if (procedure.steps.isNotEmpty() && completedSteps.size == procedure.steps.size) {
                        item {
                            ProcedureCompletedCard(onReset = { completedSteps = emptySet() })
                        }
                    }
                }
            }
            1 -> {
                // Tab 1: TOOLS & BITS
                ToolsAndBitsTabContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    toolsAndBits = allToolsAndBits,
                    activeFilter = activeToolOrBitFilter,
                    onSelectFilterAndNavigate = { selected ->
                        activeToolOrBitFilter = selected
                        selectedBottomTab = 0 // Switch to steps view
                    },
                    searchQuery = toolsSearchQuery,
                    onSearchQueryChange = { toolsSearchQuery = it },
                    selectedCategory = selectedToolCategory,
                    onCategorySelect = { selectedToolCategory = it },
                    trayCheckedIds = trayCheckedIds,
                    onToggleTrayItem = { id ->
                        trayCheckedIds = if (trayCheckedIds.contains(id)) {
                            trayCheckedIds - id
                        } else {
                            trayCheckedIds + id
                        }
                    },
                    onMarkAllTray = {
                        trayCheckedIds = allToolsAndBits.map { it.id }.toSet()
                    },
                    onClearTray = {
                        trayCheckedIds = emptySet()
                    }
                )
            }
            2 -> {
                // Tab 2: SAFETY & TORQUE
                SafetyAndTorqueTabContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    safetyNotes = procedure.safetyNotes,
                    torqueSpecs = procedure.torqueSpecs,
                    onTorqueSpecClick = { torqueSpec ->
                        val matched = allToolsAndBits.firstOrNull {
                            it.name.contains(torqueSpec.componentName, ignoreCase = true) ||
                                    torqueSpec.componentName.contains(it.name, ignoreCase = true)
                        }
                        if (matched != null) {
                            activeToolOrBitFilter = matched
                            selectedBottomTab = 0
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActiveToolFilterBanner(
    activeTool: ToolOrBit,
    displayedCount: Int,
    totalCount: Int,
    onClearFilter: () -> Unit,
    onViewToolsTab: () -> Unit
) {
    Surface(
        color = CyanAccent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = CyanAccent,
                    shape = CircleShape,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "FILTERING STEPS BY TOOL / BIT",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${activeTool.name} (${activeTool.spec})",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Showing $displayedCount of $totalCount steps",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onViewToolsTab,
                    colors = ButtonDefaults.textButtonColors(contentColor = CyanAccent)
                ) {
                    Text(
                        text = "TOOLS TAB",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                TextButton(
                    onClick = onClearFilter,
                    colors = ButtonDefaults.textButtonColors(contentColor = MintGreen)
                ) {
                    Text(
                        text = "SHOW ALL",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Tools & Bits Tab Content.
 * Displays a full-screen inventory checklist with categorization, real-time search,
 * "In Tray" readiness tracking, and direct step-filtering triggers.
 */
@Composable
fun ToolsAndBitsTabContent(
    modifier: Modifier = Modifier,
    toolsAndBits: List<ToolOrBit>,
    activeFilter: ToolOrBit?,
    onSelectFilterAndNavigate: (ToolOrBit?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: ToolOrBitType,
    onCategorySelect: (ToolOrBitType) -> Unit,
    trayCheckedIds: Set<String>,
    onToggleTrayItem: (String) -> Unit,
    onMarkAllTray: () -> Unit,
    onClearTray: () -> Unit
) {
    val filteredItems = remember(toolsAndBits, searchQuery, selectedCategory) {
        toolsAndBits.filter { item ->
            val matchesCategory = selectedCategory == ToolOrBitType.ALL || item.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.spec.contains(searchQuery, ignoreCase = true) ||
                    (item.oemPartNumber != null && item.oemPartNumber.contains(searchQuery, ignoreCase = true))
            matchesCategory && matchesSearch
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Tool Tray Readiness Header Card
        item {
            Surface(
                color = WorkshopSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Handyman,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "WORKSHOP TOOL TRAY & BITS",
                                    color = CyanAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${toolsAndBits.size} items needed • ${trayCheckedIds.size}/${toolsAndBits.size} ready in tray",
                                    color = if (trayCheckedIds.size == toolsAndBits.size && toolsAndBits.isNotEmpty()) MintGreen else TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (activeFilter != null) {
                            Surface(
                                color = CyanAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                            ) {
                                Text(
                                    text = "FILTER ACTIVE",
                                    color = CyanAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val trayProgress = if (toolsAndBits.isNotEmpty()) trayCheckedIds.size.toFloat() / toolsAndBits.size else 0f
                    LinearProgressIndicator(
                        progress = { trayProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (trayProgress == 1f) MintGreen else CyanAccent,
                        trackColor = WorkshopBorder
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Check items off as you lay them into your workspace tray:",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = onMarkAllTray,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("ALL READY", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MintGreen)
                            }
                            TextButton(
                                onClick = onClearTray,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("RESET", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // 2. Search & Category Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Filter tools & bits (e.g. 15mm, Torx, bolt, pin)...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = WorkshopSurface,
                        unfocusedContainerColor = WorkshopSurface,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = WorkshopBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Category Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ToolOrBitType.values().forEach { cat ->
                        val isSelected = selectedCategory == cat
                        val count = if (cat == ToolOrBitType.ALL) {
                            toolsAndBits.size
                        } else {
                            toolsAndBits.count { it.category == cat }
                        }

                        if (count > 0 || cat == ToolOrBitType.ALL) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyanAccent else WorkshopSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) CyanAccent else WorkshopBorder
                                ),
                                modifier = Modifier.clickable { onCategorySelect(cat) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) DarkBackground else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${cat.label.uppercase()} ($count)",
                                        color = if (isSelected) DarkBackground else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Items List
        if (filteredItems.isEmpty()) {
            item {
                Surface(
                    color = WorkshopSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tools or bits match the current filter.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            items(filteredItems.size, key = { filteredItems[it].id }) { idx ->
                val item = filteredItems[idx]
                val isInTray = trayCheckedIds.contains(item.id)
                val isFilteringThis = activeFilter?.id == item.id

                Surface(
                    color = if (isFilteringThis) CyanAccent.copy(alpha = 0.12f) else WorkshopSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isFilteringThis) CyanAccent else if (isInTray) MintGreen.copy(alpha = 0.4f) else WorkshopBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Tray Checkbox + Icon + Details
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Tray Checkbox
                            Surface(
                                color = if (isInTray) MintGreen else WorkshopBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isInTray) MintGreen else TextSecondary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { onToggleTrayItem(item.id) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isInTray) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "In Tray",
                                            tint = DarkBackground,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Category Icon
                            Surface(
                                color = BlueprintGrid.copy(alpha = 0.4f),
                                shape = CircleShape,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.category.icon,
                                        contentDescription = null,
                                        tint = if (isInTray) MintGreen else CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Info details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        color = if (isInTray) TextPrimary else TextPrimary.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.spec.isNotBlank()) {
                                        Surface(
                                            color = WorkshopSurfaceElevated,
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, WorkshopBorder)
                                        ) {
                                            Text(
                                                text = item.spec,
                                                color = CyanAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.oemPartNumber != null) {
                                        Text(
                                            text = "OEM: ${item.oemPartNumber}",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    if (item.torqueNm != null) {
                                        Surface(
                                            color = GoldenBolt.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, GoldenBolt)
                                        ) {
                                            Text(
                                                text = "${item.torqueNm} Nm",
                                                color = GoldenBolt,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (item.stepNumbers.isNotEmpty()) {
                                        Text(
                                            text = "• Steps: ${item.stepNumbers.joinToString()}",
                                            color = TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        // Right: Filter Steps Action Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFilteringThis) CyanAccent else WorkshopSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isFilteringThis) CyanAccent else WorkshopBorder
                            ),
                            modifier = Modifier.clickable {
                                onSelectFilterAndNavigate(if (isFilteringThis) null else item)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFilteringThis) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (isFilteringThis) DarkBackground else CyanAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isFilteringThis) "ACTIVE" else "FILTER STEPS",
                                    color = if (isFilteringThis) DarkBackground else CyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Safety & Torque Tab Content.
 * Displays mandatory workshop safety standards, procedure-specific cautions,
 * and critical fastener torque specifications.
 */
@Composable
fun SafetyAndTorqueTabContent(
    modifier: Modifier = Modifier,
    safetyNotes: List<String>,
    torqueSpecs: List<TorqueSpec>,
    onTorqueSpecClick: (TorqueSpec) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Mandatory Workshop Safety Rules
        item {
            Surface(
                color = CoralAlert.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CoralAlert.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = CoralAlert,
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "MANDATORY WORKSHOP SAFETY RULES",
                                color = CoralAlert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "PSA Peugeot Citroën Workshop Standards",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Core PSA safety standards
                    val coreRules = listOf(
                        "BSI Multiplex Sleep Rule: Wait 3 full minutes after switching off ignition before disconnecting the 12V battery to prevent EEPROM corruptions.",
                        "Common Rail Diesel Injection: System runs at up to 1600 bar. Never crack fuel line unions while engine is cranking or running.",
                        "Dynamic Belt Tensioner Precaution: Lock tensioner roller with 4mm pin before loosening alternator or bracket fasteners to avoid injury."
                    )

                    (coreRules + safetyNotes).distinct().forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "⚠",
                                color = CoralAlert,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rule,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Critical Fastener Torque Specifications
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = GoldenBolt,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.size(width = 4.dp, height = 16.dp)
                    ) {}
                    Text(
                        text = "FASTENER TORQUE SPECIFICATIONS (${torqueSpecs.size})",
                        color = GoldenBolt,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (torqueSpecs.isEmpty()) {
            item {
                Surface(
                    color = WorkshopSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "No dedicated torque overrides recorded for this procedure.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Refer to the PSA DV6 Standard Fastener Torque Chart below for nominal bolt ratings.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        } else {
            items(torqueSpecs.size, key = { torqueSpecs[it].componentName + it }) { idx ->
                val torque = torqueSpecs[idx]
                Surface(
                    color = WorkshopSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = torque.componentName,
                                color = TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Fastener: ${torque.fastenerSize}",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (torque.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = torque.notes,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Surface(
                            color = GoldenBolt.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt),
                            modifier = Modifier.clickable { onTorqueSpecClick(torque) }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${torque.torqueNm} Nm",
                                    color = GoldenBolt,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "SPEC",
                                    color = GoldenBolt.copy(alpha = 0.8f),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. General PSA DV6 Fastener Standard Torque Chart
        item {
            Surface(
                color = WorkshopSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BlueprintGrid.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hardware,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "PSA STANDARD FASTENER TORQUE REFERENCE",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val standardTorques = listOf(
                        "M6 Bolts (8.8 grade)" to "9 - 10 Nm (Heat shields, brackets, sensor mounts)",
                        "M8 Bolts (8.8 grade)" to "22 - 25 Nm (Idler pulleys, fuel filter housing, EGR)",
                        "M10 Bolts (8.8 grade)" to "45 - 50 Nm (Alternator pivot, engine mounting brackets)",
                        "M12 Bolts (10.9 grade)" to "80 - 85 Nm (Subframe, caliper carriers)",
                        "DV6 Glow Plugs (M8x1.0)" to "10 - 12 Nm (Never exceed 15 Nm; risk of shear)",
                        "Oil Sump Drain Plug (M10)" to "25 Nm (Always fit fresh copper crush washer)"
                    )

                    standardTorques.forEach { (bolt, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bolt,
                                color = GoldenBolt,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(0.45f)
                            )
                            Text(
                                text = desc,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                modifier = Modifier.weight(0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility wrapper for ToolsAndBitsFilterSection.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ToolsAndBitsFilterSection(
    toolsAndBits: List<ToolOrBit>,
    activeFilter: ToolOrBit?,
    onSelectFilter: (ToolOrBit?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: ToolOrBitType,
    onCategorySelect: (ToolOrBitType) -> Unit,
    trayCheckedIds: Set<String>,
    onToggleTrayItem: (String) -> Unit,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit = {}
) {
    ToolsAndBitsTabContent(
        toolsAndBits = toolsAndBits,
        activeFilter = activeFilter,
        onSelectFilterAndNavigate = onSelectFilter,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        selectedCategory = selectedCategory,
        onCategorySelect = onCategorySelect,
        trayCheckedIds = trayCheckedIds,
        onToggleTrayItem = onToggleTrayItem,
        onMarkAllTray = {},
        onClearTray = {}
    )
}

@Composable
private fun ProcedureOverviewHeader(
    procedure: RepairProcedure,
    completedCount: Int,
    totalCount: Int,
    toolsCount: Int = 0,
    onNavigateToTools: () -> Unit = {},
    onNavigateToSafetyTorque: () -> Unit = {}
) {
    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Badges row: Category, Difficulty, Estimated Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BlueprintGrid.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = procedure.category.title.uppercase(),
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, diffColor)
                ) {
                    Text(
                        text = "${procedure.difficulty.label.uppercase()} ${"★".repeat(procedure.difficulty.stars)}",
                        color = diffColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = procedure.summary,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Progress Bar and Quick Stats
            val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${procedure.estimatedMinutes} minutes estimated",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}% completed",
                    color = if (progress == 1f) MintGreen else CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progress == 1f) MintGreen else CitroenRed,
                trackColor = WorkshopBorder
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Jump Bottom-Tab Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanAccent.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToTools() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handyman,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (toolsCount > 0) "TOOLS & BITS ($toolsCount)" else "TOOLS & BITS",
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldenBolt.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToSafetyTorque() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GoldenBolt,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "SAFETY & TORQUE",
                            color = GoldenBolt,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyNotesBanner(
    safetyNotes: List<String>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = CoralAlert.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CoralAlert.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CoralAlert,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "MANDATORY WORKSHOP SAFETY RULES (${safetyNotes.size})",
                        color = CoralAlert,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CoralAlert,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                safetyNotes.forEach { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "⚠",
                            color = CoralAlert,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = note,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepInfographCard(
    step: DisassemblyStep,
    stepIndex: Int,
    totalSteps: Int,
    isCompleted: Boolean,
    matchedTorqueSpec: TorqueSpec?,
    onToggleComplete: () -> Unit,
    onToolChipClick: (String) -> Unit
) {
    Surface(
        color = if (isCompleted) WorkshopSurface.copy(alpha = 0.65f) else WorkshopSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isCompleted) 1.dp else 1.5.dp,
            color = if (isCompleted) MintGreen.copy(alpha = 0.5f) else WorkshopBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Step Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Step Number or Completed Checkbox
                    Surface(
                        color = if (isCompleted) MintGreen else CitroenRed,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onToggleComplete() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = WorkshopBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = "$stepIndex",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "STEP $stepIndex OF $totalSteps",
                                color = if (isCompleted) MintGreen else CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                            if (isCompleted) {
                                Surface(
                                    color = MintGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "DONE",
                                        color = MintGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = step.title,
                            color = if (isCompleted) TextSecondary else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Interactive Toggle Checkbox Icon
                IconButton(
                    onClick = onToggleComplete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Toggle Complete",
                        tint = if (isCompleted) MintGreen else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dedicated Technical Infograph Canvas for this Step
            StepTechnicalInfograph(
                step = step,
                stepNumber = stepIndex
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed Written Workshop Instructions
            Text(
                text = step.instructions,
                color = if (isCompleted) TextSecondary else TextPrimary,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )

            // Warning Banner
            step.warning?.let { warn ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = CoralAlert.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralAlert)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CoralAlert,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = warn,
                            color = CoralAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Caution Banner
            step.caution?.let { caut ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = AmberWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = caut,
                            color = AmberWarning,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // Tool & Fastener Torque Footer
            if (step.tool != null || matchedTorqueSpec != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step.tool != null) {
                        Surface(
                            color = WorkshopSurfaceElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable { onToolChipClick(step.tool.name) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${step.tool.name} (${step.tool.size})",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    if (matchedTorqueSpec != null) {
                        Surface(
                            color = GoldenBolt.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldenBolt)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GoldenBolt,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "TORQUE: ${matchedTorqueSpec.torqueNm} Nm",
                                    color = GoldenBolt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Technical Infograph Canvas dedicated to the action of each step.
 */
@Composable
private fun StepTechnicalInfograph(
    step: DisassemblyStep,
    stepNumber: Int
) {
    Surface(
        color = WorkshopBackground,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlueprintGrid),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Blueprint Grid Background Pattern
            val gridSpacing = 24.dp.toPx()
            var x = 0f
            while (x < canvasWidth) {
                drawLine(
                    color = BlueprintGrid.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }
            var y = 0f
            while (y < canvasHeight) {
                drawLine(
                    color = BlueprintGrid.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }

            // Draw Step Specific Technical Schematics
            val effectiveStepId = when {
                step.graphicKey.isNotEmpty() -> step.graphicKey
                else -> "step_$stepNumber"
            }

            when (if (effectiveStepId.startsWith("step_")) stepNumber else stepNumber) {
                1 -> {
                    // STEP 1: Battery Disconnect & BSI 3-Minute Sleep
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(40f, 30f),
                        size = Size(110f, 70f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = CyanAccent,
                        topLeft = Offset(40f, 30f),
                        size = Size(110f, 70f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2f)
                    )
                    drawRect(color = CitroenRed, topLeft = Offset(52f, 20f), size = Size(16f, 10f))
                    drawRect(color = Color(0xFF94A3B8), topLeft = Offset(118f, 20f), size = Size(16f, 10f))

                    val arrowPath = Path().apply {
                        moveTo(126f, 15f)
                        lineTo(126f, -5f)
                        lineTo(150f, -5f)
                    }
                    drawPath(
                        path = arrowPath,
                        color = CoralAlert,
                        style = Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                    )
                    drawCircle(color = CoralAlert, radius = 4f, center = Offset(150f, -5f))

                    val clockCenter = Offset(canvasWidth - 90f, canvasHeight / 2)
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 36f,
                        center = clockCenter
                    )
                    drawCircle(
                        color = MintGreen,
                        radius = 36f,
                        center = clockCenter,
                        style = Stroke(width = 2.5f)
                    )
                    drawLine(
                        color = Color.White,
                        start = clockCenter,
                        end = Offset(clockCenter.x, clockCenter.y - 24f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = MintGreen,
                        start = clockCenter,
                        end = Offset(clockCenter.x + 18f, clockCenter.y),
                        strokeWidth = 2.5f
                    )
                }

                2 -> {
                    // STEP 2: Auxiliary Drive Serpentine Belt & Tensioner
                    val crankCenter = Offset(60f, canvasHeight / 2 + 15f)
                    val altCenter = Offset(canvasWidth / 2 - 20f, 38f)
                    val tensionerCenter = Offset(canvasWidth / 2 + 20f, canvasHeight / 2 + 20f)
                    val acCenter = Offset(canvasWidth - 70f, canvasHeight / 2)

                    drawCircle(color = Color(0xFF334155), radius = 26f, center = crankCenter)
                    drawCircle(color = CyanAccent, radius = 26f, center = crankCenter, style = Stroke(2f))

                    drawCircle(color = GoldenBolt, radius = 20f, center = altCenter)
                    drawCircle(color = Color.White, radius = 20f, center = altCenter, style = Stroke(2f))

                    drawCircle(color = CitroenRed, radius = 16f, center = tensionerCenter)
                    drawCircle(color = Color.White, radius = 16f, center = tensionerCenter, style = Stroke(2f))

                    drawCircle(color = Color(0xFF334155), radius = 22f, center = acCenter)
                    drawCircle(color = CyanAccent, radius = 22f, center = acCenter, style = Stroke(2f))

                    val beltPath = Path().apply {
                        moveTo(crankCenter.x, crankCenter.y - 26f)
                        lineTo(altCenter.x - 20f, altCenter.y)
                        lineTo(altCenter.x + 20f, altCenter.y)
                        lineTo(tensionerCenter.x - 16f, tensionerCenter.y)
                        lineTo(acCenter.x, acCenter.y + 22f)
                        lineTo(crankCenter.x, crankCenter.y + 26f)
                        close()
                    }
                    drawPath(
                        path = beltPath,
                        color = WarningOrange,
                        style = Stroke(width = 3.5f)
                    )

                    drawCircle(color = Color.White, radius = 4f, center = tensionerCenter)
                    val rotArrow = Path().apply {
                        moveTo(tensionerCenter.x + 22f, tensionerCenter.y - 12f)
                        lineTo(tensionerCenter.x + 26f, tensionerCenter.y + 12f)
                    }
                    drawPath(path = rotArrow, color = CoralAlert, style = Stroke(width = 2.5f))
                }

                3 -> {
                    // STEP 3: Electrical Connections (B+ 13mm Nut & 2-Pin Excitation Plug)
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(50f, 25f),
                        size = Size(canvasWidth - 100f, 80f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = CyanAccent,
                        topLeft = Offset(50f, 25f),
                        size = Size(canvasWidth - 100f, 80f),
                        cornerRadius = CornerRadius(10f, 10f),
                        style = Stroke(width = 2f)
                    )

                    val bPlusCenter = Offset(110f, 65f)
                    drawCircle(color = GoldenBolt, radius = 14f, center = bPlusCenter)
                    drawCircle(color = Color.Black, radius = 6f, center = bPlusCenter)
                    drawCircle(
                        color = CoralAlert,
                        radius = 20f,
                        center = bPlusCenter,
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                        )
                    )

                    val plugRect = Offset(canvasWidth - 140f, 48f)
                    drawRoundRect(
                        color = MintGreen,
                        topLeft = plugRect,
                        size = Size(45f, 34f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    val unclipPath = Path().apply {
                        moveTo(plugRect.x + 22f, plugRect.y - 4f)
                        lineTo(plugRect.x + 22f, plugRect.y - 18f)
                    }
                    drawPath(path = unclipPath, color = Color.White, style = Stroke(width = 2.5f))
                }

                4 -> {
                    // STEP 4: Upper Mounting Bolts (Dual 13mm Hex)
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(60f, 30f),
                        size = Size(canvasWidth - 120f, 70f),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    drawRoundRect(
                        color = BlueprintGrid,
                        topLeft = Offset(60f, 30f),
                        size = Size(canvasWidth - 120f, 70f),
                        cornerRadius = CornerRadius(8f, 8f),
                        style = Stroke(2f)
                    )

                    val bolt1Center = Offset(120f, 65f)
                    drawCircle(color = GoldenBolt, radius = 12f, center = bolt1Center)
                    drawCircle(color = Color.Black, radius = 5f, center = bolt1Center)

                    val bolt2Center = Offset(canvasWidth - 120f, 65f)
                    drawCircle(color = GoldenBolt, radius = 12f, center = bolt2Center)
                    drawCircle(color = Color.Black, radius = 5f, center = bolt2Center)

                    drawLine(
                        color = CyanAccent,
                        start = bolt1Center,
                        end = Offset(bolt1Center.x, bolt1Center.y - 35f),
                        strokeWidth = 2.5f
                    )
                    drawLine(
                        color = CyanAccent,
                        start = bolt2Center,
                        end = Offset(bolt2Center.x, bolt2Center.y - 35f),
                        strokeWidth = 2.5f
                    )
                }

                5 -> {
                    // STEP 5: Lower Pivot Bolt & Extraction Path
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = 35f,
                        center = Offset(90f, 50f)
                    )
                    drawCircle(
                        color = CyanAccent,
                        radius = 35f,
                        center = Offset(90f, 50f),
                        style = Stroke(2f)
                    )

                    drawLine(
                        color = GoldenBolt,
                        start = Offset(90f, 85f),
                        end = Offset(90f, 125f),
                        strokeWidth = 5f
                    )
                    drawCircle(color = GoldenBolt, radius = 8f, center = Offset(90f, 125f))

                    val trajPath = Path().apply {
                        moveTo(130f, 50f)
                        cubicTo(190f, 50f, 220f, 95f, canvasWidth - 60f, 100f)
                    }
                    drawPath(
                        path = trajPath,
                        color = MintGreen,
                        style = Stroke(
                            width = 3.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f))
                        )
                    )
                    drawCircle(color = MintGreen, radius = 5f, center = Offset(canvasWidth - 60f, 100f))
                }

                else -> {
                    // Generic Blueprint Action Schematic
                    val center = Offset(canvasWidth / 2, canvasHeight / 2)
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = 40f,
                        center = center
                    )
                    drawCircle(
                        color = CyanAccent,
                        radius = 40f,
                        center = center,
                        style = Stroke(2f)
                    )
                    drawLine(
                        color = BlueprintGrid,
                        start = Offset(center.x - 50f, center.y),
                        end = Offset(center.x + 50f, center.y),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = BlueprintGrid,
                        start = Offset(center.x, center.y - 50f),
                        end = Offset(center.x, center.y + 50f),
                        strokeWidth = 1.5f
                    )
                    drawCircle(color = GoldenBolt, radius = 10f, center = center)
                }
            }
        }
    }
}

@Composable
private fun ProcedureCompletedCard(onReset: () -> Unit) {
    Surface(
        color = MintGreen.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MintGreen),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MintGreen,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "PROCEDURE COMPLETED SUCCESSFULLY",
                color = MintGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "All disassembly and servicing steps have been checked off. Verify all fasteners are torqued to specification before test run.",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onReset,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen),
                border = androidx.compose.foundation.BorderStroke(1.dp, MintGreen)
            ) {
                Text(
                    text = "START PROCEDURE OVER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
