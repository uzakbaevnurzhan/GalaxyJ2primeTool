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
}
