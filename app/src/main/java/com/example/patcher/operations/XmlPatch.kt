package com.example.patcher.operations

import com.example.patcher.PatchOperation
import com.example.patcher.SinglePatchExecutionResult
import com.example.patcher.SnapshotManager
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object XmlPatch {

    fun apply(workspaceDir: File, op: PatchOperation): SinglePatchExecutionResult {
        val startTime = System.currentTimeMillis()
        val targetFile = com.example.utils.SecurityUtil.safeResolve(workspaceDir, op.targetPath)
        
        if (!targetFile.exists()) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Target XML file does not exist: ${op.targetPath}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val originalContent = targetFile.readText(Charsets.UTF_8)
        val oldHash = SnapshotManager.calculateSha256(targetFile)
        val oldSize = targetFile.length()

        val doc: Document
        try {
            val dbFactory = com.example.utils.XmlSecurityUtil.createSecureDocumentBuilderFactory()
            dbFactory.isNamespaceAware = false
            val dBuilder = dbFactory.newDocumentBuilder()
            doc = dBuilder.parse(ByteArrayInputStream(originalContent.toByteArray(Charsets.UTF_8)))
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "Failed to parse XML in ${op.targetPath}: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val action = op.xmlAction ?: "MODIFY_ATTRIBUTE"
        val tagName = op.xmlXPathOrTag ?: ""
        val attrName = op.xmlAttributeName
        val attrVal = op.xmlAttributeValue
        val textVal = op.xmlContent

        try {
            when (action) {
                "MODIFY_ATTRIBUTE" -> {
                    val elements = doc.getElementsByTagName(tagName)
                    if (elements.length == 0) {
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = false,
                            message = "No XML tag named '$tagName' found in ${op.targetPath}",
                            oldHash = oldHash,
                            oldSize = oldSize,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                    if (attrName != null && attrVal != null) {
                        for (i in 0 until elements.length) {
                            val el = elements.item(i) as? Element
                            el?.setAttribute(attrName, attrVal)
                        }
                    }
                }
                "ADD_NODE" -> {
                    val root = doc.documentElement
                    if (textVal != null) {
                        // Parse node snippet and import into doc
                        val snippetDoc = com.example.utils.XmlSecurityUtil.createSecureDocumentBuilderFactory().newDocumentBuilder()
                            .parse(ByteArrayInputStream("<wrapper>$textVal</wrapper>".toByteArray(Charsets.UTF_8)))
                        val nodes = snippetDoc.documentElement.childNodes
                        for (i in 0 until nodes.length) {
                            val importedNode = doc.importNode(nodes.item(i), true)
                            root.appendChild(importedNode)
                        }
                    } else if (tagName.isNotEmpty()) {
                        val newEl = doc.createElement(tagName)
                        if (attrName != null && attrVal != null) newEl.setAttribute(attrName, attrVal)
                        root.appendChild(newEl)
                    }
                }
                "REMOVE_NODE" -> {
                    val elements = doc.getElementsByTagName(tagName)
                    val toRemove = mutableListOf<Node>()
                    for (i in 0 until elements.length) {
                        val el = elements.item(i)
                        if (attrName != null && attrVal != null) {
                            if ((el as? Element)?.getAttribute(attrName) == attrVal) {
                                toRemove.add(el)
                            }
                        } else {
                            toRemove.add(el)
                        }
                    }
                    if (toRemove.isEmpty()) {
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = false,
                            message = "No matching node found to remove: tag='$tagName', attr='$attrName'",
                            oldHash = oldHash,
                            oldSize = oldSize,
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                    toRemove.forEach { it.parentNode?.removeChild(it) }
                }
                "MODIFY_TEXT" -> {
                    val elements = doc.getElementsByTagName(tagName)
                    if (elements.length > 0 && textVal != null) {
                        elements.item(0).textContent = textVal
                    }
                }
                "REPLACE_BLOCK" -> {
                    if (textVal != null) {
                        // Full content replacement with validation
                        validateXmlString(textVal)
                        targetFile.writeText(textVal, Charsets.UTF_8)
                        val newHash = SnapshotManager.calculateSha256(targetFile)
                        return SinglePatchExecutionResult(
                            operationId = op.id,
                            success = true,
                            message = "XML file block replaced in ${op.targetPath}",
                            oldHash = oldHash,
                            newHash = newHash,
                            oldSize = oldSize,
                            newSize = targetFile.length(),
                            executionTimeMs = System.currentTimeMillis() - startTime
                        )
                    }
                }
            }

            // Serialize XML back
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")
            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))
            val updatedXml = writer.toString()

            targetFile.writeText(updatedXml, Charsets.UTF_8)
            val newHash = SnapshotManager.calculateSha256(targetFile)

            return SinglePatchExecutionResult(
                operationId = op.id,
                success = true,
                message = "XML patch successfully applied to ${op.targetPath}",
                oldHash = oldHash,
                newHash = newHash,
                oldSize = oldSize,
                newSize = targetFile.length(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return SinglePatchExecutionResult(
                operationId = op.id,
                success = false,
                message = "XML manipulation failed: ${e.message}",
                oldHash = oldHash,
                oldSize = oldSize,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    fun validateXmlString(xmlContent: String): Boolean {
        val dbFactory = com.example.utils.XmlSecurityUtil.createSecureDocumentBuilderFactory()
        val dBuilder = dbFactory.newDocumentBuilder()
        dBuilder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        return true
    }
}
