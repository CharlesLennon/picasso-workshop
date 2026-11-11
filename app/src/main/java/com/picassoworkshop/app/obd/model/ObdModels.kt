package com.picassoworkshop.app.obd.model

enum class DtcSeverity(val label: String) {
    CRITICAL("CRITICAL FAULT"),
    WARNING("WARNING"),
    INFO("INFORMATIONAL")
}

enum class EcuTarget(
    val code: String,
    val displayName: String,
    val requestHeader: String,
    val responseHeader: String,
    val description: String
) {
    GENERIC_OBD("GENERIC", "Generic OBD-II", "7DF", "7E8", "Standard powertrain emissions broadcast"),
    ENGINE_EDC16("ENGINE", "Engine ECU (EDC16/DCM3.4)", "7E0", "7E8", "PSA 1.6/2.0 HDi Engine Management"),
    BSI_BODY("BSI", "BSI (Body Systems Interface)", "752", "75A", "Central comfort, lighting & security gateway"),
    AIR_SUSPENSION_BAP("SUSPENSION", "Pneumatic Air Suspension (BAP)", "7A0", "7A8", "Rear axle compressor & height sensors"),
    EGS6_TRANSMISSION("GEARBOX", "EGS6 Automated Transmission", "7E1", "7E9", "Robotized 6-speed clutch & hydraulic actuators")
}

data class DiagnosticFault(
    val code: String,
    val symptomByte: String? = null,
    val ecu: EcuTarget,
    val title: String,
    val technicalDescription: String,
    val commonCauses: List<String>,
    val procedureIdLink: String? = null, // Links to PicassoWorkshop repair procedure!
    val rawHex: String = "",
    val severity: DtcSeverity = DtcSeverity.WARNING,
    val isClearable: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    val fullCodeDisplay: String
        get() = if (symptomByte.isNullOrBlank()) code else "$code-$symptomByte"
}

data class LiveTelemetry(
    val rpm: Int = 0,
    val speedKmh: Int = 0,
    val coolantTempC: Int = 0,
    val batteryVoltage: Float = 0f,
    val boostPressureBar: Float = 0f,
    val railPressureBar: Int = 0,
    val intakeAirTempC: Int = 0,
    val engineLoadPercent: Int = 0,
    val throttlePercent: Int = 0,
    val fuelLevelPercent: Int = 0,
    val mafRateGps: Float = 0f,
    val oilTempC: Int = 0,
    val isEngineRunning: Boolean = false,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val sensorValues: Map<String, Double> = emptyMap(),
    val customPidValues: Map<String, Double> = emptyMap()
) {
    fun getValueForPid(pidId: String): Double {
        sensorValues[pidId]?.let { return it }
        customPidValues[pidId]?.let { return it }

        return when (pidId) {
            "ENGINE_RPM" -> rpm.toDouble()
            "SPEED_KMH" -> speedKmh.toDouble()
            "COOLANT_TEMP" -> coolantTempC.toDouble()
            "BATTERY_VOLTS" -> batteryVoltage.toDouble()
            "BOOST_PRESSURE" -> boostPressureBar.toDouble()
            "RAIL_PRESSURE" -> railPressureBar.toDouble()
            "INTAKE_AIR_TEMP" -> intakeAirTempC.toDouble()
            "ENGINE_LOAD" -> engineLoadPercent.toDouble()
            "THROTTLE_POS" -> throttlePercent.toDouble()
            "FUEL_LEVEL" -> fuelLevelPercent.toDouble()
            "MAF_RATE" -> mafRateGps.toDouble()
            "OIL_TEMP" -> oilTempC.toDouble()
            else -> 0.0
        }
    }
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean = true
)

enum class ScanMode {
    GENERIC_POWERTRAIN,
    DEEP_PSA_MULTI_ECU
}

sealed class ObdConnectionState {
    object Disconnected : ObdConnectionState()
    object ScanningDevices : ObdConnectionState()
    data class Connecting(val deviceName: String) : ObdConnectionState()
    data class Initializing(val step: String) : ObdConnectionState()
    data class Connected(val deviceName: String, val elmVersion: String) : ObdConnectionState()
    data class ScanningDtcs(val currentEcu: EcuTarget, val progress: Float) : ObdConnectionState()
    data class ClearingDtcs(val ecu: EcuTarget) : ObdConnectionState()
    data class Error(val message: String) : ObdConnectionState()
}
