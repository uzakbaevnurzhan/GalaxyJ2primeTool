package com.example.ui.analyzer.kernel.studio.analyzer

import java.nio.ByteBuffer
import java.nio.ByteOrder

object KernelArchitectureDetector {

    fun detect(
        bytes: ByteArray,
        strings: List<String> = emptyList(),
        headerArch: String = "unknown"
    ): Pair<String, String?> {
        val archVotes = mutableMapOf<String, Int>()

        // 1. Direct header evidence
        if (headerArch != "unknown") {
            archVotes[headerArch] = archVotes.getOrDefault(headerArch, 0) + 10
        }

        // 2. Binary check
        if (bytes.size >= 64) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            // ARM64 magic at 56
            if (buf.getInt(56) == 0x644d5241) {
                archVotes["ARM64"] = archVotes.getOrDefault("ARM64", 0) + 15
            }
        }
        if (bytes.size >= 40) {
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            // ARM32 zImage magic at 36
            if (buf.getInt(36) == 0x016f2818) {
                archVotes["ARM32"] = archVotes.getOrDefault("ARM32", 0) + 15
            }
        }

        // 3. String evidence
        var arm64StringHits = 0
        var arm32StringHits = 0
        var x86StringHits = 0
        var x86_64StringHits = 0

        for (s in strings) {
            if (s.contains("aarch64", ignoreCase = true) || s.contains("arch/arm64", ignoreCase = true)) {
                arm64StringHits++
            }
            if (s.contains("arm-linux-androideabi", ignoreCase = true) ||
                s.contains("arch/arm/", ignoreCase = true) ||
                s.contains("armv7-a", ignoreCase = true) ||
                s.contains("armv7l", ignoreCase = true)
            ) {
                arm32StringHits++
            }
            if (s.contains("x86_64", ignoreCase = true) || s.contains("arch/x86_64", ignoreCase = true)) {
                x86_64StringHits++
            }
            if (s.contains("i386", ignoreCase = true) || s.contains("arch/i386", ignoreCase = true) || s.contains("i686", ignoreCase = true)) {
                x86StringHits++
            }
        }

        if (arm64StringHits > 0) archVotes["ARM64"] = archVotes.getOrDefault("ARM64", 0) + arm64StringHits.coerceAtMost(5)
        if (arm32StringHits > 0) archVotes["ARM32"] = archVotes.getOrDefault("ARM32", 0) + arm32StringHits.coerceAtMost(5)
        if (x86_64StringHits > 0) archVotes["x86_64"] = archVotes.getOrDefault("x86_64", 0) + x86_64StringHits.coerceAtMost(5)
        if (x86StringHits > 0) archVotes["x86"] = archVotes.getOrDefault("x86", 0) + x86StringHits.coerceAtMost(5)

        // Cross-check for conflict
        val arm64Score = archVotes["ARM64"] ?: 0
        val arm32Score = archVotes["ARM32"] ?: 0
        val x86_64Score = archVotes["x86_64"] ?: 0
        val x86Score = archVotes["x86"] ?: 0

        val topScores = listOf(
            "ARM64" to arm64Score,
            "ARM32" to arm32Score,
            "x86_64" to x86_64Score,
            "x86" to x86Score
        ).sortedByDescending { it.second }

        if (topScores[0].second == 0) {
            return Pair("unknown", null)
        }

        // If strong conflict between headers and strings (e.g. Header ARM64 but strong ARM32 evidence or vice versa with close scores)
        if (topScores[0].second >= 10 && topScores[1].second >= 10 && (topScores[0].second - topScores[1].second < 5)) {
            return Pair("ARCHITECTURE_CONFLICT", "Contradictory evidence: ${topScores[0].first} (score ${topScores[0].second}) vs ${topScores[1].first} (score ${topScores[1].second})")
        }

        return Pair(topScores[0].first, null)
    }
}
