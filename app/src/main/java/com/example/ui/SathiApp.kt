package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.NetworkMonitor
import com.example.core.SathiAnimationStyle
import com.example.core.SathiColorTheme
import com.example.core.SathiPreferences
import com.example.data.DeviceActionExecutor
import com.example.data.GeminiRestImageGenRepository
import com.example.data.OfflineCompanionEngine
import com.example.data.OfflinePackManager
import com.example.data.SathiAiService
import com.example.data.SathiDatabase
import com.example.data.SathiLocalRepository
import com.example.data.VoiceController
import com.example.data.WebSearchService
import com.example.model.SathiLanguage
import com.example.overlay.SathiWakeWordService
import com.example.ui.theme.sathiHorizonBackgroundBrush

private enum class SathiDestination(
    val labelBn: String,
    val labelEn: String,
    val icon: ImageVector
) {
    VOICE("সাথী", "Home", Icons.Default.Home),
    STUDIO("স্টুডিও", "Studio", Icons.Default.AutoAwesome),
    LIVE("লাইভ", "Live", Icons.Default.Mic),
    MEMORY("স্মৃতি", "Memory", Icons.Default.Psychology),
    SETTINGS("সেটিংস", "Settings", Icons.Default.Settings)
}

@Composable
fun SathiAppNavigation(
    wakeTriggerTick: Int = 0,
    isDarkTheme: Boolean = true,
    colorTheme: SathiColorTheme = SathiColorTheme.OCEAN
) {
    SathiMainShell(
        currentUserId = SathiLocalRepository.LOCAL_OWNER_UID,
        userEmail = SathiLocalRepository.OWNER_EMAIL,
        displayName = SathiLocalRepository.OWNER_DEFAULT_NAME,
        wakeTriggerTick = wakeTriggerTick,
        isDarkTheme = isDarkTheme,
        colorTheme = colorTheme
    )
}

