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
    }
}
