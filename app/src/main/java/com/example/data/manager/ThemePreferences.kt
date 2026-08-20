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
            _maxArchiveSize.value = prefs?.getInt(KEY_MAX_ARCHIVE_SIZE, 2048) ?: 2048
            _concurrentTasks.value = prefs?.getInt(KEY_CONCURRENT_TASKS, 2) ?: 2
            _largeFileThreshold.value = prefs?.getInt(KEY_LARGE_FILE_THRESHOLD, 100) ?: 100
            _logCacheLines.value = prefs?.getInt(KEY_LOG_CACHE_LINES, 5000) ?: 5000
            _backgroundScan.value = prefs?.getBoolean(KEY_BACKGROUND_SCAN, true) ?: true
            _memoryMode.value = prefs?.getString(KEY_MEMORY_MODE, "Balanced") ?: "Balanced"
            _uiDensity.value = prefs?.getString(KEY_UI_DENSITY, "Normal") ?: "Normal"
            _reduceMotion.value = prefs?.getBoolean(KEY_REDUCE_MOTION, false) ?: false
        }
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
}
