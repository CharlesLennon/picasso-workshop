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
    var showSafetyNotes by remember { mutableStateOf(procedure.safetyNotes.isNotEmpty()) }
    var showToolsSection by remember { mutableStateOf(true) }

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
        containerColor = WorkshopBackground
    ) { paddingValues ->
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
                    totalCount = procedure.steps.size
                )
            }

            // 2. Critical Safety Instructions Banner (Collapsible)
            if (procedure.safetyNotes.isNotEmpty()) {
                item {
                    SafetyNotesBanner(
                        safetyNotes = procedure.safetyNotes,
                        isExpanded = showSafetyNotes,
                        onToggle = { showSafetyNotes = !showSafetyNotes }
                    )
                }
            }

            // 3. Dedicated Tools & Bits Needed Filter List
            if (allToolsAndBits.isNotEmpty()) {
                item {
                    ToolsAndBitsFilterSection(
                        toolsAndBits = allToolsAndBits,
                        activeFilter = activeToolOrBitFilter,
                        onSelectFilter = { selected ->
                            activeToolOrBitFilter = if (activeToolOrBitFilter?.id == selected?.id) null else selected
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
                        isExpanded = showToolsSection,
                        onToggleExpand = { showToolsSection = !showToolsSection }
                    )
                }
            }

            // 4. Active Tool Filter Banner (when filtering steps by a specific tool/bit)
            if (activeToolOrBitFilter != null) {
                item {
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
                                            tint = WorkshopBackground,
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
                                        text = "${activeToolOrBitFilter!!.name} (${activeToolOrBitFilter!!.spec})",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Showing ${displayedSteps.size} of ${procedure.steps.size} steps",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            TextButton(
                                onClick = { activeToolOrBitFilter = null },
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

            // 5. Section Title: Step-by-Step Infographs
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

            // 6. Individual Step-by-Step Infograph Cards
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

            // 7. Final Completion Card
            if (procedure.steps.isNotEmpty() && completedSteps.size == procedure.steps.size) {
                item {
                    ProcedureCompletedCard(onReset = { completedSteps = emptySet() })
                }
            }
        }
    }
}

/**
 * Dedicated Tools & Bits Filter Section.
 * Displays an inventory checklist with categorization, real-time search,
 * "In Tray" readiness tracking, and direct step-filtering triggers.
 */
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
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Surface(
        color = WorkshopSurface,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkshopBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Tray Progress & Expand/Collapse Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Handyman,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "TOOLS & BITS NEEDED FILTER LIST",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${toolsAndBits.size} items required • ${trayCheckedIds.size}/${toolsAndBits.size} ready in tray",
                            color = if (trayCheckedIds.size == toolsAndBits.size && toolsAndBits.isNotEmpty()) MintGreen else TextSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeFilter != null) {
                        Surface(
                            color = CyanAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent)
                        ) {
                            Text(
                                text = "1 FILTER ACTIVE",
                                color = CyanAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar for Tools & Bits
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
                        focusedContainerColor = WorkshopBackground,
                        unfocusedContainerColor = WorkshopBackground,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = WorkshopBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                                        tint = if (isSelected) WorkshopBackground else TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${cat.label.uppercase()} ($count)",
                                        color = if (isSelected) WorkshopBackground else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filtered List of Tools and Bits
                val filteredItems = toolsAndBits.filter { item ->
                    val matchesCategory = selectedCategory == ToolOrBitType.ALL || item.category == selectedCategory
                    val matchesSearch = searchQuery.isBlank() ||
                            item.name.contains(searchQuery, ignoreCase = true) ||
                            item.spec.contains(searchQuery, ignoreCase = true) ||
                            (item.oemPartNumber != null && item.oemPartNumber.contains(searchQuery, ignoreCase = true))
                    matchesCategory && matchesSearch
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tools or bits match the active filter",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filteredItems.forEach { item ->
                            val isFilteringThis = activeFilter?.id == item.id
                            val isInTray = trayCheckedIds.contains(item.id)

                            Surface(
                                color = if (isFilteringThis) CyanAccent.copy(alpha = 0.12f) else WorkshopBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isFilteringThis) 1.5.dp else 1.dp,
                                    color = if (isFilteringThis) CyanAccent else WorkshopBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: In-Tray Checkbox & Icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        IconButton(
                                            onClick = { onToggleTrayItem(item.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isInTray) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "In Tray",
                                                tint = if (isInTray) MintGreen else TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    color = if (isInTray) TextSecondary else TextPrimary,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (item.isCrucial) {
                                                    Surface(
                                                        color = CitroenRed.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(3.dp)
                                                    ) {
                                                        Text(
                                                            text = "CRUCIAL",
                                                            color = CitroenRed,
                                                            fontSize = 8.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Spec: ${item.spec}",
                                                    color = CyanAccent,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (item.torqueNm != null) {
                                                    Surface(
                                                        color = GoldenBolt.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(3.dp)
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

                                    // Right: Step-Filter Trigger Button
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isFilteringThis) CyanAccent else WorkshopSurfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isFilteringThis) CyanAccent else WorkshopBorder
                                        ),
                                        modifier = Modifier.clickable { onSelectFilter(item) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFilteringThis) Icons.Default.FilterListOff else Icons.Default.FilterList,
                                                contentDescription = null,
                                                tint = if (isFilteringThis) WorkshopBackground else CyanAccent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (isFilteringThis) "FILTERING" else "FILTER",
                                                color = if (isFilteringThis) WorkshopBackground else CyanAccent,
                                                fontSize = 9.5.sp,
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
        }
    }
}

@Composable
private fun ProcedureOverviewHeader(
    procedure: RepairProcedure,
    completedCount: Int,
    totalCount: Int
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
