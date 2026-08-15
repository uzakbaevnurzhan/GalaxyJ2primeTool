package com.example.ui.analyzer.getprop

enum class PortingCheckLevel(val displayName: String) {
    PASS("Pass"),
    WARNING("Warning"),
    ERROR("Error")
}

data class PortingCheckItem(
    val category: String,
    val title: String,
    val level: PortingCheckLevel,
    val message: String,
    val details: String? = null,
    val propKeyA: String? = null,
    val valueA: String? = null,
    val propKeyB: String? = null,
    val valueB: String? = null
)

data class GetpropPortingCheckResult(
    val baseSnapshotName: String,
    val portSnapshotName: String,
    val items: List<PortingCheckItem>,
    val passedCount: Int,
    val warningCount: Int,
    val errorCount: Int,
    val disclaimer: String = "Static configuration check. 'Potential mismatch' indicates divergence in build/vendor properties and does not guarantee or preclude bootability."
) {
    val overallLevel: PortingCheckLevel get() = when {
        errorCount > 0 -> PortingCheckLevel.ERROR
        warningCount > 0 -> PortingCheckLevel.WARNING
        else -> PortingCheckLevel.PASS
    }
}

object GetpropPortingChecker {

    fun performCheck(base: GetpropSnapshot, port: GetpropSnapshot): GetpropPortingCheckResult {
        val items = mutableListOf<PortingCheckItem>()

        // 1. ABI Compatibility Check
        val baseAbi = base.deviceSummary.primaryAbi
        val portAbi = port.deviceSummary.primaryAbi
        val baseAbiType = base.deviceSummary.abiType
        val portAbiType = port.deviceSummary.abiType

        if (baseAbiType == "ARM32 only" && portAbiType.contains("64")) {
            items.add(
                PortingCheckItem(
                    category = "CPU & Architecture",
                    title = "ABI Architecture Mismatch",
                    level = PortingCheckLevel.ERROR,
                    message = "Base device is ARM32 only ($baseAbi), but Port ROM targets 64-bit ($portAbi).",
                    details = "64-bit native binaries/libraries in the port ROM will fail to execute on 32-bit CPU/kernel.",
                    propKeyA = "ro.product.cpu.abi",
                    valueA = baseAbi,
                    propKeyB = "ro.product.cpu.abi",
                    valueB = portAbi
                )
            )
        } else if (baseAbi != portAbi && baseAbi != "Unknown" && portAbi != "Unknown") {
            items.add(
                PortingCheckItem(
                    category = "CPU & Architecture",
                    title = "Primary ABI Difference",
                    level = PortingCheckLevel.WARNING,
                    message = "Primary ABI differs between base ($baseAbi) and port ($portAbi).",
                    details = "Verify multi-abi libraries in /system/lib and /vendor/lib.",
                    propKeyA = "ro.product.cpu.abi",
                    valueA = baseAbi,
                    propKeyB = "ro.product.cpu.abi",
                    valueB = portAbi
                )
            )
        } else {
            items.add(
                PortingCheckItem(
                    category = "CPU & Architecture",
                    title = "ABI Compatibility",
                    level = PortingCheckLevel.PASS,
                    message = "ABI architecture matches ($baseAbi).",
                    details = "Base and Port ABIs are aligned.",
                    propKeyA = "ro.product.cpu.abi",
                    valueA = baseAbi,
                    propKeyB = "ro.product.cpu.abi",
                    valueB = portAbi
                )
            )
        }

        // 2. Android SDK Version Check
        val baseSdk = base.deviceSummary.sdk
        val portSdk = port.deviceSummary.sdk

        if (baseSdk > 0 && portSdk > 0) {
            val sdkDiff = portSdk - baseSdk
            when {
                sdkDiff > 3 -> {
                    items.add(
                        PortingCheckItem(
                            category = "Android Version",
                            title = "Major Android SDK Leap",
                            level = PortingCheckLevel.WARNING,
                            message = "Port SDK ($portSdk, Android ${port.deviceSummary.androidVersion}) is significantly newer than Base ($baseSdk, Android ${base.deviceSummary.androidVersion}).",
                            details = "Large SDK jumps usually require vendor HAL shims, HIDL/AIDL adaptation, and updated kernel binder drivers.",
                            propKeyA = "ro.build.version.sdk",
                            valueA = "$baseSdk",
                            propKeyB = "ro.build.version.sdk",
                            valueB = "$portSdk"
                        )
                    )
                }
                sdkDiff < 0 -> {
                    items.add(
                        PortingCheckItem(
                            category = "Android Version",
                            title = "Downgrade Version Detected",
                            level = PortingCheckLevel.WARNING,
                            message = "Port SDK ($portSdk) is older than Base SDK ($baseSdk).",
                            details = "Downgrading may require older framework dependencies or modified blobs.",
                            propKeyA = "ro.build.version.sdk",
                            valueA = "$baseSdk",
                            propKeyB = "ro.build.version.sdk",
                            valueB = "$portSdk"
                        )
                    )
                }
                else -> {
                    items.add(
                        PortingCheckItem(
                            category = "Android Version",
                            title = "Android SDK Alignment",
                            level = PortingCheckLevel.PASS,
                            message = "Base (SDK $baseSdk) and Port (SDK $portSdk) have compatible versions.",
                            propKeyA = "ro.build.version.sdk",
                            valueA = "$baseSdk",
                            propKeyB = "ro.build.version.sdk",
                            valueB = "$portSdk"
                        )
                    )
                }
            }
        }

        // 3. Hardware / SoC Platform Check
        val basePlatform = base.deviceSummary.platform.ifEmpty { base.deviceSummary.hardware }
        val portPlatform = port.deviceSummary.platform.ifEmpty { port.deviceSummary.hardware }

        if (basePlatform != "Unknown" && portPlatform != "Unknown" && !basePlatform.equals(portPlatform, ignoreCase = true)) {
            // Check if same chip family (e.g. mt6737 vs mt6735 or exynos7570)
            val isSevere = !isSameSoCFamily(basePlatform, portPlatform)
            items.add(
                PortingCheckItem(
                    category = "Hardware / SoC",
                    title = "SoC Platform Mismatch",
                    level = if (isSevere) PortingCheckLevel.ERROR else PortingCheckLevel.WARNING,
                    message = "Base platform ($basePlatform) does not match Port platform ($portPlatform).",
                    details = if (isSevere) 
                        "Porting across completely different SoC architectures (e.g. MediaTek vs Qualcomm) requires vendor binary replacement and custom HAL adaptation."
                        else "Different chip revision within related platform family.",
                    propKeyA = "ro.board.platform",
                    valueA = basePlatform,
                    propKeyB = "ro.board.platform",
                    valueB = portPlatform
                )
            )
        } else {
            items.add(
                PortingCheckItem(
                    category = "Hardware / SoC",
                    title = "Hardware Platform Compatibility",
                    level = PortingCheckLevel.PASS,
                    message = "Platform matches: $basePlatform",
                    propKeyA = "ro.board.platform",
                    valueA = basePlatform,
                    propKeyB = "ro.board.platform",
                    valueB = portPlatform
                )
            )
        }

        // 4. Graphics & EGL Driver Check
        val baseEgl = base.properties["ro.hardware.egl"]?.value ?: base.properties["ro.board.platform"]?.value ?: "Unknown"
        val portEgl = port.properties["ro.hardware.egl"]?.value ?: port.properties["ro.board.platform"]?.value ?: "Unknown"

        if (baseEgl != "Unknown" && portEgl != "Unknown" && !baseEgl.equals(portEgl, ignoreCase = true)) {
            items.add(
                PortingCheckItem(
                    category = "Graphics",
                    title = "EGL / GPU Driver Divergence",
                    level = PortingCheckLevel.WARNING,
                    message = "EGL hardware property mismatch: Base ($baseEgl) vs Port ($portEgl).",
                    details = "SurfaceFlinger and UI rendering will fail if libGLES_mali or target gralloc HAL is not properly linked.",
                    propKeyA = "ro.hardware.egl",
                    valueA = baseEgl,
                    propKeyB = "ro.hardware.egl",
                    valueB = portEgl
                )
            )
        } else {
            items.add(
                PortingCheckItem(
                    category = "Graphics",
                    title = "Graphics EGL Configuration",
                    level = PortingCheckLevel.PASS,
                    message = "EGL configuration consistent or shared.",
                    propKeyA = "ro.hardware.egl",
                    valueA = baseEgl,
                    propKeyB = "ro.hardware.egl",
                    valueB = portEgl
                )
            )
        }

        // 5. RIL & Telephony Check
        val baseRild = base.properties["rild.libpath"]?.value ?: base.properties["ro.telephony.ril_class"]?.value ?: "Standard"
        val portRild = port.properties["rild.libpath"]?.value ?: port.properties["ro.telephony.ril_class"]?.value ?: "Standard"

        if (baseRild != "Standard" && portRild != "Standard" && baseRild != portRild) {
            items.add(
                PortingCheckItem(
                    category = "Telephony / RIL",
                    title = "RIL Driver Path / Class Difference",
                    level = PortingCheckLevel.WARNING,
                    message = "RIL library or custom class differs between ROMs.",
                    details = "Ensure matching vendor libril / mtk-ril / sec-ril binary and radio service definitions.",
                    propKeyA = "rild.libpath",
                    valueA = baseRild,
                    propKeyB = "rild.libpath",
                    valueB = portRild
                )
            )
        } else {
            items.add(
                PortingCheckItem(
                    category = "Telephony / RIL",
                    title = "RIL Configuration",
                    level = PortingCheckLevel.PASS,
                    message = "RIL definitions consistent.",
                    propKeyA = "rild.libpath",
                    valueA = baseRild,
                    propKeyB = "rild.libpath",
                    valueB = portRild
                )
            )
        }

        // 6. SELinux Check
        val baseSelinux = base.deviceSummary.selinuxMode
        val portSelinux = port.deviceSummary.selinuxMode

        if (baseSelinux.equals("Enforcing", ignoreCase = true) && portSelinux.equals("Permissive", ignoreCase = true)) {
            items.add(
                PortingCheckItem(
                    category = "Security / SELinux",
                    title = "SELinux Mode Difference",
                    level = PortingCheckLevel.WARNING,
                    message = "Base uses Enforcing SELinux, while Port specifies Permissive.",
                    details = "Port ROM might require permissive mode during early development until sepolicy avc denials are addressed.",
                    propKeyA = "ro.boot.selinux",
                    valueA = baseSelinux,
                    propKeyB = "ro.boot.selinux",
                    valueB = portSelinux
                )
            )
        } else {
            items.add(
                PortingCheckItem(
                    category = "Security / SELinux",
                    title = "SELinux Configuration",
                    level = PortingCheckLevel.PASS,
                    message = "SELinux configurations verified ($baseSelinux / $portSelinux).",
                    propKeyA = "ro.boot.selinux",
                    valueA = baseSelinux,
                    propKeyB = "ro.boot.selinux",
                    valueB = portSelinux
                )
            )
        }

        // 7. Debuggable & Root ADB Check
        val baseDebug = base.properties["ro.debuggable"]?.value ?: "0"
        val portDebug = port.properties["ro.debuggable"]?.value ?: "0"

        if (portDebug == "1") {
            items.add(
                PortingCheckItem(
                    category = "Debug & Development",
                    title = "Userdebug Build Detected",
                    level = PortingCheckLevel.PASS,
                    message = "Port ROM has ro.debuggable=1 which facilitates adb root and logcat debugging during porting.",
                    propKeyA = "ro.debuggable",
                    valueA = baseDebug,
                    propKeyB = "ro.debuggable",
                    valueB = portDebug
                )
            )
        }

        val passed = items.count { it.level == PortingCheckLevel.PASS }
        val warnings = items.count { it.level == PortingCheckLevel.WARNING }
        val errors = items.count { it.level == PortingCheckLevel.ERROR }

        return GetpropPortingCheckResult(
            baseSnapshotName = base.name,
            portSnapshotName = port.name,
            items = items,
            passedCount = passed,
            warningCount = warnings,
            errorCount = errors
        )
    }

    private fun isSameSoCFamily(soc1: String, soc2: String): Boolean {
        val s1 = soc1.lowercase()
        val s2 = soc2.lowercase()
        if (s1 == s2) return true
        if (s1.startsWith("mt") && s2.startsWith("mt")) return true
        if (s1.startsWith("exynos") && s2.startsWith("exynos")) return true
        if ((s1.startsWith("msm") || s1.startsWith("sdm") || s1.startsWith("qcom")) &&
            (s2.startsWith("msm") || s2.startsWith("sdm") || s2.startsWith("qcom"))) return true
        if (s1.startsWith("sc") && s2.startsWith("sc")) return true
        return false
    }
}
