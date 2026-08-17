package com.example.ui.analyzer.ril

import com.example.ui.analyzer.vendor.models.*

object RilCompatibilityAnalyzer {

    fun analyzeRilCompatibility(
        daemons: List<RilDaemonInfo>,
        libraries: List<RilLibraryInfo>,
        properties: List<RilPropertyInfo>,
        initServices: List<RilInitService>,
        selinuxDenials: List<RilSelinuxDenial>,
        logErrorsCount: Int = 0,
        trebleStatus: TrebleStatus = TrebleStatus.UNKNOWN
    ): Pair<RilReadinessScore, List<VendorIssue>> {
        val issues = mutableListOf<VendorIssue>()
        val scoreEvidence = mutableListOf<String>()

        // 1. Structure Check
        val hasRilLib = libraries.isNotEmpty()
        val hasDaemon = daemons.isNotEmpty()
        val hasInit = initServices.isNotEmpty()
        val structureStatus = when {
            hasRilLib && (hasDaemon || hasInit) -> StageStatus.FOUND
            hasRilLib || hasDaemon || hasInit -> StageStatus.FOUND
            else -> StageStatus.MISSING
        }
        scoreEvidence.add("RIL Structure: Found ${daemons.size} daemons, ${libraries.size} radio libs, ${initServices.size} init services.")

        // 2. Binary Check
        val missingDaemonBinary = initServices.any { !it.isBinaryFound }
        val binaryStatus = when {
            daemons.isNotEmpty() && !missingDaemonBinary -> StageStatus.FOUND
            daemons.isNotEmpty() && missingDaemonBinary -> StageStatus.CONFLICT
            daemons.isEmpty() && hasInit -> StageStatus.MISSING
            else -> StageStatus.UNKNOWN
        }
        if (missingDaemonBinary) {
            val missing = initServices.filter { !it.isBinaryFound }
            issues.add(
                VendorIssue(
                    type = VendorIssueType.MISSING_BINARY,
                    severity = Severity.CRITICAL,
                    message = "RIL init service declared, but daemon binary is missing from filesystem.",
                    evidence = "Declared binary: ${missing.joinToString { it.binaryPath }}",
                    source = "RilCompatibilityAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Provide the daemon binary (e.g. /vendor/bin/hw/rild or /system/bin/rild)."
                )
            )
        }

        // 3. Dependency Check
        val allMissingLibs = (daemons.flatMap { it.missingLibraries } + libraries.flatMap { it.missingLibraries }).distinct()
        val dependencyStatus = when {
            allMissingLibs.isNotEmpty() -> StageStatus.CONFLICT
            daemons.isNotEmpty() || libraries.isNotEmpty() -> StageStatus.FOUND
            else -> StageStatus.UNKNOWN
        }

        if (allMissingLibs.isNotEmpty()) {
            issues.add(
                VendorIssue(
                    type = VendorIssueType.DEPENDENCY_MISSING,
                    severity = Severity.CRITICAL,
                    message = "RIL daemon/libraries have missing dependencies (${allMissingLibs.joinToString(", ")}). Radio stack will fail to link.",
                    evidence = "Missing libraries: ${allMissingLibs.joinToString()}",
                    source = "RilCompatibilityAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Copy missing .so libraries to /vendor/lib or /system/lib."
                )
            )
        }

        // 4. SELinux Check
        val rilDenialsCount = selinuxDenials.size
        val selinuxStatus = when {
            rilDenialsCount > 5 -> StageStatus.CONFLICT
            rilDenialsCount > 0 -> StageStatus.FOUND // Denials detected
            else -> StageStatus.UNKNOWN // Unknown or no denials
        }

        if (rilDenialsCount > 0) {
            issues.add(
                VendorIssue(
                    type = VendorIssueType.SELINUX_DENIAL,
                    severity = if (rilDenialsCount > 3) Severity.ERROR else Severity.WARNING,
                    message = "SELinux denials detected for RIL domains ($rilDenialsCount events).",
                    evidence = selinuxDenials.take(3).joinToString("; ") { "${it.scontext} -> ${it.tcontext} (${it.permission})" },
                    source = "RilCompatibilityAnalyzer",
                    confidence = Confidence.HIGH,
                    recommendation = "Add allow rules to vendor sepolicy for rild / hal_telephony domains."
                )
            )
        }

        // 5. Logs Status
        val logsStatus = when {
            logErrorsCount > 5 -> LogStatus.ERRORS_FOUND
            logErrorsCount > 0 -> LogStatus.WARNINGS_FOUND
            else -> LogStatus.NO_LOGS
        }

        // Overall RIL Readiness (Deterministic calculation)
        // High Readiness requires: Structure present, Binary present, Dependencies resolved, No severe SELinux blocking
        val overallReadiness = when {
            structureStatus == StageStatus.MISSING && binaryStatus == StageStatus.MISSING -> RilReadinessLevel.MISSING_OR_INCOMPATIBLE
            dependencyStatus == StageStatus.CONFLICT || binaryStatus == StageStatus.CONFLICT -> RilReadinessLevel.HIGH_RISK
            structureStatus == StageStatus.FOUND && dependencyStatus == StageStatus.FOUND && rilDenialsCount == 0 -> RilReadinessLevel.HIGH_READINESS
            structureStatus == StageStatus.FOUND -> RilReadinessLevel.PARTIAL_READINESS
            else -> RilReadinessLevel.UNKNOWN
        }

        var scorePct = 0
        if (structureStatus == StageStatus.FOUND) scorePct += 30
        if (binaryStatus == StageStatus.FOUND) scorePct += 30
        if (dependencyStatus == StageStatus.FOUND) scorePct += 25
        if (properties.isNotEmpty()) scorePct += 15
        if (dependencyStatus == StageStatus.CONFLICT) scorePct -= 35
        if (binaryStatus == StageStatus.CONFLICT) scorePct -= 35
        if (rilDenialsCount > 0) scorePct -= 15
        scorePct = scorePct.coerceIn(0, 100)

        val summary = when (overallReadiness) {
            RilReadinessLevel.HIGH_READINESS -> "RIL components and dependencies are intact. Ready for runtime testing."
            RilReadinessLevel.PARTIAL_READINESS -> "Basic RIL structure detected, but runtime dependencies or configs need verification."
            RilReadinessLevel.HIGH_RISK -> "High risk: Missing binaries, missing shared libraries, or severe SELinux denials will block telephony."
            RilReadinessLevel.MISSING_OR_INCOMPATIBLE -> "No functional RIL daemon or vendor radio libraries detected in image."
            RilReadinessLevel.UNKNOWN -> "Insufficient data to determine RIL readiness."
        }

        val score = RilReadinessScore(
            structureStatus = structureStatus,
            binaryStatus = binaryStatus,
            dependencyStatus = dependencyStatus,
            selinuxStatus = selinuxStatus,
            logsStatus = logsStatus,
            overallReadiness = overallReadiness,
            readinessPercentage = scorePct,
            diagnosticSummary = summary,
            scoreEvidence = scoreEvidence
        )

        return Pair(score, issues)
    }
}
