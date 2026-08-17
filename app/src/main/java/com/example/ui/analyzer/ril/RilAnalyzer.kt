package com.example.ui.analyzer.ril

import com.example.ui.analyzer.boot.InitRcParser
import com.example.ui.analyzer.vendor.models.*
import java.io.File

object RilAnalyzer {

    fun analyzeRil(
        rootDirectory: File?,
        vendorBinaries: List<VendorBinary> = emptyList(),
        vendorLibraries: List<VendorLibrary> = emptyList(),
        properties: Map<String, String> = emptyMap(),
        selinuxLogs: List<String> = emptyList(),
        logLines: List<String> = emptyList(),
        trebleStatus: TrebleStatus = TrebleStatus.UNKNOWN
    ): RilInfo {
        val findings = mutableListOf<EvidenceFinding>()
        val issues = mutableListOf<VendorIssue>()

        // 1. Detect Daemons and Libraries
        val daemons = RilServiceAnalyzer.analyzeRilDaemons(vendorBinaries)
        val libraries = RilLibraryAnalyzer.analyzeRilLibraries(vendorLibraries)

        // 2. Parse Init Services for RIL
        val initServices = mutableListOf<RilInitService>()
        if (rootDirectory != null && rootDirectory.exists()) {
            val rcFiles = mutableListOf<File>()
            rootDirectory.walkTopDown().maxDepth(4).forEach {
                if (it.isFile && (it.name.endsWith(".rc") || it.name.startsWith("init."))) {
                    rcFiles.add(it)
                }
            }
            val parsedInitServices = rcFiles.flatMap {
                try {
                    InitRcParser.parse(it.readText(Charsets.UTF_8), it.name).services
                } catch (e: Exception) {
                    emptyList()
                }
            }
            initServices.addAll(RilServiceAnalyzer.analyzeRilInitServices(parsedInitServices, daemons))
        }

        // 3. Properties
        val rilProps = RilPropertyAnalyzer.analyzeProperties(properties)

        // 4. SELinux Denials Extraction
        val selinuxDenials = mutableListOf<RilSelinuxDenial>()
        val avcRegex = Regex("""avc:\s+denied\s+\{\s*([^}]+)\s*\}\s+for.*scontext=([^\s]+)\s+tcontext=([^\s]+)\s+tclass=([^\s]+)""")
        for (log in selinuxLogs + logLines) {
            val match = avcRegex.find(log)
            if (match != null) {
                val perm = match.groupValues[1].trim()
                val scontext = match.groupValues[2].trim()
                val tcontext = match.groupValues[3].trim()
                val tclass = match.groupValues[4].trim()

                if (scontext.contains("rild") || scontext.contains("radio") || scontext.contains("hal_telephony") ||
                    tcontext.contains("rild") || tcontext.contains("radio") || tcontext.contains("telephony")) {
                    selinuxDenials.add(
                        RilSelinuxDenial(
                            scontext = scontext,
                            tcontext = tcontext,
                            tclass = tclass,
                            permission = perm,
                            rawLog = log,
                            impact = "May cause RIL daemon restart, modem hang, or socket communication failure."
                        )
                    )
                }
            }
        }

        // 5. Build Dependency Chain
        val primaryDaemon = daemons.firstOrNull()?.name ?: initServices.firstOrNull()?.binaryPath?.substringAfterLast("/") ?: "rild"
        val primaryLib = libraries.firstOrNull { it.isVendorSpecific }?.name ?: libraries.firstOrNull()?.name ?: "libril.so"
        val vendorImpl = libraries.firstOrNull { it.vendorFlavor != "AOSP Reference RIL" && it.name.contains("ril", ignoreCase = true) }?.name ?: "libmtk-ril.so / libsec-ril.so"

        val chain = RilDependencyChain(
            initService = initServices.firstOrNull()?.serviceName ?: "ril-daemon",
            initStatus = if (initServices.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            daemonBinary = primaryDaemon,
            daemonStatus = if (daemons.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            rilLibrary = primaryLib,
            rilLibStatus = if (libraries.isNotEmpty()) StageStatus.FOUND else StageStatus.MISSING,
            vendorImplLibrary = vendorImpl,
            vendorImplStatus = if (libraries.any { it.isVendorSpecific }) StageStatus.FOUND else StageStatus.MISSING,
            halService = if (trebleStatus == TrebleStatus.TREBLE) "android.hardware.radio@1.0+" else "Legacy Direct IPC",
            halStatus = if (trebleStatus == TrebleStatus.TREBLE) StageStatus.FOUND else StageStatus.UNKNOWN,
            kernelInterface = "/dev/ccci* or /dev/smd* or /dev/ttyACM*",
            kernelStatus = StageStatus.UNKNOWN,
            selinuxDenialsCount = selinuxDenials.size,
            logIssuesCount = logLines.count { it.contains("RIL", ignoreCase = true) && (it.contains("E ") || it.contains("FATAL") || it.contains("crash")) }
        )

        // 6. Compatibility & Readiness Score
        val (readinessScore, compatIssues) = RilCompatibilityAnalyzer.analyzeRilCompatibility(
            daemons = daemons,
            libraries = libraries,
            properties = rilProps,
            initServices = initServices,
            selinuxDenials = selinuxDenials,
            logErrorsCount = chain.logIssuesCount,
            trebleStatus = trebleStatus
        )
        issues.addAll(compatIssues)

        findings.add(
            EvidenceFinding(
                fact = "RIL Telephony Stack Readiness",
                evidence = "Level: ${readinessScore.overallReadiness} (${readinessScore.readinessPercentage}%). ${readinessScore.diagnosticSummary}",
                severity = when (readinessScore.overallReadiness) {
                    RilReadinessLevel.HIGH_READINESS -> Severity.INFO
                    RilReadinessLevel.PARTIAL_READINESS -> Severity.WARNING
                    RilReadinessLevel.HIGH_RISK -> Severity.ERROR
                    RilReadinessLevel.MISSING_OR_INCOMPATIBLE -> Severity.ERROR
                    RilReadinessLevel.UNKNOWN -> Severity.INFO
                },
                confidence = Confidence.HIGH,
                source = "RilAnalyzer"
            )
        )

        val detectedFlavors = libraries.map { it.vendorFlavor }.distinct()
        if (detectedFlavors.isNotEmpty()) {
            findings.add(
                EvidenceFinding(
                    fact = "RIL Vendor Flavor",
                    evidence = "Detected implementations: ${detectedFlavors.joinToString()}",
                    severity = Severity.INFO,
                    confidence = Confidence.HIGH,
                    source = "RilLibraryAnalyzer"
                )
            )
        }

        return RilInfo(
            daemons = daemons,
            libraries = libraries,
            properties = rilProps,
            initServices = initServices,
            selinuxDenials = selinuxDenials,
            dependencyChain = chain,
            readinessScore = readinessScore,
            findings = findings,
            issues = issues
        )
    }
}
