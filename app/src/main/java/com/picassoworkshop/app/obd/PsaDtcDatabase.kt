package com.picassoworkshop.app.obd

import com.picassoworkshop.app.data.model.DtcDefinition
import com.picassoworkshop.app.obd.model.DiagnosticFault
import com.picassoworkshop.app.obd.model.DtcSeverity
import com.picassoworkshop.app.obd.model.EcuTarget

object PsaDtcDatabase {

    fun lookupFault(
        code: String,
        ecu: EcuTarget = EcuTarget.GENERIC_OBD,
        rawHex: String = "",
        definitions: List<DtcDefinition> = emptyList()
    ): DiagnosticFault {
        val cleanCode = code.trim().uppercase()
        val definition = definitions.firstOrNull { it.code.equals(cleanCode, ignoreCase = true) }

        if (definition != null) {
            val severity = when (definition.severity.uppercase()) {
                "CRITICAL" -> DtcSeverity.CRITICAL
                "WARNING" -> DtcSeverity.WARNING
                "INFO" -> DtcSeverity.INFO
                else -> DtcSeverity.WARNING
            }

            val targetEcu = when (definition.ecuId.uppercase()) {
                "ENGINE_EDC16", "ENGINE" -> EcuTarget.ENGINE_EDC16
                "BSI_BODY", "BSI" -> EcuTarget.BSI_BODY
                "AIR_SUSPENSION_BAP", "SUSPENSION" -> EcuTarget.AIR_SUSPENSION_BAP
                "EGS6_TRANSMISSION", "GEARBOX" -> EcuTarget.EGS6_TRANSMISSION
                else -> ecu
            }

            return DiagnosticFault(
                code = cleanCode,
                ecu = targetEcu,
                title = definition.title,
                technicalDescription = definition.technicalDescription,
                commonCauses = definition.commonCauses,
                procedureIdLink = definition.procedureIdLink,
                rawHex = rawHex,
                severity = severity
            )
        }

        // Generic fallback generation for uncatalogued codes
        val prefix = cleanCode.firstOrNull() ?: 'P'
        val generatedTitle = when (prefix) {
            'P' -> "Powertrain / Engine Management Fault ($cleanCode)"
            'C' -> "Chassis / Suspension / Brakes Fault ($cleanCode)"
            'B' -> "Body / Comfort / Interior Systems Fault ($cleanCode)"
            'U' -> "Network Communication / CAN Bus Fault ($cleanCode)"
            'F' -> "PSA BSI Multiplex System Fault ($cleanCode)"
            else -> "Diagnostic Trouble Code ($cleanCode)"
        }

        val generatedSeverity = when (prefix) {
            'P' -> if (cleanCode.contains("02") || cleanCode.contains("00")) DtcSeverity.CRITICAL else DtcSeverity.WARNING
            'C' -> DtcSeverity.CRITICAL
            'B' -> DtcSeverity.WARNING
            'F' -> DtcSeverity.INFO
            else -> DtcSeverity.WARNING
        }

        return DiagnosticFault(
            code = cleanCode,
            ecu = ecu,
            title = generatedTitle,
            technicalDescription = "Diagnostic Trouble Code reported by ${ecu.displayName}. Check wiring, sensor supply voltage, and associated actuators.",
            commonCauses = listOf(
                "Sensor signal out of specified range",
                "Harness connector loose or pins oxidised",
                "Related fuse blown or supply relay faulty"
            ),
            procedureIdLink = if (ecu == EcuTarget.AIR_SUSPENSION_BAP) "c4_rear_air_suspension_bellows" else null,
            rawHex = rawHex,
            severity = generatedSeverity
        )
    }
}
