package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
            SathiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SathiAppNavigation(wakeTriggerTick = wakeTriggerTick)
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
