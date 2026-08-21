sed -i '/fun setPerformanceMode(mode: String) {/,/^    }/c\
    fun setPerformanceMode(mode: String) {\
        _performanceMode.value = mode\
        prefs?.edit()?.putString(KEY_PERFORMANCE_MODE, mode)?.apply()\
        Thread {\
            try {\
                if (mode.contains("Low")) {\
                    com.example.utils.RootShell.executeCommand("echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")\
                } else if (mode.contains("Medium")) {\
                    com.example.utils.RootShell.executeCommand("echo interactive > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")\
                } else {\
                    com.example.utils.RootShell.executeCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")\
                }\
            } catch (e: Exception) {}\
        }.start()\
    }' app/src/main/java/com/example/data/manager/ThemePreferences.kt

sed -i '/fun setFastAnimations(enabled: Boolean) {/,/^    }/c\
    fun setFastAnimations(enabled: Boolean) {\
        _fastAnimations.value = enabled\
        prefs?.edit()?.putBoolean(KEY_FAST_ANIMATIONS, enabled)?.apply()\
        Thread {\
            try {\
                val scale = if (enabled) "0.5" else "1.0"\
                com.example.utils.RootShell.executeCommand("settings put global window_animation_scale $scale")\
                com.example.utils.RootShell.executeCommand("settings put global transition_animation_scale $scale")\
                com.example.utils.RootShell.executeCommand("settings put global animator_duration_scale $scale")\
            } catch (e: Exception) {}\
        }.start()\
    }' app/src/main/java/com/example/data/manager/ThemePreferences.kt
