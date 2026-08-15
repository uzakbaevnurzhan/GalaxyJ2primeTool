package com.example.ui.analyzer.getprop

import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyTypeTest {

    @Test
    fun testDetectPropertyTypes() {
        assertEquals(PropertyValueType.BOOLEAN, PropertyValueType.detect("true"))
        assertEquals(PropertyValueType.BOOLEAN, PropertyValueType.detect("false"))
        assertEquals(PropertyValueType.BOOLEAN, PropertyValueType.detect("TRUE"))
        assertEquals(PropertyValueType.BOOLEAN, PropertyValueType.detect("FALSE"))

        assertEquals(PropertyValueType.HEX, PropertyValueType.detect("0x00030002"))
        assertEquals(PropertyValueType.HEX, PropertyValueType.detect("0xFF"))
        assertEquals(PropertyValueType.HEX, PropertyValueType.detect("0X1A4"))

        assertEquals(PropertyValueType.VERSION, PropertyValueType.detect("8.1.0"))
        assertEquals(PropertyValueType.VERSION, PropertyValueType.detect("13.0"))
        assertEquals(PropertyValueType.VERSION, PropertyValueType.detect("1.0.0-rc1"))

        assertEquals(PropertyValueType.INTEGER, PropertyValueType.detect("123"))
        assertEquals(PropertyValueType.INTEGER, PropertyValueType.detect("-456"))
        assertEquals(PropertyValueType.INTEGER, PropertyValueType.detect("0"))

        assertEquals(PropertyValueType.LONG, PropertyValueType.detect("9999999999999"))

        assertEquals(PropertyValueType.LIST, PropertyValueType.detect("armeabi-v7a,armeabi"))
        assertEquals(PropertyValueType.LIST, PropertyValueType.detect("arm64-v8a,armeabi-v7a,armeabi"))

        assertEquals(PropertyValueType.STRING, PropertyValueType.detect("SM-G532F"))
        assertEquals(PropertyValueType.STRING, PropertyValueType.detect("mt6737t"))
        assertEquals(PropertyValueType.STRING, PropertyValueType.detect("256m"))

        assertEquals(PropertyValueType.UNKNOWN, PropertyValueType.detect(""))
        assertEquals(PropertyValueType.UNKNOWN, PropertyValueType.detect("   "))
    }
}
