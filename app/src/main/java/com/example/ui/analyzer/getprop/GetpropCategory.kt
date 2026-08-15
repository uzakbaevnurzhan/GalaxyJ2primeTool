package com.example.ui.analyzer.getprop

/**
 * Standardized categorization for Android system properties.
 */
enum class GetpropCategory(val displayName: String) {
    PRODUCT("Product"),
    BUILD("Build"),
    ANDROID("Android"),
    KERNEL("Kernel"),
    HARDWARE("Hardware"),
    SOC("SoC"),
    DISPLAY("Display"),
    GRAPHICS("Graphics"),
    AUDIO("Audio"),
    CAMERA("Camera"),
    MEDIA("Media"),
    NETWORK("Network"),
    WIFI("Wi-Fi"),
    BLUETOOTH("Bluetooth"),
    TELEPHONY("Telephony"),
    RIL("RIL"),
    RADIO("Radio"),
    USB("USB"),
    STORAGE("Storage"),
    SECURITY("Security"),
    SELINUX("SELinux"),
    DALVIK("Dalvik"),
    ART("ART"),
    PERFORMANCE("Performance"),
    DEBUG("Debug"),
    VENDOR("Vendor"),
    SYSTEM("System"),
    POWER("Power"),
    BATTERY("Battery"),
    UNKNOWN("Unknown");

