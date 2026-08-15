package com.example.ui.analyzer.dat.engine

object DatValidator {
    enum class Status { VALID, WARNING, INVALID }
    data class Result(val status: Status, val messages: List<String>)

    fun validate(list: DatTransferList): Result {
        val msgs = mutableListOf<String>()
        var status = Status.VALID

        if (list.version !in 1..4) {
            msgs.add("Unsupported transfer list version: ${list.version}")
            status = Status.INVALID
        }
        if (list.isIncremental) {
            msgs.add("Incremental OTA detected (contains move/stash commands). Conversion to RAW requires base image (not supported).")
            if (status == Status.VALID) status = Status.WARNING
        }
        if (list.newBlocks == 0L && list.zeroBlocks == 0L) {
            msgs.add("No 'new' or 'zero' commands found. The resulting image will be empty.")
            status = Status.INVALID
        }
        
        // Check for overlaps in NEW commands
        val allRanges = list.commands.filterIsInstance<DatCommand.New>()
            .flatMap { it.blockSet.ranges }
            .sortedBy { it.start }
        
        var overlap = false
        for (i in 0 until allRanges.size - 1) {
            if (allRanges[i].end > allRanges[i+1].start) {
                overlap = true
                break
            }
        }
        
        if (overlap) {
            msgs.add("Overlapping blocks detected in 'new' commands. The transfer list may be corrupted.")
            status = Status.INVALID
        }
        
        // Check if total blocks loosely match what we're going to write
        val maxBlock = allRanges.maxOfOrNull { it.end } ?: 0L
        if (maxBlock > list.totalBlocks) {
            msgs.add("Found block range exceeding total blocks declared in header ($maxBlock > ${list.totalBlocks}).")
            status = Status.INVALID
        }

        if (msgs.isEmpty()) {
            msgs.add("Transfer list is fully valid and ready for conversion.")
        }

        return Result(status, msgs)
    }
}
