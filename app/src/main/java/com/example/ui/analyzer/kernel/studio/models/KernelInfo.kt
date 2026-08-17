package com.example.ui.analyzer.kernel.studio.models

import kotlinx.serialization.Serializable

@Serializable
data class KernelFormatInfo(
    val format: String, // zImage, Image, Image.gz, Image.lz4, Image.xz, Image.zst, uImage, raw, unknown
    val compression: String, // none, gzip, lz4, xz, zstd, lzma, unknown
    val offset: Long = 0L,
    val size: Long = 0L,
    val architecture: String = "unknown"
)

@Serializable
data class KernelVersionInfo(
    val fullString: String = "UNKNOWN",
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val extraVersion: String = "",
    val buildDate: String = "UNKNOWN",
    val compiler: String = "UNKNOWN", // gcc, clang, LLVM
    val compilerVersion: String = "UNKNOWN",
    val isSmp: Boolean = false,
    val isPreempt: Boolean = false,
    val hasModuleSupport: Boolean = false
)

@Serializable
data class KernelModuleInfo(
    val name: String,
    val path: String,
    val size: Long,
    val architecture: String,
    val elfType: String,
    val vermagic: String = "UNKNOWN",
    val dependencies: List<String> = emptyList(),
    val isLoaded: Boolean = false
)

@Serializable
data class KernelInfo(
    val formatInfo: KernelFormatInfo,
    val versionInfo: KernelVersionInfo,
    val architecture: String, // ARM32, ARM64, x86, x86_64, unknown, ARCHITECTURE_CONFLICT
    val rawSize: Long,
    val decompressedSize: Long,
    val detectedStringsCount: Int,
    val modules: List<KernelModuleInfo> = emptyList(),
    val notes: List<String> = emptyList()
)
