package com.example.ui.analyzer.getprop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ui.analyzer.core.AnalyzerResult
import com.example.ui.analyzer.core.AnalyzerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object GetpropAnalyzer {

    suspend fun analyzeUris(
        context: Context,
        uris: List<Uri>,
        snapshotName: String = "Properties Snapshot"
    ): GetpropAnalysisResult = withContext(Dispatchers.IO) {
        val rawList = mutableListOf<RawParsedProperties>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (uri in uris) {
            try {
                var fileName = "build.prop"
                var fileSize = 0L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: "file.prop"
                        if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val raw = GetpropParser.parseStream(inputStream, fileName, uri.toString())
                    rawList.add(raw)
                    warnings.addAll(raw.warnings)
                } else {
                    errors.add("Unable to open input stream for $fileName ($uri)")
                }
            } catch (e: Exception) {
                errors.add("Error reading URI $uri: ${e.message}")
            }
        }

        if (rawList.isEmpty() && errors.isNotEmpty()) {
            val emptySnapshot = GetpropSnapshot(
                name = snapshotName,
                sources = emptyList(),
                properties = emptyMap(),
                allEntries = emptyList(),
                deviceSummary = DeviceSummary()
            )
            return@withContext GetpropAnalysisResult(
                status = AnalyzerStatus.ERROR,
                snapshot = emptySnapshot,
                errors = errors,
                rawSummary = "Analysis Failed",
                rawDetails = errors.joinToString("\n")
            )
        }

        analyzeRawList(rawList, snapshotName, warnings, errors)
    }

    suspend fun analyzeLive(): GetpropAnalysisResult = withContext(Dispatchers.IO) {
        val liveRes = RootGetpropCollector.collectLiveProperties()
        if (liveRes.isSuccess && liveRes.rawProperties != null) {
            val rawList = listOf(liveRes.rawProperties)
            val name = if (liveRes.isRootUsed) "Live System Properties (Root)" else "Live System Properties"
            analyzeRawList(rawList, name, emptyList(), emptyList())
        } else {
            val emptySnapshot = GetpropSnapshot(
                name = "Live Properties (Failed)",
                sources = emptyList(),
                properties = emptyMap(),
                allEntries = emptyList(),
                deviceSummary = DeviceSummary()
            )
            GetpropAnalysisResult(
                status = AnalyzerStatus.ERROR,
                snapshot = emptySnapshot,
                errors = listOf(liveRes.errorMessage ?: "Failed to collect live properties"),
                rawSummary = "Live Property Collection Failed",
                rawDetails = liveRes.errorMessage ?: "Unknown error"
            )
        }
    }

    fun analyzeString(
        content: String,
        sourceName: String = "build.prop",
        snapshotName: String = "Parsed Snapshot"
    ): GetpropAnalysisResult {
        val raw = GetpropParser.parseString(content, sourceName)
        return analyzeRawList(listOf(raw), snapshotName, raw.warnings, emptyList())
    }

    fun analyzeRawList(
        rawList: List<RawParsedProperties>,
        snapshotName: String,
        existingWarnings: List<String>,
        existingErrors: List<String>
    ): GetpropAnalysisResult {
        val sourceFiles = rawList.map {
            SourceFileInfo(
                fileName = it.sourceName,
                path = it.sourcePath,
                sizeBytes = it.sizeBytes,
                sha256 = it.sha256,
                parsedCount = it.parsedCount,
                skippedCount = it.skippedCount
            )
        }

        // Aggregate entries and detect duplicates / conflicts
        val occurrencesMap = mutableMapOf<String, MutableList<PropertyOccurrence>>()
        val allEntries = mutableListOf<GetpropEntry>()

        for (raw in rawList) {
            for (entry in raw.entries) {
                allEntries.add(entry)
                val list = occurrencesMap.getOrPut(entry.key) { mutableListOf() }
                list.add(PropertyOccurrence(entry.source, entry.lineNumber, entry.value))
            }
        }

        val consolidatedProperties = mutableMapOf<String, GetpropEntry>()
        val duplicatesList = mutableListOf<GetpropEntry>()
        val conflictsList = mutableListOf<GetpropEntry>()
        val allWarnings = existingWarnings.toMutableList()

        for ((key, occList) in occurrencesMap) {
            val primaryOcc = occList.first()
            val distinctValues = occList.map { it.value }.distinct()
            val isDuplicate = occList.size > 1
            val isConflict = distinctValues.size > 1

            val conflictStatus = when {
                isConflict -> ConflictStatus.CONFLICT_VALUE_MISMATCH
                isDuplicate -> ConflictStatus.DUPLICATE_IDENTICAL
                else -> ConflictStatus.NONE
            }

            val entry = GetpropEntry(
                key = key,
                value = primaryOcc.value,
                source = primaryOcc.source,
                lineNumber = primaryOcc.lineNumber,
                category = GetpropCategory.categorize(key),
                valueType = PropertyValueType.detect(primaryOcc.value),
                isDuplicate = isDuplicate,
                conflictStatus = conflictStatus,
                occurrences = occList
            )

            consolidatedProperties[key] = entry

            if (isDuplicate) {
                duplicatesList.add(entry)
            }
            if (isConflict) {
                conflictsList.add(entry)
                val conflictSources = occList.joinToString(", ") { "${it.source}(L${it.lineNumber}): '${it.value}'" }
                allWarnings.add("Property conflict for '$key': $conflictSources")
            }
        }

        // 1. Extract Device & Build Summary
        val deviceSummary = extractDeviceSummary(consolidatedProperties, allWarnings)

        // 2. Extract Subsystems
        val hardwareSoc = extractHardwareSoc(consolidatedProperties, allWarnings)
        val graphics = extractGraphics(consolidatedProperties)
        val runtimeArt = extractRuntimeArt(consolidatedProperties)
        val display = extractDisplay(consolidatedProperties)
        val telephonyRil = extractTelephonyRil(consolidatedProperties)
        val media = extractMedia(consolidatedProperties)
        val securitySelinux = extractSecuritySelinux(consolidatedProperties, allWarnings)

        // Category and Type Statistics
        val categoryCounts = mutableMapOf<GetpropCategory, Int>()
        val typeCounts = mutableMapOf<PropertyValueType, Int>()

        for (entry in consolidatedProperties.values) {
            categoryCounts[entry.category] = (categoryCounts[entry.category] ?: 0) + 1
            typeCounts[entry.valueType] = (typeCounts[entry.valueType] ?: 0) + 1
        }

        val snapshot = GetpropSnapshot(
            name = snapshotName,
            sources = sourceFiles,
            properties = consolidatedProperties,
            allEntries = allEntries,
            deviceSummary = deviceSummary,
            totalPropertiesCount = consolidatedProperties.size,
            duplicateCount = duplicatesList.size,
            conflictCount = conflictsList.size
        )

        val status = when {
            existingErrors.isNotEmpty() -> AnalyzerStatus.ERROR
            conflictsList.isNotEmpty() || allWarnings.isNotEmpty() -> AnalyzerStatus.WARNING
            else -> AnalyzerStatus.SUCCESS
        }

        val summaryText = buildRawSummary(snapshot, hardwareSoc, graphics, securitySelinux)
        val detailsText = buildRawDetails(snapshot, consolidatedProperties)

        return GetpropAnalysisResult(
            status = status,
            snapshot = snapshot,
            hardwareSoc = hardwareSoc,
            graphics = graphics,
            runtimeArt = runtimeArt,
            display = display,
            telephonyRil = telephonyRil,
            media = media,
            securitySelinux = securitySelinux,
            categoryCounts = categoryCounts,
            typeCounts = typeCounts,
            duplicatesList = duplicatesList,
            conflictsList = conflictsList,
            warnings = allWarnings,
            errors = existingErrors,
            rawSummary = summaryText,
            rawDetails = detailsText
        )
    }

    private fun extractDeviceSummary(props: Map<String, GetpropEntry>, warnings: MutableList<String>): DeviceSummary {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val model = prop("ro.product.model").ifEmpty { prop("ro.product.vendor.model") }.ifEmpty { "Unknown" }
        val name = prop("ro.product.name").ifEmpty { prop("ro.product.vendor.name") }.ifEmpty { "Unknown" }
        val device = prop("ro.product.device").ifEmpty { prop("ro.product.vendor.device") }.ifEmpty { "Unknown" }
        val board = prop("ro.product.board").ifEmpty { prop("ro.board.platform") }.ifEmpty { "Unknown" }
        val brand = prop("ro.product.brand").ifEmpty { prop("ro.product.vendor.brand") }.ifEmpty { "Unknown" }
        val manufacturer = prop("ro.product.manufacturer").ifEmpty { prop("ro.product.vendor.manufacturer") }.ifEmpty { "Unknown" }
        val hardware = prop("ro.hardware").ifEmpty { prop("ro.boot.hardware") }.ifEmpty { "Unknown" }
        val platform = prop("ro.board.platform").ifEmpty { prop("ro.boot.hardware.platform") }.ifEmpty { "Unknown" }
        val socModel = prop("ro.soc.model").ifEmpty { prop("ro.chipname") }.ifEmpty { platform }

        val androidVersion = prop("ro.build.version.release").ifEmpty { "Unknown" }
        val sdk = prop("ro.build.version.sdk").toIntOrNull() ?: 0
        val codename = prop("ro.build.version.codename").ifEmpty { "Unknown" }
        val buildId = prop("ro.build.id").ifEmpty { "Unknown" }
        val buildDisplayId = prop("ro.build.display.id").ifEmpty { "Unknown" }
        val securityPatch = prop("ro.build.version.security_patch").ifEmpty { "Unknown" }
        val incremental = prop("ro.build.version.incremental").ifEmpty { "Unknown" }

        val primaryAbi = prop("ro.product.cpu.abi").ifEmpty { "Unknown" }
        val abilistRaw = prop("ro.product.cpu.abilist").ifEmpty { prop("ro.product.cpu.abi") }
        val abilist32 = prop("ro.product.cpu.abilist32")
        val abilist64 = prop("ro.product.cpu.abilist64")

        val abiList = if (abilistRaw.isNotEmpty()) abilistRaw.split(",").map { it.trim() } else emptyList()

        val abiType = when {
            primaryAbi.contains("arm64") || abilist64.isNotEmpty() -> "ARM64 (64-bit)"
            primaryAbi.startsWith("armeabi") && abilist64.isEmpty() -> "ARM32 only"
            primaryAbi.contains("x86_64") -> "x86_64"
            primaryAbi.contains("x86") -> "x86"
            primaryAbi != "Unknown" -> "Mixed / $primaryAbi"
            else -> "Unknown"
        }

        val selinuxMode = when (prop("ro.boot.selinux").lowercase()) {
            "permissive" -> "Permissive"
            "enforcing" -> "Enforcing"
            "disabled" -> "Disabled"
            else -> if (prop("ro.build.selinux") == "1") "Enforcing" else "Unknown"
        }

        val debuggable = prop("ro.debuggable").let { if (it == "1") true else if (it == "0") false else null }
        val secure = prop("ro.secure").let { if (it == "1") true else if (it == "0") false else null }
        val adbSecure = prop("ro.adb.secure").let { if (it == "1") true else if (it == "0") false else null }
        val buildTags = prop("ro.build.tags").ifEmpty { "Unknown" }

        return DeviceSummary(
            model = model,
            name = name,
            device = device,
            board = board,
            brand = brand,
            manufacturer = manufacturer,
            hardware = hardware,
            platform = platform,
            socModel = socModel,
            androidVersion = androidVersion,
            sdk = sdk,
            codename = codename,
            buildId = buildId,
            buildDisplayId = buildDisplayId,
            securityPatch = securityPatch,
            incremental = incremental,
            primaryAbi = primaryAbi,
            abiList = abiList,
            abiType = abiType,
            selinuxMode = selinuxMode,
            isDebuggable = debuggable,
            isSecure = secure,
            isAdbSecure = adbSecure,
            buildTags = buildTags
        )
    }

    private fun extractHardwareSoc(props: Map<String, GetpropEntry>, warnings: MutableList<String>): HardwareSocDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val hardware = prop("ro.hardware")
        val board = prop("ro.product.board")
        val platform = prop("ro.board.platform")
        val socModel = prop("ro.soc.model")
        val bootHardware = prop("ro.boot.hardware")
        val bootPlatform = prop("ro.boot.hardware.platform")
        val chipname = prop("ro.chipname")

        val conflictList = mutableListOf<String>()

        if (hardware.isNotEmpty() && bootHardware.isNotEmpty() && !hardware.equals(bootHardware, ignoreCase = true)) {
            conflictList.add("Hardware discrepancy: ro.hardware='$hardware' vs ro.boot.hardware='$bootHardware'")
        }
        if (platform.isNotEmpty() && bootPlatform.isNotEmpty() && !platform.equals(bootPlatform, ignoreCase = true)) {
            conflictList.add("Platform discrepancy: ro.board.platform='$platform' vs ro.boot.hardware.platform='$bootPlatform'")
        }

        warnings.addAll(conflictList)

        return HardwareSocDetails(
            hardware = hardware.ifEmpty { "Unknown" },
            board = board.ifEmpty { "Unknown" },
            platform = platform.ifEmpty { "Unknown" },
            socModel = socModel.ifEmpty { "Unknown" },
            bootHardware = bootHardware.ifEmpty { "Unknown" },
            bootPlatform = bootPlatform.ifEmpty { "Unknown" },
            chipname = chipname.ifEmpty { "Unknown" },
            hasConflict = conflictList.isNotEmpty(),
            warnings = conflictList
        )
    }

    private fun extractGraphics(props: Map<String, GetpropEntry>): GraphicsDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val eglHardware = prop("ro.hardware.egl").ifEmpty { prop("ro.board.platform") }.ifEmpty { "Unknown" }
        val glesRaw = prop("ro.opengles.version")

        val glesFormatted = if (glesRaw.isNotEmpty()) {
            val num = glesRaw.toIntOrNull()
            if (num != null) {
                val major = (num shr 16) and 0xFFFF
                val minor = num and 0xFFFF
                "OpenGL ES $major.$minor (0x${num.toString(16).padStart(8, '0')})"
            } else {
                glesRaw
            }
        } else {
            "Unknown"
        }

        val hwuiRenderer = prop("debug.hwui.renderer").ifEmpty { prop("ro.hwui.renderer") }.ifEmpty { "Default (OpenGL/Vulkan)" }
        val isHwuiDetected = props.keys.any { it.startsWith("ro.hwui.") || it.startsWith("debug.hwui.") }

        val graphicsProps = props.values.filter { it.category == GetpropCategory.GRAPHICS }

        return GraphicsDetails(
            eglHardware = eglHardware,
            glesVersionRaw = glesRaw.ifEmpty { "Unknown" },
            glesVersionFormatted = glesFormatted,
            hwuiRenderer = hwuiRenderer,
            isHwuiDetected = isHwuiDetected,
            graphicsProperties = graphicsProps
        )
    }

    private fun extractRuntimeArt(props: Map<String, GetpropEntry>): RuntimeArtDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val heapStartSize = prop("dalvik.vm.heapstartsize").ifEmpty { "Unknown" }
        val heapGrowthLimit = prop("dalvik.vm.heapgrowthlimit").ifEmpty { "Unknown" }
        val heapSize = prop("dalvik.vm.heapsize").ifEmpty { "Unknown" }
        val heapMinFree = prop("dalvik.vm.heapminfree").ifEmpty { "Unknown" }
        val heapMaxFree = prop("dalvik.vm.heapmaxfree").ifEmpty { "Unknown" }
        val heapTargetUtilization = prop("dalvik.vm.heaptargetutilization").ifEmpty { "Unknown" }
        val useJit = prop("dalvik.vm.usejit").ifEmpty { "Unknown" }
        val dex2oatFilter = prop("dalvik.vm.dex2oat-filter").ifEmpty { prop("pm.dexopt.install") }.ifEmpty { "Unknown" }
        val imageDex2oatXms = prop("dalvik.vm.image-dex2oat-Xms").ifEmpty { "Unknown" }
        val imageDex2oatXmx = prop("dalvik.vm.image-dex2oat-Xmx").ifEmpty { "Unknown" }

        val dalvikProps = props.values.filter { it.category == GetpropCategory.DALVIK || it.category == GetpropCategory.ART }

        return RuntimeArtDetails(
            heapStartSize = heapStartSize,
            heapGrowthLimit = heapGrowthLimit,
            heapSize = heapSize,
            heapMinFree = heapMinFree,
            heapMaxFree = heapMaxFree,
            heapTargetUtilization = heapTargetUtilization,
            useJit = useJit,
            dex2oatFilter = dex2oatFilter,
            imageDex2oatXms = imageDex2oatXms,
            imageDex2oatXmx = imageDex2oatXmx,
            dalvikProperties = dalvikProps
        )
    }

    private fun extractDisplay(props: Map<String, GetpropEntry>): DisplayDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val lcdDensity = prop("ro.sf.lcd_density").ifEmpty { prop("ro.sf.density") }.ifEmpty { "Unknown" }
        val sfProps = props.values.filter { it.category == GetpropCategory.DISPLAY }

        return DisplayDetails(
            lcdDensity = lcdDensity,
            surfaceFlingerProperties = sfProps
        )
    }

    private fun extractTelephonyRil(props: Map<String, GetpropEntry>): TelephonyRilDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val rilImpl = prop("gsm.version.ril-impl").ifEmpty { prop("ro.telephony.ril_class") }.ifEmpty { "Unknown" }
        val rildLibPath = prop("rild.libpath").ifEmpty { prop("vendor.rild.libpath") }.ifEmpty { "Unknown" }
        val telephonyProps = props.values.filter {
            it.category == GetpropCategory.TELEPHONY ||
            it.category == GetpropCategory.RIL ||
            it.category == GetpropCategory.RADIO
        }

        return TelephonyRilDetails(
            rilImplementation = rilImpl,
            rildLibPath = rildLibPath,
            telephonyProperties = telephonyProps,
            isRilDetected = telephonyProps.isNotEmpty()
        )
    }

    private fun extractMedia(props: Map<String, GetpropEntry>): MediaDetails {
        val cameraProps = props.values.filter { it.category == GetpropCategory.CAMERA }
        val audioProps = props.values.filter { it.category == GetpropCategory.AUDIO }
        val mediaProps = props.values.filter { it.category == GetpropCategory.MEDIA }

        return MediaDetails(
            cameraProperties = cameraProps,
            audioProperties = audioProps,
            mediaProperties = mediaProps
        )
    }

    private fun extractSecuritySelinux(props: Map<String, GetpropEntry>, warnings: MutableList<String>): SecuritySelinuxDetails {
        fun prop(key: String): String = props[key]?.value?.trim() ?: ""

        val selinuxBoot = prop("ro.boot.selinux").ifEmpty { "Unknown" }
        val selinuxMode = if (selinuxBoot.equals("permissive", ignoreCase = true)) "Permissive" else "Enforcing / Default"
        val buildTags = prop("ro.build.tags").ifEmpty { "Unknown" }
        val debuggable = prop("ro.debuggable").ifEmpty { "Unknown" }
        val secure = prop("ro.secure").ifEmpty { "Unknown" }
        val adbSecure = prop("ro.adb.secure").ifEmpty { "Unknown" }
        val cryptoState = prop("ro.crypto.state").ifEmpty { "Unknown" }

        val secWarnings = mutableListOf<String>()
        if (selinuxBoot.equals("permissive", ignoreCase = true)) {
            secWarnings.add("SELinux configured as Permissive via ro.boot.selinux")
        }
        if (debuggable == "1") {
            secWarnings.add("Debuggable build flag enabled (ro.debuggable=1)")
        }
        if (secure == "0") {
            secWarnings.add("System security flag disabled (ro.secure=0)")
        }
        if (buildTags.contains("test-keys")) {
            secWarnings.add("Build signed with non-production test-keys")
        }

        warnings.addAll(secWarnings)

        val secProps = props.values.filter {
            it.category == GetpropCategory.SECURITY || it.category == GetpropCategory.SELINUX
        }

        return SecuritySelinuxDetails(
            selinuxBoot = selinuxBoot,
            selinuxMode = selinuxMode,
            buildTags = buildTags,
            debuggable = debuggable,
            secure = secure,
            adbSecure = adbSecure,
            cryptoState = cryptoState,
            warnings = secWarnings,
            securityProperties = secProps
        )
    }

    private fun buildRawSummary(
        snapshot: GetpropSnapshot,
        hw: HardwareSocDetails,
        gfx: GraphicsDetails,
        sec: SecuritySelinuxDetails
    ): String {
        val s = snapshot.deviceSummary
        return buildString {
            appendLine("=== ANDROID SYSTEM PROPERTIES SUMMARY ===")
            appendLine("Device: ${s.manufacturer} ${s.model} (${s.device}, Board: ${s.board})")
            appendLine("Android OS: ${s.androidVersion} (SDK ${s.sdk}, Codename: ${s.codename})")
            appendLine("Build ID: ${s.buildId} [${s.buildDisplayId}]")
            appendLine("Security Patch: ${s.securityPatch}")
            appendLine("ABI: ${s.primaryAbi} [${s.abiType}]")
            appendLine("SoC / Platform: ${hw.platform} (Hardware: ${hw.hardware}, Model: ${hw.socModel})")
            appendLine("Graphics: EGL: ${gfx.eglHardware}, GLES: ${gfx.glesVersionFormatted}")
            appendLine("SELinux: ${sec.selinuxMode}, Debuggable: ${sec.debuggable}, Secure: ${sec.secure}")
            appendLine("Total Properties: ${snapshot.totalPropertiesCount}")
            if (snapshot.duplicateCount > 0) appendLine("Duplicate Keys: ${snapshot.duplicateCount}")
            if (snapshot.conflictCount > 0) appendLine("Conflict Keys: ${snapshot.conflictCount}")
        }.trim()
    }

    private fun buildRawDetails(snapshot: GetpropSnapshot, props: Map<String, GetpropEntry>): String {
        return buildString {
            props.toSortedMap().forEach { (k, v) ->
                val dupTag = if (v.conflictStatus == ConflictStatus.CONFLICT_VALUE_MISMATCH) " [CONFLICT]" else if (v.isDuplicate) " [DUPLICATE]" else ""
                appendLine("$k = ${v.value} (${v.category.displayName}, ${v.valueType})$dupTag")
            }
        }.trim()
    }

    /**
     * Legacy adapter for UnifiedAnalyzer
     */
    suspend fun parseLive(): AnalyzerResult = withContext(Dispatchers.IO) {
        val res = analyzeLive()
        AnalyzerResult(
            status = res.status,
            summary = res.rawSummary,
            details = res.rawDetails
        )
    }
}
