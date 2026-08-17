package com.example.ui.studio.formats

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream

object CompressionHandler {
    
    enum class CompressionType {
        GZIP, LZ4, XZ, NONE
    }

    fun detectCompression(file: File): CompressionType {
        if (!file.exists() || file.length() < 6) return CompressionType.NONE
        
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(6)
            raf.read(magic)
            
            // GZIP: 1F 8B
            if (magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()) return CompressionType.GZIP
            
            // LZ4: 04 22 4D 18
            if (magic[0] == 0x04.toByte() && magic[1] == 0x22.toByte() && magic[2] == 0x4D.toByte() && magic[3] == 0x18.toByte()) return CompressionType.LZ4
            
            // XZ: FD 37 7A 58 5A 00
            if (magic[0] == 0xFD.toByte() && magic[1] == 0x37.toByte() && magic[2] == 0x7A.toByte() && magic[3] == 0x58.toByte() && magic[4] == 0x5A.toByte() && magic[5] == 0x00.toByte()) return CompressionType.XZ
        }
        return CompressionType.NONE
    }

    fun decompress(inputFile: File, outputFile: File): Boolean {
        val type = detectCompression(inputFile)
        return try {
            when (type) {
                CompressionType.GZIP -> {
                    GZIPInputStream(FileInputStream(inputFile)).use { gis ->
                        FileOutputStream(outputFile).use { fos ->
                            gis.copyTo(fos)
                        }
                    }
                    true
                }
                CompressionType.LZ4 -> {
                    // Placeholder for LZ4, needs external lib or custom implementation
                    false
                }
                CompressionType.XZ -> {
                    // Placeholder for XZ
                    false
                }
                CompressionType.NONE -> {
                    inputFile.copyTo(outputFile, overwrite = true)
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
