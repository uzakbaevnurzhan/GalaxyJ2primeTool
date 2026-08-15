package com.example.ui.analyzer.dat.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

object DatConverter {
    suspend fun convert(
        context: Context,
        datUri: Uri,
        isBrotli: Boolean,
        transferList: DatTransferList,
        outputUri: Uri,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (transferList.isIncremental) {
            throw UnsupportedOperationException("Incremental OTAs are not supported. They require the original base system image to patch.")
        }

        val blockSize = 4096L
        
        var baseInputStream: InputStream? = null
        var dataStream: InputStream? = null
        
        try {
            baseInputStream = context.contentResolver.openInputStream(datUri)
            dataStream = if (isBrotli && baseInputStream != null) BrotliInputStream(baseInputStream) else baseInputStream
            
            if (dataStream == null) throw IllegalStateException("Could not open DAT stream")

            val pfd = context.contentResolver.openFileDescriptor(outputUri, "rw")
                ?: throw IllegalStateException("Could not open output file")
            
            pfd.use { fd ->
                val outChannel = FileOutputStream(fd.fileDescriptor).channel
                
                // Truncate or extend to exact total size
                val totalBytes = transferList.totalBlocks * blockSize
                outChannel.truncate(totalBytes)
                
                val bufferSize = 1024 * 128 // 128KB chunks
                val buffer = ByteArray(bufferSize)
                
                var totalNewBlocksProcessed = 0L
                val totalNewBlocks = transferList.newBlocks
                
                var lastProgressTime = System.currentTimeMillis()
                
                for (cmd in transferList.commands) {
                    ensureActive()
                    
                    when (cmd) {
                        is DatCommand.New -> {
                            for (range in cmd.blockSet.ranges) {
                                outChannel.position(range.start * blockSize)
                                var blocksToRead = range.blocks
                                
                                while (blocksToRead > 0) {
                                    ensureActive()
                                    val bytesToRead = (minOf(blocksToRead * blockSize, bufferSize.toLong())).toInt()
                                    var read = 0
                                    while (read < bytesToRead) {
                                        val r = dataStream.read(buffer, read, bytesToRead - read)
                                        if (r == -1) throw Exception("Unexpected EOF in DAT file")
                                        read += r
                                    }
                                    
                                    outChannel.write(ByteBuffer.wrap(buffer, 0, read))
                                    blocksToRead -= (read / blockSize)
                                    
                                    totalNewBlocksProcessed += (read / blockSize)
                                    
                                    val currentTime = System.currentTimeMillis()
                                    if (totalNewBlocks > 0 && currentTime - lastProgressTime > 200) {
                                        lastProgressTime = currentTime
                                        onProgress((totalNewBlocksProcessed.toFloat() / totalNewBlocks) * 100f, "Writing new blocks: ${totalNewBlocksProcessed}/${totalNewBlocks}")
                                    }
                                }
                            }
                        }
                        is DatCommand.Zero -> {
                            val zeroBuffer = ByteArray(bufferSize)
                            for (range in cmd.blockSet.ranges) {
                                outChannel.position(range.start * blockSize)
                                var blocksToWrite = range.blocks
                                while (blocksToWrite > 0) {
                                    ensureActive()
                                    val bytesToWrite = (minOf(blocksToWrite * blockSize, bufferSize.toLong())).toInt()
                                    outChannel.write(ByteBuffer.wrap(zeroBuffer, 0, bytesToWrite))
                                    blocksToWrite -= (bytesToWrite / blockSize)
                                }
                            }
                        }
                        else -> {
                            // Erase, Free, Unknown are ignored during direct RAW construction
                        }
                    }
                }
            }
        } finally {
            try { dataStream?.close() } catch (e: Exception) {}
            try { baseInputStream?.close() } catch (e: Exception) {}
        }
    }
}
