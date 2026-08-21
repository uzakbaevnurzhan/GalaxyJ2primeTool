package com.example.utils

import javax.xml.parsers.DocumentBuilderFactory

object XmlSecurityUtil {
    fun createSecureDocumentBuilderFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            factory.isXIncludeAware = false
            factory.isExpandEntityReferences = false
        } catch (e: Exception) {
            // Ignore feature unsupported exceptions
        }
        return factory
    }
}
