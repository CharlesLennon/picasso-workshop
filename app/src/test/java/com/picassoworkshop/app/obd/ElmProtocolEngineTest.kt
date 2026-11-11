package com.picassoworkshop.app.obd

import com.picassoworkshop.app.data.model.DtcDefinition
import com.picassoworkshop.app.obd.model.EcuTarget
import org.junit.Assert.*
import org.junit.Test

class ElmProtocolEngineTest {

    private val engine = ElmProtocolEngine()

    private val sampleDefinitions = listOf(
        DtcDefinition(
            code = "P1351",
            ecuId = "ENGINE_EDC16",
            title = "Preheating Relay Circuit (Glow Plugs Not Supplied)",
            technicalDescription = "Glow plugs open circuit.",
            severity = "WARNING"
        ),
        DtcDefinition(
            code = "P1382",
            ecuId = "ENGINE_EDC16",
            title = "Alternator Freewheel Clutch Slip",
            technicalDescription = "Alternator excitation error.",
            procedureIdLink = "dv6_alternator_removal",
            severity = "CRITICAL"
        ),
        DtcDefinition(
            code = "C1196",
            ecuId = "AIR_SUSPENSION_BAP",
            title = "Air Compressor Max Runtime Exceeded",
            technicalDescription = "Thermal cut-off tripped.",
            procedureIdLink = "c4_rear_air_suspension_bellows",
            severity = "CRITICAL"
        )
    )

    @Test
    fun testDecode2ByteDtc() {
        assertEquals("P0299", engine.decode2ByteDtc("0299"))
        assertEquals("P1351", engine.decode2ByteDtc("1351"))
        assertEquals("P1382", engine.decode2ByteDtc("1382"))
        assertEquals("C1196", engine.decode2ByteDtc("5196"))
        assertEquals("P0087", engine.decode2ByteDtc("0087"))
    }

    @Test
    fun testParseStandardMode03Response() {
        val raw = "43 02 02 99 13 82\r\r>"
        val faults = engine.parseStandardMode03Response(raw, EcuTarget.ENGINE_EDC16, sampleDefinitions)

        assertEquals(2, faults.size)
        assertEquals("P0299", faults[0].code)
        assertEquals("P1382", faults[1].code)
        assertEquals("dv6_alternator_removal", faults[1].procedureIdLink)
    }

    @Test
    fun testParsePsaUdsService19Response() {
        // 59 02 FF [1351 00 28] [5196 12 28]
        val raw = "59 02 FF 13 51 00 28 51 96 12 28\r\r>"
        val faults = engine.parsePsaUdsService19Response(raw, EcuTarget.AIR_SUSPENSION_BAP, sampleDefinitions)

        assertEquals(2, faults.size)
        assertEquals("P1351", faults[0].code)
        assertEquals("00", faults[0].symptomByte)

        assertEquals("C1196", faults[1].code)
        assertEquals("12", faults[1].symptomByte)
        assertEquals("c4_rear_air_suspension_bellows", faults[1].procedureIdLink)
    }

    @Test
    fun testParseBsiHexResponse() {
        val raw = "BSI RESPONSE: FC82 00 F000 28 F015\r\r>"
        val faults = engine.parseBsiHexResponse(raw)

        assertEquals(3, faults.size)
        assertEquals("FC82", faults[0].code)
        assertEquals("F000", faults[1].code)
        assertEquals("F015", faults[2].code)
        assertEquals(EcuTarget.BSI_BODY, faults[0].ecu)
    }

