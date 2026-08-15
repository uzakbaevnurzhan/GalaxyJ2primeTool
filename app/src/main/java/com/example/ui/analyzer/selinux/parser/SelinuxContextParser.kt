package com.example.ui.analyzer.selinux.parser

import com.example.ui.analyzer.selinux.model.SelinuxContext

object SelinuxContextParser {

    /**
     * Parses an SELinux security context string into components: user, role, type, level.
     * Examples:
     * - u:r:system_server:s0
     * - u:object_r:vendor_file:s0:c123,c456
     * - u:r:shell:s0-s0:c0.c1023
     * - system_u:system_r:init_t:s0
     */
    fun parse(raw: String?): SelinuxContext? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val parts = trimmed.split(":")
        if (parts.size < 3) return null

        val user = parts[0]
        val role = parts[1]
        val type = parts[2]
        
        // Level can contain colons for MLS/MCS categories, e.g. s0:c123,c456 or range s0-s0:c0.c1023
        val level = if (parts.size > 3) {
            parts.subList(3, parts.size).joinToString(":")
        } else {
            null
        }

        return SelinuxContext(
            raw = trimmed,
            user = user,
            role = role,
            type = type,
            level = level
        )
    }
}
