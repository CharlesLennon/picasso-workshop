package com.picassoworkshop.app.dashboard.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Supported gauge visualization styles for dashboard widgets.
 */
@Serializable
enum class GaugeType(val displayName: String) {
    RADIAL_DIAL("Dial Gauge (Needle)"),
    DIGITAL_CARD("Digital Readout"),
    BAR_GAUGE("Bar Meter"),
    TREND_GRAPH("Live Waveform Graph"),
    STATUS_LIGHT("Warning / Status Tile")
}

/**
 * Configuration for an individual gauge widget inside a custom dashboard.
 */
@Serializable
data class DashboardWidgetConfig(
    val id: String = UUID.randomUUID().toString(),
    val pidId: String,                    // References PidMetadata.id (e.g. "ENGINE_RPM", "BOOST_PRESSURE")
    val customTitle: String? = null,      // User-defined title override
    val gaugeType: GaugeType = GaugeType.DIGITAL_CARD,
    val minVal: Double = 0.0,
    val maxVal: Double = 100.0,
    val warnThreshold: Double? = null,
    val dangerThreshold: Double? = null,
    val unitOverride: String? = null
)

/**
 * A custom or preset dashboard containing an ordered collection of telemetry gauge widgets.
 */
@Serializable
data class CustomDashboard(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val isPreset: Boolean = false,
    val isEditable: Boolean = true,
    val widgets: List<DashboardWidgetConfig> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Rich metadata for selectable OBD-II PIDs and sensors.
 */
data class PidMetadata(
    val id: String,
    val displayName: String,
    val standardCommand: String,
    val category: PidCategory,
    val defaultUnit: String,
    val defaultMin: Double,
    val defaultMax: Double,
    val defaultWarn: Double?,
    val defaultDanger: Double?,
    val recommendedGauge: GaugeType,
    val description: String
)

enum class PidCategory(val title: String) {
    ENGINE("Engine & RPM"),
    AIR_TURBO("Intake & Boost"),
    FUEL("Fuel & Rail Pressure"),
    ELECTRICAL("Battery & Charging"),
    SPEED_TRIP("Speed & Vehicle")
}

/**
 * Master catalog of all supported telemetry PIDs on the Citroën C4 Grand Picasso (DV6 1.6 HDi).
 */
object PidCatalog {
    const val PID_RPM = "ENGINE_RPM"
    const val PID_SPEED = "SPEED_KMH"
    const val PID_COOLANT_TEMP = "COOLANT_TEMP"
    const val PID_BATTERY_VOLT = "BATTERY_VOLTS"
    const val PID_BOOST_PRESSURE = "BOOST_PRESSURE"
    const val PID_RAIL_PRESSURE = "RAIL_PRESSURE"
    const val PID_INTAKE_TEMP = "INTAKE_AIR_TEMP"
    const val PID_ENGINE_LOAD = "ENGINE_LOAD"
    const val PID_THROTTLE_POS = "THROTTLE_POS"
    const val PID_FUEL_LEVEL = "FUEL_LEVEL"
    const val PID_MAF_RATE = "MAF_RATE"
    const val PID_OIL_TEMP = "OIL_TEMP"

    val ALL_PIDS = listOf(
        PidMetadata(
            id = PID_RPM,
            displayName = "Engine RPM",
            standardCommand = "010C",
            category = PidCategory.ENGINE,
            defaultUnit = "RPM",
            defaultMin = 0.0,
            defaultMax = 5000.0,
            defaultWarn = 4200.0,
            defaultDanger = 4600.0,
            recommendedGauge = GaugeType.RADIAL_DIAL,
            description = "Engine crankshaft rotational speed (DV6 idle 800-850 RPM, redline 4500 RPM)"
        ),
        PidMetadata(
            id = "SPEED_KMH",
            displayName = "Vehicle Speed",
            standardCommand = "010D",
            category = PidCategory.SPEED_TRIP,
            defaultUnit = "km/h",
            defaultMin = 0.0,
            defaultMax = 220.0,
            defaultWarn = 135.0,
            defaultDanger = 160.0,
            recommendedGauge = GaugeType.RADIAL_DIAL,
            description = "Vehicle road speed from ABS/ESP wheel speed sensors via CAN"
        ),
        PidMetadata(
            id = "COOLANT_TEMP",
            displayName = "Coolant Temperature",
            standardCommand = "0105",
            category = PidCategory.ENGINE,
            defaultUnit = "°C",
            defaultMin = 40.0,
            defaultMax = 120.0,
            defaultWarn = 96.0,
            defaultDanger = 105.0,
            recommendedGauge = GaugeType.BAR_GAUGE,
            description = "Engine coolant temperature (Nominal operating range: 82°C – 92°C)"
        ),
        PidMetadata(
            id = "BATTERY_VOLTS",
            displayName = "Battery / Charging Voltage",
            standardCommand = "ATRV",
            category = PidCategory.ELECTRICAL,
            defaultUnit = "V",
            defaultMin = 10.0,
            defaultMax = 16.0,
            defaultWarn = 12.0,
            defaultDanger = 11.5,
            recommendedGauge = GaugeType.DIGITAL_CARD,
            description = "12V system voltage (Key on: 12.2-12.6V, Alternator charging: 13.8-14.4V)"
        ),
        PidMetadata(
            id = "BOOST_PRESSURE",
            displayName = "Turbo Boost Pressure",
            standardCommand = "010B",
            category = PidCategory.AIR_TURBO,
            defaultUnit = "Bar",
            defaultMin = 0.0,
            defaultMax = 2.5,
            defaultWarn = 1.4,
            defaultDanger = 1.6,
            recommendedGauge = GaugeType.TREND_GRAPH,
            description = "Relative manifold gauge pressure from MAP sensor (DV6 1.6 HDi peak boost ~1.3 Bar)"
        ),
        PidMetadata(
            id = "RAIL_PRESSURE",
            displayName = "Common Rail Fuel Pressure",
            standardCommand = "0123",
            category = PidCategory.FUEL,
            defaultUnit = "Bar",
            defaultMin = 0.0,
            defaultMax = 1600.0,
            defaultWarn = 1500.0,
            defaultDanger = 1650.0,
            recommendedGauge = GaugeType.BAR_GAUGE,
            description = "Bosch CP1H high pressure rail (Idle ~250-300 Bar, Full throttle up to 1600 Bar)"
        ),
        PidMetadata(
            id = "INTAKE_AIR_TEMP",
            displayName = "Intake Air Temperature (IAT)",
            standardCommand = "010F",
            category = PidCategory.AIR_TURBO,
            defaultUnit = "°C",
            defaultMin = -10.0,
            defaultMax = 80.0,
            defaultWarn = 55.0,
            defaultDanger = 68.0,
            recommendedGauge = GaugeType.DIGITAL_CARD,
            description = "Air temperature downstream of intercooler"
        ),
        PidMetadata(
            id = "ENGINE_LOAD",
            displayName = "Calculated Engine Load",
            standardCommand = "0104",
            category = PidCategory.ENGINE,
            defaultUnit = "%",
            defaultMin = 0.0,
            defaultMax = 100.0,
            defaultWarn = 90.0,
            defaultDanger = 98.0,
            recommendedGauge = GaugeType.BAR_GAUGE,
            description = "Percentage of peak available engine torque being demanded"
        ),
        PidMetadata(
            id = "THROTTLE_POS",
            displayName = "Accelerator Pedal Position",
            standardCommand = "0111",
            category = PidCategory.ENGINE,
            defaultUnit = "%",
            defaultMin = 0.0,
            defaultMax = 100.0,
            defaultWarn = null,
            defaultDanger = null,
            recommendedGauge = GaugeType.BAR_GAUGE,
            description = "Electronic accelerator pedal twin-potentiometer travel"
        ),
        PidMetadata(
            id = "FUEL_LEVEL",
            displayName = "Fuel Tank Level",
            standardCommand = "012F",
            category = PidCategory.FUEL,
            defaultUnit = "%",
            defaultMin = 0.0,
            defaultMax = 100.0,
            defaultWarn = 15.0,
            defaultDanger = 7.0,
            recommendedGauge = GaugeType.BAR_GAUGE,
            description = "Diesel tank float sensor input from BSI gateway (60L tank capacity)"
        ),
        PidMetadata(
            id = "MAF_RATE",
            displayName = "Mass Air Flow (MAF)",
            standardCommand = "0110",
            category = PidCategory.AIR_TURBO,
            defaultUnit = "g/s",
            defaultMin = 0.0,
            defaultMax = 120.0,
            defaultWarn = 100.0,
            defaultDanger = 115.0,
            recommendedGauge = GaugeType.DIGITAL_CARD,
            description = "Air mass entering intake past hot-film sensor (Idle ~6-9 g/s, Load up to 90 g/s)"
        ),
        PidMetadata(
            id = "OIL_TEMP",
            displayName = "Engine Oil Temperature",
            standardCommand = "015C",
            category = PidCategory.ENGINE,
            defaultUnit = "°C",
            defaultMin = 40.0,
            defaultMax = 150.0,
            defaultWarn = 115.0,
            defaultDanger = 130.0,
            recommendedGauge = GaugeType.RADIAL_DIAL,
            description = "Engine sump oil temperature (Nominal ~90-105°C during sustained motorway driving)"
        )
    )

    fun getById(pidId: String): PidMetadata? = ALL_PIDS.firstOrNull { it.id == pidId }
}
