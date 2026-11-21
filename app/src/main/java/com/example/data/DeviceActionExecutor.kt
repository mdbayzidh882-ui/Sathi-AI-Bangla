package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import com.example.model.PendingConfirmation
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import java.net.URLEncoder

sealed interface ActionOutcome {
    data class Executed(val label: String) : ActionOutcome
    data class NeedsConfirmation(val confirmation: PendingConfirmation) : ActionOutcome
    data class Failed(val message: String) : ActionOutcome
}

class DeviceActionExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun executeAction(
        type: PhoneActionType,
        param: String,
        secondary: String,
        language: SathiLanguage
    ): ActionOutcome {
        return when (type) {
            PhoneActionType.NONE,
            PhoneActionType.WEB_SEARCH -> ActionOutcome.Executed("")

            PhoneActionType.YOUTUBE -> openYouTube(param, language)
            PhoneActionType.SET_ALARM -> setAlarm(param, secondary, language)
            PhoneActionType.SET_TIMER -> setTimer(param, secondary, language)
            PhoneActionType.ADD_REMINDER -> openCalendarReminder(param, language)
            PhoneActionType.FLASHLIGHT_ON -> toggleFlashlight(true, language)
            PhoneActionType.FLASHLIGHT_OFF -> toggleFlashlight(false, language)
            PhoneActionType.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE, language)
            PhoneActionType.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER, language)
            PhoneActionType.OPEN_APP -> openInstalledApp(param, language)
            PhoneActionType.CALL_CONFIRM -> ActionOutcome.NeedsConfirmation(
                PendingConfirmation(PhoneActionType.CALL_CONFIRM, param.ifBlank { "" })
            )
            PhoneActionType.SMS_CONFIRM -> ActionOutcome.NeedsConfirmation(
                PendingConfirmation(
                    PhoneActionType.SMS_CONFIRM,
                    param.ifBlank { "" },
                    secondary
                )
            )
            PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> ActionOutcome.NeedsConfirmation(
                PendingConfirmation(
                    type = PhoneActionType.SOCIAL_MESSAGE_CONFIRM,
                    target = param.ifBlank { "WhatsApp" },
                    messageBody = secondary,
                    extraParam = param.lowercase()
                )
            )
        }
    }

    fun executeConfirmedCall(target: String, language: SathiLanguage): ActionOutcome {
        return try {
            val cleaned = target.filter { it.isDigit() || it == '+' }
            val uri = if (cleaned.isNotEmpty()) Uri.parse("tel:$cleaned") else Uri.parse("tel:")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "ডায়ালার খোলা হয়েছে" else "Opened Dialer"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "কল স্ক্রিন খোলা যায়নি।" else "Could not open phone dialer."
            )
        }
    }

    fun executeConfirmedSms(target: String, body: String, language: SathiLanguage): ActionOutcome {
        return try {
            val cleaned = target.filter { it.isDigit() || it == '+' }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleaned")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "মেসেজ অ্যাপ খোলা হয়েছে" else "Opened Messages"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "মেসেজ অ্যাপ পাওয়া যায়নি।" else "Could not open messaging app."
            )
        }
    }

    fun executeConfirmedSocialMessage(
        platform: String,
        messageBody: String,
        language: SathiLanguage
    ): ActionOutcome {
        val lower = platform.lowercase()
        val textToSend = messageBody.trim()

        // Always copy drafted text to clipboard so pasting as a comment/reply takes 1 tap
        if (textToSend.isNotBlank()) {
            try {
                val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Sathi Draft", textToSend))
            } catch (_: Exception) {
            }
        }

        val targetPackages = when {
            lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস") ->
                listOf("com.whatsapp", "com.whatsapp.w4b")
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") ->
                listOf("com.facebook.orca", "com.facebook.mlite")
            lower.contains("facebook") || lower.contains("ফেসবুক") ->
                listOf("com.facebook.katana", "com.facebook.lite")
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") ->
                listOf("com.instagram.android")
            lower.contains("tiktok") || lower.contains("টিকটক") ->
                listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            else -> listOf("com.whatsapp", "com.facebook.orca")
        }

        val pm = appContext.packageManager
        val installedPkg = targetPackages.firstOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null
        }

        // Direct Share Intent works natively for WhatsApp & Messenger!
        if (installedPkg != null && (installedPkg.contains("whatsapp") || installedPkg.contains("orca") || installedPkg.contains("mlite"))) {
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textToSend)
                    setPackage(installedPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(sendIntent)
                return ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) "$platform-এ লেখা প্রস্তুত করে পাঠানো হয়েছে (ক্লিপবোর্ডেও কপি আছে)।"
                    else "Opened $platform with your message ready."
                )
            } catch (_: Exception) {
            }
        }

        if (installedPkg != null) {
            pm.getLaunchIntentForPackage(installedPkg)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launch)
                return ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) "$platform খোলা হয়েছে ও লেখাটি কপি করা হয়েছে — কমেন্ট বক্সে পেস্ট করুন।"
                    else "Opened $platform and copied your text — paste it into the comment box."
                )
            }
        }

        // Fallback to Share Chooser or open web app
        return try {
            val chooser = Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textToSend)
                },
                if (language == SathiLanguage.BN) "মেসেজ বা কমেন্ট পাঠান" else "Send message or comment"
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(chooser)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "শেয়ার অপশন খোলা হয়েছে (লেখা কপি করা আছে)"
                else "Opened share sheet (text copied)"
            )
        } catch (_: Exception) {
            openInstalledApp(platform, language)
        }
    }

    private fun openYouTube(query: String, language: SathiLanguage): ActionOutcome {
        return try {
            val encoded = URLEncoder.encode(query.ifBlank { "Bangla music" }, "UTF-8")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "ইউটিউবে খোঁজা হচ্ছে: $query" else "Opening YouTube: $query"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "ইউটিউব খোলা যায়নি।" else "Could not open YouTube."
            )
        }
    }

    private fun setAlarm(timeParam: String, label: String, language: SathiLanguage): ActionOutcome {
        return try {
            val parts = timeParam.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
            val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Sathi Alarm" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "অ্যালার্ম সেট: $hour:${minute.toString().padStart(2, '0')}"
                else "Alarm set for $hour:${minute.toString().padStart(2, '0')}"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "অ্যালার্ম অ্যাপ খোলা যায়নি।" else "No alarm app available."
            )
        }
    }

    private fun setTimer(secondsParam: String, label: String, language: SathiLanguage): ActionOutcome {
        return try {
            val seconds = secondsParam.toIntOrNull()?.coerceIn(5, 86400) ?: 60
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Sathi Timer" })
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "টাইমার: $seconds সেকেন্ড" else "Timer: ${seconds}s"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "টাইমার চালু করা যায়নি।" else "Could not start timer."
            )
        }
    }

    private fun openCalendarReminder(title: String, language: SathiLanguage): ActionOutcome {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Sathi Reminder" })
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600_000L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "ক্যালেন্ডার রিমাইন্ডার খোলা হয়েছে" else "Opened Calendar Event"
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "ক্যালেন্ডার অ্যাপ পাওয়া যায়নি।" else "No calendar app found."
            )
        }
    }

    private fun toggleFlashlight(turnOn: Boolean, language: SathiLanguage): ActionOutcome {
        return try {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
                ?: return ActionOutcome.Failed(
                    if (language == SathiLanguage.BN) "এই ফোনে ফ্ল্যাশলাইট পাওয়া যায়নি।"
                    else "Flashlight unavailable on this device."
                )
            cameraManager.setTorchMode(cameraId, turnOn)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) {
                    if (turnOn) "ফ্ল্যাশলাইট চালু হয়েছে" else "ফ্ল্যাশলাইট বন্ধ হয়েছে"
                } else {
                    if (turnOn) "Flashlight turned ON" else "Flashlight turned OFF"
                }
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "ফ্ল্যাশলাইট নিয়ন্ত্রণ করা যায়নি।"
                else "Could not toggle flashlight."
            )
        }
    }

    private fun adjustVolume(direction: Int, language: SathiLanguage): ActionOutcome {
        return try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) {
                    if (direction == AudioManager.ADJUST_RAISE) "ভলিউম বাড়ানো হয়েছে" else "ভলিউম কমানো হয়েছে"
                } else {
                    if (direction == AudioManager.ADJUST_RAISE) "Volume increased" else "Volume decreased"
                }
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "ভলিউম পরিবর্তন করা যায়নি।" else "Could not adjust volume."
            )
        }
    }

    private data class KnownAppAlias(
        val keywords: List<String>,
        val packages: List<String>,
        val webFallback: String?,
        val displayBn: String,
        val displayEn: String
    )

    private val knownApps = listOf(
        KnownAppAlias(
            keywords = listOf("youtube", "ইউটিউব"),
            packages = listOf("com.google.android.youtube"),
            webFallback = "https://www.youtube.com",
            displayBn = "ইউটিউব",
            displayEn = "YouTube"
        ),
        KnownAppAlias(
            keywords = listOf("facebook", "ফেসবুক", "ফেসবুকে", "fb"),
            packages = listOf("com.facebook.katana", "com.facebook.lite"),
            webFallback = "https://m.facebook.com",
            displayBn = "ফেসবুক",
            displayEn = "Facebook"
        ),
        KnownAppAlias(
            keywords = listOf("instagram", "ইনস্টাগ্রাম", "ইন্সটাগ্রাম", "insta"),
            packages = listOf("com.instagram.android"),
            webFallback = "https://www.instagram.com",
            displayBn = "ইনস্টাগ্রাম",
            displayEn = "Instagram"
        ),
        KnownAppAlias(
            keywords = listOf("tiktok", "টিকটক"),
            packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            webFallback = "https://www.tiktok.com",
            displayBn = "টিকটক",
            displayEn = "TikTok"
        ),
        KnownAppAlias(
            keywords = listOf("messenger", "মেসেঞ্জার", "ম্যাসেঞ্জার"),
            packages = listOf("com.facebook.orca", "com.facebook.mlite"),
            webFallback = "https://www.messenger.com",
            displayBn = "মেসেঞ্জার",
            displayEn = "Messenger"
        ),
        KnownAppAlias(
            keywords = listOf("whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "হোয়াটস", "হোয়াটস"),
            packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
            webFallback = "https://web.whatsapp.com",
            displayBn = "হোয়াটসঅ্যাপ",
            displayEn = "WhatsApp"
        ),
        KnownAppAlias(
            keywords = listOf("chrome", "ক্রোম", "ব্রাউজার", "browser"),
            packages = listOf("com.android.chrome"),
            webFallback = "https://www.google.com",
            displayBn = "ক্রোম",
            displayEn = "Chrome"
        ),
        KnownAppAlias(
            keywords = listOf("maps", "google maps", "ম্যাপ", "মানচিত্র"),
            packages = listOf("com.google.android.apps.maps"),
            webFallback = "https://maps.google.com",
            displayBn = "গুগল ম্যাপস",
            displayEn = "Google Maps"
        ),
        KnownAppAlias(
            keywords = listOf("gmail", "জিমেইল", "ইমেইল"),
            packages = listOf("com.google.android.gm"),
            webFallback = "https://mail.google.com",
            displayBn = "জিমেইল",
            displayEn = "Gmail"
        ),
        KnownAppAlias(
            keywords = listOf("play store", "প্লে স্টোর", "প্লেস্টোর"),
            packages = listOf("com.android.vending"),
            webFallback = "https://play.google.com/store",
            displayBn = "প্লে স্টোর",
            displayEn = "Play Store"
        )
    )

    private fun openInstalledApp(appQuery: String, language: SathiLanguage): ActionOutcome {
        return try {
            val pm = appContext.packageManager
            val queryLower = appQuery.lowercase().trim()

            // 1. Settings shortcut
            if (queryLower.contains("setting") || queryLower.contains("সেটিং")) {
                appContext.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ActionOutcome.Executed(if (language == SathiLanguage.BN) "সেটিংস খোলা হয়েছে" else "Opened Settings")
            }

            // 2. Known popular apps (YouTube, Facebook, Instagram, TikTok, Messenger, WhatsApp, etc.)
            val known = knownApps.firstOrNull { alias ->
                alias.keywords.any { kw -> queryLower.contains(kw) }
            }
            if (known != null) {
                for (pkg in known.packages) {
                    val launch = pm.getLaunchIntentForPackage(pkg)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launch != null) {
                        appContext.startActivity(launch)
                        val label = if (language == SathiLanguage.BN) known.displayBn else known.displayEn
                        return ActionOutcome.Executed(
                            if (language == SathiLanguage.BN) "$label খোলা হয়েছে" else "Opened $label"
                        )
                    }
                }
                if (!known.webFallback.isNullOrBlank()) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(known.webFallback)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(webIntent)
                    val label = if (language == SathiLanguage.BN) known.displayBn else known.displayEn
                    return ActionOutcome.Executed(
                        if (language == SathiLanguage.BN) "$label অ্যাপ ইনস্টল না থাকায় ওয়েবে খোলা হয়েছে"
                        else "Opened $label in browser"
                    )
                }
            }

            // 3. Dynamic scan across ALL launcher apps installed on the phone
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            val match = apps.firstOrNull { info ->
                val label = info.loadLabel(pm).toString().lowercase()
                label.contains(queryLower) ||
                    queryLower.contains(label) ||
                    info.activityInfo.packageName.lowercase().contains(queryLower)
            }
            if (match != null) {
                val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launch != null) {
                    appContext.startActivity(launch)
                    return ActionOutcome.Executed(
                        if (language == SathiLanguage.BN) "${match.loadLabel(pm)} খোলা হয়েছে"
                        else "Opened ${match.loadLabel(pm)}"
                    )
                }
            }
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "'$appQuery' অ্যাপটি ফোনে খুঁজে পাওয়া যায়নি।"
                else "Could not find app '$appQuery' on this phone."
            )
        } catch (_: Exception) {
            ActionOutcome.Failed(
                if (language == SathiLanguage.BN) "অ্যাপটি খোলা যায়নি।" else "Failed to open app."
            )
        }
    }
}
