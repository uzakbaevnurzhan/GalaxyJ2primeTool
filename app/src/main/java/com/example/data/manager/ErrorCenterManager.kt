package com.example.data.manager

import com.example.data.model.AppErrorLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ErrorCenterManager {
    private val _errors = MutableStateFlow<List<AppErrorLog>>(emptyList())
    val errors: StateFlow<List<AppErrorLog>> = _errors.asStateFlow()

    fun recordError(
        module: String,
        operation: String,
        stage: String,
        message: String,
        cause: String? = null,
        evidence: String? = null,
        stackTrace: String? = null,
        suggestedAction: String = "Check logs and verify system prerequisites."
    ) {
        val errorItem = AppErrorLog(
            id = UUID.randomUUID().toString(),
            module = module,
            operation = operation,
            stage = stage,
            message = message,
            cause = cause,
            evidence = evidence,
            stackTrace = stackTrace,
            suggestedAction = suggestedAction,
            timestamp = System.currentTimeMillis()
        )
        _errors.update { list ->
            (listOf(errorItem) + list).take(100)
        }
    }

    fun clearErrors() {
        _errors.value = emptyList()
    }
}
