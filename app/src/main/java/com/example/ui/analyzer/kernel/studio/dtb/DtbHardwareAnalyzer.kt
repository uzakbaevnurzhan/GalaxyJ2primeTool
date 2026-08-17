package com.example.ui.analyzer.kernel.studio.dtb

import com.example.ui.analyzer.kernel.studio.models.DtbHardwareNode
import com.example.ui.analyzer.kernel.studio.models.KernelNode

object DtbHardwareAnalyzer {

    fun extractCompatibleStrings(rootNode: KernelNode): List<String> {
        val list = mutableListOf<String>()
        val allNodes = rootNode.collectAllNodes()

        for (node in allNodes) {
            val compatProp = node.findProperty("compatible")
            if (compatProp != null) {
                list.addAll(compatProp.stringList)
            }
        }

        return list.distinct().sorted()
    }

    fun detectHardware(rootNode: KernelNode): List<DtbHardwareNode> {
        val hardwareList = mutableListOf<DtbHardwareNode>()
        val allNodes = rootNode.collectAllNodes()

        for (node in allNodes) {
            val compats = node.findProperty("compatible")?.stringList ?: emptyList()
            val lowerPath = node.fullPath.lowercase()
            val lowerCompats = compats.map { it.lowercase() }

            val category = when {
                lowerPath.contains("/cpus") || lowerPath.startsWith("/cpu@") || lowerCompats.any { it.contains("arm,cortex") || it.contains("cpu") } -> "CPU"
                lowerPath.contains("/memory") || lowerPath.contains("reserved-memory") -> "Memory"
                lowerPath.contains("mmc") || lowerPath.contains("emmc") || lowerPath.contains("sdcard") || lowerCompats.any { it.contains("mmc") } -> "Storage"
                lowerPath.contains("dsi") || lowerPath.contains("panel") || lowerPath.contains("dispsys") || lowerPath.contains("display") || lowerCompats.any { it.contains("panel") || it.contains("display") } -> "Display"
                lowerPath.contains("camera") || lowerPath.contains("seninf") || lowerPath.contains("csi") || lowerCompats.any { it.contains("camera") || it.contains("sensor") && lowerPath.contains("cam") } -> "Camera"
                lowerPath.contains("audio") || lowerPath.contains("sound") || lowerPath.contains("codec") || lowerPath.contains("i2s") || lowerCompats.any { it.contains("sound") || it.contains("audio") || it.contains("codec") } -> "Audio"
                lowerPath.contains("usb") || lowerPath.contains("musb") || lowerPath.contains("dwc3") || lowerCompats.any { it.contains("usb") } -> "USB"
                lowerPath.contains("wlan") || lowerPath.contains("wifi") || lowerCompats.any { it.contains("wlan") || it.contains("wifi") || it.contains("bcmdhd") } -> "Wi-Fi"
                lowerPath.contains("bluetooth") || lowerPath.contains("bt") || lowerCompats.any { it.contains("bluetooth") || it.contains("btmtk") } -> "Bluetooth"
                lowerPath.contains("touchscreen") || lowerPath.contains("touch") || lowerPath.contains("ts@") || lowerCompats.any { it.contains("touch") } -> "Touchscreen"
                lowerPath.contains("sensor") || lowerPath.contains("accel") || lowerPath.contains("gyro") || lowerCompats.any { it.contains("sensor") || it.contains("accel") } -> "Sensors"
                lowerPath.contains("pmic") || lowerPath.contains("regulator") || lowerCompats.any { it.contains("pmic") || it.contains("regulator") || it.contains("mt6328") } -> "Power"
                lowerPath.contains("i2c") || lowerPath.contains("spi") || lowerPath.contains("uart") || lowerPath.contains("serial") -> "Bus / Serial"
                else -> null
            }

            if (category != null) {
                hardwareList.add(
                    DtbHardwareNode(
                        category = category,
                        name = node.name,
                        path = node.fullPath,
                        compatible = compats,
                        status = "DETECTED"
                    )
                )
            }
        }

        return hardwareList.sortedWith(compareBy({ it.category }, { it.name }))
    }
}
