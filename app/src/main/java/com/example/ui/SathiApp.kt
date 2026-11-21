package com.example.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.core.NetworkMonitor
import com.example.core.SathiPreferences
import com.example.data.DeviceActionExecutor
import com.example.data.OfflineCompanionEngine
import com.example.data.OfflinePackManager
import com.example.data.SathiAiService
import com.example.data.SathiDatabase
import com.example.data.SathiLocalRepository
import com.example.data.VoiceController
import com.example.data.WebSearchService
import com.example.model.AppConfig
import com.example.model.SathiLanguage
import com.example.overlay.SathiWakeWordService
import com.example.ui.theme.sathiHorizonBackgroundBrush
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

private enum class SathiDestination(
    val labelBn: String,
    val labelEn: String,
    val icon: ImageVector
) {
    VOICE("সাথী", "Sathi", Icons.Default.Mic),
    STUDIO("স্টুডিও", "Studio", Icons.Default.AutoAwesome),
    MEMORY("স্মৃতি", "Memory", Icons.Default.Psychology),
    SETTINGS("সেটিংস", "Settings", Icons.Default.Settings)
}

@Composable
fun SathiAppNavigation(wakeTriggerTick: Int = 0) {
    SathiMainShell(
        currentUserId = SathiLocalRepository.LOCAL_OWNER_UID,
        userEmail = SathiLocalRepository.OWNER_EMAIL,
        displayName = SathiLocalRepository.OWNER_DEFAULT_NAME,
        wakeTriggerTick = wakeTriggerTick
    )
}

@Composable
private fun SathiMainShell(
    currentUserId: String,
    userEmail: String,
    displayName: String,
    wakeTriggerTick: Int
) {
    var lateViewModel by remember { mutableStateOf<SathiViewModel?>(null) }

    val viewModel: SathiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = SathiDatabase.getInstance(app)
                val repo = SathiLocalRepository(db.sathiDao())
                val packManager = OfflinePackManager(app)
                val voiceController = VoiceController(app) { spoken ->
                    lateViewModel?.sendUserMessage(spoken, speakReply = true)
                }
                SathiViewModel(
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    displayName = displayName,
                    repository = repo,
                    aiService = SathiAiService(),
                    webSearchService = WebSearchService(),
                    offlineEngine = OfflineCompanionEngine(packManager),
                    offlinePackManager = packManager,
                    voiceController = voiceController,
                    deviceActions = DeviceActionExecutor(app),
                    preferences = SathiPreferences(app),
                    networkMonitor = NetworkMonitor(app)
                )
            }
        }
    )
    lateViewModel = viewModel

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val globalConfig by viewModel.globalConfig.collectAsStateWithLifecycle()
    val serviceWakeCount by SathiWakeWordService.wakeTriggerCount.collectAsStateWithLifecycle()
    val language = SathiLanguage.fromCode(profile?.preferredLanguage)

    var selectedTab by remember { mutableStateOf(SathiDestination.VOICE) }
    var showAdminPanel by remember { mutableStateOf(false) }

    LaunchedEffect(wakeTriggerTick) {
        if (wakeTriggerTick > 0) {
            selectedTab = SathiDestination.VOICE
            showAdminPanel = false
            viewModel.onWakeWordTriggered()
        }
    }

    LaunchedEffect(serviceWakeCount) {
        if (serviceWakeCount > 0) {
            selectedTab = SathiDestination.VOICE
            showAdminPanel = false
            viewModel.onWakeWordTriggered()
        }
    }

    if (showAdminPanel) {
        AdminPanelScreen(
            viewModel = viewModel,
            onBack = { showAdminPanel = false }
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(sathiHorizonBackgroundBrush())
    ) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.26f)) {
                    SathiDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = selectedTab == dest,
                            onClick = { selectedTab = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.labelEn) },
                            label = { Text(if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn) }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SathiTabContent(
                        selectedTab = selectedTab,
                        viewModel = viewModel,
                        onJumpToSettings = { selectedTab = SathiDestination.SETTINGS },
                        onOpenAdmin = { showAdminPanel = true },
                        modifier = Modifier.weight(1f)
                    )
                    if (globalConfig.bannerAdEnabled) {
                        SathiAdBannerBar(config = globalConfig, language = language)
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.navigationBars,
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (globalConfig.bannerAdEnabled) {
                            SathiAdBannerBar(config = globalConfig, language = language)
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            SathiDestination.entries.forEach { dest ->
                                NavigationBarItem(
                                    selected = selectedTab == dest,
                                    onClick = { selectedTab = dest },
                                    icon = { Icon(dest.icon, contentDescription = dest.labelEn) },
                                    label = { Text(if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                SathiTabContent(
                    selectedTab = selectedTab,
                    viewModel = viewModel,
                    onJumpToSettings = { selectedTab = SathiDestination.SETTINGS },
                    onOpenAdmin = { showAdminPanel = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun SathiAdBannerBar(
    config: AppConfig,
    language: SathiLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val isRobolectric = remember {
        "robolectric".equals(Build.FINGERPRINT, ignoreCase = true)
    }

    val unitId = remember(config.bannerAdUnitId) {
        config.bannerAdUnitId.trim().ifBlank {
            try {
                context.getString(R.string.admob_banner_unit_id)
            } catch (_: Exception) {
                "ca-app-pub-3940256099942544/6300978111"
            }
        }
    }

    if (isInspection || isRobolectric) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            modifier = modifier
                .fillMaxWidth()
                .height(34.dp)
                .testTag("bottom_banner_ad")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (language == SathiLanguage.BN) "সাথী স্পন্সর্ড ব্যানার" else "Sathi Banner Ad",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        try {
            MobileAds.initialize(context) {}
        } catch (_: Throwable) {
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("bottom_banner_ad")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = unitId
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {
                        }
                    }
                },
                update = { adView ->
                    if (adView.adUnitId != unitId) {
                        adView.adUnitId = unitId
                        try {
                            adView.loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SathiTabContent(
    selectedTab: SathiDestination,
    viewModel: SathiViewModel,
    onJumpToSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (selectedTab) {
        SathiDestination.VOICE -> HomeVoiceScreen(viewModel = viewModel, modifier = modifier)
        SathiDestination.STUDIO -> StudioToolsScreen(
            viewModel = viewModel,
            onNavigateToUpgrade = onJumpToSettings,
            modifier = modifier
        )
        SathiDestination.MEMORY -> MemoryScreen(viewModel = viewModel, modifier = modifier)
        SathiDestination.SETTINGS -> SettingsAndPlanScreen(
            viewModel = viewModel,
            onOpenAdminPanel = onOpenAdmin,
            modifier = modifier
        )
    }
}
