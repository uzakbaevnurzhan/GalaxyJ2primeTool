package com.example.config

/**
 * Single Source of Truth for Application Versioning and Platform Metadata.
 * Used across Gradle, UI, About, Settings, Dashboard, and Reports.
 */
object AppVersionConfig {
    const val VERSION_NAME = "0.4.0 Beta4"
    const val RELEASE_NAME = "Beta 4"
    const val VERSION_CODE = 4
    const val BUILD_NUMBER = 4
    const val BUILD_DATE = "August 2026"
    const val MIN_ANDROID = "Android 7.0 (API 24)"
    const val TARGET_ANDROID = "Android 14+ (API 36)"
    const val APP_NAME = "Galaxy J2 Prime Tool"
    const val APP_PACKAGE = "com.example"
    const val REPOSITORY_URL = "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool"
    const val RELEASES_URL = "https://github.com/uzakbaevnurzhan/GalaxyJ2primeTool/releases"
    const val TARGET_DEVICE = "Samsung Galaxy J2 Prime (SM-G532F/G/M)"
    const val TARGET_CHIPSET = "MediaTek MT6737T (ARM32 Cortex-A53 / MT6735 base)"
    const val TARGET_PORT_OS = "Android 11 (LineageOS 18.1)"
    const val LICENSES = "Apache License 2.0 & GNU General Public License v2"
}
