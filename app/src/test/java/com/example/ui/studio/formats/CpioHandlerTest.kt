package com.example.ui.studio.formats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CpioHandlerTest {

    @Test
    fun testCpioUnpackInvalidDataReturnsFalse() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cpio_test_invalid")
        tempDir.mkdirs()
        
        val dummyCpio = File(tempDir, "test.cpio")
        dummyCpio.writeText("invalid_corrupt_data_not_cpio")
        
        val outDir = File(tempDir, "out")
        val result = CpioHandler.unpack(dummyCpio, outDir)
        
        // Corrupt CPIO must return false
        assertFalse(result)
        
        tempDir.deleteRecursively()
    }

    @Test
    fun testCpioUnpackNonExistentFileReturnsFalse() {
        val nonExistent = File(System.getProperty("java.io.tmpdir"), "non_existent.cpio")
        val outDir = File(System.getProperty("java.io.tmpdir"), "out_dummy")
        val result = CpioHandler.unpack(nonExistent, outDir)
        assertFalse(result)
    }
}

