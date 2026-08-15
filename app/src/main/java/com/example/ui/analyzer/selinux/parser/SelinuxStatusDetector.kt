package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.SelinuxMode
import com.example.ui.analyzer.selinux.model.SelinuxStatusDetection

object SelinuxStatusDetector {

    /**
     * Analyzes collected evidence (properties, log lines, command outputs) to deduce SELinux runtime mode.
     */
    fun detectMode(
        getenforceOutput: String? = null,
        props: Map<String, String> = emptyMap(),
        hasPermissiveAudit: Boolean = false,
        hasEnforcingAudit: Boolean = false,
        cmdline: String? = null
    ): SelinuxStatusDetection {
        val evidence = mutableListOf<String>()
        val detectedModes = mutableSetOf<SelinuxMode>()
        val warnings = mutableListOf<String>()

        // 1. Check getenforce
        if (!getenforceOutput.isNullOrBlank()) {
            val clean = getenforceOutput.trim().lowercase()
            when {
                clean.contains("enforcing") -> {
                    detectedModes.add(SelinuxMode.ENFORCING)
                    evidence.add("getenforce: Enforcing")
                }
                clean.contains("permissive") -> {
                    detectedModes.add(SelinuxMode.PERMISSIVE)
                    evidence.add("getenforce: Permissive")
                }
                clean.contains("disabled") -> {
                    detectedModes.add(SelinuxMode.DISABLED)
                    evidence.add("getenforce: Disabled")
                }
            }
        }

        // 2. Check ro.boot.selinux / ro.build.selinux
        val bootSelinux = props["ro.boot.selinux"] ?: props["ro.build.selinux"]
        if (!bootSelinux.isNullOrBlank()) {
            val clean = bootSelinux.trim().lowercase()
            when (clean) {
                "enforcing", "1" -> {
                    detectedModes.add(SelinuxMode.ENFORCING)
                    evidence.add("Property ro.boot.selinux = $clean")
                }
                "permissive", "0" -> {
                    detectedModes.add(SelinuxMode.PERMISSIVE)
                    evidence.add("Property ro.boot.selinux = $clean")
                }
                "disabled" -> {
                    detectedModes.add(SelinuxMode.DISABLED)
                    evidence.add("Property ro.boot.selinux = disabled")
                }
            }
        }

        // 3. Check kernel cmdline
        if (!cmdline.isNullOrBlank()) {
            when {
                cmdline.contains("androidboot.selinux=permissive") || cmdline.contains("selinux=1 androidboot.selinux=permissive") -> {
                    detectedModes.add(SelinuxMode.PERMISSIVE)
                    evidence.add("Kernel cmdline contains androidboot.selinux=permissive")
                }
                cmdline.contains("selinux=0") -> {
                    detectedModes.add(SelinuxMode.DISABLED)
                    evidence.add("Kernel cmdline contains selinux=0 (Disabled)")
                }
                cmdline.contains("androidboot.selinux=enforcing") -> {
                    detectedModes.add(SelinuxMode.ENFORCING)
                    evidence.add("Kernel cmdline contains androidboot.selinux=enforcing")
                }
            }
        }

        // 4. Check audit log evidence
        if (hasEnforcingAudit) {
            evidence.add("Audit logs contain permissive=0 (Enforcing denials)")
            detectedModes.add(SelinuxMode.ENFORCING)
        }
        if (hasPermissiveAudit) {
            evidence.add("Audit logs contain permissive=1 (Permissive warnings)")
            detectedModes.add(SelinuxMode.PERMISSIVE)
        }

        val hasConflict = detectedModes.size > 1
        if (hasConflict) {
            warnings.add("Conflicting SELinux status indicators detected across sources (${detectedModes.joinToString(", ")}). The device might have per-domain permissive rules or status changed during boot.")
        }

        val finalMode = when {
            detectedModes.isEmpty() -> SelinuxMode.UNKNOWN
            hasConflict -> {
                // If getenforce or audit confirms enforcing, note it
                if (detectedModes.contains(SelinuxMode.ENFORCING)) SelinuxMode.ENFORCING else SelinuxMode.PERMISSIVE
            }
            detectedModes.contains(SelinuxMode.ENFORCING) -> SelinuxMode.ENFORCING
            detectedModes.contains(SelinuxMode.PERMISSIVE) -> SelinuxMode.PERMISSIVE
            detectedModes.contains(SelinuxMode.DISABLED) -> SelinuxMode.DISABLED
            else -> SelinuxMode.UNKNOWN
        }

        return SelinuxStatusDetection(
            mode = finalMode,
            sourceEvidence = evidence,
            warnings = warnings,
            hasConflict = hasConflict
        )
    }
}
