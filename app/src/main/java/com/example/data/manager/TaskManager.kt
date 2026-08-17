package com.example.data.manager

import com.example.data.model.TaskItem
import com.example.data.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TaskManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startTask(
        title: String,
        description: String,
        type: String,
        canCancel: Boolean = true,
        block: suspend (
            updateStage: suspend (stage: String, progress: Float) -> Unit,
            appendLog: suspend (log: String) -> Unit,
            checkCancelled: () -> Boolean
        ) -> String
    ): String {
        val taskId = UUID.randomUUID().toString()
        val newTask = TaskItem(
            id = taskId,
            title = title,
            description = description,
            type = type,
            status = TaskStatus.QUEUED,
            progress = 0f,
            currentStage = "Queued",
            startTime = System.currentTimeMillis(),
            canCancel = canCancel
        )

        _tasks.update { listOf(newTask) + it }

        val job = scope.launch(Dispatchers.IO) {
            updateTask(taskId) {
                it.copy(status = TaskStatus.PREPARING, currentStage = "Preparing environment")
            }

            try {
                updateTask(taskId) {
                    it.copy(status = TaskStatus.RUNNING, currentStage = "Running")
                }

                val summary = block(
                    { stage, progress ->
                        updateTask(taskId) {
                            it.copy(currentStage = stage, progress = progress.coerceIn(0f, 1f))
                        }
                    },
                    { log ->
                        updateTask(taskId) {
                            it.copy(logs = it.logs + log)
                        }
                    },
                    {
                        activeJobs[taskId]?.isCancelled == true
                    }
                )

                updateTask(taskId) {
                    it.copy(
                        status = TaskStatus.SUCCESS,
                        progress = 1.0f,
                        currentStage = "Completed",
                        endTime = System.currentTimeMillis(),
                        resultSummary = summary
                    )
                }
                ActivityTracker.recordActivity(title, "Completed successfully: $summary", type, taskId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateTask(taskId) {
                    it.copy(
                        status = TaskStatus.CANCELLED,
                        currentStage = "Cancelled by user",
                        endTime = System.currentTimeMillis()
                    )
                }
                ActivityTracker.recordActivity(title, "Task was cancelled", type, taskId)
            } catch (e: Throwable) {
                val errorMsg = e.message ?: e.localizedMessage ?: "Unknown error"
                updateTask(taskId) {
                    it.copy(
                        status = TaskStatus.FAILED,
                        currentStage = "Failed",
                        endTime = System.currentTimeMillis(),
                        errorDetails = errorMsg
                    )
                }
                ErrorCenterManager.recordError(
                    module = type,
                    operation = title,
                    stage = _tasks.value.find { it.id == taskId }?.currentStage ?: "Execution",
                    message = errorMsg,
                    cause = e.cause?.message,
                    evidence = e.stackTraceToString().take(500),
                    stackTrace = e.stackTraceToString(),
                    suggestedAction = "Verify input files, check permissions and retry the operation."
                )
                ActivityTracker.recordActivity(title, "Failed: $errorMsg", type, taskId)
            } finally {
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
        return taskId
    }

    fun cancelTask(taskId: String) {
        val job = activeJobs[taskId]
        if (job != null && job.isActive) {
            job.cancel()
        } else {
            updateTask(taskId) {
                if (it.status == TaskStatus.RUNNING || it.status == TaskStatus.PREPARING || it.status == TaskStatus.QUEUED) {
                    it.copy(status = TaskStatus.CANCELLED, endTime = System.currentTimeMillis())
                } else it
            }
        }
    }

    fun clearCompleted() {
        _tasks.update { list ->
            list.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.PREPARING || it.status == TaskStatus.QUEUED }
        }
    }

    private fun updateTask(taskId: String, transform: (TaskItem) -> TaskItem) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
