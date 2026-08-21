cat << 'MAIN_EOF' > app/src/main/java/com/example/MainActivity.kt
package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.data.manager.ThemePreferences
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    lifecycleScope.launch {
        ThemePreferences.maxFpsEnabled.collect { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    val maxMode = window.windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate }
                    preferredDisplayModeId = maxMode?.modeId ?: 0
                }
            } else if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = 0
                }
            }
        }
    }

    setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }
  }
}
MAIN_EOF
