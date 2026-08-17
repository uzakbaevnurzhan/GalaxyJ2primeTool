package com.example.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object FavoritesManager {
    // Default favorites
    private val _favorites = MutableStateFlow<Set<String>>(
        setOf(
            "root_center",
            "rom_studio",
            "device_info",
            "rom_compare",
            "report_generator"
        )
    )
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun toggleFavorite(route: String) {
        _favorites.update { set ->
            if (set.contains(route)) {
                set - route
            } else {
                set + route
            }
        }
    }

    fun isFavorite(route: String): Boolean {
        return _favorites.value.contains(route)
    }
}
