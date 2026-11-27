package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalSettingsState
import com.example.core.SathiPreferences
import com.example.core.SathiThemeMode
import com.example.overlay.SathiFloatingService
import com.example.overlay.SathiScreenReaderService
import com.example.overlay.SathiWakeWordService
import com.example.ui.SathiAppNavigation
import com.example.ui.theme.SathiTheme

class MainActivity : ComponentActivity() {
    private var wakeTriggerTick by mutableIntStateOf(0)
    private var autoVirtualWindowEnabled: Boolean = true
    private var skipAutoWindowOnce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(SathiWakeWordService.EXTRA_WAKE_TRIGGERED, false) == true) {
            wakeTriggerTick++
        }

        enableEdgeToEdge()
        setContent {
            val prefs = remember { SathiPreferences(applicationContext) }
            val settings by prefs.settingsFlow.collectAsStateWithLifecycle(initialValue = LocalSettingsState())
            LaunchedEffect(settings.autoVirtualWindowEnabled) {
                autoVirtualWindowEnabled = settings.autoVirtualWindowEnabled
            }
            val isNightTheme = when (settings.themeMode) {
                SathiThemeMode.DAY -> false
                SathiThemeMode.NIGHT -> true
                SathiThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val effectiveDark = isNightTheme || settings.colorTheme.isDarkPalette

            val keyguardManager = remember {
                getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            }
            val deviceHasLock = remember { keyguardManager?.isDeviceSecure == true }
            var isAppUnlocked by rememberSaveable { mutableStateOf(false) }

            val unlockLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { res ->
                if (res.resultCode == RESULT_OK) {
                    isAppUnlocked = true
                }
            }

            LaunchedEffect(settings.appLockEnabled) {
                if (settings.appLockEnabled && deviceHasLock && !isAppUnlocked) {
                    @Suppress("DEPRECATION")
                    val confirmIntent = keyguardManager?.createConfirmDeviceCredentialIntent(
                        "Sathi AI",
                        if (settings.preferredLanguageFallback == "bn") "অ্যাপ খুলতে ফোনের পিন বা বায়োমেট্রিক দিন" else "Unlock Sathi AI with your device lock"
                    )
                    if (confirmIntent != null) {
                        skipAutoWindowOnce = true
                        runCatching { unlockLauncher.launch(confirmIntent) }
                    } else {
                        isAppUnlocked = true
                    }
                }
            }

            SathiTheme(darkTheme = effectiveDark, colorTheme = settings.colorTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (settings.appLockEnabled && deviceHasLock && !isAppUnlocked) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "App Locked",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (settings.preferredLanguageFallback == "bn") "Sathi AI সুরক্ষিত আছে" else "Sathi AI is Locked",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (settings.preferredLanguageFallback == "bn") {
                                    "বস, ভেতরে প্রবেশ করতে ফোনের পিন, প্যাটার্ন বা ফিঙ্গারপ্রিন্ট দিন।"
                                } else {
                                    "Boss, verify with your device PIN, pattern, or fingerprint to continue."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    @Suppress("DEPRECATION")
                                    val confirmIntent = keyguardManager?.createConfirmDeviceCredentialIntent(
                                        "Sathi AI",
                                        "Unlock Sathi AI"
                                    )
                                    if (confirmIntent != null) {
                                        runCatching { unlockLauncher.launch(confirmIntent) }
                                    } else {
                                        isAppUnlocked = true
                                    }
                                },
                                modifier = Modifier.testTag("unlock_app_button")
                            ) {
                                Text(if (settings.preferredLanguageFallback == "bn") "আনলক করুন" else "Unlock")
                            }
                        }
                    } else {
                        SathiAppNavigation(
                            wakeTriggerTick = wakeTriggerTick,
                            isDarkTheme = effectiveDark,
                            colorTheme = settings.colorTheme
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        skipAutoWindowOnce = false
        if (SathiFloatingService.isRunning.value) {
            runCatching {
                startService(
                    Intent(this, SathiFloatingService::class.java).apply {
                        action = SathiFloatingService.ACTION_DOCK_ON_APP_OPEN
                    }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeAutoOpenVirtualWindow()
    }

    override fun onStop() {
        super.onStop()
        maybeAutoOpenVirtualWindow()
    }

    private fun maybeAutoOpenVirtualWindow() {
        if (isChangingConfigurations || isFinishing || skipAutoWindowOnce) return
        if (!autoVirtualWindowEnabled) return
        if (!Settings.canDrawOverlays(this)) return
        if (SathiScreenReaderService.isGameModePaused.value) return
        val intent = Intent(this, SathiFloatingService::class.java).apply {
            action = SathiFloatingService.ACTION_SHOW_VIRTUAL_WINDOW
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
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
