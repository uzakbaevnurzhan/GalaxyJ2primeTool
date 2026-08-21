package com.example.ui.analyzer.partition

import com.example.ui.analyzer.image.SuperImageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SuperImageParserTest {

    @Test
    fun testLpGeometryMagicConstant() {
        assertEquals(0x616c4467.toLong(), SuperImageAnalyzer.LP_METADATA_GEOMETRY_MAGIC.toLong())
    }
}
