package com.example.ui.analyzer.ril

import com.example.ui.analyzer.vendor.models.RilPropertyInfo

object RilPropertyAnalyzer {

    fun isRilProperty(key: String): Boolean {
        val lower = key.lowercase()
        return lower.startsWith("ro.telephony.") ||
                lower.startsWith("ro.radio.") ||
                lower.startsWith("persist.radio.") ||
                lower.startsWith("vendor.ril.") ||
                lower.startsWith("ro.ril.") ||
                lower.startsWith("rild.") ||
                lower.startsWith("gsm.") ||
                lower.startsWith("ril.")
    }

    fun analyzeProperties(
        properties: Map<String, String>,
        source: String = "build.prop"
    ): List<RilPropertyInfo> {
        val list = mutableListOf<RilPropertyInfo>()
        for ((k, v) in properties) {
            if (isRilProperty(k)) {
                val cat = when {
                    k.contains("baseband") || k.contains("version") -> "Baseband & Version"
                    k.contains("multisim") || k.contains("num_slots") -> "Multi-SIM Configuration"
                    k.contains("libpath") || k.contains("libargs") || k.contains("ril_class") -> "RIL Driver & Library"
                    k.contains("network") || k.contains("type") || k.contains("mode") -> "Network & RAT"
                    k.contains("ecc") || k.contains("emergency") -> "Emergency & ECC"
                    else -> "Telephony"
                }
                list.add(RilPropertyInfo(property = k, value = v, source = source, category = cat))
            }
        }
        return list.sortedBy { it.property }
    }
}
