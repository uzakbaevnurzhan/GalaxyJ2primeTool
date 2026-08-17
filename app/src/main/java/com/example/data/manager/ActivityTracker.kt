package com.example.data.manager

import com.example.data.model.RecentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

object ActivityTracker {
    private val _activities = MutableStateFlow<List<RecentActivity>>(emptyList())
    val activities: StateFlow<List<RecentActivity>> = _activities.asStateFlow()

    fun recordActivity(title: String, description: String, actionType: String, relatedId: String? = null) {
        val item = RecentActivity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            actionType = actionType,
            timestamp = System.currentTimeMillis(),
            relatedId = relatedId
        )
        _activities.update { current ->
            (listOf(item) + current).take(50)
        }
    }

    fun clearActivities() {
        _activities.value = emptyList()
    }
}
