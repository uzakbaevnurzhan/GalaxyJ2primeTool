package com.example.ui.analyzer.boot

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

object InitRcParser {

    fun parse(content: String, sourceFileName: String = "init.rc"): InitAnalysisInfo {
        return parseReader(BufferedReader(StringReader(content)), sourceFileName)
    }

    fun parseStream(inputStream: InputStream, sourceFileName: String = "init.rc"): InitAnalysisInfo {
        return parseReader(BufferedReader(InputStreamReader(inputStream)), sourceFileName)
    }

    private fun parseReader(reader: BufferedReader, sourceFileName: String): InitAnalysisInfo {
        val stagesFound = mutableMapOf<String, MutableList<InitActionBlock>>()
        val services = mutableListOf<InitServiceBlock>()
        val imports = mutableListOf<String>()
        val setProps = mutableMapOf<String, String>()
        val mountCommands = mutableListOf<String>()
        val issues = mutableListOf<BootIssue>()

        var currentActionBlock: MutableActionBlock? = null
        var currentServiceBlock: MutableServiceBlock? = null

        var lineNumber = 0

        fun flushAction() {
            currentActionBlock?.let { block ->
                val stageKey = normalizeStage(block.trigger)
                val list = stagesFound.getOrPut(stageKey) { mutableListOf() }
                list.add(InitActionBlock(block.trigger, stageKey, block.commands))
            }
            currentActionBlock = null
        }

        fun flushService() {
            currentServiceBlock?.let { s ->
                services.add(
                    InitServiceBlock(
                        name = s.name,
                        binaryPath = s.binaryPath,
                        arguments = s.arguments,
                        className = s.className,
                        user = s.user,
                        group = s.group,
                        seclabel = s.seclabel,
                        isDisabled = s.isDisabled,
                        isOneshot = s.isOneshot,
                        isCritical = s.isCritical,
                        restartBehavior = s.restartBehavior
                    )
                )
            }
            currentServiceBlock = null
        }

        reader.forEachLine { rawLine ->
            lineNumber++
            val line = rawLine.trim()

            // Skip empty or comment lines
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEachLine
            }

            val tokens = splitTokens(line)
            if (tokens.isEmpty()) return@forEachLine

            val firstWord = tokens[0]

            // Top-level sections: on <trigger>, service <name> <path>, import <path>
            if (firstWord == "on") {
                flushAction()
                flushService()
                val trigger = tokens.drop(1).joinToString(" ")
                currentActionBlock = MutableActionBlock(trigger)
            } else if (firstWord == "service") {
                flushAction()
                flushService()
                if (tokens.size >= 3) {
                    val sName = tokens[1]
                    val sPath = tokens[2]
                    val sArgs = tokens.drop(3)
                    currentServiceBlock = MutableServiceBlock(name = sName, binaryPath = sPath, arguments = sArgs)
                } else {
                    issues.add(
                        BootIssue(
                            type = BootIssueType.INIT_PARSER_ERROR,
                            severity = BootIssueSeverity.WARNING,
                            title = "Invalid service definition",
                            description = "Service declaration requires at least a name and binary path.",
                            evidence = line,
                            file = sourceFileName,
                            line = lineNumber,
                            possibleCause = "Syntax error in init script"
                        )
                    )
                }
            } else if (firstWord == "import") {
                val imp = tokens.drop(1).joinToString(" ")
                if (imp.isNotEmpty()) imports.add(imp)
            } else {
                // Command inside current action or service modifier
                if (currentServiceBlock != null) {
                    when (firstWord) {
                        "class" -> currentServiceBlock!!.className = tokens.getOrElse(1) { "default" }
                        "user" -> currentServiceBlock!!.user = tokens.getOrElse(1) { "root" }
                        "group" -> currentServiceBlock!!.group = tokens.drop(1).joinToString(" ")
                        "seclabel" -> currentServiceBlock!!.seclabel = tokens.getOrElse(1) { "" }
                        "disabled" -> currentServiceBlock!!.isDisabled = true
                        "oneshot" -> currentServiceBlock!!.isOneshot = true
                        "critical" -> currentServiceBlock!!.isCritical = true
                        "restart" -> currentServiceBlock!!.restartBehavior = line
                        "onrestart" -> currentServiceBlock!!.restartBehavior = line
                    }
                } else if (currentActionBlock != null) {
                    currentActionBlock!!.commands.add(line)

                    // Track setprop
                    if (firstWord == "setprop" && tokens.size >= 3) {
                        setProps[tokens[1]] = tokens.drop(2).joinToString(" ")
                    }
                    // Track mount
                    if (firstWord == "mount" || firstWord == "mount_all") {
                        mountCommands.add(line)
                    }
                }
            }
        }

        flushAction()
        flushService()

        return InitAnalysisInfo(
            totalFilesParsed = 1,
            stagesFound = stagesFound,
            services = services,
            imports = imports,
            setProps = setProps,
            mountCommands = mountCommands,
            issuesFound = issues
        )
    }

    private fun normalizeStage(trigger: String): String {
        val t = trigger.lowercase()
        return when {
            t.startsWith("early-init") -> "EARLY_INIT"
            t.startsWith("init") -> "INIT"
            t.startsWith("early-fs") -> "EARLY_FS"
            t.startsWith("fs") -> "FS"
            t.startsWith("post-fs-data") -> "POST_FS_DATA"
            t.startsWith("post-fs") -> "POST_FS"
            t.startsWith("early-boot") -> "EARLY_BOOT"
            t.startsWith("boot") -> "BOOT"
            t.startsWith("late-init") -> "LATE_INIT"
            t.startsWith("property:") -> "PROPERTY"
            else -> "CUSTOM"
        }
    }

    private fun splitTokens(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (c in line) {
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c.isWhitespace() && !inQuotes) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.setLength(0)
                }
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens
    }

    private data class MutableActionBlock(
        val trigger: String,
        val commands: MutableList<String> = mutableListOf()
    )

    private data class MutableServiceBlock(
        val name: String,
        val binaryPath: String,
        val arguments: List<String> = emptyList(),
        var className: String = "default",
        var user: String = "root",
        var group: String = "root",
        var seclabel: String? = null,
        var isDisabled: Boolean = false,
        var isOneshot: Boolean = false,
        var isCritical: Boolean = false,
        var restartBehavior: String = "default"
    )
}
