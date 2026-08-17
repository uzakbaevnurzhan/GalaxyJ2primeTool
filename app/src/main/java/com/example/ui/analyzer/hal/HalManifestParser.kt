package com.example.ui.analyzer.hal

import com.example.ui.analyzer.vendor.models.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

object HalManifestParser {

    fun categorizeHal(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("audio") || lower.contains("soundtrigger") -> "Audio"
            lower.contains("camera") -> "Camera"
            lower.contains("radio") || lower.contains("ril") || lower.contains("cellular") -> "Radio"
            lower.contains("wifi") || lower.contains("wlan") -> "Wi-Fi"
            lower.contains("bluetooth") || lower.contains("bt") -> "Bluetooth"
            lower.contains("gnss") || lower.contains("gps") -> "GNSS/GPS"
            lower.contains("sensor") -> "Sensors"
            lower.contains("graphics") || lower.contains("composer") || lower.contains("allocator") || lower.contains("mapper") || lower.contains("display") || lower.contains("renderscript") -> "Display"
            lower.contains("usb") -> "USB"
            lower.contains("vibrator") -> "Vibrator"
            lower.contains("light") -> "Lights"
            lower.contains("power") || lower.contains("thermal") -> "Power & Thermal"
            lower.contains("biometrics") || lower.contains("fingerprint") || lower.contains("face") -> "Biometrics"
            lower.contains("health") || lower.contains("battery") -> "Health"
            lower.contains("drm") || lower.contains("cas") || lower.contains("media") -> "Media & DRM"
            lower.contains("gatekeeper") || lower.contains("keymaster") || lower.contains("keymint") || lower.contains("weaver") || lower.contains("authfactor") -> "Security & Keystore"
            lower.contains("nfc") -> "NFC"
            lower.contains("broadcastradio") || lower.contains("tethering") || lower.contains("netd") -> "Network"
            else -> "General"
        }
    }

    fun parseManifestXml(content: String, sourceFileName: String = "manifest.xml"): List<HalEntry> {
        val hals = mutableListOf<HalEntry>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var currentHalFormat = HalFormat.HIDL
            var currentHalName = ""
            var currentTransport = HalTransport.HWBINDER
            val currentVersions = mutableListOf<String>()
            val currentInterfaces = mutableListOf<HalInterfaceInstance>()
            var currentInterfaceName = ""
            val currentInstances = mutableListOf<String>()
            var insideHal = false
            var insideInterface = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name
                        if (tag == "hal") {
                            insideHal = true
                            val formatAttr = parser.getAttributeValue(null, "format") ?: "hidl"
                            currentHalFormat = when (formatAttr.lowercase()) {
                                "hidl" -> HalFormat.HIDL
                                "aidl" -> HalFormat.AIDL
                                "passthrough" -> HalFormat.PASSTHROUGH
                                else -> HalFormat.HIDL
                            }
                            currentHalName = ""
                            currentTransport = if (currentHalFormat == HalFormat.AIDL) HalTransport.AIDL_IPC else HalTransport.HWBINDER
                            currentVersions.clear()
                            currentInterfaces.clear()
                        } else if (insideHal) {
                            when (tag) {
                                "name" -> {
                                    if (!insideInterface) {
                                        currentHalName = parser.nextText().trim()
                                    } else {
                                        currentInterfaceName = parser.nextText().trim()
                                    }
                                }
                                "transport" -> {
                                    val t = parser.nextText().trim().lowercase()
                                    currentTransport = if (t.contains("passthrough")) HalTransport.PASSTHROUGH else HalTransport.HWBINDER
                                }
                                "version" -> {
                                    val v = parser.nextText().trim()
                                    if (v.isNotEmpty()) currentVersions.add(v)
                                }
                                "interface" -> {
                                    insideInterface = true
                                    currentInterfaceName = ""
                                    currentInstances.clear()
                                }
                                "instance" -> {
                                    val inst = parser.nextText().trim()
                                    if (inst.isNotEmpty()) currentInstances.add(inst)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name
                        if (tag == "interface") {
                            if (currentInterfaceName.isNotEmpty()) {
                                currentInterfaces.add(
                                    HalInterfaceInstance(
                                        interfaceName = currentInterfaceName,
                                        instances = currentInstances.toList()
                                    )
                                )
                            }
                            insideInterface = false
                        } else if (tag == "hal") {
                            if (currentHalName.isNotEmpty()) {
                                hals.add(
                                    HalEntry(
                                        name = currentHalName,
                                        format = currentHalFormat,
                                        transport = currentTransport,
                                        versions = currentVersions.toList(),
                                        interfaces = currentInterfaces.toList(),
                                        sourceFile = sourceFileName,
                                        category = categorizeHal(currentHalName)
                                    )
                                )
                            }
                            insideHal = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Regex fallback if parser encounters non-standard XML
            val halBlockRegex = Regex("""<hal\b[^>]*>([\s\S]*?)</hal>""")
            val nameRegex = Regex("""<name>([^<]+)</name>""")
            val versionRegex = Regex("""<version>([^<]+)</version>""")

            halBlockRegex.findAll(content).forEach { match ->
                val block = match.groupValues[1]
                val name = nameRegex.find(block)?.groupValues?.get(1)?.trim() ?: ""
                val versions = versionRegex.findAll(block).map { it.groupValues[1].trim() }.toList()
                if (name.isNotEmpty()) {
                    hals.add(
                        HalEntry(
                            name = name,
                            format = if (block.contains("""format="aidl"""")) HalFormat.AIDL else HalFormat.HIDL,
                            transport = if (block.contains("passthrough")) HalTransport.PASSTHROUGH else HalTransport.HWBINDER,
                            versions = versions,
                            interfaces = emptyList(),
                            sourceFile = sourceFileName,
                            category = categorizeHal(name)
                        )
                    )
                }
            }
        }
        return hals
    }

    fun scanManifestsInDir(dir: File): List<Pair<String, List<HalEntry>>> {
        val results = mutableListOf<Pair<String, List<HalEntry>>>()
        if (!dir.exists() || !dir.isDirectory) return results

        val candidatePaths = listOf(
            "vendor/etc/vintf/manifest.xml",
            "system/etc/vintf/manifest.xml",
            "vendor/manifest.xml",
            "system/manifest.xml",
            "vendor/etc/vintf/compatibility_matrix.xml",
            "system/etc/vintf/compatibility_matrix.xml"
        )

        for (cp in candidatePaths) {
            val f = File(dir, cp)
            if (f.exists() && f.isFile) {
                try {
                    val content = f.readText(Charsets.UTF_8)
                    val parsed = parseManifestXml(content, cp)
                    results.add(Pair(cp, parsed))
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        return results
    }
}
