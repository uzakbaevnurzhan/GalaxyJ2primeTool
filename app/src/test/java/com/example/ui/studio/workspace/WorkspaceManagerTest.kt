package com.example.ui.studio.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WorkspaceManagerTest {

    @Test
    fun testCreateProject() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = WorkspaceManager.createProject(context, "TestProject")
        
        assertEquals("TestProject", project.name)
        val root = File(project.rootPath)
        assertTrue(root.exists())
        assertTrue(File(root, "workspace").exists())
        assertTrue(File(root, "input").exists())
        assertTrue(File(root, "output").exists())
        assertTrue(File(root, "metadata").exists())
        }
    }
}
