package com.example.ui.analyzer.kernel.engine

/**
 * A lightweight circular ring buffer of fixed capacity for storing recent log lines
 * without allocating large lists in memory.
 */
class RingBuffer<T>(private val capacity: Int) {
    @Suppress("UNCHECKED_CAST")
    private val buffer: Array<Any?> = arrayOfNulls(capacity)
    private var writeIndex = 0
    private var count = 0

    fun add(item: T) {
        buffer[writeIndex] = item
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) {
            count++
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> {
        val result = ArrayList<T>(count)
        val start = if (count < capacity) 0 else writeIndex
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            result.add(buffer[idx] as T)
        }
        return result
    }

    fun clear() {
        buffer.fill(null)
        writeIndex = 0
        count = 0
    }

    val size: Int
        get() = count
}
