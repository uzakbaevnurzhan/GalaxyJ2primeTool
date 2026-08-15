package com.example.ui.analyzer.dat.engine

data class DatBlockRange(val start: Long, val end: Long) {
    val blocks: Long get() = end - start
    
    init {
        require(start >= 0) { "Start block cannot be negative" }
        require(end >= start) { "End block must be >= start block" }
    }
}

data class DatBlockSet(val count: Int, val ranges: List<DatBlockRange>) {
    val totalBlocks: Long get() = ranges.sumOf { it.blocks }

    companion object {
        fun parse(rangeString: String): DatBlockSet {
            val parts = rangeString.split(",").map { it.trim().toLong() }
            if (parts.isEmpty()) throw IllegalArgumentException("Empty block range")
            val count = parts[0].toInt()
            if (parts.size != count + 1) throw IllegalArgumentException("Block range count mismatch")
            val ranges = mutableListOf<DatBlockRange>()
            for (i in 1..count step 2) {
                if (i + 1 < parts.size) {
                    ranges.add(DatBlockRange(parts[i], parts[i + 1]))
                }
            }
            return DatBlockSet(count, ranges)
        }
    }
}