@Composable
private fun SathiMainShell(
    currentUserId: String,
    userEmail: String,
    displayName: String,
    wakeTriggerTick: Int,
    isDarkTheme: Boolean,
    colorTheme: SathiColorTheme
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
                    appContext = app,
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    displayName = displayName,
                    repository = repo,
                    aiService = SathiAiService(packManager),
                    webSearchService = WebSearchService(),
                    offlineEngine = OfflineCompanionEngine(packManager),
                    offlinePackManager = packManager,
                    voiceController = voiceController,
                    deviceActions = DeviceActionExecutor(app),
                    preferences = SathiPreferences(app),
                    networkMonitor = NetworkMonitor(app),
                    imageGenRepository = GeminiRestImageGenRepository(
                        apiKeyProvider = { lateViewModel?.localSettings?.value?.customGeminiApiKey.orEmpty() }
                    )
                )
            }
        }
    )
    lateViewModel = viewModel

    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val serviceWakeCount by SathiWakeWordService.wakeTriggerCount.collectAsStateWithLifecycle()
    val language = SathiLanguage.fromCode(profile?.preferredLanguage)

    var selectedTab by remember { mutableStateOf(SathiDestination.VOICE) }
    var openLookAndFeelInSettings by remember { mutableStateOf(false) }
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

    val effectiveReduceMotion = localSettings.reduceMotion || localSettings.batterySaver
    val activeAnimStyle = if (effectiveReduceMotion) SathiAnimationStyle.OFF else localSettings.animationStyle

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(sathiHorizonBackgroundBrush(darkTheme = isDarkTheme, colorTheme = colorTheme))
    ) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                    SathiDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = selectedTab == dest,
                            onClick = {
                                openLookAndFeelInSettings = false
                                selectedTab = dest
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.labelEn) },
                            label = { Text(if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn) }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SathiTabContent(
                        selectedTab = selectedTab,
                        animationStyle = activeAnimStyle,
                        viewModel = viewModel,
                        openLookAndFeelInitially = openLookAndFeelInSettings,
                        onOpenThemes = {
                            openLookAndFeelInSettings = true
                            selectedTab = SathiDestination.SETTINGS
                        },
                        onJumpToSettings = {
                            openLookAndFeelInSettings = false
                            selectedTab = SathiDestination.SETTINGS
                        },
                        onOpenLive = { selectedTab = SathiDestination.LIVE },
                        onOpenMemory = { selectedTab = SathiDestination.MEMORY },
                        onMinimizeLive = { selectedTab = SathiDestination.VOICE },
                        onOpenAdmin = { showAdminPanel = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.navigationBars,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                            ),
                            shadowElevation = 10.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                SathiDestination.entries.forEach { dest ->
                                    val isSelected = selectedTab == dest
                                    val isCenterLive = dest == SathiDestination.LIVE
                                    val labelText = if (language == SathiLanguage.BN) dest.labelBn else dest.labelEn
                                    if (isCenterLive) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(24.dp))
                                                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                                                .clickable {
                                                    openLookAndFeelInSettings = false
                                                    selectedTab = dest
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = if (isSelected) {
                                                                listOf(Color(0xFF00E5FF), Color(0xFF7C3AED))
                                                            } else {
                                                                listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                                                            }
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = dest.icon,
                                                    contentDescription = labelText,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Text(
                                                text = labelText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .defaultMinSize(minHeight = 48.dp)
                                                .clickable {
                                                    openLookAndFeelInSettings = false
                                                    selectedTab = dest
                                                }
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = dest.icon,
                                                contentDescription = dest.labelEn,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(21.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = labelText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                SathiTabContent(
                    selectedTab = selectedTab,
                    animationStyle = activeAnimStyle,
                    viewModel = viewModel,
                    openLookAndFeelInitially = openLookAndFeelInSettings,
                    onOpenThemes = {
                        openLookAndFeelInSettings = true
                        selectedTab = SathiDestination.SETTINGS
                    },
                    onJumpToSettings = {
                        openLookAndFeelInSettings = false
                        selectedTab = SathiDestination.SETTINGS
                    },
                    onOpenLive = { selectedTab = SathiDestination.LIVE },
                    onOpenMemory = { selectedTab = SathiDestination.MEMORY },
                    onMinimizeLive = { selectedTab = SathiDestination.VOICE },
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
private fun SathiTabContent(
    selectedTab: SathiDestination,
    animationStyle: SathiAnimationStyle,
    viewModel: SathiViewModel,
    openLookAndFeelInitially: Boolean,
    onOpenThemes: () -> Unit,
    onJumpToSettings: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenMemory: () -> Unit,
    onMinimizeLive: () -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            when (animationStyle) {
                SathiAnimationStyle.OFF -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                SathiAnimationStyle.ZOOM -> (scaleIn(initialScale = 0.94f, animationSpec = tween(210)) + fadeIn(tween(190)))
                    .togetherWith(scaleOut(targetScale = 1.03f, animationSpec = tween(170)) + fadeOut(tween(150)))
                SathiAnimationStyle.BOUNCY -> (slideInHorizontally(tween(240)) { it / 8 } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 10 } + fadeOut(tween(160)))
                SathiAnimationStyle.SMOOTH -> fadeIn(tween(190)) togetherWith fadeOut(tween(150))
            }
        },
        modifier = modifier,
        label = "sathi_tab_transition"
    ) { tab ->
        when (tab) {
            SathiDestination.VOICE -> HomeVoiceScreen(
                viewModel = viewModel,
                onOpenAdminPanel = onOpenAdmin,
                onOpenThemes = onOpenThemes,
                onOpenLive = onOpenLive,
                onOpenMemory = onOpenMemory,
                onOpenSettings = onJumpToSettings,
                modifier = Modifier.fillMaxSize()
            )
            SathiDestination.LIVE -> LiveVoiceChatScreen(
                viewModel = viewModel,
                onOpenAdminPanel = onOpenAdmin,
                onMinimizeToHome = onMinimizeLive,
                modifier = Modifier.fillMaxSize()
            )
            SathiDestination.STUDIO -> StudioToolsScreen(
                viewModel = viewModel,
                onNavigateToUpgrade = onJumpToSettings,
                modifier = Modifier.fillMaxSize()
            )
            SathiDestination.MEMORY -> MemoryScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            SathiDestination.SETTINGS -> SettingsAndPlanScreen(
                viewModel = viewModel,
                onOpenAdminPanel = onOpenAdmin,
                initialOpenLookAndFeel = openLookAndFeelInitially,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
