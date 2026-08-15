package com.example.ui.analyzer.image

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

object SparseImageParser {
    const val SPARSE_HEADER_MAGIC = 0xED26FF3A.toInt()
    const val CHUNK_TYPE_RAW = 0xCAC1
    const val CHUNK_TYPE_FILL = 0xCAC2
    const val CHUNK_TYPE_DONT_CARE = 0xCAC3
    const val CHUNK_TYPE_CRC32 = 0xCAC4

    data class ChunkStats(
        val rawChunks: Int = 0,
        val fillChunks: Int = 0,
        val dontCareChunks: Int = 0,
        val crcChunks: Int = 0,
        val totalChunks: Int = 0,
        val totalBlocks: Long = 0L,
        val rawBlocks: Long = 0L,
        val fillBlocks: Long = 0L,
        val dontCareBlocks: Long = 0L
    )

    data class SparseHeader(
        val magic: Int,
        val majorVersion: Short,
        val minorVersion: Short,
        val fileHdrSz: Short,
        val chunkHdrSz: Short,
        val blkSz: Int,
        val totalBlks: Long,
        val totalChunks: Long,
        val imageChecksum: Long
    )

    fun parseHeader(channel: FileChannel): Pair<SparseHeader, ChunkStats>? {
        val originalPos = channel.position()
        try {
            if (channel.size() < 28) return null
            channel.position(0)

            val hdrBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            val read = channel.read(hdrBuf)
            if (read < 28) return null
            hdrBuf.flip()

            val magic = hdrBuf.getInt()
            if (magic != SPARSE_HEADER_MAGIC) return null

            val major = hdrBuf.getShort()
            val minor = hdrBuf.getShort()
            val fileHdrSz = hdrBuf.getShort()
            val chunkHdrSz = hdrBuf.getShort()
            val blkSz = hdrBuf.getInt()
            val totalBlks = hdrBuf.getInt().toLong() and 0xFFFFFFFFL
            val totalChunks = hdrBuf.getInt().toLong() and 0xFFFFFFFFL
            val imageChecksum = hdrBuf.getInt().toLong() and 0xFFFFFFFFL

            val header = SparseHeader(magic, major, minor, fileHdrSz, chunkHdrSz, blkSz, totalBlks, totalChunks, imageChecksum)

            // Seek to first chunk
            channel.position(fileHdrSz.toLong())

            var rawChunks = 0
            var fillChunks = 0
            var dontCareChunks = 0
            var crcChunks = 0
            var rawBlocks = 0L
            var fillBlocks = 0L
            var dontCareBlocks = 0L

            val chunkHdrBuf = ByteBuffer.allocate(chunkHdrSz.toInt()).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until totalChunks) {
                chunkHdrBuf.clear()
                val cr = channel.read(chunkHdrBuf)
                if (cr < chunkHdrSz.toInt()) break
                chunkHdrBuf.flip()

                val chunkType = chunkHdrBuf.getShort().toInt() and 0xFFFF
                val reserved = chunkHdrBuf.getShort()
                val chunkBlks = chunkHdrBuf.getInt().toLong() and 0xFFFFFFFFL
                val totalSz = chunkHdrBuf.getInt().toLong() and 0xFFFFFFFFL

                when (chunkType) {
                    CHUNK_TYPE_RAW -> {
                        rawChunks++
                        rawBlocks += chunkBlks
                        val dataSize = totalSz - chunkHdrSz
                        if (dataSize > 0) {
                            channel.position(channel.position() + dataSize)
                        }
                    }
                    CHUNK_TYPE_FILL -> {
                        fillChunks++
                        fillBlocks += chunkBlks
                        val dataSize = totalSz - chunkHdrSz
                        if (dataSize > 0) {
                            channel.position(channel.position() + dataSize)
                        }
                    }
                    CHUNK_TYPE_DONT_CARE -> {
                        dontCareChunks++
                        dontCareBlocks += chunkBlks
                    }
                    CHUNK_TYPE_CRC32 -> {
                        crcChunks++
                        val dataSize = totalSz - chunkHdrSz
                        if (dataSize > 0) {
                            channel.position(channel.position() + dataSize)
                        }
                    }
                    else -> {
                        // Unknown chunk type
                        val dataSize = totalSz - chunkHdrSz
                        if (dataSize > 0) {
                            channel.position(channel.position() + dataSize)
                        }
                    }
                }
            }

            val stats = ChunkStats(
                rawChunks = rawChunks,
                fillChunks = fillChunks,
                dontCareChunks = dontCareChunks,
                crcChunks = crcChunks,
                totalChunks = totalChunks.toInt(),
                totalBlocks = totalBlks,
                rawBlocks = rawBlocks,
                fillBlocks = fillBlocks,
                dontCareBlocks = dontCareBlocks
            )

            return Pair(header, stats)
        } catch (e: Exception) {
            return null
        } finally {
            channel.position(originalPos)
        }
    }

    /**
     * Unsparse streaming converter to raw output stream.
     * Guarantees 0-OOM streaming using reusable 64KB buffers.
     */
    fun convertSparseToRaw(
        channel: FileChannel,
        outputStream: OutputStream,
        onProgress: (bytesWritten: Long, totalRawBytes: Long, speedMbPerSec: Double) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): String {
        val originalPos = channel.position()
        val md5Digest = MessageDigest.getInstance("MD5")
        val sha256Digest = MessageDigest.getInstance("SHA-256")

        try {
            channel.position(0)
            val hdrBuf = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
            val read = channel.read(hdrBuf)
            if (read < 28) throw IllegalArgumentException("Sparse header too small")
            hdrBuf.flip()

            val magic = hdrBuf.getInt()
            if (magic != SPARSE_HEADER_MAGIC) throw IllegalArgumentException("Invalid sparse magic 0x${Integer.toHexString(magic)}")

            val major = hdrBuf.getShort()
            val minor = hdrBuf.getShort()
            val fileHdrSz = hdrBuf.getShort()
            val chunkHdrSz = hdrBuf.getShort()
            val blkSz = hdrBuf.getInt()
            val totalBlks = hdrBuf.getInt().toLong() and 0xFFFFFFFFL
            val totalChunks = hdrBuf.getInt().toLong() and 0xFFFFFFFFL

            val totalRawBytes = totalBlks * blkSz.toLong()
            channel.position(fileHdrSz.toLong())

            val chunkHdrBuf = ByteBuffer.allocate(chunkHdrSz.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            val ioBuffer = ByteArray(64 * 1024)
            val zeroBuffer = ByteArray(64 * 1024)

            var bytesWritten = 0L
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            for (i in 0 until totalChunks) {
                if (isCancelled()) break

                chunkHdrBuf.clear()
                val cr = channel.read(chunkHdrBuf)
                if (cr < chunkHdrSz.toInt()) break
                chunkHdrBuf.flip()

                val chunkType = chunkHdrBuf.getShort().toInt() and 0xFFFF
                val reserved = chunkHdrBuf.getShort()
                val chunkBlks = chunkHdrBuf.getInt().toLong() and 0xFFFFFFFFL
                val totalSz = chunkHdrBuf.getInt().toLong() and 0xFFFFFFFFL

                val chunkBytes = chunkBlks * blkSz.toLong()

                when (chunkType) {
                    CHUNK_TYPE_RAW -> {
                        var remaining = chunkBytes
                        val directBuf = ByteBuffer.wrap(ioBuffer)
                        while (remaining > 0) {
                            if (isCancelled()) break
                            val toRead = minOf(remaining, ioBuffer.size.toLong()).toInt()
                            directBuf.clear()
                            directBuf.limit(toRead)
                            val r = channel.read(directBuf)
                            if (r <= 0) break

                            outputStream.write(ioBuffer, 0, r)
                            md5Digest.update(ioBuffer, 0, r)
                            sha256Digest.update(ioBuffer, 0, r)

                            bytesWritten += r
                            bytesSinceLastTime += r
                            remaining -= r

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 300) {
                                val speed = (bytesSinceLastTime / (1024.0 * 1024.0)) / ((now - lastTime) / 1000.0)
                                onProgress(bytesWritten, totalRawBytes, speed)
                                lastTime = now
                                bytesSinceLastTime = 0L
                            }
                        }
                    }
                    CHUNK_TYPE_FILL -> {
                        val fillValBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        channel.read(fillValBuf)
                        fillValBuf.flip()
                        val fillVal = fillValBuf.getInt()

                        // Fill repeat buffer
                        val fillArray = ByteArray(4096)
                        val fb = ByteBuffer.wrap(fillArray).order(ByteOrder.LITTLE_ENDIAN)
                        for (f in 0 until 1024) fb.putInt(fillVal)

                        var remaining = chunkBytes
                        while (remaining > 0) {
                            if (isCancelled()) break
                            val toWrite = minOf(remaining, fillArray.size.toLong()).toInt()
                            outputStream.write(fillArray, 0, toWrite)
                            md5Digest.update(fillArray, 0, toWrite)
                            sha256Digest.update(fillArray, 0, toWrite)
                            bytesWritten += toWrite
                            remaining -= toWrite
                        }
                    }
                    CHUNK_TYPE_DONT_CARE -> {
                        var remaining = chunkBytes
                        while (remaining > 0) {
                            if (isCancelled()) break
                            val toWrite = minOf(remaining, zeroBuffer.size.toLong()).toInt()
                            outputStream.write(zeroBuffer, 0, toWrite)
                            md5Digest.update(zeroBuffer, 0, toWrite)
                            sha256Digest.update(zeroBuffer, 0, toWrite)
                            bytesWritten += toWrite
                            remaining -= toWrite
                        }
                    }
                    CHUNK_TYPE_CRC32 -> {
                        // Skip CRC value
                        val dataSize = totalSz - chunkHdrSz
                        if (dataSize > 0) channel.position(channel.position() + dataSize)
                    }
                }
            }

            outputStream.flush()
            val sha256Hex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            return sha256Hex
        } finally {
            channel.position(originalPos)
        }
    }
}
