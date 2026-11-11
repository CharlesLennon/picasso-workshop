package com.picassoworkshop.app.obd

import com.picassoworkshop.app.obd.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class ObdDiagnosticsCoordinator(
    val connectionManager: BluetoothConnectionManager
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

            try {
                if (mode == ScanMode.GENERIC_POWERTRAIN) {
                    scanGenericPowertrain(results)
                } else {
                    scanDeepPsaMultiEcu(results)
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

    private suspend fun scanGenericPowertrain(results: MutableList<DiagnosticFault>) {
        // Set header to standard generic OBD broadcast
        connectionManager.sendCommand("ATSH 7DF")

        // 1. Stored DTCs (Mode 03)
        val mode03Response = connectionManager.sendCommand("03")
        val storedFaults = protocolEngine.parseStandardMode03Response(mode03Response, EcuTarget.GENERIC_OBD)
        results.addAll(storedFaults)

        // 2. Pending DTCs (Mode 07)
        val mode07Response = connectionManager.sendCommand("07")
        val pendingFaults = protocolEngine.parseStandardMode03Response(mode07Response, EcuTarget.GENERIC_OBD)
        results.addAll(pendingFaults)
    }

    private suspend fun scanDeepPsaMultiEcu(results: MutableList<DiagnosticFault>) {
        val ecusToScan = listOf(
            EcuTarget.ENGINE_EDC16,
            EcuTarget.AIR_SUSPENSION_BAP,
            EcuTarget.EGS6_TRANSMISSION,
            EcuTarget.BSI_BODY
        )

        for (ecu in ecusToScan) {
            try {
                // Set CAN header for target ECU
                connectionManager.sendCommand("ATSH ${ecu.requestHeader}")

                // 1. Try PSA UDS Service 19 02 FF (Read DTC Information)
                val udsResponse = connectionManager.sendCommand("19 02 FF")
                val udsFaults = protocolEngine.parsePsaUdsService19Response(udsResponse, ecu)
                results.addAll(udsFaults)

                // 2. Fallback to standard Mode 03 for this header
                val mode03Response = connectionManager.sendCommand("03")
                val mode03Faults = protocolEngine.parseStandardMode03Response(mode03Response, ecu)
                results.addAll(mode03Faults)

                // 3. For BSI, also scan for raw hex codes (Fxxx)
                if (ecu == EcuTarget.BSI_BODY) {
                    val bsiHexFaults = protocolEngine.parseBsiHexResponse(udsResponse + " " + mode03Response)
                    results.addAll(bsiHexFaults)
                }

            } catch (e: Exception) {
                Timber.w(e, "Error scanning ECU ${ecu.displayName}")
            }
        }

        // In simulation mode, add typical Picasso DV6 faults if none returned
        if (connectionManager.isSimulationMode && results.isEmpty()) {
            results.add(PsaDtcDatabase.lookupFault("P1351", EcuTarget.ENGINE_EDC16))
            results.add(PsaDtcDatabase.lookupFault("P1382", EcuTarget.ENGINE_EDC16))
            results.add(PsaDtcDatabase.lookupFault("C1196", EcuTarget.AIR_SUSPENSION_BAP))
            results.add(PsaDtcDatabase.lookupFault("FC82", EcuTarget.BSI_BODY))
        }
    }

    fun clearAllFaults(onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                // 1. Generic Clear (Mode 04)
                connectionManager.sendCommand("ATSH 7DF")
                connectionManager.sendCommand("04")

                // 2. Clear target ECUs with UDS 14 FF 00
                val ecus = listOf(
                    EcuTarget.ENGINE_EDC16,
                    EcuTarget.AIR_SUSPENSION_BAP,
                    EcuTarget.EGS6_TRANSMISSION,
                    EcuTarget.BSI_BODY
                )

                for (ecu in ecus) {
                    connectionManager.sendCommand("ATSH ${ecu.requestHeader}")
                    connectionManager.sendCommand("14 FF 00")
                }

                _detectedFaults.value = emptyList()
                Timber.i("All DTCs cleared across ECUs")
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
            // Restore generic header for PIDs
            connectionManager.sendCommand("ATSH 7DF")

            while (isActive) {
                try {
                    // Battery Voltage
                    val vRaw = connectionManager.sendCommand("ATRV", timeoutMs = 600)
                    val volts = protocolEngine.parseBatteryVoltage(vRaw)

                    // Coolant Temp
                    val cRaw = connectionManager.sendCommand("0105", timeoutMs = 600)
                    val coolant = protocolEngine.parseCoolantTemp(cRaw)

                    // RPM
                    val rpmRaw = connectionManager.sendCommand("010C", timeoutMs = 600)
                    val rpm = protocolEngine.parseRpm(rpmRaw)

                    // Speed
                    val spdRaw = connectionManager.sendCommand("010D", timeoutMs = 600)
                    val speed = protocolEngine.parseSpeed(spdRaw)

                    // Boost
                    val bRaw = connectionManager.sendCommand("010B", timeoutMs = 600)
                    val boost = protocolEngine.parseBoostPressure(bRaw)

                    // Rail Pressure
                    val rRaw = connectionManager.sendCommand("0123", timeoutMs = 600)
                    val rail = protocolEngine.parseRailPressure(rRaw)

                    _liveTelemetry.value = LiveTelemetry(
                        rpm = if (connectionManager.isSimulationMode && rpm == 0) 824 else rpm,
                        speedKmh = speed,
                        coolantTempC = if (connectionManager.isSimulationMode && coolant == 0) 88 else coolant,
                        batteryVoltage = if (connectionManager.isSimulationMode && volts == 0f) 14.2f else volts,
                        boostPressureBar = if (connectionManager.isSimulationMode && boost == 0f) 0.22f else boost,
                        railPressureBar = if (connectionManager.isSimulationMode && rail == 0) 295 else rail,
                        isEngineRunning = (rpm > 400 || (connectionManager.isSimulationMode))
                    )

                } catch (e: Exception) {
                    Timber.w(e, "Error polling live telemetry PIDs")
                }

                delay(600) // Poll interval
            }
        }
    }

    fun stopLiveTelemetryPolling() {
        telemetryJob?.cancel()
        telemetryJob = null
    }
}