    @Test
    fun testParseLiveTelemetryPids() {
        // Battery Voltage
        assertEquals(14.2f, engine.parseBatteryVoltage("14.2V\r\r>"), 0.01f)

        // Coolant Temp (80 hex = 128 dec; 128 - 40 = 88 °C)
        assertEquals(88, engine.parseCoolantTemp("41 05 80\r\r>"))

        // Engine RPM (0C E0 hex = 3296 / 4 = 824 RPM)
        assertEquals(824, engine.parseRpm("41 0C 0C E0\r\r>"))

        // Vehicle Speed (32 hex = 50 km/h)
        assertEquals(50, engine.parseSpeed("41 0D 32\r\r>"))

        // Fuel Rail Pressure (0B 80 hex = 2944 kPa -> 294 bar)
        assertEquals(294, engine.parseRailPressure("41 23 0B 80\r\r>"))

        // Intake Air Temp (46 hex = 70 - 40 = 30 °C)
        assertEquals(30, engine.parseIntakeAirTemp("41 0F 46\r\r>"))

        // Engine Load (64 hex = 100 * 100 / 255 = 39.2 %)
        assertEquals(39.2f, engine.parseEngineLoad("41 04 64\r\r>"), 0.1f)

        // Throttle Position (32 hex = 50 * 100 / 255 = 19.6 %)
        assertEquals(19.6f, engine.parseThrottlePos("41 11 32\r\r>"), 0.1f)

        // Fuel Level (80 hex = 128 * 100 / 255 = 50.2 %)
        assertEquals(50.2f, engine.parseFuelLevel("41 2F 80\r\r>"), 0.1f)

        // MAF Rate (03 E8 hex = 1000 / 100 = 10.0 g/s)
        assertEquals(10.0f, engine.parseMafRate("41 10 03 E8\r\r>"), 0.1f)

        // Oil Temp (82 hex = 130 - 40 = 90 °C)
        assertEquals(90, engine.parseOilTemp("41 5C 82\r\r>"))
    }

    @Test
    fun testRedTeamTruncatedAndMalformedFramesDoNotCrash() {
        // Red team fuzz: truncated frames that previously threw StringIndexOutOfBoundsException
        assertTrue(engine.parsePsaUdsService19Response("5902", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parsePsaUdsService19Response("59 02 FF", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parsePsaUdsService19Response("", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parseStandardMode03Response("43", EcuTarget.GENERIC_OBD).isEmpty())
        assertTrue(engine.parseStandardMode03Response("", EcuTarget.GENERIC_OBD).isEmpty())

        // Negative Response Codes (NRC)
        assertTrue(engine.parsePsaUdsService19Response("7F 19 22", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parsePsaUdsService19Response("7F 14 22", EcuTarget.ENGINE_EDC16).isEmpty())

        // Error and No Data variants
        assertTrue(engine.parsePsaUdsService19Response("NO DATA\r\n>", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parsePsaUdsService19Response("CAN ERROR\r\n>", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parsePsaUdsService19Response("BUFFER FULL\r\n>", EcuTarget.ENGINE_EDC16).isEmpty())
        assertTrue(engine.parseStandardMode03Response("BUS INIT: ... ERROR\r\n>", EcuTarget.GENERIC_OBD).isEmpty())
    }

    @Test
    fun testRedTeamIsoTpMultiFrameResponseParsing() {
        // Multi-frame response with ISO-TP line markers 0: and 1:
        val multiFrame = """
            0: 59 02 FF 13 51 00 28
            1: 51 96 12 28 02 99 00 28
            >
        """.trimIndent()

        val faults = engine.parsePsaUdsService19Response(multiFrame, EcuTarget.AIR_SUSPENSION_BAP, sampleDefinitions)
        assertEquals(3, faults.size)
        assertEquals("P1351", faults[0].code)
        assertEquals("C1196", faults[1].code)
        assertEquals("P0299", faults[2].code)
    }

    @Test
    fun testRedTeamBsiHexResponseWithUAndFCodes() {
        // BSI response with both proprietary F codes and standard CAN U codes
        val bsiRaw = "BSI: FC82 00 F000 28 U1213 00 U1118\r\n>"
        val faults = engine.parseBsiHexResponse(bsiRaw)

        val codes = faults.map { it.code }
        assertTrue(codes.contains("FC82"))
        assertTrue(codes.contains("F000"))
        assertTrue(codes.contains("U1213"))
        assertTrue(codes.contains("U1118"))
    }
}
