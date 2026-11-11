package com.picassoworkshop.app.data

import com.picassoworkshop.app.data.model.EngineType
import com.picassoworkshop.app.data.model.Generation
import com.picassoworkshop.app.data.model.VehicleSelection
import com.picassoworkshop.app.data.model.WorkshopContentPackage
import com.picassoworkshop.app.dashboard.model.toPidMetadata
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkshopDataManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParseWorkshopDataPackage() {
        val testJson = """
        {
          "schemaVersion": 1,
          "dataVersion": "20261111_01",
          "releaseDate": "2026-11-11",
          "title": "Citroën C4 Grand Picasso Technical Workshop Dataset",
          "quickTorqueLookup": {
            "Alternator Lower Pivot Bolt": 40.0,
            "Alternator Upper Bolts": 40.0
          },
          "procedures": [
            {
              "id": "dv6_alternator_removal",
              "title": "Alternator Removal & Auxiliary Drive Servicing",
              "targetGeneration": "MK1_B58",
              "targetEngine": "DV6_16_HDI",
              "category": "AUXILIARY_DRIVE",
              "difficulty": "ADVANCED",
              "estimatedMinutes": 120,
              "summary": "Step-by-step alternator removal for DV6 1.6 HDi.",
              "safetyNotes": ["Disconnect battery negative clamp."],
              "toolsNeeded": [
                {
                  "name": "15mm Offset Ring Spanner",
                  "size": "15mm",
                  "category": "WRENCH",
                  "isCrucial": true
                }
              ],
              "torqueSpecs": [
                {
                  "componentName": "Lower Pivot Bolt",
                  "torqueNm": 40.0,
                  "fastenerSize": "13mm Hex"
                }
              ],
              "steps": [
                {
                  "stepNumber": 1,
                  "title": "BSI Sleep & Battery Disconnect",
                  "instructions": "Wait 3 mins and unhook clamp."
                }
              ],
              "explodedParts": [
                {
                  "id": "part_alternator",
                  "calloutNumber": 1,
                  "name": "150A Alternator Assembly",
                  "oemPartNumber": "5705.AS",
                  "torqueNm": 40.0,
                  "assemblyX": 250.0,
                  "assemblyY": 260.0,
                  "explodedX": 250.0,
                  "explodedY": 260.0,
                  "explodeDirX": 0.0,
                  "explodeDirY": 0.0,
                  "explodeDirZ": 0.0,
                  "explodeDistance": 0.0,
                  "meshShape": "CYLINDER"
                },
                {
                  "id": "part_pivot_bolt",
                  "calloutNumber": 2,
                  "name": "Lower Pivot Mounting Bolt",
                  "oemPartNumber": "5703.A7",
                  "torqueNm": 40.0,
                  "assemblyX": 210.0,
                  "assemblyY": 310.0,
                  "explodedX": 110.0,
                  "explodedY": 370.0,
                  "explodeDirX": -1.2,
                  "explodeDirY": 0.8,
                  "explodeDirZ": -0.4,
                  "explodeDistance": 1.2,
                  "meshShape": "BOLT"
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<WorkshopContentPackage>(testJson)
        assertEquals(1, parsed.schemaVersion)
        assertEquals("20261111_01", parsed.dataVersion)
        assertEquals(1, parsed.procedures.size)

        val altProc = parsed.procedures.first()
        assertEquals("dv6_alternator_removal", altProc.id)
        assertEquals(Generation.MK1_B58, altProc.targetGeneration)
        assertEquals(EngineType.DV6_16_HDI, altProc.targetEngine)
        assertEquals(2, altProc.explodedParts.size)
        assertEquals(40.0, altProc.explodedParts[0].torqueNm ?: 0.0, 0.001)
    }

    @Test
    fun testVehicleMatching() {
        val mk1Selection = VehicleSelection(
            generation = Generation.MK1_B58,
            engine = EngineType.DV6_16_HDI
        )

        assertTrue(mk1Selection.matches(Generation.MK1_B58, EngineType.DV6_16_HDI))
        assertFalse(mk1Selection.matches(Generation.MK2_B78, EngineType.DW10_20_HDI))
    }

    @Test
    fun testDataVersionComparison() {
        assertTrue(WorkshopDataManager.isNewerDataVersion("20261111_02", "20261111_01"))
        assertTrue(WorkshopDataManager.isNewerDataVersion("20261112_01", "20261111_99"))
        assertFalse(WorkshopDataManager.isNewerDataVersion("20261111_01", "20261111_01"))
        assertFalse(WorkshopDataManager.isNewerDataVersion("20261110_01", "20261111_01"))
    }

    @Test
    fun testExplodeInterpolation() {
        // Assembly pos: (210, 310), Exploded pos: (110, 370)
        val assemblyX = 210f
        val assemblyY = 310f
        val explodedX = 110f
        val explodedY = 370f

        // 0% explode (fully assembled)
        val x0 = WorkshopDataManager.interpolatePos(assemblyX, explodedX, 0f)
        val y0 = WorkshopDataManager.interpolatePos(assemblyY, explodedY, 0f)
        assertEquals(210f, x0, 0.001f)
        assertEquals(310f, y0, 0.001f)

        // 100% explode (fully blown out)
        val x100 = WorkshopDataManager.interpolatePos(assemblyX, explodedX, 1f)
        val y100 = WorkshopDataManager.interpolatePos(assemblyY, explodedY, 1f)
        assertEquals(110f, x100, 0.001f)
        assertEquals(370f, y100, 0.001f)

        // 50% explode (halfway)
        val x50 = WorkshopDataManager.interpolatePos(assemblyX, explodedX, 0.5f)
        val y50 = WorkshopDataManager.interpolatePos(assemblyY, explodedY, 0.5f)
        assertEquals(160f, x50, 0.001f)
        assertEquals(340f, y50, 0.001f)
    }

    @Test
    fun testParseDiagnosticEcusAndDtcDefinitions() {
        val testJson = """
        {
          "schemaVersion": 1,
          "dataVersion": "20261111_03",
          "releaseDate": "2026-11-11",
          "title": "Citroën C4 Grand Picasso Technical Workshop Dataset",
          "diagnosticEcus": [
            {
              "id": "ENGINE_EDC16",
              "displayName": "Bosch EDC16C34 Engine Management",
              "shortCode": "EDC16",
              "requestHeader": "7E0",
              "responseHeader": "7E8",
              "initSequence": ["ATSP6", "ATSH7DF", "0100"],
              "dtcReadCommands": ["03", "19 02 FF"],
              "dtcClearCommands": ["04", "14 FF 00"],
              "targetGeneration": "MK1_B58",
              "targetEngine": "DV6_16_HDI"
            }
          ],
          "dtcDefinitions": [
            {
              "code": "P0299",
              "ecuId": "ENGINE_EDC16",
              "title": "Turbocharger / Supercharger Underboost Condition",
              "technicalDescription": "Measured manifold pressure lower than setpoint.",
              "commonCauses": ["Split vacuum pipe", "Faulty turbo electrovalve"],
              "severity": "WARNING",
              "procedureIdLink": "dv6_turbo_electrovalve_check"
            }
          ],
          "livePids": [
            {
              "pidId": "BATTERY_VOLTS",
              "displayName": "Battery Voltage",
              "command": "ATRV",
              "formulaType": "ATRV_VOLTS",
              "unit": "V",
              "normalMin": 12.0,
              "normalMax": 14.8
            }
          ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<WorkshopContentPackage>(testJson)
        assertEquals(1, parsed.diagnosticEcus.size)
        assertEquals(1, parsed.dtcDefinitions.size)
        assertEquals(1, parsed.livePids.size)

        val ecu = parsed.diagnosticEcus.first()
        assertEquals("ENGINE_EDC16", ecu.id)
        assertEquals("7E0", ecu.requestHeader)
        assertEquals(listOf("03", "19 02 FF"), ecu.dtcReadCommands)

        val dtc = parsed.dtcDefinitions.first()
        assertEquals("P0299", dtc.code)
        assertEquals("dv6_turbo_electrovalve_check", dtc.procedureIdLink)

        val pid = parsed.livePids.first()
        assertEquals("ATRV", pid.command)
        assertEquals("ATRV_VOLTS", pid.formulaType)
    }

    @Test
    fun testSensorPointerMappingAndPidMetadataConversion() {
        val config = com.picassoworkshop.app.data.model.LivePidConfig(
            pidId = "DPF_DIFF_PRESSURE",
            displayName = "DPF Differential Pressure",
            command = "017A",
            ecuHeader = "7E0",
            responsePrefix = "417A",
            unit = "mbar",
            formulaType = "RAW_INT",
            category = "AIR_TURBO",
            defaultMin = 0.0,
            defaultMax = 200.0,
            warnThreshold = 80.0,
            dangerThreshold = 140.0,
            recommendedGauge = "BAR_GAUGE",
            description = "Differential pressure sensor across DPF"
        )

        val meta = config.toPidMetadata()
        assertEquals("DPF_DIFF_PRESSURE", meta.id)
        assertEquals("DPF Differential Pressure", meta.displayName)
        assertEquals("017A", meta.standardCommand)
        assertEquals(com.picassoworkshop.app.dashboard.model.PidCategory.AIR_TURBO, meta.category)
        assertEquals("mbar", meta.defaultUnit)
        assertEquals(0.0, meta.defaultMin, 0.01)
        assertEquals(200.0, meta.defaultMax, 0.01)
        assertEquals(80.0, meta.defaultWarn ?: 0.0, 0.01)
        assertEquals(140.0, meta.defaultDanger ?: 0.0, 0.01)
        assertEquals(com.picassoworkshop.app.dashboard.model.GaugeType.BAR_GAUGE, meta.recommendedGauge)
    }

    @Test
    fun testBundledWorkshopDataLivePids() {
        val file = java.io.File("src/main/assets/workshop_data.json")
        assertTrue("workshop_data.json should exist in assets", file.exists())

        val content = file.readText()
        val pkg = json.decodeFromString<WorkshopContentPackage>(content)

        assertTrue("Should have at least 12 live sensor pointers", pkg.livePids.size >= 12)
        val pidsMap = pkg.livePids.associateBy { it.pidId }

        assertTrue(pidsMap.containsKey("ENGINE_RPM"))
        assertTrue(pidsMap.containsKey("SPEED_KMH"))
        assertTrue(pidsMap.containsKey("COOLANT_TEMP"))
        assertTrue(pidsMap.containsKey("BATTERY_VOLTS"))
        assertTrue(pidsMap.containsKey("BOOST_PRESSURE"))
        assertTrue(pidsMap.containsKey("RAIL_PRESSURE"))
        assertTrue(pidsMap.containsKey("INTAKE_AIR_TEMP"))
        assertTrue(pidsMap.containsKey("ENGINE_LOAD"))
        assertTrue(pidsMap.containsKey("THROTTLE_POS"))
        assertTrue(pidsMap.containsKey("FUEL_LEVEL"))
        assertTrue(pidsMap.containsKey("MAF_RATE"))
        assertTrue(pidsMap.containsKey("OIL_TEMP"))

        // Verify formulas
        assertEquals("RPM_DIV4", pidsMap["ENGINE_RPM"]?.formulaType)
        assertEquals("ATRV_VOLTS", pidsMap["BATTERY_VOLTS"]?.formulaType)
        assertEquals("BOOST_KPA_TO_BAR", pidsMap["BOOST_PRESSURE"]?.formulaType)
        assertEquals("RAIL_PRESSURE_BAR", pidsMap["RAIL_PRESSURE"]?.formulaType)
    }
}
