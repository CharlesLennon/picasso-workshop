package com.picassoworkshop.app.dashboard

import android.content.Context
import com.picassoworkshop.app.dashboard.model.CustomDashboard
import com.picassoworkshop.app.dashboard.model.DashboardWidgetConfig
import com.picassoworkshop.app.dashboard.model.GaugeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

class DashboardManager(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("picasso_dashboards_prefs", Context.MODE_PRIVATE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _dashboards = MutableStateFlow<List<CustomDashboard>>(emptyList())
    val dashboards: StateFlow<List<CustomDashboard>> = _dashboards.asStateFlow()

    private val _activeDashboard = MutableStateFlow(getFactoryPresets().first())
    val activeDashboard: StateFlow<CustomDashboard> = _activeDashboard.asStateFlow()

    init {
        loadDashboards()
    }

    private fun loadDashboards() {
        val rawJson = prefs.getString("saved_dashboards", null)
        val list = if (!rawJson.isNullOrBlank()) {
            try {
                val parsed = json.decodeFromString<List<CustomDashboard>>(rawJson)
                if (parsed.isNotEmpty()) parsed else getFactoryPresets()
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse saved dashboards, restoring defaults")
                getFactoryPresets()
            }
        } else {
            getFactoryPresets()
        }

        _dashboards.value = list

        val activeId = prefs.getString("active_dashboard_id", null)
        val selected = list.find { it.id == activeId } ?: list.firstOrNull() ?: getFactoryPresets().first()
        _activeDashboard.value = selected
    }

    private fun persistDashboards(list: List<CustomDashboard>) {
        try {
            val encoded = json.encodeToString(list)
            prefs.edit()
                .putString("saved_dashboards", encoded)
                .putString("active_dashboard_id", _activeDashboard.value.id)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist dashboards")
        }
    }

    fun selectDashboard(dashboardId: String) {
        val match = _dashboards.value.find { it.id == dashboardId }
        if (match != null) {
            _activeDashboard.value = match
            prefs.edit().putString("active_dashboard_id", match.id).apply()
        }
    }

    fun createDashboard(
        title: String,
        description: String = "",
        widgets: List<DashboardWidgetConfig> = emptyList()
    ): CustomDashboard {
        val newDashboard = CustomDashboard(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            title = title.ifBlank { "Custom Dashboard" },
            description = description,
            isPreset = false,
            isEditable = true,
            widgets = widgets,
            createdAt = System.currentTimeMillis()
        )

        val updatedList = _dashboards.value + newDashboard
        _dashboards.value = updatedList
        _activeDashboard.value = newDashboard
        persistDashboards(updatedList)
        return newDashboard
    }

    fun saveDashboard(updated: CustomDashboard) {
        val updatedList = _dashboards.value.map {
            if (it.id == updated.id) updated else it
        }
        _dashboards.value = updatedList
        if (_activeDashboard.value.id == updated.id) {
            _activeDashboard.value = updated
        }
        persistDashboards(updatedList)
    }

    fun deleteDashboard(dashboardId: String) {
        val filtered = _dashboards.value.filter { it.id != dashboardId }
        val finalList = if (filtered.isEmpty()) getFactoryPresets() else filtered
        _dashboards.value = finalList

        if (_activeDashboard.value.id == dashboardId) {
            _activeDashboard.value = finalList.first()
        }
        persistDashboards(finalList)
    }

    fun resetToDefaults() {
        val defaults = getFactoryPresets()
        _dashboards.value = defaults
        _activeDashboard.value = defaults.first()
        persistDashboards(defaults)
    }

    fun addWidget(dashboardId: String, widget: DashboardWidgetConfig) {
        val dashboard = _dashboards.value.find { it.id == dashboardId } ?: return
        val updated = dashboard.copy(widgets = dashboard.widgets + widget)
        saveDashboard(updated)
    }

    fun removeWidget(dashboardId: String, widgetId: String) {
        val dashboard = _dashboards.value.find { it.id == dashboardId } ?: return
        val updated = dashboard.copy(widgets = dashboard.widgets.filter { it.id != widgetId })
        saveDashboard(updated)
    }

    companion object {
        fun getFactoryPresets(): List<CustomDashboard> {
            return listOf(
                CustomDashboard(
                    id = "preset_master_cluster",
                    title = "Master Instrument Cluster",
                    description = "Comprehensive cockpit view with road speed, diesel tachometer, coolant temperature, and 12V charging voltage.",
                    isPreset = true,
                    isEditable = false,
                    widgets = listOf(
                        DashboardWidgetConfig(
                            id = "w_rpm",
                            pidId = "ENGINE_RPM",
                            customTitle = "Engine Tachometer",
                            gaugeType = GaugeType.RADIAL_DIAL,
                            minVal = 0.0,
                            maxVal = 5000.0,
                            warnThreshold = 4200.0,
                            dangerThreshold = 4600.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_spd",
                            pidId = "SPEED_KMH",
                            customTitle = "Road Speed",
                            gaugeType = GaugeType.RADIAL_DIAL,
                            minVal = 0.0,
                            maxVal = 220.0,
                            warnThreshold = 130.0,
                            dangerThreshold = 160.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_cool",
                            pidId = "COOLANT_TEMP",
                            customTitle = "Coolant Temperature",
                            gaugeType = GaugeType.BAR_GAUGE,
                            minVal = 40.0,
                            maxVal = 120.0,
                            warnThreshold = 95.0,
                            dangerThreshold = 105.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_volts",
                            pidId = "BATTERY_VOLTS",
                            customTitle = "Battery / Alternator",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = 10.0,
                            maxVal = 16.0,
                            warnThreshold = 12.0,
                            dangerThreshold = 11.5
                        ),
                        DashboardWidgetConfig(
                            id = "w_fuel",
                            pidId = "FUEL_LEVEL",
                            customTitle = "Fuel Tank Level",
                            gaugeType = GaugeType.BAR_GAUGE,
                            minVal = 0.0,
                            maxVal = 100.0,
                            warnThreshold = 15.0,
                            dangerThreshold = 8.0
                        )
                    )
                ),
                CustomDashboard(
                    id = "preset_dv6_turbo_rail",
                    title = "DV6 Turbo & Rail Pressure",
                    description = "Specialized workshop diagnostics for Garrett GT1544V turbocharger boost pressure and Bosch CP1H Common Rail fuel delivery.",
                    isPreset = true,
                    isEditable = false,
                    widgets = listOf(
                        DashboardWidgetConfig(
                            id = "w_boost",
                            pidId = "BOOST_PRESSURE",
                            customTitle = "Turbo Boost (MAP)",
                            gaugeType = GaugeType.TREND_GRAPH,
                            minVal = 0.0,
                            maxVal = 2.5,
                            warnThreshold = 1.4,
                            dangerThreshold = 1.6
                        ),
                        DashboardWidgetConfig(
                            id = "w_rail",
                            pidId = "RAIL_PRESSURE",
                            customTitle = "Common Rail High Pressure",
                            gaugeType = GaugeType.BAR_GAUGE,
                            minVal = 0.0,
                            maxVal = 1600.0,
                            warnThreshold = 1450.0,
                            dangerThreshold = 1600.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_rpm_diag",
                            pidId = "ENGINE_RPM",
                            customTitle = "Engine RPM",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = 0.0,
                            maxVal = 5000.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_iat",
                            pidId = "INTAKE_AIR_TEMP",
                            customTitle = "Intercooler Air Temp",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = -10.0,
                            maxVal = 80.0,
                            warnThreshold = 55.0,
                            dangerThreshold = 68.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_load",
                            pidId = "ENGINE_LOAD",
                            customTitle = "Engine Load Demand",
                            gaugeType = GaugeType.BAR_GAUGE,
                            minVal = 0.0,
                            maxVal = 100.0,
                            warnThreshold = 90.0,
                            dangerThreshold = 98.0
                        )
                    )
                ),
                CustomDashboard(
                    id = "preset_electrical_charging",
                    title = "Electrical & Charging Circuit",
                    description = "Alternator output health, preheating glow plug cycle detection, and low battery voltage protection.",
                    isPreset = true,
                    isEditable = false,
                    widgets = listOf(
                        DashboardWidgetConfig(
                            id = "w_volt_trend",
                            pidId = "BATTERY_VOLTS",
                            customTitle = "Charging Voltage Waveform",
                            gaugeType = GaugeType.TREND_GRAPH,
                            minVal = 10.0,
                            maxVal = 16.0,
                            warnThreshold = 12.2,
                            dangerThreshold = 11.5
                        ),
                        DashboardWidgetConfig(
                            id = "w_volt_status",
                            pidId = "BATTERY_VOLTS",
                            customTitle = "Charging Status Beacon",
                            gaugeType = GaugeType.STATUS_LIGHT,
                            minVal = 10.0,
                            maxVal = 16.0,
                            warnThreshold = 12.4,
                            dangerThreshold = 11.8
                        ),
                        DashboardWidgetConfig(
                            id = "w_rpm_elec",
                            pidId = "ENGINE_RPM",
                            customTitle = "Alternator Drive RPM",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = 0.0,
                            maxVal = 5000.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_cool_elec",
                            pidId = "COOLANT_TEMP",
                            customTitle = "Coolant Temperature",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = 40.0,
                            maxVal = 120.0
                        )
                    )
                ),
                CustomDashboard(
                    id = "preset_thermal_cooling",
                    title = "Thermal & Cooling Circuit",
                    description = "Monitors engine coolant, sump oil temperature, and high temperature warning alarms to prevent head gasket failures.",
                    isPreset = true,
                    isEditable = false,
                    widgets = listOf(
                        DashboardWidgetConfig(
                            id = "w_cool_dial",
                            pidId = "COOLANT_TEMP",
                            customTitle = "Engine Coolant",
                            gaugeType = GaugeType.RADIAL_DIAL,
                            minVal = 40.0,
                            maxVal = 130.0,
                            warnThreshold = 96.0,
                            dangerThreshold = 105.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_oil_bar",
                            pidId = "OIL_TEMP",
                            customTitle = "Sump Oil Temperature",
                            gaugeType = GaugeType.BAR_GAUGE,
                            minVal = 40.0,
                            maxVal = 150.0,
                            warnThreshold = 115.0,
                            dangerThreshold = 130.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_overheat_status",
                            pidId = "COOLANT_TEMP",
                            customTitle = "Thermal Overheat Alarm",
                            gaugeType = GaugeType.STATUS_LIGHT,
                            minVal = 40.0,
                            maxVal = 130.0,
                            warnThreshold = 98.0,
                            dangerThreshold = 108.0
                        ),
                        DashboardWidgetConfig(
                            id = "w_speed_therm",
                            pidId = "SPEED_KMH",
                            customTitle = "Airflow Speed",
                            gaugeType = GaugeType.DIGITAL_CARD,
                            minVal = 0.0,
                            maxVal = 220.0
                        )
                    )
                )
            )
        }
    }
}
