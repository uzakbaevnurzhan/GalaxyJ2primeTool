package com.example.ui.analyzer.kernel.studio.dtb

import com.example.ui.analyzer.kernel.studio.models.KernelNode
import com.example.ui.analyzer.kernel.studio.models.KernelProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DtbNodeParser {

    private const val FDT_BEGIN_NODE = 0x00000001
    private const val FDT_END_NODE   = 0x00000002
    private const val FDT_PROP       = 0x00000003
    private const val FDT_NOP        = 0x00000004
    private const val FDT_END        = 0x00000009

    fun parseTree(bytes: ByteArray, header: FdtHeader, baseOffset: Int = 0): KernelNode? {
        if (!header.isValid) return null

        val structStart = (baseOffset + header.offDtStruct).toInt()
        val stringsStart = (baseOffset + header.offDtStrings).toInt()

        if (structStart >= bytes.size || stringsStart >= bytes.size) {
            return null
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buf.position(structStart)

        val nodeStack = ArrayDeque<KernelNode>()
        var rootNode: KernelNode? = null

        val maxPosition = bytes.size.coerceAtMost(
            if (header.sizeDtStruct > 0) (structStart + header.sizeDtStruct).toInt() else bytes.size
        )

        while (buf.position() < maxPosition) {
            if (buf.remaining() < 4) break
            val token = buf.getInt()

            when (token) {
                FDT_BEGIN_NODE -> {
                    val nodeOffset = buf.position() - 4
                    val name = readNullTerminatedStringAligned(buf, bytes)
                    val nodeName = if (name.isEmpty() && nodeStack.isEmpty()) "/" else name

                    val parentPath = nodeStack.lastOrNull()?.fullPath ?: ""
                    val currentPath = when {
                        parentPath.isEmpty() || parentPath == "/" -> "/$nodeName".removePrefix("//")
                        else -> "$parentPath/$nodeName"
                    }

                    val newNode = KernelNode(
                        name = nodeName,
                        fullPath = currentPath,
                        offset = nodeOffset
                    )

                    if (rootNode == null) {
                        rootNode = newNode
                    } else {
                        nodeStack.lastOrNull()?.children?.add(newNode)
                    }

                    nodeStack.addLast(newNode)
                }

                FDT_END_NODE -> {
                    if (nodeStack.isNotEmpty()) {
                        nodeStack.removeLast()
                    }
                }

                FDT_PROP -> {
                    if (buf.remaining() < 8) break
                    val propLen = buf.getInt()
                    val nameOff = buf.getInt()

                    val propName = readStringFromOffset(bytes, stringsStart + nameOff)

                    val propBytes = if (propLen > 0 && buf.remaining() >= propLen) {
                        val pb = ByteArray(propLen)
                        buf.get(pb)
                        // Align position to 4 bytes
                        val aligned = ((propLen + 3) / 4) * 4
                        val padding = aligned - propLen
                        if (padding > 0 && buf.remaining() >= padding) {
                            buf.position(buf.position() + padding)
                        }
                        pb
                    } else {
                        ByteArray(0)
                    }

                    val kernelProp = DtbPropertyParser.parseProperty(propName, propBytes)

                    val currentNode = nodeStack.lastOrNull()
                    if (currentNode != null) {
                        val currentProps = currentNode.properties.toMutableList()
                        currentProps.add(kernelProp)
                        // Update phandle if found
                        val ph = if (kernelProp.phandle != null) kernelProp.phandle else currentNode.phandle
                        val updatedNode = currentNode.copy(properties = currentProps, phandle = ph)
                        nodeStack.removeLast()
                        nodeStack.addLast(updatedNode)

                        // Update parent reference
                        if (nodeStack.size > 1) {
                            val parent = nodeStack[nodeStack.size - 2]
                            val idx = parent.children.indexOfFirst { it.fullPath == currentNode.fullPath }
                            if (idx >= 0) {
                                parent.children[idx] = updatedNode
                            }
                        } else if (currentNode.fullPath == "/" || rootNode?.fullPath == currentNode.fullPath) {
                            rootNode = updatedNode
                        }
                    }
                }

                FDT_NOP -> {
                    // No operation
                }

                FDT_END -> {
                    break
                }

                else -> {
                    // Unknown token or alignment issue, try scanning forward to next 4-byte boundary
                    break
                }
            }
        }

        return rootNode
    }

    private fun readNullTerminatedStringAligned(buf: ByteBuffer, bytes: ByteArray): String {
        val start = buf.position()
        var end = start
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        val len = end - start
        val str = if (len > 0) String(bytes, start, len, Charsets.UTF_8) else ""

        val totalLenWithNull = len + 1
        val alignedLen = ((totalLenWithNull + 3) / 4) * 4
        buf.position(start + alignedLen.coerceAtMost(buf.remaining()))

        return str
    }

    private fun readStringFromOffset(bytes: ByteArray, offset: Int): String {
        if (offset < 0 || offset >= bytes.size) return "unknown_prop"
        var end = offset
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8).trim()
    }
}
