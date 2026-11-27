package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.LocalSettingsState
import com.example.core.SathiAnimationStyle
import com.example.core.SathiColorTheme
import com.example.core.SathiOrbStyle
import com.example.model.SathiLanguage

@Composable
fun LookAndFeelScreen(
    localSettings: LocalSettingsState,
    language: SathiLanguage,
    onSelectOrbStyle: (SathiOrbStyle) -> Unit,
    onToggleStartEndSounds: (Boolean) -> Unit,
    onToggleReduceMotion: (Boolean) -> Unit,
    onSelectAnimationStyle: (SathiAnimationStyle) -> Unit,
    onSelectColorTheme: (SathiColorTheme) -> Unit,
    onBack: () -> Unit,
    initialOpenOrbPicker: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showOrbPicker by remember { mutableStateOf(initialOpenOrbPicker) }
    var showAnimationsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val effectiveReduceMotion = localSettings.reduceMotion || localSettings.batterySaver

    if (showOrbPicker) {
        OrbStylePickerScreen(
            selectedStyle = localSettings.orbStyle,
            reduceMotion = effectiveReduceMotion,
            animationStyle = localSettings.animationStyle,
            language = language,
            onSelectStyle = onSelectOrbStyle,
            onBack = { showOrbPicker = false },
            modifier = modifier
        )
        return
    }

    BackHandler(onBack = onBack)

    if (showAnimationsDialog) {
        AlertDialog(
            onDismissRequest = { showAnimationsDialog = false },
            title = {
                Text(
                    text = if (language == SathiLanguage.BN) "অ্যানিমেশন (Animations)" else "Animations",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SathiAnimationStyle.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("animation_option_${option.name.lowercase()}")
                                .clickable {
                                    onSelectAnimationStyle(option)
                                    showAnimationsDialog = false
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = localSettings.animationStyle == option,
                                onClick = {
                                    onSelectAnimationStyle(option)
                                    showAnimationsDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (language == SathiLanguage.BN) option.labelBn else option.labelEn,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimationsDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "বন্ধ" else "Close")
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            icon = { Icon(Icons.Default.Palette, contentDescription = null) },
            title = {
                Text(
                    text = if (language == SathiLanguage.BN) "কালার থিম বেছে নিন" else "Choose a colour theme",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SathiColorTheme.entries.forEach { themeOption ->
                        val selected = localSettings.colorTheme == themeOption
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = if (selected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .testTag("color_theme_option_${themeOption.name.lowercase()}")
                                .clickable {
                                    onSelectColorTheme(themeOption)
                                    showThemeDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(themeOption.previewBgHex),
                                                    Color(themeOption.previewPrimaryHex)
                                                )
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (language == SathiLanguage.BN) themeOption.labelBn else themeOption.labelEn,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(if (language == SathiLanguage.BN) "সম্পন্ন" else "Done")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("look_and_feel_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("look_and_feel_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (language == SathiLanguage.BN) "পিছনে যান" else "Back"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == SathiLanguage.BN) "লুক ও থিমস (Look & feel)" else "Look & feel",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (language == SathiLanguage.BN) {
                            "অর্ব স্টাইল, মোশন, অ্যানিমেশন ও কালার থিম"
                        } else {
                            "Orb style, sound cues, motion, animations & theme"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    // 1. Orb style
                    val orbInteract = remember { MutableInteractionSource() }
                    val orbPressed by orbInteract.collectIsPressedAsState()
                    val orbScale = rememberSathiInteractiveScale(orbPressed, localSettings.animationStyle, effectiveReduceMotion)
                    LookAndFeelChevronRow(
                        icon = Icons.Default.Tune,
                        iconTint = Color(0xFF38BDF8),
                        title = if (language == SathiLanguage.BN) "অর্ব স্টাইল (Orb style)" else "Orb style",
                        subtitle = if (language == SathiLanguage.BN) {
                            "অর্ব স্টাইল বেছে নিন ও লাইভ চলতে দেখুন"
                        } else {
                            "Pick the orb and see it move live"
                        },
                        badgeText = if (language == SathiLanguage.BN) {
                            localSettings.orbStyle.titleBn
                        } else {
                            localSettings.orbStyle.titleEn
                        },
                        onClick = { showOrbPicker = true },
                        interactionSource = orbInteract,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = orbScale
                                scaleY = orbScale
                            }
                            .testTag("open_orb_style_picker_button")
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // 2. Start and end sounds
                    LookAndFeelToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        iconTint = Color(0xFFF59E0B),
                        title = if (language == SathiLanguage.BN) "শুরু ও শেষের শব্দ" else "Start and end sounds",
                        subtitle = if (language == SathiLanguage.BN) {
                            "সাথী শোনা শুরু ও শেষ করলে মৃদু টোন বাজবে"
                        } else {
                            "A soft tone when Sathi starts and stops listening"
                        },
                        checked = localSettings.startEndSoundsEnabled,
                        onCheckedChange = onToggleStartEndSounds,
                        switchTestTag = "start_end_sounds_switch"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // 3. Reduce motion
                    LookAndFeelToggleRow(
                        icon = Icons.Default.SlowMotionVideo,
                        iconTint = Color(0xFF60A5FA),
                        title = if (language == SathiLanguage.BN) "মোশন কমান (Reduce motion)" else "Reduce motion",
                        subtitle = if (language == SathiLanguage.BN) {
                            "কম অ্যানিমেশন — ব্যাটারি ও চোখের আরামের জন্য"
                        } else {
                            "Fewer animations across Home, Orb & Voice mode"
                        },
                        checked = localSettings.reduceMotion,
                        onCheckedChange = onToggleReduceMotion,
                        switchTestTag = "reduce_motion_switch"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // 4. Animations
                    val animInteract = remember { MutableInteractionSource() }
                    val animPressed by animInteract.collectIsPressedAsState()
                    val animScale = rememberSathiInteractiveScale(animPressed, localSettings.animationStyle, effectiveReduceMotion)
                    LookAndFeelChevronRow(
                        icon = Icons.Default.Animation,
                        iconTint = Color(0xFF34D399),
                        title = if (language == SathiLanguage.BN) "অ্যানিমেশন (Animations)" else "Animations",
                        subtitle = if (language == SathiLanguage.BN) {
                            "বাটন চাপ ও পেজ পরিবর্তনের অনুভূতি"
                        } else {
                            "Button press and page change feel"
                        },
                        badgeText = if (language == SathiLanguage.BN) {
                            localSettings.animationStyle.labelBn
                        } else {
                            localSettings.animationStyle.labelEn
                        },
                        onClick = { showAnimationsDialog = true },
                        interactionSource = animInteract,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = animScale
                                scaleY = animScale
                            }
                            .testTag("animations_picker_row")
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // 5. Theme
                    val themeInteract = remember { MutableInteractionSource() }
                    val themePressed by themeInteract.collectIsPressedAsState()
                    val themeScale = rememberSathiInteractiveScale(themePressed, localSettings.animationStyle, effectiveReduceMotion)
                    LookAndFeelChevronRow(
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFF38BDF8),
                        title = if (language == SathiLanguage.BN) "থিম (Theme)" else "Theme",
                        subtitle = if (language == SathiLanguage.BN) {
                            "ট্যাপ করে পছন্দের কালার থিম বেছে নিন"
                        } else {
                            "Tap to choose a colour theme"
                        },
                        badgeText = if (language == SathiLanguage.BN) {
                            localSettings.colorTheme.labelBn
                        } else {
                            localSettings.colorTheme.labelEn
                        },
                        onClick = { showThemeDialog = true },
                        interactionSource = themeInteract,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = themeScale
                                scaleY = themeScale
                            }
                            .testTag("theme_picker_row")
                    )
                }
            }
        }
    }
}

@Composable
private fun LookAndFeelChevronRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconTint.copy(alpha = 0.16f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LookAndFeelToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = iconTint.copy(alpha = 0.16f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
fun OrbStylePickerScreen(
    selectedStyle: SathiOrbStyle,
    reduceMotion: Boolean,
    animationStyle: SathiAnimationStyle,
    language: SathiLanguage,
    onSelectStyle: (SathiOrbStyle) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var previewState by remember { mutableStateOf(OrbPreviewState.IDLE) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .testTag("orb_style_picker_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("orb_style_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (language == SathiLanguage.BN) "পিছনে যান" else "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == SathiLanguage.BN) "অর্ব স্টাইল (Orb style)" else "Orb style",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (language == SathiLanguage.BN) {
                        "হোম, ভয়েস মোড এবং ভাসমান অর্ব-এ ব্যবহার করতে নিচের যেকোনো স্টাইলে ট্যাপ করুন। শান্ত, শুনছে, ভাবছে ও বলছে অবস্থায় কেমন দেখায় তা পরীক্ষা করুন।"
                    } else {
                        "Tap a style to use it on Home, in Voice mode and on the floating orb. Try Idle, Listening, Thinking and Speaking below."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                SathiStyledOrbCanvas(
                    style = selectedStyle,
                    orbState = previewState,
                    reduceMotion = reduceMotion,
                    modifier = Modifier
                        .size(138.dp)
                        .testTag("orb_live_preview_canvas")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (language == SathiLanguage.BN) selectedStyle.titleBn else selectedStyle.titleEn,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (language == SathiLanguage.BN) selectedStyle.subtitleBn else selectedStyle.subtitleEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    OrbPreviewState.entries.forEach { stateOption ->
                        FilterChip(
                            selected = previewState == stateOption,
                            onClick = { previewState = stateOption },
                            label = {
                                Text(
                                    text = if (language == SathiLanguage.BN) stateOption.labelBn else stateOption.labelEn,
                                    maxLines = 1
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("orb_preview_state_${stateOption.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (language == SathiLanguage.BN) "সবগুলো স্টাইল (${SathiOrbStyle.entries.size})" else "ALL STYLES",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(SathiOrbStyle.entries, key = { it.name }) { style ->
            val isSelected = selectedStyle == style
            val cardInteract = remember { MutableInteractionSource() }
            val pressed by cardInteract.collectIsPressedAsState()
            val scale = rememberSathiInteractiveScale(pressed, animationStyle, reduceMotion)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    }
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                ),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = cardInteract,
                        indication = androidx.compose.material3.ripple(),
                        onClick = { onSelectStyle(style) }
                    )
                    .testTag("orb_style_card_${style.name.lowercase()}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SathiStyledOrbCanvas(
                        style = style,
                        orbState = if (isSelected) previewState else OrbPreviewState.IDLE,
                        reduceMotion = reduceMotion,
                        modifier = Modifier.size(72.dp),
                        phaseOffset = (style.ordinal * 0.45f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (language == SathiLanguage.BN) style.titleBn else style.titleEn,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (language == SathiLanguage.BN) style.subtitleBn else style.subtitleEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        minLines = 2
                    )
                }
            }
        }
    }
}
