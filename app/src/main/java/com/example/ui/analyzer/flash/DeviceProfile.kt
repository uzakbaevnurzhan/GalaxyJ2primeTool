package com.example.ui.analyzer.flash

data class DeviceProfile(
    val id: String,
    val modelName: String,
    val marketingName: String,
    val chipset: String,
    val arch: String = "arm", // arm (32-bit), arm64 (64-bit), x86
    val totalRamBytes: Long = 1536L * 1024 * 1024, // 1.5GB
    val totalStorageBytes: Long = 8L * 1024 * 1024 * 1024, // 8GB eMMC
    val isTreble: Boolean = false,
    val isSystemAsRoot: Boolean = false,
    val hasDynamicPartitions: Boolean = false,
    val stockAndroidVersion: String = "6.0.1 (Marshmallow)",
    val stockKernelVersion: String = "3.18.19",
    val requiredPartitions: List<String> = listOf("boot", "system", "recovery", "cache", "userdata"),
    val protectedPartitions: List<String> = listOf("preloader", "nvram", "nvdata", "protect1", "protect2", "proinfo", "secro", "efs", "param"),
    val referencePartitions: Map<String, Long> = emptyMap(),
    val notes: String = ""
) {
    companion object {
        val GALAXY_J2_PRIME = DeviceProfile(
            id = "samsung_g532f",
            modelName = "SM-G532F / SM-G532G / SM-G532M",
            marketingName = "Samsung Galaxy J2 Prime (Grand Prime Plus)",
            chipset = "MediaTek MT6737T",
            arch = "arm",
            totalRamBytes = 1536L * 1024 * 1024,
            totalStorageBytes = 8L * 1024 * 1024 * 1024,
            isTreble = false,
            isSystemAsRoot = false,
            hasDynamicPartitions = false,
            stockAndroidVersion = "6.0.1 (Marshmallow)",
            stockKernelVersion = "3.18.35+",
            requiredPartitions = listOf("preloader", "lk", "boot", "recovery", "system", "cache", "userdata", "nvram", "secro"),
            protectedPartitions = listOf("preloader", "nvram", "nvdata", "protect_f", "protect_s", "proinfo", "secro", "seccfg", "efs"),
            referencePartitions = mapOf(
                "preloader" to 256L * 1024, // 256KB
                "lk" to 1024L * 1024, // 1MB
                "boot" to 32L * 1024 * 1024, // 32MB
                "recovery" to 32L * 1024 * 1024, // 32MB
                "system" to 2400L * 1024 * 1024, // ~2.34GB
                "cache" to 200L * 1024 * 1024, // 200MB
                "userdata" to 4200L * 1024 * 1024, // ~4.1GB
                "nvram" to 5L * 1024 * 1024, // 5MB
                "logo" to 8L * 1024 * 1024 // 8MB
            ),
            notes = "Target Reference Device: ARM 32-bit Cortex-A53 running in 32-bit mode. Strict MTK DA / SP Flash Tool layout."
        )

        val GENERIC_ARM32_MTK = DeviceProfile(
            id = "generic_mtk_arm32",
            modelName = "Generic MTK ARM32",
            marketingName = "Generic MediaTek MT6735 / MT6737 / MT6580",
            chipset = "MediaTek MT6737 / MT6735",
            arch = "arm",
            totalRamBytes = 2048L * 1024 * 1024,
            totalStorageBytes = 16L * 1024 * 1024 * 1024,
            isTreble = false,
            isSystemAsRoot = false,
            hasDynamicPartitions = false,
            stockAndroidVersion = "7.0 / 8.1",
            stockKernelVersion = "3.18.x / 4.4.x",
            requiredPartitions = listOf("boot", "system", "recovery", "userdata"),
            protectedPartitions = listOf("preloader", "nvram", "nvdata", "protect1", "protect2")
        )

        val GENERIC_ARM64_TREBLE = DeviceProfile(
            id = "generic_arm64_treble",
            modelName = "Generic ARM64 Treble",
            marketingName = "Generic Project Treble Device (A/B or A-only)",
            chipset = "Modern ARM64",
            arch = "arm64",
            totalRamBytes = 4096L * 1024 * 1024,
            totalStorageBytes = 64L * 1024 * 1024 * 1024,
            isTreble = true,
            isSystemAsRoot = true,
            hasDynamicPartitions = true,
            stockAndroidVersion = "10.0 / 11.0",
            stockKernelVersion = "4.14 / 4.19 / 5.4",
            requiredPartitions = listOf("boot", "super", "vbmeta", "userdata"),
            protectedPartitions = listOf("modem", "persist", "fsg", "efs")
        )

        val ALL_PROFILES = listOf(GALAXY_J2_PRIME, GENERIC_ARM32_MTK, GENERIC_ARM64_TREBLE)
    }
}
