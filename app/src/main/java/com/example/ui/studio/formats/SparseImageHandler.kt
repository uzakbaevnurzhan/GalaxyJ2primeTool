package com.example.ui.studio.formats

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseImageHandler {
    private const val SPARSE_HEADER_MAGIC = 0xed26ff3a.toInt()
    private const val SPARSE_HEADER_LEN = 28
    private const val CHUNK_HEADER_LEN = 12

    fun sparseToRaw(sparseFile: File, rawFile: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        if (!sparseFile.exists()) return false

        try {
            RandomAccessFile(sparseFile, "r").use { rafIn ->
                RandomAccessFile(rawFile, "rw").use { rafOut ->
                    rafIn.seek(0)
                    val headerBytes = ByteArray(SPARSE_HEADER_LEN)
                    if (rafIn.read(headerBytes) != SPARSE_HEADER_LEN) return false
                    
                    val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val magic = headerBuf.getInt()
                    if (magic != SPARSE_HEADER_MAGIC) return false
                    
                    val majorVersion = headerBuf.getShort()
                    val minorVersion = headerBuf.getShort()
                    val fileHeaderSize = headerBuf.getShort()
                    val chunkHeaderSize = headerBuf.getShort()
                    val blockSize = headerBuf.getInt()
                    val totalBlocks = headerBuf.getInt()
                    val totalChunks = headerBuf.getInt()
                    val imageChecksum = headerBuf.getInt()

                    if (fileHeaderSize.toInt() > SPARSE_HEADER_LEN) {
                        rafIn.skipBytes(fileHeaderSize - SPARSE_HEADER_LEN)
                    }

                    for (i in 0 until totalChunks) {
                        val chunkHeaderBytes = ByteArray(CHUNK_HEADER_LEN)
                        if (rafIn.read(chunkHeaderBytes) != CHUNK_HEADER_LEN) return false
                        
                        val chunkBuf = ByteBuffer.wrap(chunkHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkType = chunkBuf.getShort()
                        val reserved1 = chunkBuf.getShort()
                        val chunkBlocks = chunkBuf.getInt()
                        val totalSize = chunkBuf.getInt()

                        val chunkSize = chunkBlocks * blockSize.toLong()

                        when (chunkType.toInt()) {
                            0xCAC1 -> { // CHUNK_TYPE_RAW
                                val data = ByteArray(totalSize - CHUNK_HEADER_LEN)
                                rafIn.read(data)
                                rafOut.write(data)
                            }
                            0xCAC2 -> { // CHUNK_TYPE_FILL
                                val fillData = ByteArray(4)
                                rafIn.read(fillData)
                                val fillBuf = ByteArray(blockSize)
                                for (j in 0 until blockSize step 4) {
                                    System.arraycopy(fillData, 0, fillBuf, j, 4)
                                }
                                for (j in 0 until chunkBlocks) {
                                    rafOut.write(fillBuf)
                                }
                            }
                            0xCAC3 -> { // CHUNK_TYPE_DONT_CARE
                                rafOut.seek(rafOut.filePointer + chunkSize)
                            }
                            0xCAC4 -> { // CHUNK_TYPE_CRC32
                                rafIn.skipBytes(totalSize - CHUNK_HEADER_LEN)
                            }
                            else -> {
                                rafIn.skipBytes(totalSize - CHUNK_HEADER_LEN)
                            }
                        }
                        onProgress?.invoke(i.toFloat() / totalChunks)
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