    companion object {
        fun categorize(key: String): GetpropCategory {
            val lower = key.lowercase()

            return when {
                // Product properties
                lower.startsWith("ro.product.") ||
                lower == "ro.hardware" ||
                lower.startsWith("ro.product_services.") -> PRODUCT

                // Android version & OS specific
                lower.startsWith("ro.build.version.") ||
                lower == "ro.build.version.release" ||
                lower == "ro.build.version.sdk" ||
                lower == "ro.build.version.security_patch" ||
                lower == "ro.build.version.codename" ||
                lower == "ro.build.version.incremental" -> ANDROID

                // Build properties
                lower.startsWith("ro.build.") ||
                lower.startsWith("ro.bootimage.build.") ||
                lower.startsWith("ro.system.build.") ||
                lower.startsWith("ro.vendor.build.") ||
                lower.startsWith("ro.odm.build.") -> BUILD

                // SELinux properties
                lower.startsWith("ro.boot.selinux") ||
                lower.startsWith("selinux.") ||
                lower.startsWith("ro.selinux.") ||
                lower == "ro.build.selinux" -> SELINUX

                // Security properties
                lower == "ro.secure" ||
                lower == "ro.adb.secure" ||
                lower == "ro.debuggable" ||
                lower.startsWith("security.") ||
                lower.startsWith("ro.security.") ||
                lower.startsWith("ro.crypto.") -> SECURITY

                // Hardware & SoC
                lower.startsWith("ro.soc.") ||
                lower.startsWith("ro.board.platform") ||
                lower.startsWith("ro.chipname") ||
                lower.startsWith("ro.arch") -> SOC

                lower.startsWith("ro.boot.hardware") ||
                lower.startsWith("ro.bootmode") ||
                lower.startsWith("ro.boot.") ||
                lower.startsWith("ro.hardware.") && !lower.startsWith("ro.hardware.egl") && !lower.startsWith("ro.hardware.gralloc") && !lower.startsWith("ro.hardware.audio") && !lower.startsWith("ro.hardware.camera") -> HARDWARE

                // Graphics properties
                lower.startsWith("ro.hardware.egl") ||
                lower.startsWith("ro.hardware.gralloc") ||
                lower.startsWith("ro.opengles.") ||
                lower.startsWith("ro.hwui.") ||
                lower.startsWith("debug.hwui.") ||
                lower.startsWith("debug.egl.") ||
                lower.startsWith("debug.render.") ||
                lower.startsWith("ro.gfx.") ||
                lower.startsWith("debug.gfx.") ||
                lower.startsWith("ro.sf.lcd_density") ||
                lower.startsWith("ro.surface_flinger.") -> {
                    if (lower.contains("density") || lower.contains("sf.") || lower.contains("surface_flinger")) {
                        DISPLAY
                    } else {
                        GRAPHICS
                    }
                }

                // Display properties
                lower.startsWith("ro.sf.") ||
                lower.startsWith("persist.sys.sf.") ||
                lower.startsWith("debug.sf.") ||
                lower.startsWith("persist.demo.hdmirotation") ||
                lower.contains("display") && !lower.startsWith("ro.build.display.id") -> DISPLAY

                // Dalvik / ART
                lower.startsWith("dalvik.vm.") ||
                lower.startsWith("persist.sys.dalvik.vm.") ||
                lower.startsWith("ro.dalvik.vm.") -> DALVIK

                lower.startsWith("runtime.") ||
                lower.startsWith("art.") ||
                lower.startsWith("ro.art.") -> ART

                // Camera
                lower.startsWith("camera.") ||
                lower.startsWith("media.camera.") ||
                lower.startsWith("persist.camera.") ||
                lower.startsWith("persist.vendor.camera.") ||
                lower.startsWith("vendor.camera.") ||
                lower.startsWith("ro.camera.") ||
                lower.startsWith("ro.hardware.camera") -> CAMERA

                // Audio
                lower.startsWith("audio.") ||
                lower.startsWith("af.resampler.") ||
                lower.startsWith("persist.audio.") ||
                lower.startsWith("persist.vendor.audio.") ||
                lower.startsWith("vendor.audio.") ||
                lower.startsWith("ro.audio.") ||
                lower.startsWith("ro.hardware.audio") -> AUDIO

                // Media
                lower.startsWith("media.") ||
                lower.startsWith("ro.media.") ||
                lower.startsWith("vendor.media.") ||
                lower.startsWith("persist.media.") -> MEDIA

                // Telephony / RIL / Radio
                lower.startsWith("ril.") ||
                lower.startsWith("vendor.ril.") ||
                lower.startsWith("ro.telephony.ril.") ||
                lower.startsWith("rild.") ||
                lower == "gsm.version.ril-impl" -> RIL

                lower.startsWith("persist.radio.") ||
                lower.startsWith("ro.radio.") ||
                lower.startsWith("radio.") -> RADIO

                lower.startsWith("gsm.") ||
                lower.startsWith("ro.telephony.") ||
                lower.startsWith("telephony.") -> TELEPHONY

                // Wireless
                lower.startsWith("wifi.") ||
                lower.startsWith("wlan.") ||
                lower.startsWith("ro.wifi.") ||
                lower.startsWith("persist.sys.wifi.") ||
                lower.startsWith("persist.vendor.wifi.") -> WIFI

                lower.startsWith("bluetooth.") ||
                lower.startsWith("ro.bluetooth.") ||
                lower.startsWith("persist.bluetooth.") ||
                lower.startsWith("net.bt.") ||
                lower.startsWith("persist.vendor.bt.") -> BLUETOOTH

                lower.startsWith("net.") ||
                lower.startsWith("network.") ||
                lower.startsWith("ro.net.") -> NETWORK

                // USB
                lower.startsWith("sys.usb.") ||
                lower.startsWith("persist.sys.usb.") ||
                lower.startsWith("ro.usb.") -> USB

                // Storage
                lower.startsWith("vold.") ||
                lower.startsWith("ro.vold.") ||
                lower.startsWith("storage.") ||
                lower.startsWith("persist.sys.storage.") ||
                lower.startsWith("ro.crypto.state") ||
                lower.startsWith("ro.crypto.type") -> STORAGE

                // Power & Battery
                lower.startsWith("sys.power.") ||
                lower.startsWith("power.") ||
                lower.startsWith("ro.power.") -> POWER

                lower.startsWith("battery.") ||
                lower.startsWith("ro.battery.") ||
                lower.startsWith("sys.battery.") -> BATTERY

                // Kernel
                lower.startsWith("kernel.") ||
                lower.startsWith("ro.kernel.") -> KERNEL

                // Performance
                lower.startsWith("perf.") ||
                lower.startsWith("ro.perf.") ||
                lower.startsWith("vendor.perf.") ||
                lower.startsWith("sys.perf.") -> PERFORMANCE

                // Debug
                lower.startsWith("debug.") ||
                lower.startsWith("persist.debug.") ||
                lower.startsWith("log.") ||
                lower.startsWith("ro.log.") -> DEBUG

                // Vendor
                lower.startsWith("vendor.") ||
                lower.startsWith("ro.vendor.") ||
                lower.startsWith("persist.vendor.") ||
                lower.startsWith("ro.odm.") ||
                lower.startsWith("odm.") -> VENDOR

                // System
                lower.startsWith("sys.") ||
                lower.startsWith("persist.sys.") ||
                lower.startsWith("ro.sys.") ||
                lower.startsWith("system.") ||
                lower.startsWith("init.svc.") -> SYSTEM

                // If completely unknown, do not guess
                else -> UNKNOWN
            }
        }
    }
}
