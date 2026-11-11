package com.picassoworkshop.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProcedureCategory(val title: String) {
    ENGINE_ELECTRICAL("Engine & Electrical"),
    AUXILIARY_DRIVE("Auxiliary Drive & Belts"),
    FUEL_AND_INTAKE("Fuel & Air Intake"),
    TURBO_EXHAUST("Turbo & Exhaust"),
    SUSPENSION("Suspension & Air Springs"),
    BRAKES_AND_CHASSIS("Brakes & Chassis")
}

@Serializable
enum class DifficultyLevel(val label: String, val stars: Int) {
    BEGINNER("Beginner", 1),
    INTERMEDIATE("Intermediate", 2),
    ADVANCED("Advanced", 3),
    EXPERT("Expert / Workshop", 4)
}

@Serializable
data class TorqueSpec(
    val componentName: String,
    val torqueNm: Double,
    val fastenerSize: String,
    val angleDegrees: Int? = null,
    val notes: String = ""
)

@Serializable
data class ToolRequirement(
    val name: String,
    val size: String = "",
    val category: String = "HAND_TOOL", // WRENCH, SOCKET, TORX, SPECIAL_PIN, JACK
    val isCrucial: Boolean = true
)

@Serializable
data class DisassemblyStep(
    val stepNumber: Int,
    val title: String,
    val instructions: String,
    val caution: String? = null,
    val warning: String? = null,
    val tool: ToolRequirement? = null,
    val associatedPartId: String? = null,
    val calloutNumber: Int? = null,
    val graphicKey: String = ""
)

@Serializable
data class ExplodedPart(
    val id: String,
    val calloutNumber: Int,
    val name: String,
    val oemPartNumber: String,
    val fastenerType: String = "",
    val torqueNm: Double? = null,
    // 2D Blown-Out schematic position coordinates
    val assemblyX: Float = 0f,
    val assemblyY: Float = 0f,
    val explodedX: Float = 0f,
    val explodedY: Float = 0f,
    // 3D Spatial parameters
    val explodeDirX: Float = 0f,
    val explodeDirY: Float = 0f,
    val explodeDirZ: Float = 0f,
    val explodeDistance: Float = 1.0f,
    val meshShape: String = "BOX", // CYLINDER, BOX, PULLEY, BOLT, BRACKET, BELT
    val width: Float = 40f,
    val height: Float = 40f,
    val depth: Float = 40f,
    val colorHex: String = "#38BDF8",
    val description: String = ""
)

@Serializable
data class RepairProcedure(
    val id: String,
    val title: String,
    val targetGeneration: Generation = Generation.MK1_B58,
    val targetEngine: EngineType = EngineType.DV6_16_HDI,
    val category: ProcedureCategory,
    val difficulty: DifficultyLevel,
    val estimatedMinutes: Int,
    val summary: String,
    val safetyNotes: List<String> = emptyList(),
    val toolsNeeded: List<ToolRequirement> = emptyList(),
    val torqueSpecs: List<TorqueSpec> = emptyList(),
    val steps: List<DisassemblyStep> = emptyList(),
    val explodedParts: List<ExplodedPart> = emptyList()
)
