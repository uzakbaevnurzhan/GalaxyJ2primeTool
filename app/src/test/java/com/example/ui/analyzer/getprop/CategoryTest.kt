package com.example.ui.analyzer.getprop

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {

    @Test
    fun testCategorizeProductAndBuild() {
        assertEquals(GetpropCategory.PRODUCT, GetpropCategory.categorize("ro.product.model"))
        assertEquals(GetpropCategory.PRODUCT, GetpropCategory.categorize("ro.product.brand"))
        assertEquals(GetpropCategory.PRODUCT, GetpropCategory.categorize("ro.hardware"))

        assertEquals(GetpropCategory.ANDROID, GetpropCategory.categorize("ro.build.version.release"))
        assertEquals(GetpropCategory.ANDROID, GetpropCategory.categorize("ro.build.version.sdk"))
        assertEquals(GetpropCategory.ANDROID, GetpropCategory.categorize("ro.build.version.security_patch"))

        assertEquals(GetpropCategory.BUILD, GetpropCategory.categorize("ro.build.id"))
        assertEquals(GetpropCategory.BUILD, GetpropCategory.categorize("ro.build.display.id"))
        assertEquals(GetpropCategory.BUILD, GetpropCategory.categorize("ro.build.tags"))
    }

    @Test
    fun testCategorizeSubsystems() {
        assertEquals(GetpropCategory.GRAPHICS, GetpropCategory.categorize("ro.hardware.egl"))
        assertEquals(GetpropCategory.GRAPHICS, GetpropCategory.categorize("ro.opengles.version"))
        assertEquals(GetpropCategory.GRAPHICS, GetpropCategory.categorize("debug.hwui.renderer"))

        assertEquals(GetpropCategory.DISPLAY, GetpropCategory.categorize("ro.sf.lcd_density"))
        assertEquals(GetpropCategory.DISPLAY, GetpropCategory.categorize("debug.sf.latch_unsignaled"))

        assertEquals(GetpropCategory.DALVIK, GetpropCategory.categorize("dalvik.vm.heapsize"))
        assertEquals(GetpropCategory.DALVIK, GetpropCategory.categorize("persist.sys.dalvik.vm.lib.2"))
        assertEquals(GetpropCategory.ART, GetpropCategory.categorize("art.opt.quick"))

        assertEquals(GetpropCategory.CAMERA, GetpropCategory.categorize("camera.disable_zsl_mode"))
        assertEquals(GetpropCategory.CAMERA, GetpropCategory.categorize("vendor.camera.hal"))

        assertEquals(GetpropCategory.AUDIO, GetpropCategory.categorize("audio.deep_buffer.media"))
        assertEquals(GetpropCategory.AUDIO, GetpropCategory.categorize("af.resampler.quality"))

        assertEquals(GetpropCategory.MEDIA, GetpropCategory.categorize("media.stagefright.enable-player"))

        assertEquals(GetpropCategory.RIL, GetpropCategory.categorize("rild.libpath"))
        assertEquals(GetpropCategory.RIL, GetpropCategory.categorize("vendor.ril.sim.count"))
        assertEquals(GetpropCategory.TELEPHONY, GetpropCategory.categorize("gsm.sim.state"))
        assertEquals(GetpropCategory.RADIO, GetpropCategory.categorize("persist.radio.multisim.config"))

        assertEquals(GetpropCategory.WIFI, GetpropCategory.categorize("wifi.interface"))
        assertEquals(GetpropCategory.BLUETOOTH, GetpropCategory.categorize("bluetooth.enable_timeout_ms"))
        assertEquals(GetpropCategory.NETWORK, GetpropCategory.categorize("net.dns1"))

        assertEquals(GetpropCategory.USB, GetpropCategory.categorize("sys.usb.config"))
        assertEquals(GetpropCategory.STORAGE, GetpropCategory.categorize("vold.decrypt"))
        assertEquals(GetpropCategory.POWER, GetpropCategory.categorize("sys.power.suspend"))
        assertEquals(GetpropCategory.BATTERY, GetpropCategory.categorize("sys.battery.charge_rate"))

        assertEquals(GetpropCategory.SELINUX, GetpropCategory.categorize("ro.boot.selinux"))
        assertEquals(GetpropCategory.SECURITY, GetpropCategory.categorize("ro.secure"))
        assertEquals(GetpropCategory.SECURITY, GetpropCategory.categorize("ro.adb.secure"))

        assertEquals(GetpropCategory.SOC, GetpropCategory.categorize("ro.board.platform"))
        assertEquals(GetpropCategory.HARDWARE, GetpropCategory.categorize("ro.boot.hardware"))

        assertEquals(GetpropCategory.DEBUG, GetpropCategory.categorize("debug.log.level"))
        assertEquals(GetpropCategory.VENDOR, GetpropCategory.categorize("vendor.mtk.custom"))
        assertEquals(GetpropCategory.SYSTEM, GetpropCategory.categorize("sys.boot_completed"))
        assertEquals(GetpropCategory.KERNEL, GetpropCategory.categorize("ro.kernel.android.checkjni"))
        assertEquals(GetpropCategory.PERFORMANCE, GetpropCategory.categorize("ro.perf.boost"))

        assertEquals(GetpropCategory.UNKNOWN, GetpropCategory.categorize("custom_unrecognized_key_xyz"))
    }
}
