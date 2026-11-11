package com.picassoworkshop.app.obd

import com.picassoworkshop.app.data.model.DtcDefinition
import com.picassoworkshop.app.obd.model.DiagnosticFault
import com.picassoworkshop.app.obd.model.EcuTarget
import com.picassoworkshop.app.obd.model.LiveTelemetry
import timber.log.Timber

class ElmProtocolEngine {

    companion object {
        const val PROMPT_CHAR = '>'
        const val ELM_OK = "OK"
        const val ELM_NO_DATA = "NO DATA"
        const val ELM_UNABLE_TO_CONNECT = "UNABLE TO CONNECT"

        val INIT_COMMANDS = listOf(
            "ATZ",       // Reset ELM327
            "ATE0",      // Echo off
            "ATL0",      // Linefeeds off
            "ATS0",      // Spaces off
            "ATH1",      // Headers ON (crucial to see which ECU responds)
            "ATCAF1",    // CAN Auto Formatting ON
            "ATSP6"      // ISO 15765-4 CAN 11-bit 500kbaud (PSA standard)
        )
    }

    fun cleanResponse(raw: String): String {
        // Strip ISO-TP multi-frame line headers (e.g. "0: 59 02 ...", "1: 51 96 ...")
        val strippedLines = raw.lines().joinToString(" ") { line ->
            line.trimStart().replace(Regex("""^[0-9A-Fa-f]:\s*"""), "")
        }

        return strippedLines.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("SEARCHING...", "")
            .replace("BUSINIT...", "")
            .replace("STOPPED", "")
            .trim()
    }

    fun isErrorResponse(cleaned: String): Boolean {
        val upper = cleaned.uppercase()
        return upper.contains("ERROR") ||
                upper.contains("UNABLE") ||
                upper.contains("BUSY") ||
                upper.contains("?") ||
                upper.contains("STOPPED") ||
                upper.contains("BUFFERFULL") ||
                upper.contains("NODATA") ||
                upper.startsWith("7F") // UDS Negative Response Code (e.g. 7F 19 22, 7F 14 22)
    }

    /**
     * Parses standard OBD-II Mode 03 / 07 / 0A response.
     * Response payload contains standard 2-byte DTCs.
     * e.g. "430102990000" or with CAN headers "7E806430102990000"
     */
    fun parseStandardMode03Response(
        rawResponse: String,
        ecu: EcuTarget = EcuTarget.GENERIC_OBD,
        definitions: List<DtcDefinition> = emptyList()
    ): List<DiagnosticFault> {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isBlank() || cleaned.contains(ELM_NO_DATA) || cleaned.contains("NODATA") || isErrorResponse(cleaned)) {
            return emptyList()
        }

        val faults = mutableListOf<DiagnosticFault>()

        // Look for response marker "43" (Mode 03 positive response) or "47" (Mode 07)
        val modeIdx = cleaned.indexOf("43").takeIf { it >= 0 } ?: cleaned.indexOf("47")
        if (modeIdx < 0) return emptyList()

        val payload = cleaned.substring(modeIdx + 2)
        // Check DTC count (first 2 hex digits)
        if (payload.length < 2) return emptyList()

        // Extract pairs of 2-byte hex codes (4 characters each)
        // Skip count byte if present, or parse 4-hex chunks
        val dtcHexChunks = payload.drop(2).chunked(4)

        for (chunk in dtcHexChunks) {
            if (chunk.length == 4 && chunk != "0000") {
                val dtcCode = decode2ByteDtc(chunk)
                if (dtcCode.isNotBlank()) {
                    faults.add(PsaDtcDatabase.lookupFault(dtcCode, ecu, rawHex = chunk, definitions = definitions))
                }
            }
        }

