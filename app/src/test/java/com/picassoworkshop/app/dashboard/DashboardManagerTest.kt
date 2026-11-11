package com.picassoworkshop.app.dashboard

import com.picassoworkshop.app.dashboard.model.GaugeType
import com.picassoworkshop.app.dashboard.model.PidCatalog
import com.picassoworkshop.app.obd.model.LiveTelemetry
import org.junit.Assert.*
import org.junit.Test

class DashboardManagerTest {

    @Test
    fun testPidCatalogCompleteness() {
        val pids = PidCatalog.ALL_PIDS
        assertEquals(12, pids.size)

        val pidIds = pids.map { it.id }.toSet()
        val expectedIds = setOf(
            PidCatalog.PID_RPM,
            PidCatalog.PID_SPEED,
            PidCatalog.PID_COOLANT_TEMP,
            PidCatalog.PID_BATTERY_VOLT,
            PidCatalog.PID_BOOST_PRESSURE,
            PidCatalog.PID_RAIL_PRESSURE,
            PidCatalog.PID_INTAKE_TEMP,
            PidCatalog.PID_ENGINE_LOAD,
            PidCatalog.PID_THROTTLE_POS,
            PidCatalog.PID_FUEL_LEVEL,
            PidCatalog.PID_MAF_RATE,
            PidCatalog.PID_OIL_TEMP
        )
        assertEquals(expectedIds, pidIds)

        // Verify getById lookups
        assertEquals("Engine RPM", PidCatalog.getById(PidCatalog.PID_RPM)?.displayName)
        assertEquals("Bar", PidCatalog.getById(PidCatalog.PID_BOOST_PRESSURE)?.defaultUnit)
        assertEquals("V", PidCatalog.getById(PidCatalog.PID_BATTERY_VOLT)?.defaultUnit)
        assertNull(PidCatalog.getById("NON_EXISTENT_PID"))
    }

    @Test
    fun testLiveTelemetryPidMapping() {
        val telemetry = LiveTelemetry(
            rpm = 2200,
            speedKmh = 110,
            coolantTempC = 88,
            batteryVoltage = 14.2f,
            boostPressureBar = 1.35f,
            railPressureBar = 1250,
            intakeAirTempC = 28,
            engineLoadPercent = 65,
            throttlePercent = 42,
            fuelLevelPercent = 75,
            mafRateGps = 48.2f,
            oilTempC = 92
        )

        assertEquals(2200.0, telemetry.getValueForPid(PidCatalog.PID_RPM), 0.01)
        assertEquals(110.0, telemetry.getValueForPid(PidCatalog.PID_SPEED), 0.01)
        assertEquals(88.0, telemetry.getValueForPid(PidCatalog.PID_COOLANT_TEMP), 0.01)
        assertEquals(14.2, telemetry.getValueForPid(PidCatalog.PID_BATTERY_VOLT), 0.01)
        assertEquals(1.35, telemetry.getValueForPid(PidCatalog.PID_BOOST_PRESSURE), 0.01)
        assertEquals(1250.0, telemetry.getValueForPid(PidCatalog.PID_RAIL_PRESSURE), 0.01)
        assertEquals(28.0, telemetry.getValueForPid(PidCatalog.PID_INTAKE_TEMP), 0.01)
        assertEquals(65.0, telemetry.getValueForPid(PidCatalog.PID_ENGINE_LOAD), 0.01)
        assertEquals(42.0, telemetry.getValueForPid(PidCatalog.PID_THROTTLE_POS), 0.01)
        assertEquals(75.0, telemetry.getValueForPid(PidCatalog.PID_FUEL_LEVEL), 0.01)
        assertEquals(48.2, telemetry.getValueForPid(PidCatalog.PID_MAF_RATE), 0.01)
        assertEquals(92.0, telemetry.getValueForPid(PidCatalog.PID_OIL_TEMP), 0.01)
        assertEquals(0.0, telemetry.getValueForPid("UNKNOWN_PID"), 0.01)
    }

    @Test
    fun testFactoryPresetConfigs() {
        val presets = DashboardManager.getFactoryPresets()
        assertEquals(4, presets.size)

        val master = presets.first { it.id == "preset_master_cluster" }
        assertEquals("Master Instrument Cluster", master.title)
        assertTrue(master.isPreset)
        assertEquals(5, master.widgets.size)
        assertEquals(GaugeType.RADIAL_DIAL, master.widgets[0].gaugeType)
        assertEquals(GaugeType.RADIAL_DIAL, master.widgets[1].gaugeType)
        assertEquals(GaugeType.BAR_GAUGE, master.widgets[2].gaugeType)
        assertEquals(GaugeType.DIGITAL_CARD, master.widgets[3].gaugeType)
        assertEquals(GaugeType.BAR_GAUGE, master.widgets[4].gaugeType)

        val turbo = presets.first { it.id == "preset_dv6_turbo_rail" }
        assertEquals("DV6 Turbo & Rail Pressure", turbo.title)
        assertTrue(turbo.widgets.any { it.gaugeType == GaugeType.TREND_GRAPH })

        val electrical = presets.first { it.id == "preset_electrical_charging" }
        assertEquals("Electrical & Charging Circuit", electrical.title)
        assertTrue(electrical.widgets.any { it.gaugeType == GaugeType.STATUS_LIGHT })

        val thermal = presets.first { it.id == "preset_thermal_cooling" }
        assertEquals("Thermal & Cooling Circuit", thermal.title)

        // Verify all preset widgets reference valid PIDs
        for (preset in presets) {
            for (widget in preset.widgets) {
                assertNotNull("Preset widget references valid PID", PidCatalog.getById(widget.pidId))
                assertTrue("Max value greater than min value", widget.maxVal > widget.minVal)
            }
        }
    }
}
