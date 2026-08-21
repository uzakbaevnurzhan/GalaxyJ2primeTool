sed -i 's/private val _reduceMotion = MutableStateFlow(false)/private val _reduceMotion = MutableStateFlow(false)\n    private val _performanceMode = MutableStateFlow("High (89-100%)")\n    val performanceMode: StateFlow<String> = _performanceMode.asStateFlow()\n    private val _maxFpsEnabled = MutableStateFlow(true)\n    val maxFpsEnabled: StateFlow<Boolean> = _maxFpsEnabled.asStateFlow()\n    private val _fastAnimations = MutableStateFlow(true)\n    val fastAnimations: StateFlow<Boolean> = _fastAnimations.asStateFlow()/g' app/src/main/java/com/example/data/manager/ThemePreferences.kt
sed -i 's/val KEY_REDUCE_MOTION = "reduce_motion"/val KEY_REDUCE_MOTION = "reduce_motion"\n    private const val KEY_PERFORMANCE_MODE = "performance_mode"\n    private const val KEY_MAX_FPS = "max_fps"\n    private const val KEY_FAST_ANIMATIONS = "fast_animations"/g' app/src/main/java/com/example/data/manager/ThemePreferences.kt
sed -i 's/_reduceMotion.value = prefs?.getBoolean(KEY_REDUCE_MOTION, false) ?: false/_reduceMotion.value = prefs?.getBoolean(KEY_REDUCE_MOTION, false) ?: false\n            _performanceMode.value = prefs?.getString(KEY_PERFORMANCE_MODE, "High (89-100%)") ?: "High (89-100%)"\n            _maxFpsEnabled.value = prefs?.getBoolean(KEY_MAX_FPS, true) ?: true\n            _fastAnimations.value = prefs?.getBoolean(KEY_FAST_ANIMATIONS, true) ?: true/g' app/src/main/java/com/example/data/manager/ThemePreferences.kt
cat << 'INNER_EOF' >> app/src/main/java/com/example/data/manager/ThemePreferences.kt

    fun setPerformanceMode(mode: String) {
        _performanceMode.value = mode
        prefs?.edit()?.putString(KEY_PERFORMANCE_MODE, mode)?.apply()
    }
    fun setMaxFpsEnabled(enabled: Boolean) {
        _maxFpsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_MAX_FPS, enabled)?.apply()
    }
    fun setFastAnimations(enabled: Boolean) {
        _fastAnimations.value = enabled
        prefs?.edit()?.putBoolean(KEY_FAST_ANIMATIONS, enabled)?.apply()
    }
}
INNER_EOF
sed -i 's/^}$//g' app/src/main/java/com/example/data/manager/ThemePreferences.kt
