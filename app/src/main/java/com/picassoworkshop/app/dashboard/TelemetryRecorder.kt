package com.picassoworkshop.app.dashboard

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.picassoworkshop.app.obd.model.LiveTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedTelemetrySample(
    val timestamp: Long,
    val rpm: Int,
    val speedKmh: Int,
    val coolantTempC: Int,
    val batteryVoltage: Float,
    val boostPressureBar: Float,
    val railPressureBar: Int,
    val intakeAirTempC: Int,
    val engineLoadPercent: Int,
    val throttlePercent: Int,
    val fuelLevelPercent: Int,
    val mafRateGps: Float,
    val oilTempC: Int
)

data class RecordingSessionSummary(
    val file: File,
    val sampleCount: Int,
    val durationSeconds: Long,
    val maxRpm: Int,
    val maxSpeed: Int,
    val maxBoostBar: Float,
    val maxRailPressureBar: Int
)

class TelemetryRecorder(private val context: Context) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedSamplesCount = MutableStateFlow(0)
    val recordedSamplesCount: StateFlow<Int> = _recordedSamplesCount.asStateFlow()

    private val samples = mutableListOf<RecordedTelemetrySample>()
    private var recordingStartTime = 0L

    fun startRecording() {
        samples.clear()
        recordingStartTime = System.currentTimeMillis()
        _recordedSamplesCount.value = 0
        _isRecording.value = true
        Timber.i("Started telemetry recording run")
    }

    fun recordSample(telemetry: LiveTelemetry) {
        if (!_isRecording.value) return

        val sample = RecordedTelemetrySample(
            timestamp = System.currentTimeMillis(),
            rpm = telemetry.rpm,
            speedKmh = telemetry.speedKmh,
            coolantTempC = telemetry.coolantTempC,
            batteryVoltage = telemetry.batteryVoltage,
            boostPressureBar = telemetry.boostPressureBar,
            railPressureBar = telemetry.railPressureBar,
            intakeAirTempC = telemetry.intakeAirTempC,
            engineLoadPercent = telemetry.engineLoadPercent,
            throttlePercent = telemetry.throttlePercent,
            fuelLevelPercent = telemetry.fuelLevelPercent,
            mafRateGps = telemetry.mafRateGps,
            oilTempC = telemetry.oilTempC
        )

        samples.add(sample)
        _recordedSamplesCount.value = samples.size
    }

    fun stopRecording(): RecordingSessionSummary? {
        if (!_isRecording.value || samples.isEmpty()) {
            _isRecording.value = false
            return null
        }

        _isRecording.value = false
        val durationMs = System.currentTimeMillis() - recordingStartTime
        val durationSec = durationMs / 1000

        val maxRpm = samples.maxOfOrNull { it.rpm } ?: 0
        val maxSpeed = samples.maxOfOrNull { it.speedKmh } ?: 0
        val maxBoost = samples.maxOfOrNull { it.boostPressureBar } ?: 0f
        val maxRail = samples.maxOfOrNull { it.railPressureBar } ?: 0

        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val csvFile = File(context.cacheDir, "PicassoTelemetry_DV6_$dateStr.csv")

        try {
            FileWriter(csvFile).use { writer ->
                // Header row
                writer.append("Timestamp_ms,Time_ISO,Elapsed_s,RPM,Speed_kmh,Coolant_C,Battery_V,Boost_Bar,RailPressure_Bar,IntakeAirTemp_C,EngineLoad_pct,Throttle_pct,Fuel_pct,MAF_gps,Oil_C\n")

                val baseTime = samples.firstOrNull()?.timestamp ?: recordingStartTime
                val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

                for (s in samples) {
                    val elapsedSec = String.format(Locale.US, "%.2f", (s.timestamp - baseTime) / 1000.0)
                    val timeIso = timeFormat.format(Date(s.timestamp))
                    writer.append("${s.timestamp},$timeIso,$elapsedSec,${s.rpm},${s.speedKmh},${s.coolantTempC},${s.batteryVoltage},${s.boostPressureBar},${s.railPressureBar},${s.intakeAirTempC},${s.engineLoadPercent},${s.throttlePercent},${s.fuelLevelPercent},${s.mafRateGps},${s.oilTempC}\n")
                }
            }

            Timber.i("Saved telemetry run to ${csvFile.absolutePath} (${samples.size} samples)")
            return RecordingSessionSummary(
                file = csvFile,
                sampleCount = samples.size,
                durationSeconds = durationSec,
                maxRpm = maxRpm,
                maxSpeed = maxSpeed,
                maxBoostBar = maxBoost,
                maxRailPressureBar = maxRail
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to write CSV telemetry log")
            return null
        }
    }

    fun shareCsvFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Citroën C4 Picasso Telemetry Run (${file.name})")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Telemetry CSV Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to share CSV file via intent")
        }
    }
}
