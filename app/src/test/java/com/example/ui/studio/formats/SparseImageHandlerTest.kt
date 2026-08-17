package com.example.ui.studio.formats

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class SparseImageHandlerTest {

    @Test
    fun testSparseImageInvalid() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "sparse_test")
        tempDir.mkdirs()
        
        val dummySparse = File(tempDir, "test.sparse")
        dummySparse.writeText("not a sparse image")
        
        val rawFile = File(tempDir, "test.raw")
        val result = SparseImageHandler.sparseToRaw(dummySparse, rawFile)
        
        // Should fail cleanly due to invalid magic
        assertFalse(result)
        
        tempDir.deleteRecursively()
    }
}
