package com.example.ui.analyzer.hardware

import com.example.ui.analyzer.vendor.models.*
import java.io.File

object AudioAnalyzer {

    fun analyze(
        rootDirectory: File?,
        hals: List<HalEntry>,
        services: List<HalServiceMapItem>,
        binaries: List<VendorBinary>,
        libraries: List<VendorLibrary>,
        properties: List<VendorProperty>
    ): SpecificHardwareAnalysis {
        val findings = mutableListOf<EvidenceFinding>()
        val issues = mutableListOf<VendorIssue>()
        val configFiles = mutableListOf<String>()

        val audioHals = hals.filter { it.category == "Audio" || it.name.contains("audio") || it.name.contains("soundtrigger") }
        val audioServices = services.filter { it.category == "Audio" }
        val audioBinaries = binaries.filter { it.name.contains("audio") || it.name.contains("sound") || it.name.contains("alsa") }
        val audioLibraries = libraries.filter { it.name.contains("audio") || it.name.contains("sound") || it.name.contains("alsa") || it.name.contains("tinyalsa") }
        val audioProps = properties.filter { it.category == "Audio" }

        // Check for audio policy & mixer configs
        val candidateConfigs = listOf(
            "vendor/etc/audio_policy_configuration.xml",
            "system/etc/audio_policy_configuration.xml",
            "vendor/etc/audio_policy.conf",
            "system/etc/audio_policy.conf",
            "vendor/etc/mixer_paths.xml",
            "vendor/etc/mixer_paths_0.xml",
            "vendor/etc/audio_effects.xml",
            "vendor/etc/sound_trigger_platform_info.xml"
        )

        if (rootDirectory != null && rootDirectory.exists()) {
            for (c in candidateConfigs) {
                if (File(rootDirectory, c).exists()) {
                    configFiles.add(c)
                }
            }
        }

        val hasHal = audioHals.isNotEmpty()
        val hasLibs = audioLibraries.isNotEmpty()
        val hasConfigs = configFiles.isNotEmpty()
        val hasMissingLibs = audioLibraries.any { it.missingLibraries.isNotEmpty() } || audioBinaries.any { it.missingLibraries.isNotEmpty() }

        val presence = when {
            hasHal && hasLibs && hasConfigs && !hasMissingLibs -> HardwarePresenceStatus.LIKELY_PRESENT
            hasMissingLibs -> HardwarePresenceStatus.CONFLICT
            hasHal || hasLibs || hasConfigs -> HardwarePresenceStatus.PARTIALLY_PRESENT
            else -> HardwarePresenceStatus.MISSING
        }

        findings.add(
            EvidenceFinding(
                fact = "Audio Subsystem Integration",
                evidence = "HALs: ${audioHals.size}, Services: ${audioServices.size}, Libs: ${audioLibraries.size}, Config files: ${configFiles.size} (${configFiles.joinToString()})",
                severity = Severity.INFO,
                confidence = Confidence.HIGH,
                source = "AudioAnalyzer"
            )
        )

        if (!hasConfigs && (hasHal || hasLibs)) {
            issues.add(
                VendorIssue(
                    type = VendorIssueType.HARDWARE_INTERFACE_MISSING,
                    severity = Severity.WARNING,
                    message = "Audio HAL detected but no audio_policy_configuration.xml or mixer_paths.xml found.",
                    evidence = "Audio libraries present: ${audioLibraries.take(3).joinToString { it.name }}",
                    source = "AudioAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Provide audio_policy_configuration.xml in /vendor/etc/."
                )
            )
        }

        return SpecificHardwareAnalysis(
            categoryName = "Audio",
            halEntries = audioHals,
            serviceItems = audioServices,
            binaries = audioBinaries,
            libraries = audioLibraries,
            configFiles = configFiles,
            properties = audioProps,
            presenceStatus = presence,
            findings = findings,
            issues = issues,
            technicalDetails = "ALSA Kernel Nodes: /dev/snd/*, Policy: ${configFiles.joinToString()}"
        )
    }
}
