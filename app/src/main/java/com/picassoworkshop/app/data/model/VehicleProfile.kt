package com.picassoworkshop.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Generation(val code: String, val title: String, val years: String, val isPrimaryFocus: Boolean = false) {
    MK1_B58("MK1_B58", "C4 Picasso / Grand Picasso Mk1", "2006–2013", isPrimaryFocus = true),
    MK2_B78("MK2_B78", "C4 Picasso / Grand Picasso Mk2", "2013–2022", isPrimaryFocus = false);

    val displayTitle: String get() = title
}

@Serializable
enum class EngineType(
    val code: String,
    val title: String,
    val engineFamily: String,
    val fuel: String,
    val displacement: String,
    val isPrimaryFocus: Boolean = false
) {
    DV6_16_HDI("DV6_16_HDI", "1.6 HDi 16V (110 hp • DV6TED4 / 9HZ)", "PSA DV6 / DLD-416", "Diesel", "1560 cc", isPrimaryFocus = true),
    DV6_8_EHDI("DV6_8_EHDI", "1.6 e-HDi 8V (112 hp • DV6C / 9HR)", "PSA DV6C", "Diesel", "1560 cc", isPrimaryFocus = false),
    DW10_20_HDI("DW10_20_HDI", "2.0 HDi 16V (136/150/163 hp • DW10BTED4)", "PSA DW10", "Diesel", "1997 cc", isPrimaryFocus = false),
    EP6_16_THP("EP6_16_THP", "1.6 THP Turbo (150/156 hp • EP6DT)", "Prince EP6", "Petrol", "1598 cc", isPrimaryFocus = false),
    EP6_16_VTI("EP6_16_VTI", "1.6 VTi 16V (120 hp • EP6)", "Prince EP6", "Petrol", "1598 cc", isPrimaryFocus = false),
    EW10_20_PETROL("EW10_20_PETROL", "2.0 16V Petrol (140 hp • EW10A)", "PSA EW10", "Petrol", "1997 cc", isPrimaryFocus = false);

    val displayTitle: String get() = title
}

@Serializable
enum class Transmission(val code: String, val title: String) {
    EGS6("EGS6", "EGS6 / BMP6 Robotized Manual (6-Speed)"),
    MANUAL_6("MANUAL_6", "6-Speed Manual (BVM6)"),
    MANUAL_5("MANUAL_5", "5-Speed Manual (BVM5)"),
    AUTO_AM6("AUTO_AM6", "6-Speed Torque Converter (Aisin AM6)");

    val displayTitle: String get() = title
}

@Serializable
data class VehicleSelection(
    val generation: Generation = Generation.MK1_B58,
    val engine: EngineType = EngineType.DV6_16_HDI,
    val transmission: Transmission = Transmission.EGS6
) {
    val displaySubtitle: String
        get() = "${generation.title} • ${engine.title} • ${transmission.code}"

    val isPrimaryFocus: Boolean
        get() = generation.isPrimaryFocus && engine.isPrimaryFocus

    fun matches(targetGen: Generation, targetEngine: EngineType): Boolean {
        return generation == targetGen && (engine == targetEngine || targetEngine == EngineType.DV6_16_HDI)
    }
}
