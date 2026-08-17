package com.example.ui.studio.workspace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChangeTrackerTest {

    @Test
    fun testWorkspaceTracker() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = WorkspaceManager.createProject(context, "TrackerTest")
        
        val workspaceDir = File(project.rootPath, "workspace")
        val file1 = File(workspaceDir, "file1.txt")
        file1.writeText("hello")
        
        WorkspaceTracker.createSnapshot(project)
        
        // No changes
        var changes = WorkspaceTracker.getChanges(project)
        assertEquals(0, changes.size)
        
        // Add a file
        val file2 = File(workspaceDir, "file2.txt")
        file2.writeText("world")
        
        // Modify file1
        file1.writeText("hello world")
        
        changes = WorkspaceTracker.getChanges(project)
        assertEquals(FileState.ADDED, changes["file2.txt"])
        assertEquals(FileState.MODIFIED, changes["file1.txt"])
        
        // Delete file1
        file1.delete()
        changes = WorkspaceTracker.getChanges(project)
        assertEquals(FileState.DELETED, changes["file1.txt"])
        }
    }
}
