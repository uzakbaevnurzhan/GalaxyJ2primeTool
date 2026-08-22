package com.example.data.manager

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}



object ThemePreferences {
    private const val PREFS_NAME = "j2_prime_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
    private const val KEY_ASK_BEFORE_MODIFY = "ask_before_modify"
    private const val KEY_MAX_ARCHIVE_SIZE = "max_archive_size"
    private const val KEY_CONCURRENT_TASKS = "concurrent_tasks"
    private const val KEY_LARGE_FILE_THRESHOLD = "large_file_threshold"
    private const val KEY_LOG_CACHE_LINES = "log_cache_lines"
    private const val KEY_BACKGROUND_SCAN = "background_scan"
    private const val KEY_MEMORY_MODE = "memory_mode"
    private const val KEY_UI_DENSITY = "ui_density"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_PERFORMANCE_MODE = "performance_mode"
    private const val KEY_MAX_FPS = "max_fps"
    private const val KEY_FAST_ANIMATIONS = "fast_animations"

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _autoUpdateCheck = MutableStateFlow(false)
    val autoUpdateCheck: StateFlow<Boolean> = _autoUpdateCheck.asStateFlow()

    private val _askBeforeModify = MutableStateFlow(true)
    val askBeforeModify: StateFlow<Boolean> = _askBeforeModify.asStateFlow()

    private val _maxArchiveSize = MutableStateFlow(2048)
    val maxArchiveSize: StateFlow<Int> = _maxArchiveSize.asStateFlow()

    private val _concurrentTasks = MutableStateFlow(2)
    val concurrentTasks: StateFlow<Int> = _concurrentTasks.asStateFlow()

    private val _largeFileThreshold = MutableStateFlow(100)
    val largeFileThreshold: StateFlow<Int> = _largeFileThreshold.asStateFlow()

    private val _logCacheLines = MutableStateFlow(5000)
    val logCacheLines: StateFlow<Int> = _logCacheLines.asStateFlow()

    private val _backgroundScan = MutableStateFlow(true)
    val backgroundScan: StateFlow<Boolean> = _backgroundScan.asStateFlow()

    private val _memoryMode = MutableStateFlow("Balanced")
    val memoryMode: StateFlow<String> = _memoryMode.asStateFlow()

    private val _uiDensity = MutableStateFlow("Normal")
    val uiDensity: StateFlow<String> = _uiDensity.asStateFlow()

    private val _reduceMotion = MutableStateFlow(false)
    private val _performanceMode = MutableStateFlow("Maximum Power (100% Unlocked)")
    val performanceMode: StateFlow<String> = _performanceMode.asStateFlow()
    private val _maxFpsEnabled = MutableStateFlow(true)
    val maxFpsEnabled: StateFlow<Boolean> = _maxFpsEnabled.asStateFlow()
    private val _fastAnimations = MutableStateFlow(true)
    val fastAnimations: StateFlow<Boolean> = _fastAnimations.asStateFlow()
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeStr = prefs?.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
            _themeMode.value = try {
                ThemeMode.valueOf(modeStr)
            } catch (e: Exception) {
                ThemeMode.DARK
            }
            _dynamicColor.value = prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
            _autoUpdateCheck.value = prefs?.getBoolean(KEY_AUTO_UPDATE_CHECK, false) ?: false
            _askBeforeModify.value = prefs?.getBoolean(KEY_ASK_BEFORE_MODIFY, true) ?: true
            _maxArchiveSize.value = prefs?.getInt(KEY_MAX_ARCHIVE_SIZE, 4096) ?: 4096
            _concurrentTasks.value = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
            _largeFileThreshold.value = prefs?.getInt(KEY_LARGE_FILE_THRESHOLD, 250) ?: 250
            _logCacheLines.value = prefs?.getInt(KEY_LOG_CACHE_LINES, 10000) ?: 10000
            _backgroundScan.value = prefs?.getBoolean(KEY_BACKGROUND_SCAN, true) ?: true
            _memoryMode.value = "Maximum Performance (High Throughput)"
            _uiDensity.value = prefs?.getString(KEY_UI_DENSITY, "Normal") ?: "Normal"
            _reduceMotion.value = false
            _performanceMode.value = "Maximum Power (100% Unlocked)"
            _maxFpsEnabled.value = true
            _fastAnimations.value = true

