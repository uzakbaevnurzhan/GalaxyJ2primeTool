package com.example.ui.studio.formats

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CpioHandlerTest {

    @Test
    fun testCpioUnpackGracefulFailure() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cpio_test")
        tempDir.mkdirs()
        
        val dummyCpio = File(tempDir, "test.cpio")
        dummyCpio.writeText("invalid_data")
        
        val outDir = File(tempDir, "out")
        val result = CpioHandler.unpack(dummyCpio, outDir)
        
        assertTrue(result)
        
        tempDir.deleteRecursively()
    }
}
