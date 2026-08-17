package com.example.data.manager

import android.content.Context
import android.net.Uri
import com.example.data.model.DiffItem
import com.example.data.model.DiffStatus
import com.example.data.model.RomDeepCompareResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object RomDeepCompareEngine {

    data class ZipEntryMeta(
        val name: String,
        val size: Long,
        val crc: Long
    )

    suspend fun compareRomZips(
        context: Context,
        uriA: Uri,
        nameA: String,
        uriB: Uri,
        nameB: String,
        onProgress: suspend (stage: String, progress: Float) -> Unit
    ): RomDeepCompareResult = withContext(Dispatchers.IO) {
        onProgress("Indexing entries from ROM A ($nameA)...", 0.1f)
        val entriesA = mutableMapOf<String, ZipEntryMeta>()
        context.contentResolver.openInputStream(uriA)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entriesA[entry.name] = ZipEntryMeta(entry.name, entry.size, entry.crc)
                    }
                    entry = zip.nextEntry
                }
            }
        }

        onProgress("Indexing entries from ROM B ($nameB)...", 0.4f)
        val entriesB = mutableMapOf<String, ZipEntryMeta>()
        context.contentResolver.openInputStream(uriB)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entriesB[entry.name] = ZipEntryMeta(entry.name, entry.size, entry.crc)
                    }
                    entry = zip.nextEntry
                }
            }
        }

        onProgress("Performing deep diff across partitions and subsystems...", 0.7f)
        val allKeys = (entriesA.keys + entriesB.keys).sorted()

        val allDiffs = mutableListOf<DiffItem>()
        var added = 0
        var removed = 0
        var modified = 0
        var same = 0

        for (key in allKeys) {
            val itemA = entriesA[key]
            val itemB = entriesB[key]
            when {
                itemA == null && itemB != null -> {
                    added++
                    allDiffs.add(DiffItem(key, null, "${itemB.size} B (CRC: ${itemB.crc})", DiffStatus.ADDED))
                }
                itemA != null && itemB == null -> {
                    removed++
                    allDiffs.add(DiffItem(key, "${itemA.size} B (CRC: ${itemA.crc})", null, DiffStatus.REMOVED))
                }
                itemA != null && itemB != null -> {
                    if (itemA.crc != itemB.crc || (itemA.crc == 0L && itemA.size != itemB.size)) {
                        modified++
                        allDiffs.add(DiffItem(key, "${itemA.size} B", "${itemB.size} B", DiffStatus.MODIFIED))
                    } else {
                        same++
                        allDiffs.add(DiffItem(key, "${itemA.size} B", "${itemB.size} B", DiffStatus.SAME))
                    }
                }
            }
        }

        onProgress("Categorizing subsystem components...", 0.9f)
        val propDiffs = allDiffs.filter { it.key.contains("build.prop") || it.key.endsWith(".prop") }
        val halDiffs = allDiffs.filter { it.key.contains("/hw/") || it.key.contains("manifest.xml") || it.key.contains("android.hardware.") }
        val rilDiffs = allDiffs.filter { it.key.contains("ril") || it.key.contains("telephony") || it.key.contains("libril") || it.key.contains("sec-ril") }
        val selinuxDiffs = allDiffs.filter { it.key.contains("sepolicy") || it.key.contains("file_contexts") || it.key.contains("plat_sepolicy") }
        val initDiffs = allDiffs.filter { it.key.contains("init.") || it.key.endsWith(".rc") || it.key.contains("ueventd") }
        val partitionDiffs = allDiffs.filter { it.key.endsWith(".img") || it.key.endsWith(".dat") || it.key.endsWith(".br") || it.key.endsWith(".bin") }

        onProgress("Comparison completed!", 1.0f)

        RomDeepCompareResult(
            romAName = nameA,
            romBName = nameB,
            timestamp = System.currentTimeMillis(),
            files = allDiffs,
            properties = propDiffs,
            halServices = halDiffs,
            rilFeatures = rilDiffs,
            selinuxPolicies = selinuxDiffs,
            initScripts = initDiffs,
            partitions = partitionDiffs,
            addedCount = added,
            removedCount = removed,
            modifiedCount = modified,
            sameCount = same
        )
    }
}
