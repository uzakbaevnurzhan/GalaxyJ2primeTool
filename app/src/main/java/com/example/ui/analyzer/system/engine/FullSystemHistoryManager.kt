package com.example.ui.analyzer.system.engine

import com.example.ui.analyzer.system.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object FullSystemHistoryManager {

    private val _history = MutableStateFlow<List<FullSystemAnalysisResult>>(emptyList())
    val history: StateFlow<List<FullSystemAnalysisResult>> = _history.asStateFlow()

    private val _latestResult = MutableStateFlow<FullSystemAnalysisResult?>(null)
    val latestResult: StateFlow<FullSystemAnalysisResult?> = _latestResult.asStateFlow()

    fun recordAnalysis(result: FullSystemAnalysisResult) {
        _latestResult.value = result
        _history.update { list ->
            (listOf(result) + list).take(20)
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _latestResult.value = null
    }

    fun computeRegressionDiff(
        oldSession: FullSystemAnalysisResult,
        newSession: FullSystemAnalysisResult
    ): AnalysisRegressionDiff {
        val oldErrorSignatures = oldSession.deduplicatedErrors.associateBy { "${it.subsystem}:${it.message.take(60)}" }
        val newErrorSignatures = newSession.deduplicatedErrors.associateBy { "${it.subsystem}:${it.message.take(60)}" }

        val fixedErrors = oldErrorSignatures.filterKeys { !newErrorSignatures.containsKey(it) }.values.toList()
        val newErrors = newErrorSignatures.filterKeys { !oldErrorSignatures.containsKey(it) }.values.toList()
        val persistentErrors = newErrorSignatures.filterKeys { oldErrorSignatures.containsKey(it) }.values.toList()

        val oldComponentMap = oldSession.halComponentMatrix.associateBy { it.componentKey }
        val newComponentMap = newSession.halComponentMatrix.associateBy { it.componentKey }

        val regressedComponents = mutableListOf<String>()
        val improvedComponents = mutableListOf<String>()

        newComponentMap.forEach { (key, newItem) ->
            val oldItem = oldComponentMap[key]
            if (oldItem != null) {
                // Check if regressed
                if (oldItem.status == ComponentStatus.WORKING && (newItem.status == ComponentStatus.FAILED || newItem.status == ComponentStatus.PARTIAL)) {
                    regressedComponents.add(newItem.componentName)
                } else if ((oldItem.status == ComponentStatus.FAILED || oldItem.status == ComponentStatus.PARTIAL) && newItem.status == ComponentStatus.WORKING) {
                    improvedComponents.add(newItem.componentName)
                }
            }
        }

        return AnalysisRegressionDiff(
            oldSessionId = oldSession.id,
            newSessionId = newSession.id,
            oldTimestamp = oldSession.timestamp,
            newTimestamp = newSession.timestamp,
            fixedErrors = fixedErrors,
            newErrors = newErrors,
            persistentErrors = persistentErrors,
            regressedComponents = regressedComponents,
            improvedComponents = improvedComponents,
            healthChangedFrom = oldSession.healthStatus,
            healthChangedTo = newSession.healthStatus
        )
    }
}
