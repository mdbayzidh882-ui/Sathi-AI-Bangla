package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalSettingsState
import com.example.core.SathiPreferences
import com.example.core.SathiThemeMode
import com.example.overlay.SathiWakeWordService
import com.example.ui.SathiAppNavigation
import com.example.ui.theme.SathiTheme

class MainActivity : ComponentActivity() {
    private var wakeTriggerTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(SathiWakeWordService.EXTRA_WAKE_TRIGGERED, false) == true) {
            wakeTriggerTick++
        }

        enableEdgeToEdge()
        setContent {
            val prefs = remember { SathiPreferences(applicationContext) }
            val settings by prefs.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalSettingsState())
            val isNightTheme = when (settings.themeMode) {
                SathiThemeMode.DAY -> false
                SathiThemeMode.NIGHT -> true
                SathiThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SathiTheme(darkTheme = isNightTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SathiAppNavigation(
                        wakeTriggerTick = wakeTriggerTick,
                        isDarkTheme = isNightTheme
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(SathiWakeWordService.EXTRA_WAKE_TRIGGERED, false)) {
            wakeTriggerTick++
        }
    }
}
