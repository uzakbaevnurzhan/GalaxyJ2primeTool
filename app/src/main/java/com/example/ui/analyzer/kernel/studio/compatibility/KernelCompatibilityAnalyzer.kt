package com.example.ui.analyzer.kernel.studio.compatibility

import com.example.ui.analyzer.kernel.studio.models.ConfigState
import com.example.ui.analyzer.kernel.studio.models.KernelAnalysisResult
import com.example.ui.analyzer.kernel.studio.models.KernelConfig
import com.example.ui.analyzer.kernel.studio.models.KernelInfo
import com.example.ui.analyzer.kernel.studio.models.PortingCheckSignal

object KernelCompatibilityAnalyzer {

    fun analyzeAndroid11Signals(
        kernelInfo: KernelInfo?,
        configs: List<KernelConfig>,
        dtbCompatible: List<String>
    ): List<PortingCheckSignal> {
        val signals = mutableListOf<PortingCheckSignal>()
        val configMap = configs.associateBy { it.name }

        val major = kernelInfo?.versionInfo?.major ?: 0
        val minor = kernelInfo?.versionInfo?.minor ?: 0

        // 1. Kernel Version Check
        if (major > 0) {
            when {
                major >= 5 -> {
                    signals.add(
                        PortingCheckSignal(
                            title = "Linux Kernel Version 5.x+",
                            category = "READY_SIGNAL",
                            description = "Modern Linux kernel ($major.$minor) has complete native Android 11+ GKI / Treble subsystem support.",
                            evidence = "Kernel: ${kernelInfo?.versionInfo?.fullString}"
                        )
                    )
                }
                major == 4 && minor >= 9 -> {
                    signals.add(
                        PortingCheckSignal(
                            title = "Linux Kernel Version 4.x ($major.$minor)",
                            category = "READY_SIGNAL",
                            description = "Kernel 4.9+ is officially supported for Android 11 Treble ports.",
                            evidence = "Kernel: ${kernelInfo?.versionInfo?.fullString}"
                        )
                    )
                }
                major == 3 && minor == 18 -> {
                    signals.add(
                        PortingCheckSignal(
                            title = "Legacy Linux Kernel 3.18 (J2 Prime / MT6737)",
                            category = "WARNING",
                            description = "Linux 3.18 is a legacy pre-Treble kernel. Running Android 11 requires backported Binder IPC 8, ashmem/ion or dma-buf shims, and sepolicy compatibility patches.",
                            evidence = "Kernel: ${kernelInfo?.versionInfo?.fullString}"
                        )
                    )
                }
                major < 4 -> {
                    signals.add(
                        PortingCheckSignal(
                            title = "Very Old Linux Kernel ($major.$minor)",
                            category = "BLOCKER",
                            description = "Linux versions older than 3.18 lack essential cgroups, binder, and netfilter capabilities needed for Android 11.",
                            evidence = "Kernel: ${kernelInfo?.versionInfo?.fullString}"
                        )
                    )
                }
            }
        }

        // 2. Android Binder IPC
        val binderConfig = configMap["CONFIG_ANDROID_BINDER_IPC"]
        val binderFsConfig = configMap["CONFIG_ANDROID_BINDERFS"]
        if (binderConfig != null && binderConfig.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "Android Binder IPC Driver",
                    category = "READY_SIGNAL",
                    description = "CONFIG_ANDROID_BINDER_IPC is enabled for inter-process communication.",
                    evidence = "CONFIG_ANDROID_BINDER_IPC=y"
                )
            )
        } else if (configs.isNotEmpty()) {
            signals.add(
                PortingCheckSignal(
                    title = "Missing CONFIG_ANDROID_BINDER_IPC",
                    category = "BLOCKER",
                    description = "Android cannot boot to Zygote without binder driver.",
                    evidence = "Config not found or disabled"
                )
            )
        }

        if (binderFsConfig != null && binderFsConfig.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "BinderFS Support",
                    category = "READY_SIGNAL",
                    description = "CONFIG_ANDROID_BINDERFS is enabled (standard in Android 10/11).",
                    evidence = "CONFIG_ANDROID_BINDERFS=y"
                )
            )
        } else if (major == 3 && minor == 18) {
            signals.add(
                PortingCheckSignal(
                    title = "BinderFS Not Configured (Legacy /dev/binder)",
                    category = "WARNING",
                    description = "Android 11 init attempts to mount binderfs at /dev/binderfs. Legacy kernel will require init.rc fallback to legacy /dev/binder nodes.",
                    evidence = "CONFIG_ANDROID_BINDERFS not set"
                )
            )
        }

        // 3. SELinux Check
        val selinuxConfig = configMap["CONFIG_SECURITY_SELINUX"]
        if (selinuxConfig != null && selinuxConfig.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "SELinux Security Engine",
                    category = "READY_SIGNAL",
                    description = "CONFIG_SECURITY_SELINUX is enabled.",
                    evidence = "CONFIG_SECURITY_SELINUX=y"
                )
            )
        } else if (configs.isNotEmpty()) {
            signals.add(
                PortingCheckSignal(
                    title = "SELinux Disabled",
                    category = "WARNING",
                    description = "SELinux is disabled in kernel config. Android framework services will require permissive boot flags.",
                    evidence = "CONFIG_SECURITY_SELINUX not found"
                )
            )
        }

        // 4. Filesystem Checks (EXT4, F2FS, dm-verity)
        val ext4Config = configMap["CONFIG_EXT4_FS"]
        val f2fsConfig = configMap["CONFIG_F2FS_FS"]
        val erofsConfig = configMap["CONFIG_EROFS_FS"]
        val dmVerityConfig = configMap["CONFIG_DM_VERITY"]

        if (ext4Config != null && ext4Config.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "EXT4 Filesystem Support",
                    category = "READY_SIGNAL",
                    description = "EXT4 driver is enabled for system/vendor/data partitions.",
                    evidence = "CONFIG_EXT4_FS=y"
                )
            )
        }

        if (f2fsConfig != null && f2fsConfig.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "F2FS Flash Filesystem Support",
                    category = "READY_SIGNAL",
                    description = "F2FS driver is enabled for /data partition.",
                    evidence = "CONFIG_F2FS_FS=y"
                )
            )
        }

        if (erofsConfig != null && erofsConfig.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "EROFS Read-Only Filesystem",
                    category = "READY_SIGNAL",
                    description = "EROFS driver is enabled (used by modern Android 11+ system/vendor super images).",
                    evidence = "CONFIG_EROFS_FS=y"
                )
            )
        } else if (major == 3) {
            signals.add(
                PortingCheckSignal(
                    title = "EROFS Not Supported in Legacy Kernel",
                    category = "WARNING",
                    description = "ROM build system must format system/vendor partitions as EXT4 rather than EROFS for this kernel.",
                    evidence = "CONFIG_EROFS_FS not available in 3.18"
                )
            )
        }

        // 5. USB ConfigFS
        val usbConfigfs = configMap["CONFIG_USB_CONFIGFS"]
        if (usbConfigfs != null && usbConfigfs.state == ConfigState.ENABLED) {
            signals.add(
                PortingCheckSignal(
                    title = "USB ConfigFS Support",
                    category = "READY_SIGNAL",
                    description = "CONFIG_USB_CONFIGFS is active for modern Android USB gadget controller (MTP/ADB).",
                    evidence = "CONFIG_USB_CONFIGFS=y"
                )
            )
        }

        return signals
    }
}
