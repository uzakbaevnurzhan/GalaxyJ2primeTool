package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*

object HardwareMatrixAnalyzer {

    fun generateMatrix(
        vendorInfo: VendorInfo,
        halInfo: HalInfo,
        rilInfo: RilInfo,
        audio: SpecificHardwareAnalysis,
        camera: SpecificHardwareAnalysis,
        wifi: SpecificHardwareAnalysis,
        bluetooth: SpecificHardwareAnalysis,
        sensors: SpecificHardwareAnalysis,
        gnss: SpecificHardwareAnalysis,
        display: SpecificHardwareAnalysis,
        usb: SpecificHardwareAnalysis
    ): HardwareFunctionMatrix {
        val items = mutableListOf<HardwareFunctionItem>()

        // 1. RIL & Cellular Core
        val rilItem = HardwareFunctionItem(
            functionKey = "ril",
            displayName = "Cellular / Baseband RIL",
            category = "Radio & Cellular",
            filesStatus = if (rilInfo.daemons.isNotEmpty() || rilInfo.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            halStatus = if (halInfo.hals.any { it.category == "Radio" }) StageStatus.FOUND else StageStatus.UNKNOWN,
            serviceStatus = if (rilInfo.initServices.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            librariesStatus = if (rilInfo.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (rilInfo.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            kernelStatus = StageStatus.UNKNOWN,
            logsStatus = rilInfo.readinessScore.logsStatus,
            overallStatus = when (rilInfo.readinessScore.overallReadiness) {
                RilReadinessLevel.HIGH_READINESS -> HardwarePresenceStatus.LIKELY_PRESENT
                RilReadinessLevel.PARTIAL_READINESS -> HardwarePresenceStatus.PARTIALLY_PRESENT
                RilReadinessLevel.HIGH_RISK -> HardwarePresenceStatus.CONFLICT
                RilReadinessLevel.MISSING_OR_INCOMPATIBLE -> HardwarePresenceStatus.MISSING
                RilReadinessLevel.UNKNOWN -> HardwarePresenceStatus.UNKNOWN
            },
            evidenceList = rilInfo.readinessScore.scoreEvidence,
            notes = "Score: ${rilInfo.readinessScore.readinessPercentage}%"
        )
        items.add(rilItem)

        // 2. Voice Calls & SMS
        items.add(
            HardwareFunctionItem(
                functionKey = "calls_sms",
                displayName = "Voice Calls & SMS",
                category = "Radio & Cellular",
                filesStatus = rilItem.filesStatus,
                halStatus = rilItem.halStatus,
                serviceStatus = rilItem.serviceStatus,
                librariesStatus = rilItem.librariesStatus,
                kernelStatus = StageStatus.UNKNOWN,
                logsStatus = rilItem.logsStatus,
                overallStatus = rilItem.overallStatus,
                evidenceList = listOf("Shares RIL modem daemon (${rilInfo.daemons.firstOrNull()?.name ?: "none"})"),
                notes = "Requires active SIM & baseband firmware"
            )
        )

        // 3. Mobile Data (LTE/5G)
        items.add(
            HardwareFunctionItem(
                functionKey = "cellular_data",
                displayName = "Mobile Data (Packet Service)",
                category = "Radio & Cellular",
                filesStatus = rilItem.filesStatus,
                halStatus = rilItem.halStatus,
                serviceStatus = rilItem.serviceStatus,
                librariesStatus = rilItem.librariesStatus,
                kernelStatus = StageStatus.UNKNOWN,
                logsStatus = rilItem.logsStatus,
                overallStatus = rilItem.overallStatus,
                evidenceList = listOf("Netd / RIL data call setup"),
                notes = "Requires APN and netd routing support"
            )
        )

        // 4. Wi-Fi
        items.add(
            HardwareFunctionItem(
                functionKey = "wifi",
                displayName = "Wi-Fi (802.11)",
                category = "Connectivity",
                filesStatus = if (wifi.binaries.isNotEmpty() || wifi.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (wifi.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (wifi.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (wifi.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (wifi.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = wifi.presenceStatus,
                evidenceList = wifi.findings.map { it.evidence },
                notes = wifi.technicalDetails
            )
        )

        // 5. Bluetooth
        items.add(
            HardwareFunctionItem(
                functionKey = "bluetooth",
                displayName = "Bluetooth / BLE",
                category = "Connectivity",
                filesStatus = if (bluetooth.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (bluetooth.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (bluetooth.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (bluetooth.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (bluetooth.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = bluetooth.presenceStatus,
                evidenceList = bluetooth.findings.map { it.evidence },
                notes = bluetooth.technicalDetails
            )
        )

        // 6. GNSS / GPS
        items.add(
            HardwareFunctionItem(
                functionKey = "gps",
                displayName = "GNSS / GPS Navigation",
                category = "Connectivity",
                filesStatus = if (gnss.libraries.isNotEmpty() || gnss.configFiles.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (gnss.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (gnss.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (gnss.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (gnss.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = gnss.presenceStatus,
                evidenceList = gnss.findings.map { it.evidence },
                notes = gnss.technicalDetails
            )
        )

        // 7. Audio Output / Speaker
        items.add(
            HardwareFunctionItem(
                functionKey = "audio_out",
                displayName = "Audio Playback (Speaker/Earpiece)",
                category = "Multimedia",
                filesStatus = if (audio.libraries.isNotEmpty() || audio.configFiles.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (audio.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (audio.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (audio.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (audio.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = audio.presenceStatus,
                evidenceList = audio.findings.map { it.evidence },
                notes = audio.technicalDetails
            )
        )

        // 8. Microphone / Audio Input
        items.add(
            HardwareFunctionItem(
                functionKey = "audio_in",
                displayName = "Microphone & Voice Input",
                category = "Multimedia",
                filesStatus = audio.presenceStatus.let { if (it == HardwarePresenceStatus.LIKELY_PRESENT) StageStatus.FOUND else StageStatus.MISSING },
                halStatus = if (audio.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (audio.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (audio.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (audio.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = audio.presenceStatus,
                evidenceList = listOf("Shares primary audio HAL & mixer configs"),
                notes = "Requires microphone mixer paths in mixer_paths.xml"
            )
        )

        // 9. Camera Subsystem
        items.add(
            HardwareFunctionItem(
                functionKey = "camera",
                displayName = "Camera (Rear & Front)",
                category = "Multimedia",
                filesStatus = if (camera.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (camera.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (camera.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (camera.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (camera.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = camera.presenceStatus,
                evidenceList = camera.findings.map { it.evidence },
                notes = camera.technicalDetails
            )
        )

        // 10. Sensors (Accel, Gyro, Proximity, Light)
        items.add(
            HardwareFunctionItem(
                functionKey = "sensors",
                displayName = "Motion & Environmental Sensors",
                category = "Sensors & Input",
                filesStatus = if (sensors.libraries.isNotEmpty() || sensors.configFiles.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (sensors.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (sensors.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (sensors.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (sensors.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = sensors.presenceStatus,
                evidenceList = sensors.findings.map { it.evidence },
                notes = sensors.technicalDetails
            )
        )

        // 11. Display & GPU
        items.add(
            HardwareFunctionItem(
                functionKey = "display_gpu",
                displayName = "Display & GPU Acceleration",
                category = "Multimedia",
                filesStatus = if (display.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (display.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (display.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = if (display.libraries.any { it.missingLibraries.isNotEmpty() }) StageStatus.CONFLICT else if (display.libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = display.presenceStatus,
                evidenceList = display.findings.map { it.evidence },
                notes = display.technicalDetails
            )
        )

        // 12. USB & ADB
        items.add(
            HardwareFunctionItem(
                functionKey = "usb_adb",
                displayName = "USB / MTP / ADB",
                category = "System & Power",
                filesStatus = if (usb.configFiles.isNotEmpty() || usb.binaries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                halStatus = if (usb.halEntries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                serviceStatus = if (usb.serviceItems.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
                librariesStatus = StageStatus.FOUND,
                kernelStatus = StageStatus.UNKNOWN,
                overallStatus = usb.presenceStatus,
                evidenceList = usb.findings.map { it.evidence },
                notes = usb.technicalDetails
            )
        )

        val likelyCount = items.count { it.overallStatus == HardwarePresenceStatus.LIKELY_PRESENT }
        val partialCount = items.count { it.overallStatus == HardwarePresenceStatus.PARTIALLY_PRESENT }
        val missingCount = items.count { it.overallStatus == HardwarePresenceStatus.MISSING }
        val conflictCount = items.count { it.overallStatus == HardwarePresenceStatus.CONFLICT }
        val unknownCount = items.count { it.overallStatus == HardwarePresenceStatus.UNKNOWN }

        val summary = "Hardware Function Matrix: $likelyCount / ${items.size} subsystems likely present, $partialCount partial, $conflictCount conflicts, $missingCount missing."

        return HardwareFunctionMatrix(
            items = items,
            summary = summary,
            totalCount = items.size,
            likelyPresentCount = likelyCount,
            partialCount = partialCount,
            missingCount = missingCount,
            conflictCount = conflictCount,
            unknownCount = unknownCount
        )
    }
}
