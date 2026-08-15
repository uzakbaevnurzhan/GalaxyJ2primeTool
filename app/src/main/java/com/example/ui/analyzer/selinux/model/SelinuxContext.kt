package com.example.ui.analyzer.selinux.model

/**
 * Represents a parsed SELinux Security Context (e.g. u:r:system_server:s0:c0,c1)
 */
data class SelinuxContext(
    val raw: String,
    val user: String,
    val role: String,
    val type: String,
    val level: String? = null
) {
    val isSystemServer: Boolean
        get() = type == "system_server"

    val isVendorDomain: Boolean
        get() = type.startsWith("vendor_") || type.startsWith("hal_") || type.contains("_vendor")

    val isSystemDomain: Boolean
        get() = type.startsWith("system_") || type == "init" || type == "zygote" || type == "surfaceflinger"

    val isAppDomain: Boolean
        get() = type.contains("app") || type == "untrusted_app" || type == "platform_app" || type == "priv_app"

    override fun toString(): String = raw
}
