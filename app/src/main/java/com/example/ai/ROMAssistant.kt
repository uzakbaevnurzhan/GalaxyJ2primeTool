package com.example.ai

import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel

class ROMAssistant {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-pro",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun analyzeLog(logData: String, deviceContext: String): String {
        val prompt = """
            You are J2 Prime ROM Studio AI Assistant. 
            Analyze the following log data for the device $deviceContext.
            
            1. What happened?
            2. At what stage is the problem?
            3. What data confirms this?
            4. What to check next?
            5. What files are potentially related?
            6. What NOT to change without additional information.
            
            Log Data:
            $logData
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Failed to generate analysis."
        } catch (e: Exception) {
            "AI Analysis failed: ${e.message}"
        }
    }
}
