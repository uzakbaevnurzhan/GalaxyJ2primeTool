package com.example.ui.analyzer.vendor

import com.example.ui.analyzer.vendor.models.VendorFeaturePermission
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

object VendorPermissionAnalyzer {

    fun parsePermissionXml(content: String, sourceFileName: String = "permissions.xml"): List<VendorFeaturePermission> {
        val permissions = mutableListOf<VendorFeaturePermission>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    if (tagName == "feature" || tagName == "unavailable-feature") {
                        val name = parser.getAttributeValue(null, "name")
                        val required = parser.getAttributeValue(null, "required")?.toBooleanStrictOrNull() ?: true
                        if (!name.isNullOrBlank()) {
                            permissions.add(
                                VendorFeaturePermission(
                                    featureName = name,
                                    sourceFile = sourceFileName,
                                    isRequired = if (tagName == "unavailable-feature") false else required
                                )
                            )
                        }
                    } else if (tagName == "permission") {
                        val name = parser.getAttributeValue(null, "name")
                        if (!name.isNullOrBlank()) {
                            permissions.add(
                                VendorFeaturePermission(
                                    featureName = name,
                                    sourceFile = sourceFileName,
                                    isRequired = true
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Fallback to simple regex if XML parser fails
            val featureRegex = Regex("""<feature\s+name="([^"]+)"""")
            featureRegex.findAll(content).forEach { match ->
                val feat = match.groupValues[1]
                permissions.add(
                    VendorFeaturePermission(
                        featureName = feat,
                        sourceFile = sourceFileName,
                        isRequired = true
                    )
                )
            }
        }
        return permissions
    }

    fun scanPermissionsDirectory(dir: File): List<VendorFeaturePermission> {
        val list = mutableListOf<VendorFeaturePermission>()
        if (!dir.exists() || !dir.isDirectory) return list

        dir.walkTopDown().maxDepth(3).forEach { file ->
            if (file.isFile && file.name.endsWith(".xml")) {
                try {
                    val content = file.readText(Charsets.UTF_8)
                    list.addAll(parsePermissionXml(content, file.name))
                } catch (e: Exception) {
                    // Ignore unreadable files
                }
            }
        }
        return list
    }
}
