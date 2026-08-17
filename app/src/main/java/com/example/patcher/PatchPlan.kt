package com.example.patcher

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PatchPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val projectId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val operations: List<PatchOperation> = emptyList()
) {
    val totalOperations: Int get() = operations.size
    val enabledOperations: List<PatchOperation> get() = operations.filter { it.isEnabled }
    
    val overallRisk: PatchRisk
        get() = operations.filter { it.isEnabled }
            .map { it.risk }
            .maxByOrNull { it.level } ?: PatchRisk.LOW

    fun addOperation(op: PatchOperation): PatchPlan {
        return copy(operations = operations + op)
    }

    fun removeOperation(opId: String): PatchPlan {
        return copy(operations = operations.filterNot { it.id == opId })
    }

    fun updateOperation(op: PatchOperation): PatchPlan {
        return copy(operations = operations.map { if (it.id == op.id) op else it })
    }

    fun moveOperation(fromIndex: Int, toIndex: Int): PatchPlan {
        if (fromIndex !in operations.indices || toIndex !in operations.indices) return this
        val mutable = operations.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return copy(operations = mutable)
    }

    fun toggleOperation(opId: String): PatchPlan {
        return copy(operations = operations.map {
            if (it.id == opId) it.copy(isEnabled = !it.isEnabled) else it
        })
    }
}