        return faults
    }

    /**
     * Parses PSA UDS Service 19 02 FF (ReadDTCInformation by StatusMask) response.
     * On PSA, each fault is 3 bytes (DTC High, DTC Low, Symptom Byte) + 1 Status byte.
     * Response marker: "5902FF" followed by [DTC 3 bytes][Status 1 byte]
     */
    fun parsePsaUdsService19Response(
        rawResponse: String,
        ecu: EcuTarget,
        definitions: List<DtcDefinition> = emptyList()
    ): List<DiagnosticFault> {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isBlank() || cleaned.contains(ELM_NO_DATA) || cleaned.contains("NODATA") || isErrorResponse(cleaned)) {
            return emptyList()
        }

        val faults = mutableListOf<DiagnosticFault>()
        val marker = "5902"
        val idx = cleaned.indexOf(marker)
        if (idx < 0) return emptyList()

        // Guard against truncated responses or negative response fragments (Crash safety)
        if (cleaned.length < idx + 6) return emptyList()

        // Drop marker and status mask byte (59 02 FF -> drop 6 chars)
        val payload = cleaned.substring(idx + 6)
        // Each UDS DTC entry on PSA is 8 hex characters: 6 chars for DTC + symptom, 2 chars for status
        val entries = payload.chunked(8)

        for (entry in entries) {
            if (entry.length >= 6) {
                val dtc2ByteHex = entry.substring(0, 4)
                val symptomByte = entry.substring(4, 6)
                if (dtc2ByteHex != "0000") {
                    val baseDtc = decode2ByteDtc(dtc2ByteHex)
                    val fault = PsaDtcDatabase.lookupFault(baseDtc, ecu, rawHex = entry, definitions = definitions)
                    faults.add(fault.copy(symptomByte = symptomByte))
                }
            }
        }

        return faults
    }

    /**
     * Parses proprietary BSI hex fault responses (e.g., "FC82", "F000", "F015").
     */
    fun parseBsiHexResponse(
        rawResponse: String,
        definitions: List<DtcDefinition> = emptyList()
    ): List<DiagnosticFault> {
        val cleaned = cleanResponse(rawResponse)
        if (cleaned.isBlank() || cleaned.contains(ELM_NO_DATA) || cleaned.contains("NODATA") || isErrorResponse(cleaned)) {
            return emptyList()
        }

        val faults = mutableListOf<DiagnosticFault>()
        // Match PSA BSI codes starting with 'F' (e.g. F000, FC82, F015) or 'U'/'B' (e.g. U1213, U1118)
        val regex = Regex("""(?:F[0-9A-F]{3}|[UB][0-9A-F]{4})""")
        val matches = regex.findAll(cleaned.uppercase())

        for (match in matches) {
            val code = match.value
            faults.add(PsaDtcDatabase.lookupFault(code, EcuTarget.BSI_BODY, rawHex = code, definitions = definitions))
        }

        return faults
    }

    /**
     * Converts a 2-byte (4-hex character) code into standard DTC notation (e.g. "0299" -> "P0299").
     */
    fun decode2ByteDtc(hex4: String): String {
        if (hex4.length != 4) return ""
        val b0 = hex4.substring(0, 2).toIntOrNull(16) ?: return ""
        val b1 = hex4.substring(2, 4)

        // First 2 bits indicate type: 00=P, 01=C, 10=B, 11=U
        val type = when ((b0 and 0xC0) ushr 6) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            3 -> 'U'
            else -> 'P'
        }

        // Next 2 bits indicate first digit (0, 1, 2, 3)
        val digit1 = (b0 and 0x30) ushr 4
        // Remaining 4 bits indicate second digit
        val digit2 = (b0 and 0x0F).toString(16).uppercase()

        return "$type$digit1$digit2$b1".uppercase()
    }

    /**
     * Parses battery voltage from ELM "ATRV" command (e.g. "12.4V", "14.1V").
     */
    fun parseBatteryVoltage(raw: String): Float {
        val cleaned = cleanResponse(raw).replace("V", "").replace("v", "")
        return cleaned.toFloatOrNull() ?: 0f
    }

    /**
     * Parses Coolant Temperature from Mode 01 PID 05 (Formula: A - 40 °C).
     */
    fun parseCoolantTemp(raw: String): Int {
        val cleaned = cleanResponse(raw)
        val idx = cleaned.indexOf("4105")
        if (idx >= 0 && cleaned.length >= idx + 6) {
            val a = cleaned.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return 0
            return a - 40
        }
        return 0
    }

    /**
     * Parses Engine RPM from Mode 01 PID 0C (Formula: ((A * 256) + B) / 4).
     */
    fun parseRpm(raw: String): Int {
        val cleaned = cleanResponse(raw)
        val idx = cleaned.indexOf("410C").takeIf { it >= 0 } ?: cleaned.indexOf("410c")
        if (idx >= 0 && cleaned.length >= idx + 8) {
            val a = cleaned.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return 0
            val b = cleaned.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return 0
            return ((a * 256) + b) / 4
        }
        return 0
    }

    /**
     * Parses Vehicle Speed from Mode 01 PID 0D (Formula: A km/h).
     */
    fun parseSpeed(raw: String): Int {
        val cleaned = cleanResponse(raw)
        val idx = cleaned.indexOf("410D").takeIf { it >= 0 } ?: cleaned.indexOf("410d")
        if (idx >= 0 && cleaned.length >= idx + 6) {
            return cleaned.substring(idx + 4, idx + 6).toIntOrNull(16) ?: 0
        }
        return 0
    }

    /**
     * Parses Manifold Absolute Pressure (MAP) from Mode 01 PID 0B and converts to Boost Bar.
     * MAP (kPa) = A. Relative Boost = (MAP - 101) / 100 bar.
     */
    fun parseBoostPressure(raw: String): Float {
        val cleaned = cleanResponse(raw)
        val idx = cleaned.indexOf("410B").takeIf { it >= 0 } ?: cleaned.indexOf("410b")
        if (idx >= 0 && cleaned.length >= idx + 6) {
            val mapKpa = cleaned.substring(idx + 4, idx + 6).toIntOrNull(16) ?: 101
            val boost = (mapKpa - 101f) / 100f
            return maxOf(0f, boost)
        }
        return 0f
    }

    /**
     * Parses Common Rail Fuel Pressure from Mode 01 PID 23 (Formula: ((A * 256) + B) * 10 kPa).
     */
    fun parseRailPressure(raw: String): Int {
        val cleaned = cleanResponse(raw)
        val idx = cleaned.indexOf("4123")
        if (idx >= 0 && cleaned.length >= idx + 8) {
            val a = cleaned.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return 0
            val b = cleaned.substring(idx + 6, idx + 8).toIntOrNull(16) ?: return 0
            val kpa = ((a * 256) + b) * 10
            return kpa / 100 // Convert to bar
        }
        return 0
    }
}
