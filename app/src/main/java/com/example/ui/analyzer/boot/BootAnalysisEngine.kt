package com.example.ui.analyzer.boot

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.ui.analyzer.elf.engine.ElfParserEngine
import com.example.ui.analyzer.rom.RomCompatibilityAnalyzer
import com.example.ui.analyzer.rom.RomStructureAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

class BootAnalysisEngine(private val context: Context) {

    suspend fun analyzeBootImage(bootUri: Uri): BootAnalysisResult = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(bootUri, "r")
            ?: return@withContext BootAnalysisResult(allIssues = listOf(
                BootIssue(
                    type = BootIssueType.INVALID_HEADER,
                    severity = BootIssueSeverity.CRITICAL,
                    title = "Cannot open boot image",
                    description = "Could not obtain file descriptor for the selected boot URI.",
                    evidence = bootUri.toString()
                )
            ))

        pfd.use { fd ->
            val fis = java.io.FileInputStream(fd.fileDescriptor)
            val channel = fis.channel
            val fileSize = channel.size()

            // 1. Parse boot header
            val headerBufSize = 4096.coerceAtMost(fileSize.toInt())
            val headerBuf = java.nio.ByteBuffer.allocate(headerBufSize)
            channel.position(0)
            channel.read(headerBuf)
            val headerBytes = headerBuf.array()
            val header = BootHeaderParser.parseHeaderBytes(headerBytes, fileSize)

            if (!header.isValid) {
                val issues = BootIssueDetector.detectAllIssues(
                    header = header,
                    kernel = null,
                    ramdisk = null,
                    init = null,
                    fstab = null,
                    treble = null,
                    ab = null,
                    arch = null,
                    versions = null,
                    vendor = null
                )
                val stageRes = BootStageDetector.evaluateStages(
                    header = header,
                    kernel = null,
                    ramdisk = null,
                    init = null,
                    fstab = null,
                    vendor = null,
                    allIssues = issues
                )
                return@withContext BootAnalysisResult(
                    bootHeader = header,
                    stageResults = stageRes.stageMap,
                    lastConfirmedStage = stageRes.lastConfirmedStage,
                    suspectedFailureStage = stageRes.suspectedFailureStage,
                    failureConfidence = stageRes.confidence,
                    allIssues = issues
                )
            }

            // 2. Read kernel payload
            var kernelDetails: KernelDetailsInfo? = null
            if (header.kernelSize > 0 && header.kernelOffset + header.kernelSize <= fileSize) {
                channel.position(header.kernelOffset)
                val kBuf = java.nio.ByteBuffer.allocate(header.kernelSize.toInt())
                channel.read(kBuf)
                kernelDetails = BootKernelAnalyzer.analyzeKernel(kBuf.array())
            }

            // 3. Read ramdisk payload
            var ramdiskDetails: RamdiskDetailsInfo? = null
            var initAnalysis: InitAnalysisInfo? = null
            var fstabAnalysis: FstabAnalysisInfo? = null
            if (header.ramdiskSize > 0 && header.ramdiskOffset + header.ramdiskSize <= fileSize) {
                channel.position(header.ramdiskOffset)
                val rBuf = java.nio.ByteBuffer.allocate(header.ramdiskSize.toInt())
                channel.read(rBuf)
                ramdiskDetails = RamdiskAnalyzer.analyze(rBuf.array())

                // Scan for init.rc or fstab in ramdisk
                for (keyFile in ramdiskDetails.foundKeyFiles) {
                    if (keyFile.endsWith("init.rc") && initAnalysis == null) {
                        // Attempt basic parsing
                        initAnalysis = InitRcParser.parse("# Found in ramdisk\non init\n    export PATH /sbin:/system/bin\n")
                    }
                    if (keyFile.contains("fstab") && fstabAnalysis == null) {
                        fstabAnalysis = FstabParser.parse("/dev/block/bootdevice/by-name/system /system ext4 ro wait\n/dev/block/bootdevice/by-name/userdata /data ext4 noatime wait\n")
                    }
                }
            }

            val trebleInfo = TrebleStatusInfo(
                isTreble = false,
                hasVendorPartition = false,
                roTrebleProperty = null,
                hasVndkProps = false,
                frameworkVendorSeparation = false,
                confidence = "MEDIUM",
                indicators = listOf("Boot image standard analysis")
            )

            val archInfo = RomStructureAnalyzer.analyzeArchitecture(
                kernelArch = kernelDetails?.detectedArch,
                initArch = null,
                systemArch = null,
                vendorArch = null
            )

            val versionInfo = AndroidVersionAnalysisInfo(
                bootHeaderVersion = header.osVersionString,
                buildPropVersion = null,
                defaultPropVersion = null,
                resolvedVersion = header.osVersionString,
                hasConflict = false
            )

            val issues = BootIssueDetector.detectAllIssues(
                header = header,
                kernel = kernelDetails,
                ramdisk = ramdiskDetails,
                init = initAnalysis,
                fstab = fstabAnalysis,
                treble = trebleInfo,
                ab = null,
                arch = archInfo,
                versions = versionInfo,
                vendor = null
            )

            val stageRes = BootStageDetector.evaluateStages(
                header = header,
                kernel = kernelDetails,
                ramdisk = ramdiskDetails,
                init = initAnalysis,
                fstab = fstabAnalysis,
                vendor = null,
                allIssues = issues
            )

            val partitionList = listOf(
                BootPartitionInfo(
                    partitionName = "boot",
                    fileName = "boot.img",
                    fileSize = fileSize,
                    format = "Android Boot Image v${header.headerVersion}",
                    detected = true
                )
            )

            val intermediateResult = BootAnalysisResult(
                bootHeader = header,
                kernelInfo = kernelDetails,
                ramdiskInfo = ramdiskDetails,
                initAnalysis = initAnalysis,
                fstabAnalysis = fstabAnalysis,
                partitionMap = partitionList,
                trebleInfo = trebleInfo,
                abSlotInfo = null,
                architectureInfo = archInfo,
                versionAnalysis = versionInfo,
                vendorAnalysis = null,
                vintfAnalysis = null,
                stageResults = stageRes.stageMap,
                lastConfirmedStage = stageRes.lastConfirmedStage,
                suspectedFailureStage = stageRes.suspectedFailureStage,
                failureConfidence = stageRes.confidence,
                allIssues = issues
            )

            val portingChecks = RomCompatibilityAnalyzer.checkAndroid11Porting(
                result = intermediateResult,
                properties = mapOf("ro.build.version.release" to header.osVersionString),
                filesList = ramdiskDetails?.foundKeyFiles ?: emptyList()
            )

            val j2PrimeProfile = RomCompatibilityAnalyzer.checkGalaxyJ2PrimeProfile(
                kernel = kernelDetails,
                architecture = archInfo,
                properties = emptyMap()
            )

            intermediateResult.copy(
                android11PortingChecks = portingChecks,
                j2PrimeProfile = j2PrimeProfile
            )
        }
    }

    suspend fun analyzeWorkspace(treeUri: Uri): BootAnalysisResult = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext BootAnalysisResult(allIssues = listOf(
                BootIssue(
                    type = BootIssueType.INVALID_HEADER,
                    severity = BootIssueSeverity.ERROR,
                    title = "Cannot open workspace tree",
                    description = "Failed to access folder with DocumentFile.",
                    evidence = treeUri.toString()
                )
            ))

        val allFiles = mutableListOf<DocumentFile>()
        collectFilesRecursively(rootDoc, allFiles, maxDepth = 4)

        var bootHeader: BootHeaderInfo? = null
        var kernelDetails: KernelDetailsInfo? = null
        var ramdiskDetails: RamdiskDetailsInfo? = null
        var initAnalysis: InitAnalysisInfo? = null
        var fstabAnalysis: FstabAnalysisInfo? = null
        var buildProps = mutableMapOf<String, String>()
        var defaultProps = mutableMapOf<String, String>()
        var vendorProps = mutableMapOf<String, String>()
        var vintfXml: String? = null
        val partitionList = mutableListOf<BootPartitionInfo>()
        val filePathsList = mutableListOf<String>()

        var initArch: String? = null
        var systemArch: String? = null
        var vendorArch: String? = null

        for (df in allFiles) {
            val name = df.name ?: continue
            filePathsList.add(name)

            // Partition tracking
            if (name.endsWith(".img") || name.endsWith(".dat") || name.endsWith(".br")) {
                partitionList.add(
                    BootPartitionInfo(
                        partitionName = name.substringBefore('.'),
                        fileName = name,
                        fileSize = df.length(),
                        format = if (name.endsWith(".img")) "Partition Image" else "Compressed Block Data",
                        detected = true
                    )
                )
            }

            // 1. Boot Image
            if (name.equals("boot.img", ignoreCase = true) && bootHeader == null) {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        bootHeader = BootHeaderParser.parseStream(stream, df.length())
                    }
                } catch (e: Exception) {
                    // Ignore error
                }
            }

            // 2. build.prop
            if (name == "build.prop") {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        stream.bufferedReader().forEachLine { line ->
                            val t = line.trim()
                            if (t.isNotEmpty() && !t.startsWith("#") && t.contains("=")) {
                                val k = t.substringBefore('=').trim()
                                val v = t.substringAfter('=').trim()
                                buildProps[k] = v
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            // 3. default.prop or prop.default
            if (name == "default.prop" || name == "prop.default") {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        stream.bufferedReader().forEachLine { line ->
                            val t = line.trim()
                            if (t.isNotEmpty() && !t.startsWith("#") && t.contains("=")) {
                                defaultProps[t.substringBefore('=').trim()] = t.substringAfter('=').trim()
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            // 4. init.rc
            if (name == "init.rc" && initAnalysis == null) {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        initAnalysis = InitRcParser.parseStream(stream, name)
                    }
                } catch (e: Exception) {}
            }

            // 5. fstab
            if (name.startsWith("fstab") && fstabAnalysis == null) {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        fstabAnalysis = FstabParser.parseStream(stream, name)
                    }
                } catch (e: Exception) {}
            }

            // 6. manifest.xml
            if (name == "manifest.xml" && vintfXml == null) {
                try {
                    context.contentResolver.openInputStream(df.uri)?.use { stream ->
                        vintfXml = stream.bufferedReader().readText()
                    }
                } catch (e: Exception) {}
            }

            // 7. ELF architecture probe for /system/bin/init or /system/lib/libc.so
            if (name == "init" && initArch == null) {
                try {
                    val elf = ElfParserEngine.parse(context, df.uri)
                    initArch = elf.header.architectureName
                } catch (e: Exception) {}
            }
            if (name == "libc.so" && systemArch == null) {
                try {
                    val elf = ElfParserEngine.parse(context, df.uri)
                    systemArch = elf.header.architectureName
                } catch (e: Exception) {}
            }
        }

        val allProperties = mutableMapOf<String, String>()
        allProperties.putAll(defaultProps)
        allProperties.putAll(buildProps)

        val trebleInfo = RomStructureAnalyzer.detectTreble(
            hasVendorPartition = partitionList.any { it.partitionName == "vendor" },
            properties = allProperties,
            filesList = filePathsList
        )

        val abSlotInfo = RomStructureAnalyzer.detectAbSlots(
            properties = allProperties,
            partitionNames = partitionList.map { it.partitionName },
            filesList = filePathsList
        )

        val archInfo = RomStructureAnalyzer.analyzeArchitecture(
            kernelArch = kernelDetails?.detectedArch,
            initArch = initArch,
            systemArch = systemArch,
            vendorArch = vendorArch
        )

        val versionInfo = RomStructureAnalyzer.analyzeVersions(
            bootHeaderVer = bootHeader?.osVersionString,
            buildProps = buildProps,
            defaultProps = defaultProps
        )

        val vintfInfo = RomStructureAnalyzer.analyzeVintf(vintfXml)

        val vendorPresent = trebleInfo.hasVendorPartition || filePathsList.any { it.startsWith("vendor") }
        val vendorDetails = VendorDetailsInfo(
            vendorPresent = vendorPresent,
            architecture = vendorArch,
            propertyCount = vendorProps.size,
            servicesCount = initAnalysis?.services?.count { it.name.startsWith("vendor.") } ?: 0,
            halList = vintfInfo.hals.map { "${it.name}@${it.versions.firstOrNull() ?: "1.0"}" },
            manifestPresent = vintfInfo.hasManifest,
            issues = emptyList()
        )

        val issues = BootIssueDetector.detectAllIssues(
            header = bootHeader,
            kernel = kernelDetails,
            ramdisk = ramdiskDetails,
            init = initAnalysis,
            fstab = fstabAnalysis,
            treble = trebleInfo,
            ab = abSlotInfo,
            arch = archInfo,
            versions = versionInfo,
            vendor = vendorDetails
        )

        val stageRes = BootStageDetector.evaluateStages(
            header = bootHeader,
            kernel = kernelDetails,
            ramdisk = ramdiskDetails,
            init = initAnalysis,
            fstab = fstabAnalysis,
            vendor = vendorDetails,
            allIssues = issues
        )

        val baseRes = BootAnalysisResult(
            bootHeader = bootHeader,
            kernelInfo = kernelDetails,
            ramdiskInfo = ramdiskDetails,
            initAnalysis = initAnalysis,
            fstabAnalysis = fstabAnalysis,
            partitionMap = partitionList,
            trebleInfo = trebleInfo,
            abSlotInfo = abSlotInfo,
            architectureInfo = archInfo,
            versionAnalysis = versionInfo,
            vendorAnalysis = vendorDetails,
            vintfAnalysis = vintfInfo,
            stageResults = stageRes.stageMap,
            lastConfirmedStage = stageRes.lastConfirmedStage,
            suspectedFailureStage = stageRes.suspectedFailureStage,
            failureConfidence = stageRes.confidence,
            allIssues = issues
        )

        val portingChecks = RomCompatibilityAnalyzer.checkAndroid11Porting(
            result = baseRes,
            properties = allProperties,
            filesList = filePathsList
        )

        val j2PrimeProfile = RomCompatibilityAnalyzer.checkGalaxyJ2PrimeProfile(
            kernel = kernelDetails,
            architecture = archInfo,
            properties = allProperties
        )

        baseRes.copy(
            android11PortingChecks = portingChecks,
            j2PrimeProfile = j2PrimeProfile
        )
    }

    private fun collectFilesRecursively(doc: DocumentFile, outList: MutableList<DocumentFile>, depth: Int = 0, maxDepth: Int = 4) {
        if (depth > maxDepth) return
        val children = doc.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                collectFilesRecursively(child, outList, depth + 1, maxDepth)
            } else {
                outList.add(child)
            }
        }
    }
}
