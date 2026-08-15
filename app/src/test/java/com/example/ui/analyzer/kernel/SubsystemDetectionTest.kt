package com.example.ui.analyzer.kernel

import com.example.ui.analyzer.kernel.engine.KernelSubsystemDetector
import com.example.ui.analyzer.kernel.model.AnalysisConfidence
import com.example.ui.analyzer.kernel.model.KernelCrashType
import com.example.ui.analyzer.kernel.model.KernelSubsystemType
import org.junit.Assert.*
import org.junit.Test

class SubsystemDetectionTest {

    @Test
    fun testDetectMtkStorageAndWifiSubsystems() {
        val tokens = listOf("mtk_wcn", "wlan_probe", "cfg80211", "wifi", "mmc_blk", "mt6737")
        val detected = KernelSubsystemDetector.detectSubsystems(tokens)

        assertTrue(detected.any { it.type == KernelSubsystemType.MEDIATEK_SOC })
        assertTrue(detected.any { it.type == KernelSubsystemType.WIRELESS_WIFI })
        assertTrue(detected.any { it.type == KernelSubsystemType.STORAGE_EMMC })

        val wifiSub = detected.first { it.type == KernelSubsystemType.WIRELESS_WIFI }
        assertEquals(AnalysisConfidence.HIGH, wifiSub.confidence)
    }

    @Test
    fun testRootCauseAnalysisSeparation() {
        val analysis = KernelSubsystemDetector.analyzeRootCause(
            type = KernelCrashType.NULL_POINTER_DEREFERENCE,
            panicReason = "Unable to handle kernel NULL pointer dereference",
            faultAddress = "00000004",
            pcSymbol = "mtk_disp_power_on",
            stackSymbols = listOf("mtk_disp_power_on", "disp_probe", "platform_drv_probe"),
            processName = "swapper/0",
            contextLines = listOf("mtk_disp: failed to get regulator", "display panel timeout")
        )

        assertTrue(analysis.fact.contains("NULL pointer dereference"))
        assertTrue(analysis.evidenceList.isNotEmpty())
        assertTrue(analysis.possibleCauses.isNotEmpty())
        assertTrue(analysis.recommendedActions.isNotEmpty())
        assertNotNull(analysis.suspectedSubsystem)
    }
}
