package com.picassoworkshop.app.obd

import com.picassoworkshop.app.obd.model.DiagnosticFault
import com.picassoworkshop.app.obd.model.DtcSeverity
import com.picassoworkshop.app.obd.model.EcuTarget

object PsaDtcDatabase {

    private val faultDictionary: Map<String, DiagnosticFault> = listOf(
        // === 1. PSA ENGINE & EMISSIONS (DV6 1.6 HDi & DW10 2.0 HDi Focus) ===
        DiagnosticFault(
            code = "P1351",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Preheating Relay Circuit (Glow Plugs Not Supplied)",
            technicalDescription = "The preheating control unit receives power but detects an open circuit to one or more glow plugs. Very common on 1.6 HDi (DV6); does not illuminate the Check Engine Light (MIL).",
            commonCauses = listOf(
                "One or more burnt glow plugs (high internal resistance)",
                "Preheating relay contacts corroded (under battery tray)",
                "Glow plug wiring harness damaged or loose terminal connector"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "P1352",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Preheating Relay Circuit (Relay Supplied / Control Discrepancy)",
            technicalDescription = "Engine management ECU detected coherence failure between commanded glow plug state and relay feedback.",
            commonCauses = listOf(
                "Defective preheat relay unit",
                "Blown preheat supply fuse in engine BSM / battery distribution unit",
                "Earth connection fault on engine block"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "P1459",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "EGR Valve Position Sensor (Stuck Open / Flow Mismatch)",
            technicalDescription = "The exhaust gas recirculation valve potentiometer signal does not match the commanded lift position during deceleration or regeneration.",
            commonCauses = listOf(
                "Heavy carbon soot jamming Valeo EGR pintle valve",
                "EGR valve electrical actuator motor gear wear",
                "Faulty EGR position sensor wiring"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0299",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Turbocharger Underboost (Boost Pressure Below Threshold)",
            technicalDescription = "Measured manifold absolute pressure (MAP) is lower than target boost pressure by more than 300 mbar for over 5 seconds under load.",
            commonCauses = listOf(
                "Split turbo intercooler air doser rubber hose (common behind radiator)",
                "Turbo electro-pneumatic vacuum control solenoid valve failed",
                "Vacuum hose cracked, perished, or detached from wastegate actuator",
                "Turbocharger variable nozzle (VNT) mechanism carbonized and binding"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0234",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Turbocharger Overboost Condition",
            technicalDescription = "Charge air pressure exceeded maximum safe threshold. Engine may enter limp-home mode to protect intercooler and pistons.",
            commonCauses = listOf(
                "Variable vane geometry sticking in high boost position",
                "Turbo vacuum solenoid stuck venting to atmospheric pressure",
                "Faulty MAP sensor signal reading high"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P1382",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Reversible Alternator / Drive Belt Slip (Stop-Start / Charging)",
            technicalDescription = "Alternator excitation or freewheel clutch pulley slippage detected. Charging voltage intermittent or belt tensioner loose.",
            commonCauses = listOf(
                "Seized alternator freewheel overrunning clutch pulley (OAP)",
                "Auxiliary drive belt slack or dynamic tensioner damper worn out",
                "Alternator internal voltage regulator brush wear (150A Valeo/Mitsubishi)"
            ),
            procedureIdLink = "dv6_alternator_removal", // DIRECT REPAIR LINK!
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0562",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "System Charging Voltage Low (< 10.5V)",
            technicalDescription = "Engine ECU battery sense line drops below minimum operating voltage while engine is running.",
            commonCauses = listOf(
                "Failing alternator assembly or worn brushes",
                "Slack auxiliary drive belt slipping under electrical load",
                "Corroded battery earth strap or blown BPGA maxi fuse"
            ),
            procedureIdLink = "dv6_alternator_removal",
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0563",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "System Charging Voltage High (> 15.8V)",
            technicalDescription = "Alternator internal voltage regulator failure causing dangerous over-voltage to vehicle microprocessors.",
            commonCauses = listOf(
                "Alternator diode pack or regulator shorted internally",
                "Loose B+ alternator output cable nut (13mm)"
            ),
            procedureIdLink = "dv6_alternator_removal",
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P1693",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Engine Starting / Stopping Controlled by Engine ECU Discrepancy",
            technicalDescription = "Engine ECU received starting command not synchronized with BSI immobilizer key transponder authorization.",
            commonCauses = listOf(
                "Low battery voltage during cranking causing BSI reboot",
                "Ignition switch contact bounce (Neiman switch)",
                "Starter motor solenoid drawing excessive current"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "P0087",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Fuel Rail Pressure Below Minimum Setpoint",
            technicalDescription = "Common rail pressure drops below target rail pressure under heavy acceleration.",
            commonCauses = listOf(
                "Blocked diesel fuel filter (high restriction)",
                "High pressure fuel pump flow regulator valve sticking",
                "Excessive injector spill back (leak-off)"
            ),
            procedureIdLink = "dv6_fuel_filter_replacement", // DIRECT REPAIR LINK!
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0088",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Fuel Rail Pressure Exceeded Setpoint",
            technicalDescription = "Measured common rail pressure higher than commanded limit. High pressure safety limiter opened.",
            commonCauses = listOf(
                "Common rail pressure regulator valve stuck closed",
                "Fuel rail pressure sensor calibration drifted"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P1445",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "DPF Additive Quantity Injected Into Filter Exceeded Limit",
            technicalDescription = "Additive fluid dosing counter has reached theoretical maximum filter capacity. FAP soot filter service required.",
            commonCauses = listOf(
                "DPF particulate filter loaded with non-burnable cerine ash",
                "Additive pump counter requires reset after physical filter clean"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "P1446",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "DPF Additive (EOLYS) Reservoir Empty",
            technicalDescription = "Additive level sensor in the additive pouch/tank under the vehicle has detected empty fluid condition.",
            commonCauses = listOf(
                "EOLYS / Powerflex additive fluid exhausted in tank",
                "Additive level sensor float switch stuck"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P2408",
            ecu = EcuTarget.ENGINE_EDC16,
            title = "Fuel Tank Filler Cap Sensor Circuit Coherence",
            technicalDescription = "Reed switch sensor on the fuel filler neck cannot detect the magnet on the fuel cap. Additive dosing suspended.",
            commonCauses = listOf(
                "Magnet missing from aftermarket replacement fuel cap",
                "Fuel filler cap sensor reed switch wiring broken near fuel tank"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.WARNING
        ),

        // === 2. PNEUMATIC AIR SUSPENSION (BAP) ===
        DiagnosticFault(
            code = "C1196",
            ecu = EcuTarget.AIR_SUSPENSION_BAP,
            title = "Air Compressor Max Runtime Exceeded (Thermal Protection)",
            technicalDescription = "12V air suspension compressor ran continuously for over 180 seconds trying to reach target chassis ride height. Thermal cut-off triggered.",
            commonCauses = listOf(
                "Perished rear pneumatic rubber spring bellows leaking air under load",
                "Split 4mm nylon airline tube near rear suspension arm pivot",
                "Compressor piston ring worn, producing insufficient flow (< 5 bar)"
            ),
            procedureIdLink = "c4_rear_air_suspension_bellows", // DIRECT REPAIR LINK!
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "C1197",
            ecu = EcuTarget.AIR_SUSPENSION_BAP,
            title = "Air Suspension Solenoid Valve Leak / Pressure Drop",
            technicalDescription = "Chassis height drops when air valves are closed. Uncommanded deflation detected.",
            commonCauses = listOf(
                "Pneumatic bellows puncture or micro-porosity along bottom rubber fold",
                "Brass push-fit airline union seal leaking",
                "Solenoid valve manifold internal seal contamination"
            ),
            procedureIdLink = "c4_rear_air_suspension_bellows",
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "C1198",
            ecu = EcuTarget.AIR_SUSPENSION_BAP,
            title = "Rear Right Height Sensor Voltage Coherence Error",
            technicalDescription = "Sensor output voltage outside allowable physical travel range (0.5V to 4.5V).",
            commonCauses = listOf(
                "Height sensor plastic linkage arm ball joint popped off trailing arm",
                "Sensor internal resistive track water corroded",
                "Sensor harness unplugged or pinched"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "C1199",
            ecu = EcuTarget.AIR_SUSPENSION_BAP,
            title = "Rear Left Height Sensor Voltage Coherence Error",
            technicalDescription = "Sensor output voltage outside allowable physical travel range (0.5V to 4.5V).",
            commonCauses = listOf(
                "Height sensor linkage ball joint popped off axle arm",
                "Sensor water ingress",
                "Wiring harness damage"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),

        // === 3. EGS6 ROBOTIZED 6-SPEED TRANSMISSION ===
        DiagnosticFault(
            code = "P1755",
            ecu = EcuTarget.EGS6_TRANSMISSION,
            title = "Clutch Actuator Position Sensor Signal Coherence",
            technicalDescription = "Discrepancy between commanded clutch release position and actual position reported by Hall effect sensor.",
            commonCauses = listOf(
                "Clutch release bearing guide sleeve sticky or dry of grease",
                "Clutch friction plate worn to rivets, causing clutch judder",
                "Hydraulic slave cylinder internal seal leakage",
                "EGS6 clutch bite-point calibration adaptation needed"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0944",
            ecu = EcuTarget.EGS6_TRANSMISSION,
            title = "Hydraulic Circuit Pressure Loss (EGS6 Pump Unit)",
            technicalDescription = "Hydraulic pressure drops below 35 bar while vehicle is in gear. Gearbox shifts to neutral for safety.",
            commonCauses = listOf(
                "Hydraulic pressure accumulator nitrogen sphere diaphragm failed",
                "Electric hydraulic pump motor relay or brushes worn",
                "Low Tutela CS Speed fluid level in EGS actuator reservoir"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P0868",
            ecu = EcuTarget.EGS6_TRANSMISSION,
            title = "Hydraulic Fluid Pressure Low (< 38 Bar)",
            technicalDescription = "Hydraulic pump running excessively without reaching cutoff pressure (48 bar).",
            commonCauses = listOf(
                "Hydraulic pump electric motor failing",
                "Accumulator sphere lost charge",
                "Fluid leak from actuator block unions"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.CRITICAL
        ),
        DiagnosticFault(
            code = "P1704",
            ecu = EcuTarget.EGS6_TRANSMISSION,
            title = "Brake Pedal Switch Signal Coherence For Gear Selection",
            technicalDescription = "Dual brake switch contacts (normally open / normally closed) do not switch simultaneously. EGS prevents shifting into D/R.",
            commonCauses = listOf(
                "Faulty four-pin brake light switch above brake pedal",
                "Brake switch ratchet clip out of adjustment"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        ),

        // === 4. BSI BODY COMPUTER & NETWORK HEX CODES ===
        DiagnosticFault(
            code = "F000",
            ecu = EcuTarget.BSI_BODY,
            title = "BSI Internal Power Supply / Microprocessor Wake-Up Error",
            technicalDescription = "BSI main computer suffered severe transient voltage drop during engine start or sleep transition.",
            commonCauses = listOf(
                "Weak 12V vehicle battery (< 11.8V resting)",
                "High starter motor current draw dragging system voltage",
                "Loose battery positive terminal quick-release clamp"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "FC82",
            ecu = EcuTarget.BSI_BODY,
            title = "Remote Key Wake-up Synchronization Error",
            technicalDescription = "High frequency key fob waking signal received without matching transponder immobilizer handshake.",
            commonCauses = listOf(
                "Weak CR1620 battery in remote key fob",
                "Key transponder antenna coil around ignition barrel intermittent",
                "Radio frequency interference"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.INFO
        ),
        DiagnosticFault(
            code = "F015",
            ecu = EcuTarget.BSI_BODY,
            title = "Trailer Relay Module (BSG) Lost on CAN Bus",
            technicalDescription = "BSI configured for trailer module but receives no CAN acknowledgement frame.",
            commonCauses = listOf(
                "13-pin trailer socket water ingress causing short circuit",
                "Trailer module fuse blown in boot fuse panel (F12)",
                "Aftermarket towbar wiring harness disconnected"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.INFO
        ),
        DiagnosticFault(
            code = "U1213",
            ecu = EcuTarget.BSI_BODY,
            title = "Dynamic Vehicle Speed Frame Received From ABS Invalid",
            technicalDescription = "ABS/ESP ECU broadcast invalid speed value on CAN network.",
            commonCauses = listOf(
                "ABS wheel speed sensor magnetic target ring corroded on rear hub",
                "Low battery voltage causing ABS transient communication drop"
            ),
            procedureIdLink = null,
            severity = DtcSeverity.WARNING
        ),
        DiagnosticFault(
            code = "U1118",
            ecu = EcuTarget.BSI_BODY,
            title = "CAN Communication Lost With BSI Body Computer",
            technicalDescription = "CAN network timeout between Engine ECU and BSI.",
            commonCauses = listOf(
                "Engine bay wiring harness chafing under air filter box",
                "BSI 3-minute sleep protocol violated during battery reconnection"
            ),
            procedureIdLink = "c4_picasso_mk1_fuse_directory",
            severity = DtcSeverity.WARNING
        )
    ).associateBy { it.code }

    fun lookupFault(code: String, ecu: EcuTarget = EcuTarget.GENERIC_OBD, rawHex: String = ""): DiagnosticFault {
        val cleanCode = code.trim().uppercase()
        val found = faultDictionary[cleanCode]
        if (found != null) {
            return if (ecu != EcuTarget.GENERIC_OBD && found.ecu == EcuTarget.GENERIC_OBD) {
                found.copy(ecu = ecu, rawHex = rawHex)
            } else {
                found.copy(rawHex = rawHex)
            }
        }

        // Generate clean generic diagnostic fallback
        val prefix = cleanCode.firstOrNull() ?: 'P'
        val generatedTitle = when (prefix) {
            'P' -> "Powertrain / Engine Management Fault ($cleanCode)"
            'C' -> "Chassis / Suspension / Brakes Fault ($cleanCode)"
            'B' -> "Body / Comfort / Interior Systems Fault ($cleanCode)"
            'U' -> "Network Communication / CAN Bus Fault ($cleanCode)"
            'F' -> "PSA BSI Multiplex System Fault ($cleanCode)"
            else -> "Manufacturer Diagnostic Code ($cleanCode)"
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

    fun getAllKnownFaults(): List<DiagnosticFault> = faultDictionary.values.toList()
}
