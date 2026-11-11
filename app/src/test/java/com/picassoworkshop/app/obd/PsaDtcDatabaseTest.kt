package com.picassoworkshop.app.obd

import com.picassoworkshop.app.obd.model.DtcSeverity
import com.picassoworkshop.app.obd.model.EcuTarget
import org.junit.Assert.*
import org.junit.Test

class PsaDtcDatabaseTest {

    @Test
    fun testPsaSpecificFaultLookups() {
        // P1351 Preheating relay
        val p1351 = PsaDtcDatabase.lookupFault("P1351")
        assertEquals("P1351", p1351.code)
        assertEquals(EcuTarget.ENGINE_EDC16, p1351.ecu)
        assertTrue(p1351.technicalDescription.contains("glow plugs", ignoreCase = true))

        // P1382 Reversible Alternator / Drive Belt Slip
        val p1382 = PsaDtcDatabase.lookupFault("P1382")
        assertEquals("P1382", p1382.code)
        assertEquals(DtcSeverity.CRITICAL, p1382.severity)
        assertEquals("dv6_alternator_removal", p1382.procedureIdLink)

        // C1196 Pneumatic Suspension Compressor
        val c1196 = PsaDtcDatabase.lookupFault("C1196")
        assertEquals("C1196", c1196.code)
        assertEquals(EcuTarget.AIR_SUSPENSION_BAP, c1196.ecu)
        assertEquals("c4_rear_air_suspension_bellows", c1196.procedureIdLink)

        // P1755 EGS6 Clutch Actuator
        val p1755 = PsaDtcDatabase.lookupFault("P1755")
        assertEquals("P1755", p1755.code)
        assertEquals(EcuTarget.EGS6_TRANSMISSION, p1755.ecu)

        // FC82 BSI Remote Key Wake-up
        val fc82 = PsaDtcDatabase.lookupFault("FC82")
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
