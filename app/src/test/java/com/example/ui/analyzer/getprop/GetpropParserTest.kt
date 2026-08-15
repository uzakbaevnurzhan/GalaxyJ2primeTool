package com.example.ui.analyzer.getprop

import org.junit.Assert.*
import org.junit.Test

class GetpropParserTest {

    @Test
    fun testParseStandardGetpropFormat() {
        val input = """
            [ro.product.model]: [SM-G532F]
            [ro.product.brand]: [samsung]
            [ro.build.version.sdk]: [23]
            [persist.sys.timezone]: [Asia/Almaty]
            [vendor.camera.hal]: [1]
        """.trimIndent()

        val parsed = GetpropParser.parseString(input, "live_getprop.txt")
        assertEquals(5, parsed.parsedCount)
        assertEquals(0, parsed.skippedCount)

        val entryModel = parsed.entries.find { it.key == "ro.product.model" }
        assertNotNull(entryModel)
        assertEquals("SM-G532F", entryModel?.value)
        assertEquals(GetpropCategory.PRODUCT, entryModel?.category)
        assertEquals(1, entryModel?.lineNumber)

        val entrySdk = parsed.entries.find { it.key == "ro.build.version.sdk" }
        assertNotNull(entrySdk)
        assertEquals("23", entrySdk?.value)
        assertEquals(PropertyValueType.INTEGER, entrySdk?.valueType)
    }

    @Test
    fun testParseEmptyAndSpacesInBrackets() {
        val input = """
            [ro.empty.prop] : []
            [ ro.spaced.key ] : [ spaced value ]
            [ro.special.unicode]: [Казахстан / 日本語 🚀]
        """.trimIndent()

        val parsed = GetpropParser.parseString(input, "test.prop")
        assertEquals(3, parsed.parsedCount)

        val emptyProp = parsed.entries.find { it.key == "ro.empty.prop" }
        assertNotNull(emptyProp)
        assertEquals("", emptyProp?.value)

        val unicodeProp = parsed.entries.find { it.key == "ro.special.unicode" }
        assertNotNull(unicodeProp)
        assertEquals("Казахстан / 日本語 🚀", unicodeProp?.value)
    }
}
