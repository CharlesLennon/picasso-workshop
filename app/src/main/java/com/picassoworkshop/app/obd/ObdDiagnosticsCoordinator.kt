package com.picassoworkshop.app.obd

import com.picassoworkshop.app.data.WorkshopDataManager
import com.picassoworkshop.app.data.model.DiagnosticEcuConfig
import com.picassoworkshop.app.data.model.LivePidConfig
import com.picassoworkshop.app.obd.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class ObdDiagnosticsCoordinator(
    val connectionManager: BluetoothConnectionManager,
    val dataManager: WorkshopDataManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val protocolEngine = ElmProtocolEngine()

    private val _detectedFaults = MutableStateFlow<List<DiagnosticFault>>(emptyList())
    val detectedFaults: StateFlow<List<DiagnosticFault>> = _detectedFaults.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
    val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var telemetryJob: Job? = null

    fun scanForFaults(mode: ScanMode) {
        if (_isScanning.value) return

        scope.launch {
            _isScanning.value = true
            val results = mutableListOf<DiagnosticFault>()
            val pkg = dataManager.contentPackage.value
            val dtcDefs = pkg.dtcDefinitions

            try {
                if (mode == ScanMode.GENERIC_POWERTRAIN) {
                    scanGenericPowertrain(results, dtcDefs)
                } else {
                    scanDeepPsaMultiEcu(results, pkg.diagnosticEcus, dtcDefs)
                }

                // Remove duplicate fault codes
                val distinctFaults = results.distinctBy { it.fullCodeDisplay }
                _detectedFaults.value = distinctFaults
                Timber.i("OBD Scan finished. Found ${distinctFaults.size} faults.")

            } catch (e: Exception) {
                Timber.e(e, "Error during OBD diagnostic scan")
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun scanGenericPowertrain(
        results: MutableList<DiagnosticFault>,
        definitions: List<com.picassoworkshop.app.data.model.DtcDefinition>
    ) {
        // Set header to standard generic OBD broadcast
        connectionManager.sendCommand("ATSH 7DF")

        // 1. Stored DTCs (Mode 03)
        val mode03Response = connectionManager.sendCommand("03")
        val storedFaults = protocolEngine.parseStandardMode03Response(mode03Response, EcuTarget.GENERIC_OBD, definitions)
        results.addAll(storedFaults)

        // 2. Pending DTCs (Mode 07)
        val mode07Response = connectionManager.sendCommand("07")
        val pendingFaults = protocolEngine.parseStandardMode03Response(mode07Response, EcuTarget.GENERIC_OBD, definitions)
        results.addAll(pendingFaults)
    }

    private suspend fun scanDeepPsaMultiEcu(
        results: MutableList<DiagnosticFault>,
        ecuConfigs: List<DiagnosticEcuConfig>,
        definitions: List<com.picassoworkshop.app.data.model.DtcDefinition>
    ) {
        // If data package has diagnosticEcus, query those directly from data!
        // Fallback to standard PSA ECUs if data package is still loading
        val ecusToQuery: List<DiagnosticEcuConfig> = if (ecuConfigs.isNotEmpty()) {
            ecuConfigs
        } else {
            listOf(
                DiagnosticEcuConfig(
                    id = "ENGINE_EDC16",
                    displayName = "Engine Management (EDC16/DCM3.4)",
                    shortCode = "ENGINE",
                    requestHeader = "7E0",
                    responseHeader = "7E8",
                    dtcReadCommands = listOf("19 02 FF", "03"),
                    responseParser = "UDS_OR_STANDARD"
                ),
                DiagnosticEcuConfig(
                    id = "AIR_SUSPENSION_BAP",
                    displayName = "Pneumatic Air Suspension (BAP)",
                    shortCode = "SUSPENSION",
                    requestHeader = "7A0",
                    responseHeader = "7A8",
                    dtcReadCommands = listOf("19 02 FF", "03"),
                    responseParser = "UDS_OR_STANDARD"
                ),
                DiagnosticEcuConfig(
                    id = "EGS6_TRANSMISSION",
                    displayName = "EGS6 Automated Transmission",
                    shortCode = "GEARBOX",
                    requestHeader = "7E1",
                    responseHeader = "7E9",
                    dtcReadCommands = listOf("19 02 FF", "03"),
                    responseParser = "UDS_OR_STANDARD"
                ),
                DiagnosticEcuConfig(
                    id = "BSI_BODY",
                    displayName = "BSI (Body Systems Interface)",
                    shortCode = "BSI",
                    requestHeader = "752",
                    responseHeader = "75A",
                    dtcReadCommands = listOf("19 02 FF", "03"),
                    responseParser = "BSI_HEX"
                )
            )
        }

        for (ecu in ecusToQuery) {
            try {
                // Set CAN header for target ECU from data
                connectionManager.sendCommand("ATSH ${ecu.requestHeader}")

                val targetEnum = mapEcuIdToEnum(ecu.id)

                for (cmd in ecu.dtcReadCommands) {
                    val rawResp = connectionManager.sendCommand(cmd)

                    when (ecu.responseParser) {
                        "PSA_UDS_SERVICE19" -> {
                            val udsFaults = protocolEngine.parsePsaUdsService19Response(rawResp, targetEnum, definitions)
                            results.addAll(udsFaults)
                        }
                        "STANDARD_MODE03" -> {
                            val mode03Faults = protocolEngine.parseStandardMode03Response(rawResp, targetEnum, definitions)
                            results.addAll(mode03Faults)
                        }
                        "BSI_HEX" -> {
                            val bsiHexFaults = protocolEngine.parseBsiHexResponse(rawResp, definitions)
                            results.addAll(bsiHexFaults)
                        }
                        else -> { // "UDS_OR_STANDARD"
                            val udsFaults = protocolEngine.parsePsaUdsService19Response(rawResp, targetEnum, definitions)
                            if (udsFaults.isNotEmpty()) {
                                results.addAll(udsFaults)
                            } else {
                                val mode03Faults = protocolEngine.parseStandardMode03Response(rawResp, targetEnum, definitions)
                                results.addAll(mode03Faults)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.w(e, "Error scanning ECU ${ecu.displayName}")
            }
        }

        // In simulation mode, add typical Picasso DV6 faults if none returned
        if (connectionManager.isSimulationMode && results.isEmpty()) {
            results.add(PsaDtcDatabase.lookupFault("P1351", EcuTarget.ENGINE_EDC16, definitions = definitions))
            results.add(PsaDtcDatabase.lookupFault("P1382", EcuTarget.ENGINE_EDC16, definitions = definitions))
            results.add(PsaDtcDatabase.lookupFault("C1196", EcuTarget.AIR_SUSPENSION_BAP, definitions = definitions))
            results.add(PsaDtcDatabase.lookupFault("FC82", EcuTarget.BSI_BODY, definitions = definitions))
        }
    }

    private fun mapEcuIdToEnum(ecuId: String): EcuTarget {
        return when (ecuId.uppercase()) {
            "ENGINE_EDC16", "ENGINE" -> EcuTarget.ENGINE_EDC16
            "BSI_BODY", "BSI" -> EcuTarget.BSI_BODY
            "AIR_SUSPENSION_BAP", "SUSPENSION" -> EcuTarget.AIR_SUSPENSION_BAP
            "EGS6_TRANSMISSION", "GEARBOX" -> EcuTarget.EGS6_TRANSMISSION
            else -> EcuTarget.GENERIC_OBD
        }
    }

    fun clearAllFaults(onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                val pkg = dataManager.contentPackage.value
                val ecusToClear = if (pkg.diagnosticEcus.isNotEmpty()) {
                    pkg.diagnosticEcus
                } else {
                    listOf(
                        DiagnosticEcuConfig("GENERIC", "Generic", "GEN", "7DF", "7E8", dtcClearCommands = listOf("04")),
                        DiagnosticEcuConfig("ENGINE", "Engine", "ENG", "7E0", "7E8", dtcClearCommands = listOf("14 FF 00")),
                        DiagnosticEcuConfig("AIR", "Suspension", "AIR", "7A0", "7A8", dtcClearCommands = listOf("14 FF 00")),
                        DiagnosticEcuConfig("EGS", "Gearbox", "EGS", "7E1", "7E9", dtcClearCommands = listOf("14 FF 00")),
                        DiagnosticEcuConfig("BSI", "BSI", "BSI", "752", "75A", dtcClearCommands = listOf("14 FF 00"))
                    )
                }

                // 1. Generic Clear (Mode 04)
                connectionManager.sendCommand("ATSH 7DF")
                connectionManager.sendCommand("04")

                // 2. Clear target ECUs specified in data package
                for (ecu in ecusToClear) {
                    connectionManager.sendCommand("ATSH ${ecu.requestHeader}")
                    for (clearCmd in ecu.dtcClearCommands) {
                        connectionManager.sendCommand(clearCmd)
                    }
                }

                _detectedFaults.value = emptyList()
                Timber.i("All DTCs cleared across ECUs specified in data package")
                onComplete(true)
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear DTCs")
                onComplete(false)
            }
        }
    }

    fun startLiveTelemetryPolling() {
        if (telemetryJob?.isActive == true) return

        telemetryJob = scope.launch {
            var currentHeader = ""
            var simCounter = 0

            while (isActive) {
                try {
                    simCounter++
                    // In simulation mode, generate realistic slight dynamic fluctuations
                    val simPhase = (simCounter % 20) / 20.0
                    val simRpm = (820 + (Math.sin(simPhase * Math.PI * 2) * 250)).toInt()
                    val simSpeed = (48 + (Math.sin(simPhase * Math.PI) * 12)).toInt()
                    val simBoost = (0.25f + (Math.max(0.0, Math.sin(simPhase * Math.PI * 2)) * 0.85f)).toFloat()
                    val simRail = (290 + (Math.max(0.0, Math.sin(simPhase * Math.PI * 2)) * 520)).toInt()
                    val simLoad = (25 + (Math.sin(simPhase * Math.PI * 2) * 18)).toInt()
                    val simThrottle = (18 + (Math.sin(simPhase * Math.PI * 2) * 12)).toInt()
                    val simMaf = (12.4f + (Math.sin(simPhase * Math.PI * 2) * 15.0f)).toFloat()

                    val sensorValuesMap = mutableMapOf<String, Double>()
                    val sensorPointers = dataManager.getLivePids()

                    for (sensor in sensorPointers) {
                        val header = sensor.ecuHeader.ifBlank { "7DF" }
                        if (header != currentHeader && !connectionManager.isSimulationMode) {
                            connectionManager.sendCommand("ATSH $header")
                            currentHeader = header
                        }

                        val raw = connectionManager.sendCommand(sensor.command, timeoutMs = 600)
                        val decoded = protocolEngine.decodeSensorValue(sensor, raw)
                        if (decoded != null && decoded > 0.0) {
                            sensorValuesMap[sensor.pidId] = decoded
                        } else if (connectionManager.isSimulationMode) {
                            val simVal = when (sensor.pidId) {
                                "ENGINE_RPM" -> simRpm.toDouble()
                                "SPEED_KMH" -> simSpeed.toDouble()
                                "COOLANT_TEMP" -> 88.0
                                "BATTERY_VOLTS" -> 14.2
                                "BOOST_PRESSURE" -> simBoost.toDouble()
                                "RAIL_PRESSURE" -> simRail.toDouble()
                                "INTAKE_AIR_TEMP" -> 26.0
                                "ENGINE_LOAD" -> simLoad.toDouble()
                                "THROTTLE_POS" -> simThrottle.toDouble()
                                "FUEL_LEVEL" -> 68.0
                                "MAF_RATE" -> simMaf.toDouble()
                                "OIL_TEMP" -> 91.0
                                else -> 50.0
                            }
                            sensorValuesMap[sensor.pidId] = simVal
                        }
                    }

                    _liveTelemetry.value = LiveTelemetry(
                        rpm = (sensorValuesMap["ENGINE_RPM"] ?: 0.0).toInt(),
                        speedKmh = (sensorValuesMap["SPEED_KMH"] ?: 0.0).toInt(),
                        coolantTempC = (sensorValuesMap["COOLANT_TEMP"] ?: 0.0).toInt(),
                        batteryVoltage = (sensorValuesMap["BATTERY_VOLTS"] ?: 0.0).toFloat(),
                        boostPressureBar = (sensorValuesMap["BOOST_PRESSURE"] ?: 0.0).toFloat(),
                        railPressureBar = (sensorValuesMap["RAIL_PRESSURE"] ?: 0.0).toInt(),
                        intakeAirTempC = (sensorValuesMap["INTAKE_AIR_TEMP"] ?: 0.0).toInt(),
                        engineLoadPercent = (sensorValuesMap["ENGINE_LOAD"] ?: 0.0).toInt(),
                        throttlePercent = (sensorValuesMap["THROTTLE_POS"] ?: 0.0).toInt(),
                        fuelLevelPercent = (sensorValuesMap["FUEL_LEVEL"] ?: 0.0).toInt(),
                        mafRateGps = (sensorValuesMap["MAF_RATE"] ?: 0.0).toFloat(),
                        oilTempC = (sensorValuesMap["OIL_TEMP"] ?: 0.0).toInt(),
                        isEngineRunning = ((sensorValuesMap["ENGINE_RPM"] ?: 0.0) > 400.0 || connectionManager.isSimulationMode),
                        sensorValues = sensorValuesMap,
                        customPidValues = sensorValuesMap
                    )

                } catch (e: Exception) {
                    Timber.w(e, "Error polling live telemetry PIDs")
                }

                delay(600) // Poll interval
            }
        }
    }

    suspend fun sendRawCommand(command: String): String {
        return connectionManager.sendCommand(command)
    }

    fun stopLiveTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = null
    }
}
