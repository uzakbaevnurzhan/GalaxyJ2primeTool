package com.example.ui.studio.rom

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ui.studio.workspace.WorkspaceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class BootUnpackTest {

    @Test
    fun testUnpackBootImageGracefulFailure() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = WorkspaceManager.createProject(context, "BootTest")
        
        val tempDir = File(System.getProperty("java.io.tmpdir"), "boot_test")
        tempDir.mkdirs()
        
        val dummyBoot = File(tempDir, "boot.img")
        dummyBoot.writeText("invalid boot image")
        
        val uri = Uri.fromFile(dummyBoot)
        
        val result = RomUnpackEngine.unpack(context, project, uri, "boot.img") { _, _ -> }
        
        assertTrue(result is RomOperationResult.Error)
        
        tempDir.deleteRecursively()
        }
    }
}
