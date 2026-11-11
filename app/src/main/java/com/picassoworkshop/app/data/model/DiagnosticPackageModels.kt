package com.picassoworkshop.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticEcuConfig(
    val id: String,                         // e.g. "ENGINE_EDC16", "BSI_BODY", "AIR_SUSPENSION_BAP", "EGS6_TRANSMISSION"
    val displayName: String,                // e.g. "Engine Management (EDC16/DCM3.4)"
    val shortCode: String,                  // e.g. "ENGINE", "BSI", "SUSPENSION", "GEARBOX"
    val requestHeader: String,              // e.g. "7E0", "752", "7A0", "7E1", "7DF"
    val responseHeader: String,             // e.g. "7E8", "75A", "7A8", "7E9", "7E8"
    val protocolType: String = "CAN_500K",  // "CAN_500K", "KWP2000", "UDS"
    val initSequence: List<String> = emptyList(),
    val dtcReadCommands: List<String> = listOf("19 02 FF", "03"),
    val dtcClearCommands: List<String> = listOf("14 FF 00", "04"),
    val responseParser: String = "UDS_OR_STANDARD", // "STANDARD_MODE03", "PSA_UDS_SERVICE19", "BSI_HEX", "UDS_OR_STANDARD"
    val targetGeneration: Generation = Generation.MK1_B58,
    val targetEngine: EngineType = EngineType.DV6_16_HDI,
    val description: String = ""
)

@Serializable
data class DtcDefinition(
    val code: String,                         // e.g. "P1351", "C1196", "FC82", "P1382"
    val ecuId: String,                        // e.g. "ENGINE_EDC16", "AIR_SUSPENSION_BAP", "BSI_BODY"
    val title: String,                        // e.g. "Preheating Relay Circuit (Glow Plugs Not Supplied)"
    val technicalDescription: String,         // e.g. "The preheating control unit receives power..."
    val commonCauses: List<String> = emptyList(),
    val procedureIdLink: String? = null,      // e.g. "dv6_alternator_removal", "c4_rear_air_suspension_bellows"
    val severity: String = "WARNING"          // "CRITICAL", "WARNING", "INFO"
)

@Serializable
data class LivePidConfig(
    val pidId: String,                        // e.g. "BATTERY_VOLTS", "ENGINE_RPM", "COOLANT_TEMP"
    val displayName: String,                  // e.g. "Battery Voltage", "Engine RPM"
    val command: String,                      // e.g. "ATRV", "010C", "0105", "010B", "0123"
    val ecuHeader: String = "7DF",            // Header to send command to
    val responsePrefix: String = "",          // e.g. "", "410C", "4105", "410B", "4123"
    val unit: String,                         // e.g. "V", "RPM", "°C", "Bar"
    val formulaType: String,                  // "ATRV_VOLTS", "RPM_DIV4", "COOLANT_SUB40", "SPEED_RAW", "BOOST_KPA_TO_BAR", "RAIL_PRESSURE_BAR"
    val normalMin: Double? = null,
    val normalMax: Double? = null,
    val category: String = "ENGINE",          // "ENGINE", "AIR_TURBO", "FUEL", "ELECTRICAL", "SPEED_TRIP"
    val defaultMin: Double = 0.0,
    val defaultMax: Double = 100.0,
    val warnThreshold: Double? = null,
    val dangerThreshold: Double? = null,
    val recommendedGauge: String = "DIGITAL_CARD", // "RADIAL_DIAL", "BAR_GAUGE", "TREND_GRAPH", "DIGITAL_CARD", "STATUS_LIGHT"
    val description: String = ""
)
