package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.vendor.models.*
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object VendorBinaryAnalyzer {

    data class ParsedElfSummary(
        val architecture: String,
        val is64Bit: Boolean,
        val soname: String?,
        val neededLibs: List<String>
    )

    fun quickParseElf(file: File): ParsedElfSummary? {
        if (!file.exists() || !file.isFile || file.length() < 52) return null
        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                val ident = ByteBuffer.allocate(16)
                channel.read(ident)
                ident.flip()
                if (ident.get(0) != 0x7F.toByte() || ident.get(1) != 'E'.code.toByte() ||
                    ident.get(2) != 'L'.code.toByte() || ident.get(3) != 'F'.code.toByte()) {
                    return null
                }

                val is64 = ident.get(4).toInt() == 2
                val isBigEndian = ident.get(5).toInt() == 2
                val order = if (isBigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

                val headerSize = if (is64) 64 else 52
                val headerBuf = ByteBuffer.allocate(headerSize - 16).order(order)
                channel.read(headerBuf)
                headerBuf.flip()

                headerBuf.short // type
                val machine = headerBuf.short.toInt() and 0xFFFF
                val archName = when (machine) {
                    40 -> "ARM32"
                    183 -> "ARM64"
                    3 -> "x86"
                    62 -> "x86_64"
                    8 -> "MIPS"
                    243 -> "RISC-V"
                    else -> "Unknown (0x${machine.toString(16)})"
                }

                headerBuf.int // version
                val entryPoint = if (is64) headerBuf.long else headerBuf.int.toLong() and 0xFFFFFFFFL
                val phOff = if (is64) headerBuf.long else headerBuf.int.toLong() and 0xFFFFFFFFL
                val shOff = if (is64) headerBuf.long else headerBuf.int.toLong() and 0xFFFFFFFFL
                headerBuf.int // flags
                val ehSize = headerBuf.short.toInt() and 0xFFFF
                val phEntSize = headerBuf.short.toInt() and 0xFFFF
                val phNum = headerBuf.short.toInt() and 0xFFFF

                // Read program headers to find PT_DYNAMIC (type 2)
                var dynOffset = 0L
                var dynSize = 0L
                if (phNum in 1..256 && phOff > 0) {
                    val phBuf = ByteBuffer.allocate(phNum * phEntSize).order(order)
                    channel.position(phOff)
                    channel.read(phBuf)
                    phBuf.flip()

                    for (i in 0 until phNum) {
                        val pos = i * phEntSize
                        phBuf.position(pos)
                        val pType = phBuf.int
                        if (pType == 2 /* PT_DYNAMIC */) {
                            if (is64) {
                                phBuf.int // flags
                                dynOffset = phBuf.long
                                phBuf.long // vaddr
                                phBuf.long // paddr
                                dynSize = phBuf.long
                            } else {
                                dynOffset = phBuf.int.toLong() and 0xFFFFFFFFL
                                phBuf.int // vaddr
                                phBuf.int // paddr
                                dynSize = phBuf.int.toLong() and 0xFFFFFFFFL
                            }
                            break
                        }
                    }
                }

                var soname: String? = null
                val neededLibs = mutableListOf<String>()

                // Parse dynamic section if found
                if (dynOffset > 0 && dynSize > 0 && dynSize < 1000000) {
                    val entSize = if (is64) 16 else 8
                    val entNum = (dynSize / entSize).toInt()
                    val dynBuf = ByteBuffer.allocate(dynSize.toInt()).order(order)
                    channel.position(dynOffset)
                    channel.read(dynBuf)
                    dynBuf.flip()

                    var strTabOffset = 0L
                    val tagValList = mutableListOf<Pair<Long, Long>>()

                    for (i in 0 until entNum) {
                        val tag = if (is64) dynBuf.long else dynBuf.int.toLong() and 0xFFFFFFFFL
                        val value = if (is64) dynBuf.long else dynBuf.int.toLong() and 0xFFFFFFFFL
                        if (tag == 0L) break
                        if (tag == 5L /* DT_STRTAB */) {
                            strTabOffset = value
                        }
                        tagValList.add(Pair(tag, value))
                    }

                    // Convert vaddr to file offset if needed, or if strTabOffset is direct
                    var strTabFileOffset = strTabOffset
                    if (strTabFileOffset >= dynOffset && strTabFileOffset < channel.size()) {
                        // valid offset in file
                    } else if (shOff > 0) {
                        // Check section headers for strtab
                    }

                    // Attempt to read strings from strtab if within reasonable file range
                    if (strTabFileOffset > 0 && strTabFileOffset < channel.size()) {
                        val maxStrSize = minOf(65536L, channel.size() - strTabFileOffset).toInt()
                        val strBuf = ByteBuffer.allocate(maxStrSize)
                        channel.position(strTabFileOffset)
                        channel.read(strBuf)
                        strBuf.flip()

                        for ((tag, value) in tagValList) {
                            val strOffset = value.toInt()
                            if (strOffset in 0 until maxStrSize) {
                                val s = extractNullTerminatedString(strBuf, strOffset)
                                if (s.isNotEmpty()) {
                                    if (tag == 14L /* DT_SONAME */) {
                                        soname = s
                                    } else if (tag == 1L /* DT_NEEDED */) {
                                        neededLibs.add(s)
                                    }
                                }
                            }
                        }
                    }
                }

                return ParsedElfSummary(
                    architecture = archName,
                    is64Bit = is64,
                    soname = soname,
                    neededLibs = neededLibs
                )
            }
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractNullTerminatedString(buf: ByteBuffer, offset: Int): String {
        val original = buf.position()
        buf.position(offset)
        val bytes = mutableListOf<Byte>()
        while (buf.hasRemaining()) {
            val b = buf.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        buf.position(original)
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    fun analyzeBinariesAndLibraries(
        rootDirectory: File?,
        knownElfSummaries: Map<String, ParsedElfSummary> = emptyMap(),
        deviceArchHint: String? = null
    ): Pair<List<VendorBinary>, List<VendorLibrary>> {
        val binaries = mutableListOf<VendorBinary>()
        val libraries = mutableListOf<VendorLibrary>()

        val allElfFiles = mutableMapOf<String, Pair<File, ParsedElfSummary>>()

        if (rootDirectory != null && rootDirectory.exists()) {
            fun scan(f: File) {
                if (f.isDirectory) {
                    f.listFiles()?.forEach { scan(it) }
                } else if (f.isFile) {
                    val name = f.name
                    val relPath = f.relativeTo(rootDirectory).path.replace("\\", "/")
                    val isBinaryPath = relPath.contains("bin/") || relPath.contains("sbin/")
                    val isLibPath = relPath.contains("lib/") || relPath.contains("lib64/") || name.endsWith(".so")

                    if (isBinaryPath || isLibPath || name.endsWith(".so") || name.endsWith(".elf") || name.endsWith(".ko")) {
                        val summary = quickParseElf(f)
                        if (summary != null) {
                            allElfFiles[relPath] = Pair(f, summary)
                        }
                    }
                }
            }
            scan(rootDirectory)
        }

        // Add known summaries if simulated / provided
        for ((path, summary) in knownElfSummaries) {
            if (!allElfFiles.containsKey(path)) {
                allElfFiles[path] = Pair(File(path), summary)
            }
        }

        // Collect all available library names (e.g. "libc.so", "libm.so", etc.)
        val availableLibNames = mutableSetOf<String>()
        // Standard Android platform core libs that are guaranteed at runtime:
        availableLibNames.addAll(listOf("libc.so", "libm.so", "libdl.so", "liblog.so", "libstdc++.so", "libz.so"))
        for ((path, pair) in allElfFiles) {
            val name = pair.first.name
            if (name.endsWith(".so")) {
                availableLibNames.add(name)
                pair.second.soname?.let { availableLibNames.add(it) }
            }
        }

        // Build Library and Binary instances with missing dependencies check
        for ((relPath, pair) in allElfFiles) {
            val file = pair.first
            val summary = pair.second
            val name = file.name
            val isLib = relPath.contains("lib/") || relPath.contains("lib64/") || name.endsWith(".so")
            val isHwService = relPath.contains("/hw/") || name.contains("@") || name.contains("-service")

            val missing = summary.neededLibs.filter { !availableLibNames.contains(it) }

            if (isLib) {
                libraries.add(
                    VendorLibrary(
                        name = name,
                        relativePath = relPath,
                        sizeBytes = if (file.exists()) file.length() else 0L,
                        architecture = summary.architecture,
                        is64Bit = summary.is64Bit,
                        soname = summary.soname,
                        neededLibraries = summary.neededLibs,
                        missingLibraries = missing
                    )
                )
            } else {
                binaries.add(
                    VendorBinary(
                        name = name,
                        relativePath = relPath,
                        sizeBytes = if (file.exists()) file.length() else 0L,
                        architecture = summary.architecture,
                        is64Bit = summary.is64Bit,
                        soname = summary.soname,
                        neededLibraries = summary.neededLibs,
                        missingLibraries = missing,
                        isHwService = isHwService
                    )
                )
            }
        }

        return Pair(binaries, libraries)
    }

    fun detectAbiMismatches(
        binaries: List<VendorBinary>,
        libraries: List<VendorLibrary>,
        targetDeviceArch: String? // e.g. "ARM32", "ARM64"
    ): List<VendorIssue> {
        val issues = mutableListOf<VendorIssue>()

        val architectures = (binaries.map { it.architecture } + libraries.map { it.architecture }).distinct()
        if (architectures.size > 1 && !architectures.contains("Unknown")) {
            // Check if 64-bit binaries exist on a strictly 32-bit device environment
            if (targetDeviceArch != null && targetDeviceArch.equals("ARM32", ignoreCase = true)) {
                val arm64Items = binaries.filter { it.architecture == "ARM64" }.map { it.relativePath } +
                        libraries.filter { it.architecture == "ARM64" }.map { it.relativePath }
                if (arm64Items.isNotEmpty()) {
                    issues.add(
                        VendorIssue(
                            type = VendorIssueType.ABI_MISMATCH,
                            severity = Severity.CRITICAL,
                            message = "Critical ABI Mismatch: Found 64-bit (ARM64/AArch64) ELFs in an ARM32 device environment.",
                            evidence = "ARM64 binaries/libs found: ${arm64Items.take(5).joinToString()} (Device Arch: $targetDeviceArch)",
                            source = "VendorBinaryAnalyzer",
                            confidence = Confidence.HIGH,
                            recommendation = "Replace 64-bit vendor binaries with ARM32 (armeabi-v7a) counterparts."
                        )
                    )
                }
            }
        }

        // Check for missing libraries
        for (bin in binaries) {
            if (bin.missingLibraries.isNotEmpty()) {
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.MISSING_LIBRARY,
                        severity = Severity.ERROR,
                        message = "Binary '${bin.name}' has missing DT_NEEDED libraries.",
                        evidence = "Binary: ${bin.relativePath}, Missing: ${bin.missingLibraries.joinToString(", ")}",
                        source = "VendorBinaryAnalyzer",
                        confidence = Confidence.HIGH,
                        recommendation = "Provide missing libraries in /vendor/lib or /system/lib."
                    )
                )
            }
        }

        for (lib in libraries) {
            if (lib.missingLibraries.isNotEmpty()) {
                issues.add(
                    VendorIssue(
                        type = VendorIssueType.MISSING_LIBRARY,
                        severity = Severity.WARNING,
                        message = "Shared library '${lib.name}' has missing dependencies.",
                        evidence = "Library: ${lib.relativePath}, Missing: ${lib.missingLibraries.joinToString(", ")}",
                        source = "VendorBinaryAnalyzer",
                        confidence = Confidence.HIGH,
                        recommendation = "Check if required shared libraries are present in vendor or system lib search paths."
                    )
                )
            }
        }

        return issues
    }

    fun buildDependencyGraph(
        binaries: List<VendorBinary>,
        libraries: List<VendorLibrary>
    ): DependencyGraphData {
        val nodes = mutableListOf<DependencyGraphNode>()
        val libMap = libraries.associateBy { it.name }

        for (b in binaries) {
            nodes.add(
                DependencyGraphNode(
                    id = "bin:${b.name}",
                    label = b.name,
                    type = if (b.isHwService) "SERVICE" else "BINARY",
                    architecture = b.architecture,
                    exists = true,
                    dependencies = b.neededLibraries,
                    dependents = emptyList(),
                    evidence = "Binary at ${b.relativePath} (${b.architecture})"
                )
            )
        }

        for (l in libraries) {
            // Find who depends on this library
            val dependents = (binaries.filter { it.neededLibraries.contains(l.name) }.map { "bin:${it.name}" } +
                    libraries.filter { it.neededLibraries.contains(l.name) }.map { "lib:${it.name}" })

            nodes.add(
                DependencyGraphNode(
                    id = "lib:${l.name}",
                    label = l.name,
                    type = "LIBRARY",
                    architecture = l.architecture,
                    exists = true,
                    dependencies = l.neededLibraries,
                    dependents = dependents,
                    evidence = "Library at ${l.relativePath} (${l.architecture}), SONAME: ${l.soname ?: "none"}"
                )
            )
        }

        val missingCount = binaries.sumOf { it.missingLibraries.size } + libraries.sumOf { it.missingLibraries.size }

        return DependencyGraphData(
            nodes = nodes,
            missingDependenciesCount = missingCount,
            totalLibrariesCount = libraries.size,
            totalBinariesCount = binaries.size
        )
    }
}
