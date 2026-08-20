package com.example.analyzer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ReportFormat
import com.example.ui.analyzer.system.engine.FullSystemAnalyzerEngine
import com.example.ui.analyzer.system.engine.FullSystemHistoryManager
import com.example.ui.analyzer.system.engine.FullSystemReportFormatter
import com.example.ui.analyzer.system.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FullSystemAnalyzerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testFullSystemAnalysisExecution() = runBlocking {
        var progressCalled = false
        val result = FullSystemAnalyzerEngine.runFullAnalysis(
            context = context,
            mode = AnalysisMode.DEEP,
            onProgress = { stage, prog ->
                progressCalled = true
                assertTrue(prog >= 0f && prog <= 1.0f)
                assertNotNull(stage)
            }
        )

        assertTrue(progressCalled)
        assertNotNull(result.id)
        assertEquals("Beta 3", result.appVersion)
        assertNotNull(result.healthStatus)
        assertNotNull(result.lastConfirmedWorkingStage)
        assertTrue(result.halComponentMatrix.isNotEmpty())
        assertTrue(result.deviceSummary.isNotEmpty())
        assertTrue(result.partitionAudit.isNotEmpty())
        assertNotNull(result.androidVersionAudit)
        assertNotNull(result.cpuAbiAudit)
        assertNotNull(result.ramAudit)
        assertNotNull(result.storageAudit)
        assertNotNull(result.kernelAudit)
        assertNotNull(result.securityAudit)
        assertNotNull(result.selinuxAudit)
    }

    @Test
    fun testReportFormatterAllFormats() = runBlocking {
        val result = FullSystemAnalyzerEngine.runFullAnalysis(context, AnalysisMode.BASIC)

        val md = FullSystemReportFormatter.formatReport(result, ReportFormat.MARKDOWN)
        assertTrue(md.contains("Galaxy J2 Prime Tool — Full System Analysis Report"))
        assertTrue(md.contains("System Health & Executive Summary"))
        assertTrue(md.contains("Subsystem & Component Status Matrix"))

        val json = FullSystemReportFormatter.formatReport(result, ReportFormat.JSON)
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}\n"))
        assertTrue(json.contains("\"healthStatus\":"))
        assertTrue(json.contains("\"components\":"))

        val txt = FullSystemReportFormatter.formatReport(result, ReportFormat.TXT)
        assertTrue(txt.contains("GALAXY J2 PRIME TOOL — FULL SYSTEM ANALYZER REPORT"))
        assertTrue(txt.contains("SYSTEM HEALTH"))

        val csv = FullSystemReportFormatter.formatReport(result, ReportFormat.CSV)
        assertTrue(csv.startsWith("Component,Category,Status,Source,PrimaryError,Evidence"))
    }

    @Test
    fun testRegressionDiffCalculation() {
        val baseComp = SystemComponentMatrixItem(
            componentKey = "camera",
            componentName = "Camera Subsystem",
            category = "Media",
            status = ComponentStatus.WORKING,
            primaryError = null,
            source = EvidenceSource.ANDROID_API,
            evidence = "Working",
            confidence = 100,
            lastConfirmedStage = "Camera",
            relatedToolRoute = "device_info"
        )

        val oldSession = FullSystemAnalysisResult(
            id = "session-1",
            timestamp = 1000L,
            appVersion = "Beta 3",
            analysisMode = AnalysisMode.DEEP,
            capabilities = AnalysisCapabilities(true, true, true, true, true, true, true, true, true, true, true, true),
            healthStatus = SystemHealthStatus.HEALTHY,
            lastConfirmedWorkingStage = "Camera",
            suspectedFailureStage = null,
            deviceSummary = emptyMap(),
            androidVersionAudit = AndroidVersionAudit("6.0.1", 23, "6.0.1", null, null, false, null, false, "Legacy"),
            securityAudit = SecurityAudit(ComponentStatus.WORKING, "root", "Enforcing", ComponentStatus.WORKING, ComponentStatus.WORKING, "green", "unencrypted", true, "Enabled", "release-keys"),
            cpuAbiAudit = CpuAbiAudit("armv7l", "armv7l", "armeabi-v7a", listOf("armeabi-v7a"), "armeabi-v7a", "armeabi-v7a", "ELF32", false, null),
            ramAudit = RamAudit(1500000, 800000, 700000, 50000, 400000, 500000, 400000, 500000, ComponentStatus.WORKING),
            storageAudit = StorageAudit(8000000000L, 4000000000L, 8000000000L, 4000000000L, emptyList(), ComponentStatus.WORKING),
            partitionAudit = emptyList(),
            kernelAudit = KernelAudit("3.18.35+", "gcc", "arm", "console=tty0", false, 10, ComponentStatus.WORKING, "ok"),
            bootAudit = BootAudit(false, null, null, null, null, null, null, ComponentStatus.UNAVAILABLE, "ok"),
            dtbAudit = DtbAudit(false, emptyList(), "MT6737T", emptyList(), emptyList(), ComponentStatus.UNKNOWN, "ok"),
            systemVendorTrebleAudit = SystemVendorTrebleAudit(true, false, false, false, "Legacy", 50, 100, 0, 0, ComponentStatus.WORKING, "ok"),
            elfAudit = ElfAudit(10, 10, 0, emptyList(), emptyList(), emptyList(), ComponentStatus.WORKING, "ok"),
            halComponentMatrix = listOf(baseComp),
            hardwareAudit = HardwareSubsystemAudit(ComponentStatus.WORKING, "ok", ComponentStatus.WORKING, listOf("0", "1"), "ok", ComponentStatus.WORKING, "ok", ComponentStatus.WORKING, "ok", ComponentStatus.WORKING, emptyList(), "ok", ComponentStatus.WORKING, "540x960", 60f, "ok", ComponentStatus.WORKING, "MTP", "ok", ComponentStatus.WORKING, 80, 4000, 30.0f, "GOOD", "ok"),
            selinuxAudit = SelinuxAudit("Enforcing", ComponentStatus.WORKING, 0, emptyMap(), emptyList(), "ok"),
            logAudit = LogSubsystemAudit(100, 50, false, false, emptyList(), emptyList(), emptyList(), emptyList(), ComponentStatus.WORKING),
            deduplicatedErrors = listOf(
                SystemErrorItem(
                    id = "err1",
                    subsystem = ErrorSubsystem.FRAMEWORK,
                    severity = SystemSeverity.WARNING,
                    message = "Old transient warning",
                    component = "Framework",
                    stage = "Runtime",
                    rawEvidence = "Warning",
                    repeatCount = 1,
                    relatedTool = "log_analyzer",
                    suggestedAction = "Ignore"
                )
            ),
            rootCauses = emptyList(),
            fixSuggestions = emptyList(),
            workingCount = 1,
            failedCount = 0,
            partialCount = 0,
            unknownCount = 0,
            totalErrorsCount = 1,
            blockersCount = 0,
            criticalCount = 0,
            elapsedMillis = 500L,
            rawEvidenceLog = emptyList()
        )

        val regressedComp = baseComp.copy(status = ComponentStatus.FAILED, primaryError = "Camera sensor timeout")

        val newSession = oldSession.copy(
            id = "session-2",
            timestamp = 2000L,
            healthStatus = SystemHealthStatus.DEGRADED,
            halComponentMatrix = listOf(regressedComp),
            deduplicatedErrors = listOf(
                SystemErrorItem(
                    id = "err2",
                    subsystem = ErrorSubsystem.CAMERA,
                    severity = SystemSeverity.ERROR,
                    message = "Camera service connection dropped",
                    component = "Camera",
                    stage = "Camera HAL",
                    rawEvidence = "Service died",
                    repeatCount = 5,
                    relatedTool = "device_info",
                    suggestedAction = "Check camera HAL"
                )
            )
        )

        val diff = FullSystemHistoryManager.computeRegressionDiff(oldSession, newSession)

        assertEquals("session-1", diff.oldSessionId)
        assertEquals("session-2", diff.newSessionId)
        assertEquals(1, diff.fixedErrors.size)
        assertEquals("Old transient warning", diff.fixedErrors[0].message)
        assertEquals(1, diff.newErrors.size)
        assertEquals("Camera service connection dropped", diff.newErrors[0].message)
        assertEquals(1, diff.regressedComponents.size)
        assertEquals("Camera Subsystem", diff.regressedComponents[0])
        assertEquals(SystemHealthStatus.HEALTHY, diff.healthChangedFrom)
        assertEquals(SystemHealthStatus.DEGRADED, diff.healthChangedTo)
    }
}
