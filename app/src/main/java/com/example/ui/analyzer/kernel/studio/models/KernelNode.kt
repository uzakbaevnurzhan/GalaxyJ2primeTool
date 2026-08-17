package com.example.ui.analyzer.kernel.studio.models

import kotlinx.serialization.Serializable

@Serializable
enum class PropertyValueType {
    EMPTY,
    STRING,
    STRING_LIST,
    U32,
    U64,
    PHANDLE,
    BYTES,
    RAW_BYTES
}

@Serializable
data class KernelProperty(
    val name: String,
    val rawBytes: ByteArray,
    val type: PropertyValueType,
    val formattedValue: String,
    val stringList: List<String> = emptyList(),
    val u32Value: Long? = null,
    val phandle: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KernelProperty
        if (name != other.name) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false
        if (type != other.type) return false
        if (formattedValue != other.formattedValue) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + formattedValue.hashCode()
        return result
    }
}

@Serializable
data class KernelNode(
    val name: String,
    val fullPath: String,
    val properties: List<KernelProperty> = emptyList(),
    val children: MutableList<KernelNode> = mutableListOf(),
    val offset: Int = 0,
    val phandle: Long? = null
) {
    fun findProperty(propName: String): KernelProperty? {
        return properties.firstOrNull { it.name == propName }
    }

    fun findNodeByPath(path: String): KernelNode? {
        if (fullPath == path) return this
        for (child in children) {
            val found = child.findNodeByPath(path)
            if (found != null) return found
        }
        return null
    }

    fun collectAllNodes(): List<KernelNode> {
        val list = mutableListOf<KernelNode>()
        list.add(this)
        for (child in children) {
            list.addAll(child.collectAllNodes())
        }
        return list
    }

    fun collectAllProperties(): List<Pair<String, KernelProperty>> {
        val list = mutableListOf<Pair<String, KernelProperty>>()
        for (prop in properties) {
            list.add(Pair(fullPath, prop))
        }
        for (child in children) {
            list.addAll(child.collectAllProperties())
        }
        return list
    }
}
