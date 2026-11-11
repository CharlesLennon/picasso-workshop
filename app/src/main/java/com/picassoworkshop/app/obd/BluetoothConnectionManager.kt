package com.picassoworkshop.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.picassoworkshop.app.obd.model.BluetoothDeviceInfo
import com.picassoworkshop.app.obd.model.ObdConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnectionManager(private val context: Context) {

    companion object {
        // Standard Bluetooth Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val DEMO_DEVICE_ADDRESS = "00:11:22:33:44:55"
        const val DEMO_DEVICE_NAME = "ELM327 PSA Simulator (Demo Mode)"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val protocolEngine = ElmProtocolEngine()

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val _connectionState = MutableStateFlow<ObdConnectionState>(ObdConnectionState.Disconnected)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<BluetoothDeviceInfo>> = _availableDevices.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    var isSimulationMode: Boolean = false
        private set

    init {
        refreshPairedDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val list = mutableListOf<BluetoothDeviceInfo>()

        // Always include Demo Simulator device
        list.add(
            BluetoothDeviceInfo(
                name = DEMO_DEVICE_NAME,
                address = DEMO_DEVICE_ADDRESS,
                isPaired = true
            )
        )

        try {
            val paired = bluetoothAdapter?.bondedDevices
            if (paired != null) {
                for (device in paired) {
                    list.add(
                        BluetoothDeviceInfo(
                            name = device.name ?: "Unknown OBD Device",
                            address = device.address,
                            isPaired = true
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Timber.w(e, "Bluetooth permissions not yet granted for listing paired devices")
        } catch (e: Exception) {
            Timber.e(e, "Error reading paired Bluetooth devices")
        }

        _availableDevices.value = list
    }

    fun connectToDevice(deviceInfo: BluetoothDeviceInfo) {
        if (deviceInfo.address == DEMO_DEVICE_ADDRESS) {
            connectDemoSimulator()
            return
        }

        scope.launch {
            _connectionState.value = ObdConnectionState.Connecting(deviceInfo.name)
            isSimulationMode = false

            try {
                disconnectInternal()

                val device = bluetoothAdapter?.getRemoteDevice(deviceInfo.address)
                    ?: throw Exception("Device not found with address ${deviceInfo.address}")

                _connectionState.value = ObdConnectionState.Initializing("Opening RFCOMM channel...")

                @SuppressLint("MissingPermission")
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()

                activeSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                // Initialize ELM327 with AT sequence
                initializeElmAdapter(deviceInfo.name)

            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to OBD device ${deviceInfo.name}")
                disconnectInternal()
                _connectionState.value = ObdConnectionState.Error("Connection failed: ${e.message}")
            }
        }
    }

    private suspend fun initializeElmAdapter(deviceName: String) {
        _connectionState.value = ObdConnectionState.Initializing("Negotiating ELM327 protocol...")

        for (cmd in ElmProtocolEngine.INIT_COMMANDS) {
            _connectionState.value = ObdConnectionState.Initializing("Sending $cmd...")
            val response = sendCommandRaw(cmd, timeoutMs = 1500)
            Timber.d("ELM Init: $cmd -> $response")
        }

        // Query ELM version
        val versionResponse = sendCommandRaw("ATI", timeoutMs = 1000)
        val cleanVersion = protocolEngine.cleanResponse(versionResponse).ifBlank { "ELM327 v1.5" }

        _connectionState.value = ObdConnectionState.Connected(
            deviceName = deviceName,
            elmVersion = cleanVersion
        )
        Timber.i("Successfully connected and initialized ELM327 on $deviceName ($cleanVersion)")
    }

    private fun connectDemoSimulator() {
        scope.launch {
            _connectionState.value = ObdConnectionState.Connecting(DEMO_DEVICE_NAME)
            isSimulationMode = true

            // Simulate handshake steps
            _connectionState.value = ObdConnectionState.Initializing("Resetting virtual ELM327 (ATZ)...")
            kotlinx.coroutines.delay(350)
            _connectionState.value = ObdConnectionState.Initializing("Setting PSA 500k CAN Protocol (ATSP6)...")
            kotlinx.coroutines.delay(350)
            _connectionState.value = ObdConnectionState.Initializing("Enabling CAN Headers (ATH1)...")
            kotlinx.coroutines.delay(300)

            _connectionState.value = ObdConnectionState.Connected(
                deviceName = DEMO_DEVICE_NAME,
                elmVersion = "ELM327 v1.5 (PSA C4 Picasso Simulator)"
            )
        }
    }

    suspend fun sendCommand(command: String, timeoutMs: Long = 2000): String {
        return withContext(Dispatchers.IO) {
            if (isSimulationMode) {
                simulateCommandResponse(command)
            } else {
                sendCommandRaw(command, timeoutMs)
            }
        }
    }

    private fun sendCommandRaw(command: String, timeoutMs: Long = 2000): String {
        val out = outputStream ?: throw Exception("Not connected to OBD adapter")
        val inStream = inputStream ?: throw Exception("Not connected to OBD adapter")

        val commandBytes = "$command\r".toByteArray(Charsets.US_ASCII)
        out.write(commandBytes)
        out.flush()

        val buffer = StringBuilder()
        val startTime = System.currentTimeMillis()
        val readBuffer = ByteArray(128)

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (inStream.available() > 0) {
                val bytesRead = inStream.read(readBuffer)
                if (bytesRead > 0) {
                    val part = String(readBuffer, 0, bytesRead, Charsets.US_ASCII)
                    buffer.append(part)
                    if (buffer.contains(ElmProtocolEngine.PROMPT_CHAR)) {
                        break
                    }
                }
            } else {
                Thread.sleep(15)
            }
        }

        return buffer.toString()
    }

    private fun simulateCommandResponse(command: String): String {
        Thread.sleep(80) // Simulate CAN bus latency
        val cmd = command.trim().uppercase()
        return when {
            cmd == "ATZ" -> "ELM327 v1.5\r\r>"
            cmd == "ATE0" || cmd == "ATL0" || cmd == "ATS0" || cmd == "ATH1" || cmd == "ATCAF1" || cmd.startsWith("ATSP") || cmd.startsWith("ATSH") -> "OK\r\r>"
            cmd == "ATI" -> "ELM327 v1.5 (Citroën PSA Sim)\r\r>"
            cmd == "ATRV" -> "14.2V\r\r>"
            cmd == "0100" -> "41 00 BE 3F B8 11\r\r>"
            cmd == "0105" -> "41 05 80\r\r>" // 80 hex = 128 dec - 40 = 88 °C Coolant
            cmd == "010C" -> "41 0C 0C E0\r\r>" // 0C E0 hex = 3296 / 4 = 824 RPM
            cmd == "010D" -> "41 0D 00\r\r>" // 0 km/h (idle in workshop)
            cmd == "010B" -> "41 0B 7A\r\r>" // 7A hex = 122 kPa -> 0.21 bar boost at idle
            cmd == "0123" -> "41 23 0B 80\r\r>" // 0B 80 hex = 2944 kPa = 294 bar rail pressure
            cmd == "03" -> "43 02 02 99 13 82\r\r>" // P0299 (Turbo underboost), P1382 (Alternator slip)
            cmd == "07" -> "47 01 13 51 00 00\r\r>" // P1351 (Preheating relay glow plugs)
            cmd.startsWith("19 02") -> {
                // PSA UDS Response: P1351 (glow plugs), P1382 (alternator freewheel), C1196 (air suspension)
                "59 02 FF 13 51 00 28 13 82 12 28 51 96 00 28\r\r>"
            }
            cmd == "04" || cmd.startsWith("14 FF") -> "44\r\rOK\r\r>" // Clear DTC positive response
            else -> "NO DATA\r\r>"
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal()
            _connectionState.value = ObdConnectionState.Disconnected
        }
    }

    private fun disconnectInternal() {
        try {
            inputStream?.close()
            outputStream?.close()
            activeSocket?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing Bluetooth socket")
        } finally {
            inputStream = null
            outputStream = null
            activeSocket = null
        }
    }
}