            // Automatically optimize device to 100% maximum hardware performance
            applyMaximumPerformanceMode()
        }
    }

    /**
     * Applies full CPU clock frequencies, onlines all cores, and enables hardware acceleration
     */
    fun applyMaximumPerformanceMode() {
        Thread {
            try {
                // Online all CPU cores
                for (i in 0..7) {
                    com.example.utils.RootShell.executeCommand("echo 1 > /sys/devices/system/cpu/cpu$i/online")
                    com.example.utils.RootShell.executeCommand("echo performance > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
                }
                // Global scaling governor override
                com.example.utils.RootShell.executeCommand("echo performance > /sys/devices/system/cpu/cpufreq/scaling_governor")
                // Accelerate window and animator scales for instant UI feedback
                com.example.utils.RootShell.executeCommand("settings put global window_animation_scale 0.5")
                com.example.utils.RootShell.executeCommand("settings put global transition_animation_scale 0.5")
                com.example.utils.RootShell.executeCommand("settings put global animator_duration_scale 0.5")
            } catch (_: Exception) {}
        }.start()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    fun setAutoUpdateCheck(enabled: Boolean) {
        _autoUpdateCheck.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_UPDATE_CHECK, enabled)?.apply()
    }

    fun setAskBeforeModify(enabled: Boolean) {
        _askBeforeModify.value = enabled
        prefs?.edit()?.putBoolean(KEY_ASK_BEFORE_MODIFY, enabled)?.apply()
    }

    fun setMaxArchiveSize(sizeMb: Int) {
        _maxArchiveSize.value = sizeMb
        prefs?.edit()?.putInt(KEY_MAX_ARCHIVE_SIZE, sizeMb)?.apply()
    }

    fun setConcurrentTasks(count: Int) {
        _concurrentTasks.value = count
        prefs?.edit()?.putInt(KEY_CONCURRENT_TASKS, count)?.apply()
    }

    fun setLargeFileThreshold(thresholdMb: Int) {
        _largeFileThreshold.value = thresholdMb
        prefs?.edit()?.putInt(KEY_LARGE_FILE_THRESHOLD, thresholdMb)?.apply()
    }

    fun setLogCacheLines(lines: Int) {
        _logCacheLines.value = lines
        prefs?.edit()?.putInt(KEY_LOG_CACHE_LINES, lines)?.apply()
    }

    fun setBackgroundScan(enabled: Boolean) {
        _backgroundScan.value = enabled
        prefs?.edit()?.putBoolean(KEY_BACKGROUND_SCAN, enabled)?.apply()
    }

    fun setMemoryMode(mode: String) {
        _memoryMode.value = mode
        prefs?.edit()?.putString(KEY_MEMORY_MODE, mode)?.apply()
    }

    fun setUiDensity(density: String) {
        _uiDensity.value = density
        prefs?.edit()?.putString(KEY_UI_DENSITY, density)?.apply()
    }

    fun setReduceMotion(enabled: Boolean) {
        _reduceMotion.value = enabled
        prefs?.edit()?.putBoolean(KEY_REDUCE_MOTION, enabled)?.apply()
    }


    fun setPerformanceMode(mode: String) {
        _performanceMode.value = mode
        prefs?.edit()?.putString(KEY_PERFORMANCE_MODE, mode)?.apply()
        Thread {
            try {
                if (mode.contains("Low")) {
                    com.example.utils.RootShell.executeCommand("echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                } else if (mode.contains("Medium")) {
                    com.example.utils.RootShell.executeCommand("echo interactive > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                } else {
                    com.example.utils.RootShell.executeCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                }
            } catch (e: Exception) {}
        }.start()
    }
    fun setMaxFpsEnabled(enabled: Boolean) {
        _maxFpsEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_MAX_FPS, enabled)?.apply()
    }
    fun setFastAnimations(enabled: Boolean) {
        _fastAnimations.value = enabled
        prefs?.edit()?.putBoolean(KEY_FAST_ANIMATIONS, enabled)?.apply()
        Thread {
            try {
                val scale = if (enabled) "0.5" else "1.0"
                com.example.utils.RootShell.executeCommand("settings put global window_animation_scale $scale")
                com.example.utils.RootShell.executeCommand("settings put global transition_animation_scale $scale")
                com.example.utils.RootShell.executeCommand("settings put global animator_duration_scale $scale")
            } catch (e: Exception) {}
        }.start()
    }

}
