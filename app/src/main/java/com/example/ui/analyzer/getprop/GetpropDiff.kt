package com.example.ui.analyzer.getprop

enum class DiffStatus(val displayName: String) {
    ADDED("Added"),
    REMOVED("Removed"),
    CHANGED("Changed"),
    UNCHANGED("Unchanged"),
    CONFLICT("Conflict")
}

data class DiffEntry(
    val key: String,
    val status: DiffStatus,
    val valueA: String?,
    val valueB: String?,
    val sourceA: String?,
    val sourceB: String?,
    val category: GetpropCategory = GetpropCategory.categorize(key)
)

data class GetpropDiffResult(
    val snapshotAName: String,
    val snapshotBName: String,
    val addedCount: Int,
    val removedCount: Int,
    val changedCount: Int,
    val unchangedCount: Int,
    val conflictCount: Int,
    val entries: List<DiffEntry>,
    val categoryChangeCounts: Map<GetpropCategory, Int>
) {
    val totalDifferences: Int get() = addedCount + removedCount + changedCount + conflictCount
}

object GetpropDiffCalculator {
    fun compare(snapshotA: GetpropSnapshot, snapshotB: GetpropSnapshot): GetpropDiffResult {
        val mapA = snapshotA.properties
        val mapB = snapshotB.properties
        val allKeys = (mapA.keys + mapB.keys).sorted()

        val diffEntries = mutableListOf<DiffEntry>()
        var added = 0
        var removed = 0
        var changed = 0
        var unchanged = 0
        var conflicts = 0
        val categoryChanges = mutableMapOf<GetpropCategory, Int>()

        for (key in allKeys) {
            val entryA = mapA[key]
            val entryB = mapB[key]

            when {
                entryA == null && entryB != null -> {
                    added++
                    val category = entryB.category
                    categoryChanges[category] = (categoryChanges[category] ?: 0) + 1
                    diffEntries.add(
                        DiffEntry(
                            key = key,
                            status = DiffStatus.ADDED,
                            valueA = null,
                            valueB = entryB.value,
                            sourceA = null,
                            sourceB = entryB.source,
                            category = category
                        )
                    )
                }
                entryA != null && entryB == null -> {
                    removed++
                    val category = entryA.category
                    categoryChanges[category] = (categoryChanges[category] ?: 0) + 1
                    diffEntries.add(
                        DiffEntry(
                            key = key,
                            status = DiffStatus.REMOVED,
                            valueA = entryA.value,
                            valueB = null,
                            sourceA = entryA.source,
                            sourceB = null,
                            category = category
                        )
                    )
                }
                entryA != null && entryB != null -> {
                    val category = entryA.category
                    if (entryA.value == entryB.value) {
                        unchanged++
                        diffEntries.add(
                            DiffEntry(
                                key = key,
                                status = DiffStatus.UNCHANGED,
                                valueA = entryA.value,
                                valueB = entryB.value,
                                sourceA = entryA.source,
                                sourceB = entryB.source,
                                category = category
                            )
                        )
                    } else {
                        // Check if internal conflict in either snapshot
                        val isConflict = entryA.conflictStatus == ConflictStatus.CONFLICT_VALUE_MISMATCH ||
                                entryB.conflictStatus == ConflictStatus.CONFLICT_VALUE_MISMATCH

                        if (isConflict) {
                            conflicts++
                        } else {
                            changed++
                        }
                        categoryChanges[category] = (categoryChanges[category] ?: 0) + 1

                        diffEntries.add(
                            DiffEntry(
                                key = key,
                                status = if (isConflict) DiffStatus.CONFLICT else DiffStatus.CHANGED,
                                valueA = entryA.value,
                                valueB = entryB.value,
                                sourceA = entryA.source,
                                sourceB = entryB.source,
                                category = category
                            )
                        )
                    }
                }
            }
        }

        return GetpropDiffResult(
            snapshotAName = snapshotA.name,
            snapshotBName = snapshotB.name,
            addedCount = added,
            removedCount = removed,
            changedCount = changed,
            unchangedCount = unchanged,
            conflictCount = conflicts,
            entries = diffEntries,
            categoryChangeCounts = categoryChanges
        )
    }
}
