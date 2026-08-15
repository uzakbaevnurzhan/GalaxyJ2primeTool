package com.example.ui.analyzer.selinux.model

/**
 * Detailed representation of an SELinux Access Vector Cache (AVC) Denial.
 */
data class AvcDenial(
    val rawLine: String,
    val timestamp: String? = null,
    val comm: String? = null,
    val pid: Int? = null,
    val uid: Int? = null,
    val scontext: SelinuxContext? = null,
    val tcontext: SelinuxContext? = null,
    val tclass: String? = null,
    val permissions: List<String> = emptyList(),
    val path: String? = null,
    val isPermissive: Boolean = false,
    val operation: String = "denied", // "denied" or "granted"
    val ino: Long? = null,
    val dev: String? = null,
    val ioctlCmd: String? = null,
    val serviceName: String? = null
) {
    val sourceDomain: String
        get() = scontext?.type ?: "unknown"

    val targetDomain: String
        get() = tcontext?.type ?: "unknown"

    val primaryPermission: String
        get() = permissions.firstOrNull() ?: "unknown"

    val isVendorRelated: Boolean
        get() = (scontext?.isVendorDomain == true) || (tcontext?.isVendorDomain == true)

    val isSystemRelated: Boolean
        get() = (scontext?.isSystemDomain == true) || (tcontext?.isSystemDomain == true)

    val isFrameworkRelated: Boolean
        get() = sourceDomain == "system_server" || sourceDomain == "surfaceflinger" || sourceDomain == "mediaserver"

    /**
     * Analytical assessment of the denial
     */
    val analyticalDescription: AvcAnalysis
        get() {
            val fact = "Process '$comm' (PID ${pid ?: "unknown"}, domain '$sourceDomain') requested permission(s) [${permissions.joinToString(", ")}] on target '$targetDomain' (class '$tclass'${if (path != null) ", path: '$path'" else ""})."
            
            val area = when {
                tclass == "service_manager" || tclass == "binder" -> "Binder & IPC Service Access"
                tclass == "property_service" || targetDomain.endsWith("_prop") -> "System Property Control"
                tclass in listOf("chr_file", "blk_file") -> "Hardware Device Node Access (/dev)"
                tclass in listOf("file", "dir", "lnk_file") && targetDomain.startsWith("vendor_") -> "Vendor File System Access"
                tclass in listOf("file", "dir") && targetDomain.startsWith("sysfs") -> "Kernel SysFS Hardware Control"
                tclass in listOf("file", "dir") && targetDomain.startsWith("proc") -> "Kernel ProcFS / Parameter Access"
                tclass in listOf("unix_stream_socket", "netlink_route_socket", "socket") -> "Socket / Network Daemon Communication"
                sourceDomain.startsWith("hal_") || targetDomain.startsWith("hal_") -> "Hardware Abstraction Layer (HAL) Communication"
                else -> "General Security Policy Enforcement"
            }

            val possibleCause = when {
                sourceDomain == "init" && tclass == "file" -> "Init script trying to execute or modify an unlabelled or vendor binary before policy transitions."
                targetDomain.endsWith("_prop") && permissions.contains("set") -> "Process attempted to set property without `set_prop($sourceDomain, $targetDomain)` macro in policy."
                tclass == "service_manager" && permissions.contains("find") -> "Service client unable to find binder service. Check if service is registered in service_contexts or if `allow $sourceDomain $targetDomain:service_manager find;` is required."
                targetDomain == "vendor_file" && permissions.contains("execute") -> "System daemon attempting to execute non-vendor-exec tagged vendor binary or library."
                isVendorRelated && isSystemRelated -> "Treble ABI violation or missing passthrough/binderized HAL rule between system and vendor domains."
                else -> "Missing SELinux type enforcement rule or unlabelled file/node in target filesystem."
            }

            val suggestedPolicyRule = if (scontext != null && tcontext != null && tclass != null && permissions.isNotEmpty()) {
                "allow ${scontext.type} ${tcontext.type}:${tclass} { ${permissions.joinToString(" ")} };"
            } else {
                null
            }

            return AvcAnalysis(fact, area, possibleCause, suggestedPolicyRule)
        }
}

data class AvcAnalysis(
    val fact: String,
    val possibleArea: String,
    val possibleCause: String,
    val suggestedPolicyRule: String?
)

/**
 * Aggregated group of identical denial events.
 */
data class AvcGroup(
    val sourceDomain: String,
    val targetDomain: String,
    val tclass: String,
    val permission: String,
    val count: Int,
    val sampleDenial: AvcDenial,
    val isPermissive: Boolean
) {
    val suggestedRule: String
        get() = "allow $sourceDomain $targetDomain:$tclass $permission;"
}
