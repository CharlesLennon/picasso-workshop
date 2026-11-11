package com.picassoworkshop.app.obd

import com.picassoworkshop.app.data.model.DtcDefinition
import com.picassoworkshop.app.obd.model.DtcSeverity
import com.picassoworkshop.app.obd.model.EcuTarget
import org.junit.Assert.*
import org.junit.Test

class PsaDtcDatabaseTest {

    private val sampleDefinitions = listOf(
        DtcDefinition(
            code = "P1351",
            ecuId = "ENGINE_EDC16",
            title = "Preheating Relay Circuit (Glow Plugs Not Supplied)",
            technicalDescription = "The preheating control unit receives power but detects an open circuit to one or more glow plugs.",
            commonCauses = listOf("One or more burnt glow plugs"),
            severity = "WARNING"
        ),
        DtcDefinition(
            code = "P1382",
            ecuId = "ENGINE_EDC16",
            title = "Reversible Alternator / Drive Belt Slip",
            technicalDescription = "Alternator excitation or freewheel clutch pulley slippage detected.",
            procedureIdLink = "dv6_alternator_removal",
            severity = "CRITICAL"
        ),
        DtcDefinition(
            code = "C1196",
            ecuId = "AIR_SUSPENSION_BAP",
            title = "Air Compressor Max Runtime Exceeded",
            technicalDescription = "Thermal cut-off triggered.",
            procedureIdLink = "c4_rear_air_suspension_bellows",
            severity = "CRITICAL"
        ),
        DtcDefinition(
            code = "P1755",
            ecuId = "EGS6_TRANSMISSION",
            title = "Clutch Actuator Position Sensor Signal Coherence",
            technicalDescription = "Discrepancy between commanded and actual clutch release position.",
            severity = "CRITICAL"
        ),
        DtcDefinition(
            code = "FC82",
            ecuId = "BSI_BODY",
            title = "Remote Key Wake-up Synchronization Error",
            technicalDescription = "Key fob transponder handshake error.",
            severity = "INFO"
        )
    )

    @Test
    fun testPsaSpecificFaultLookups() {
        val p1351 = PsaDtcDatabase.lookupFault("P1351", definitions = sampleDefinitions)
        assertEquals("P1351", p1351.code)
        assertEquals(EcuTarget.ENGINE_EDC16, p1351.ecu)
        assertTrue(p1351.technicalDescription.contains("glow plugs", ignoreCase = true))

        val p1382 = PsaDtcDatabase.lookupFault("P1382", definitions = sampleDefinitions)
        assertEquals("P1382", p1382.code)
        assertEquals(DtcSeverity.CRITICAL, p1382.severity)
        assertEquals("dv6_alternator_removal", p1382.procedureIdLink)

        val c1196 = PsaDtcDatabase.lookupFault("C1196", definitions = sampleDefinitions)
        assertEquals("C1196", c1196.code)
        assertEquals(EcuTarget.AIR_SUSPENSION_BAP, c1196.ecu)
        assertEquals("c4_rear_air_suspension_bellows", c1196.procedureIdLink)

        val p1755 = PsaDtcDatabase.lookupFault("P1755", definitions = sampleDefinitions)
        assertEquals("P1755", p1755.code)
        assertEquals(EcuTarget.EGS6_TRANSMISSION, p1755.ecu)

        val fc82 = PsaDtcDatabase.lookupFault("FC82", definitions = sampleDefinitions)
        assertEquals("FC82", fc82.code)
        assertEquals(EcuTarget.BSI_BODY, fc82.ecu)
    }

    @Test
    fun testUnknownDtcFallback() {
        val unknown = PsaDtcDatabase.lookupFault("P0999", EcuTarget.GENERIC_OBD)
        assertEquals("P0999", unknown.code)
        assertTrue(unknown.title.contains("Powertrain", ignoreCase = true))

        val unknownChassis = PsaDtcDatabase.lookupFault("C0555", EcuTarget.AIR_SUSPENSION_BAP)
        assertEquals("C0555", unknownChassis.code)
        assertEquals(EcuTarget.AIR_SUSPENSION_BAP, unknownChassis.ecu)
    }
}
