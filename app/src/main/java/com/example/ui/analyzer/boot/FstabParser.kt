package com.example.ui.analyzer.boot

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

object FstabParser {

    fun parse(content: String, sourceFileName: String = "fstab"): FstabAnalysisInfo {
        return parseReader(BufferedReader(StringReader(content)), sourceFileName)
    }

    fun parseStream(inputStream: InputStream, sourceFileName: String = "fstab"): FstabAnalysisInfo {
        return parseReader(BufferedReader(InputStreamReader(inputStream)), sourceFileName)
    }

    private fun parseReader(reader: BufferedReader, sourceFileName: String): FstabAnalysisInfo {
        val entries = mutableListOf<FstabEntryInfo>()
        val issues = mutableListOf<BootIssue>()

        var lineNum = 0
        reader.forEachLine { rawLine ->
            lineNum++
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEachLine
            }

            val parts = line.split(Regex("""\s+"""))
            if (parts.size >= 4) {
                // fstab format: <src> <mnt_point> <type> <mnt_flags> <fs_mgr_flags...>
                val devSrc = parts[0]
                val mntPoint = parts[1]
                val fsType = parts[2]
                val mntFlags = parts[3]
                val fsMgrFlags = if (parts.size > 4) parts.drop(4).joinToString(" ") else ""

                val isMandatory = isMandatoryMount(mntPoint)

                entries.add(
                    FstabEntryInfo(
                        mountTarget = mntPoint,
                        deviceSource = devSrc,
                        filesystem = fsType,
                        flags = mntFlags,
                        fsMgrFlags = fsMgrFlags,
                        isMandatory = isMandatory,
                        partitionFileFound = true
                    )
                )
            } else {
                issues.add(
                    BootIssue(
                        type = BootIssueType.INVALID_FSTAB,
                        severity = BootIssueSeverity.WARNING,
                        title = "Malformed fstab entry",
                        description = "Fstab entry has fewer than 4 columns: '$line'",
                        evidence = line,
                        file = sourceFileName,
                        line = lineNum,
                        possibleCause = "Syntax or formatting error in device fstab"
                    )
                )
            }
        }

        // Check required mounts
        val foundMounts = entries.map { it.mountTarget.lowercase() }.toSet()
        val missingMandatory = mutableListOf<String>()

        if (!foundMounts.contains("/system") && !foundMounts.contains("system")) {
            missingMandatory.add("/system")
            issues.add(
                BootIssue(
                    type = BootIssueType.MISSING_MANDATORY_MOUNT,
                    severity = BootIssueSeverity.ERROR,
                    title = "Missing /system mount in fstab",
                    description = "The fstab does not contain a mount entry for /system partition.",
                    evidence = "Found mounts: $foundMounts",
                    file = sourceFileName,
                    possibleCause = "Incomplete or corrupt fstab",
                    recommendedFix = "Ensure /system partition is declared in fstab"
                )
            )
        }

        if (!foundMounts.contains("/data") && !foundMounts.contains("data") && !foundMounts.contains("/userdata")) {
            missingMandatory.add("/data")
            issues.add(
                BootIssue(
                    type = BootIssueType.MISSING_MANDATORY_MOUNT,
                    severity = BootIssueSeverity.WARNING,
                    title = "Missing /data mount in fstab",
                    description = "The fstab does not declare a /data or /userdata mount entry.",
                    evidence = "Found mounts: $foundMounts",
                    file = sourceFileName
                )
            )
        }

        return FstabAnalysisInfo(
            fileName = sourceFileName,
            entries = entries,
            missingMandatoryPartitions = missingMandatory,
            issuesFound = issues
        )
    }

    private fun isMandatoryMount(target: String): Boolean {
        val t = target.lowercase()
        return t == "/system" || t == "system" || t == "/vendor" || t == "vendor" ||
                t == "/data" || t == "data" || t == "/userdata" || t == "/cache"
    }
}
