package com.picassoworkshop.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkshopContentPackage(
    val schemaVersion: Int = 1,
    val dataVersion: String = "20261111_01",
    val releaseDate: String = "2026-11-11",
    val title: String = "Citroën C4 Grand Picasso Technical Workshop Dataset",
    val procedures: List<RepairProcedure> = emptyList(),
    val quickTorqueLookup: Map<String, Double> = emptyMap(),
    val diagnosticEcus: List<DiagnosticEcuConfig> = emptyList(),
    val dtcDefinitions: List<DtcDefinition> = emptyList(),
    val livePids: List<LivePidConfig> = emptyList()
)
