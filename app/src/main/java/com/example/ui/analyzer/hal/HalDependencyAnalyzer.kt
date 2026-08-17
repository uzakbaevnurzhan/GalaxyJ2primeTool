package com.example.ui.analyzer.hal

import com.example.ui.analyzer.boot.InitServiceBlock
import com.example.ui.analyzer.vendor.models.*

object HalDependencyAnalyzer {

    fun mapCategoryKernelInterface(category: String): String {
        return when (category) {
            "Audio" -> "/dev/snd/* (ALSA pcmC*D*), /dev/msm_audio_dev"
            "Camera" -> "/dev/video*, /dev/media*, /dev/camera-*"
            "Radio" -> "/dev/ccci*, /dev/ttyACM*, /dev/smd*, /dev/qmi*"
            "Wi-Fi" -> "/dev/wmtWifi, /sys/class/net/wlan0, /dev/rfkill"
            "Bluetooth" -> "/dev/stpbt, /dev/ttyHS*, /dev/rfkill"
            "Sensors" -> "/dev/input/event*, /sys/class/sensors/*, /dev/i2c-*"
            "GNSS/GPS" -> "/dev/stpgps, /dev/ttyGPS*, /dev/gps"
            "Display" -> "/dev/graphics/fb*, /dev/dri/card*, /dev/kgsl-3d0, /dev/mali0"
            "USB" -> "/sys/class/android_usb/*, /config/usb_gadget/g1"
            "Vibrator" -> "/sys/class/timed_output/vibrator/enable, /sys/class/leds/vibrator"
            "Lights" -> "/sys/class/leds/*"
            else -> "/dev/*"
        }
    }

    fun buildHalServiceMap(
        manifestHals: List<HalEntry>,
        hwBinaries: List<HalServiceAnalyzer.DiscoveredHalBinary>,
        initServices: List<InitServiceBlock>,
        libraries: List<VendorLibrary>
    ): List<HalServiceMapItem> {
        val mapItems = mutableListOf<HalServiceMapItem>()

        // 1. Map from Manifest entries
        for (hal in manifestHals) {
            val matchingBinary = hwBinaries.find { hb ->
                hb.binaryName.contains(hal.name) ||
                hb.binaryName.contains(hal.name.substringAfterLast(".")) ||
                hal.name.contains(hb.binaryName.substringBefore("-service").substringBefore("@"))
            }

            val matchingService = initServices.find { s ->
                val binTarget = s.binaryPath.substringAfterLast("/")
                binTarget.contains(hal.name) ||
                (matchingBinary != null && (s.binaryPath.contains(matchingBinary.binaryName) || binTarget == matchingBinary.binaryName))
            }

            val manifestVer = hal.versions.joinToString(", ")
            val hasBinary = matchingBinary != null
            val hasService = matchingService != null
            val missingLibs = matchingBinary?.missingLibraries ?: emptyList()
            val neededLibs = matchingBinary?.neededLibraries ?: emptyList()

            val presence = when {
                hasBinary && missingLibs.isEmpty() && hasService -> HardwarePresenceStatus.LIKELY_PRESENT
                hasBinary && missingLibs.isNotEmpty() -> HardwarePresenceStatus.CONFLICT
                hasBinary -> HardwarePresenceStatus.PARTIALLY_PRESENT
                else -> HardwarePresenceStatus.MISSING
            }

            val evidence = buildString {
                append("Manifest: ${hal.name} ($manifestVer) [${hal.format} / ${hal.transport}]. ")
                if (hasBinary) append("Binary: ${matchingBinary!!.binaryName} (${matchingBinary.architecture ?: "Unknown"}). ")
                else append("Binary: NOT FOUND. ")
                if (hasService) append("Init: ${matchingService!!.name}. ")
                if (missingLibs.isNotEmpty()) append("Missing Libs: ${missingLibs.joinToString()}. ")
            }

            mapItems.add(
                HalServiceMapItem(
                    category = hal.category,
                    halName = hal.name,
                    version = manifestVer,
                    manifestStatus = StageStatus.FOUND,
                    initServiceName = matchingService?.name,
                    initServiceStatus = if (hasService) StageStatus.FOUND else StageStatus.MISSING,
                    binaryPath = matchingBinary?.relativePath,
                    binaryStatus = if (hasBinary) StageStatus.FOUND else StageStatus.MISSING,
                    binaryArchitecture = matchingBinary?.architecture,
                    requiredLibraries = neededLibs,
                    missingLibraries = missingLibs,
                    libraryStatus = if (missingLibs.isEmpty() && neededLibs.isNotEmpty()) StageStatus.FOUND else if (missingLibs.isNotEmpty()) StageStatus.CONFLICT else StageStatus.UNKNOWN,
                    kernelInterface = mapCategoryKernelInterface(hal.category),
                    kernelInterfaceStatus = StageStatus.UNKNOWN, // Hardware presence cannot assume kernel node is active without live dmesg/dev test
                    status = presence,
                    evidence = evidence
                )
            )
        }

        // 2. Add orphan HW binaries (binaries present in vendor/bin/hw without manifest entries)
        for (hb in hwBinaries) {
            val alreadyMapped = mapItems.any { it.binaryPath == hb.relativePath || it.halName.contains(hb.binaryName) }
            if (!alreadyMapped) {
                val cat = HalManifestParser.categorizeHal(hb.binaryName)
                val matchingService = initServices.find { it.binaryPath.contains(hb.binaryName) }
                val missingLibs = hb.missingLibraries
                val presence = if (missingLibs.isEmpty()) HardwarePresenceStatus.PARTIALLY_PRESENT else HardwarePresenceStatus.CONFLICT

                mapItems.add(
                    HalServiceMapItem(
                        category = cat,
                        halName = hb.binaryName,
                        version = "binary-only",
                        manifestStatus = StageStatus.MISSING,
                        initServiceName = matchingService?.name,
                        initServiceStatus = if (matchingService != null) StageStatus.FOUND else StageStatus.UNKNOWN,
                        binaryPath = hb.relativePath,
                        binaryStatus = StageStatus.FOUND,
                        binaryArchitecture = hb.architecture,
                        requiredLibraries = hb.neededLibraries,
                        missingLibraries = missingLibs,
                        libraryStatus = if (missingLibs.isEmpty()) StageStatus.FOUND else StageStatus.CONFLICT,
                        kernelInterface = mapCategoryKernelInterface(cat),
                        kernelInterfaceStatus = StageStatus.UNKNOWN,
                        status = presence,
                        evidence = "HAL Binary '${hb.binaryName}' found without matching manifest entry. Architecture: ${hb.architecture ?: "Unknown"}."
                    )
                )
            }
        }

        return mapItems
    }
}
