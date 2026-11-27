package com.example.data

import android.Manifest
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
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.model.PendingConfirmation
import com.example.model.PhoneActionType
import com.example.model.SathiLanguage
import com.example.overlay.SathiScreenReaderService
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
        language: SathiLanguage,
        recipient: String = ""
    ): ActionOutcome {
        return when (type) {
            PhoneActionType.NONE,
            PhoneActionType.WEB_SEARCH,
            PhoneActionType.GENERATE_IMAGE -> ActionOutcome.Executed("")

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
                    secondary,
                    recipientName = recipient
                )
            )
            PhoneActionType.SOCIAL_MESSAGE_CONFIRM -> ActionOutcome.NeedsConfirmation(
                PendingConfirmation(
                    type = PhoneActionType.SOCIAL_MESSAGE_CONFIRM,
                    target = param.ifBlank { "WhatsApp" },
                    messageBody = secondary,
                    extraParam = param.lowercase(),
                    recipientName = recipient
                )
            )
            PhoneActionType.SCREEN_UI_ACTION -> {
                val ok = SathiScreenReaderService.performDirectScreenCommand(param, secondary)
                if (ok) {
                    ActionOutcome.Executed(
                        if (language == SathiLanguage.BN) "স্ক্রিন কমান্ড সম্পন্ন হয়েছে ($param)" else "Executed screen action ($param)"
                    )
                } else {
                    ActionOutcome.Failed(
                        if (language == SathiLanguage.BN) {
                            "স্ক্রিনে '$param' বাটন বা লেখার বক্স খুঁজে পাওয়া যায়নি (সেটিংস থেকে স্ক্রিন রিডার চালু রাখুন)।"
                        } else {
                            "Could not find '$param' on screen. Enable Screen Data Reader in Settings."
                        }
                    )
                }
            }
        }
    }

    fun executeConfirmedCall(target: String, language: SathiLanguage): ActionOutcome {
        return try {
            val cleaned = resolvePhoneDigits(target)
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

    fun executeConfirmedSms(
        target: String,
        body: String,
        language: SathiLanguage,
        recipientName: String = ""
    ): ActionOutcome {
        return try {
            val cleaned = resolvePhoneDigits(target.ifBlank { recipientName })
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
        language: SathiLanguage,
        recipientName: String = "",
        sendAfterType: Boolean = false
    ): ActionOutcome {
        val lower = platform.lowercase()
        val textToSend = messageBody.trim()
        val rawRecipient = recipientName.trim()
        val contactMatch = if (rawRecipient.isNotBlank()) resolveContactMatch(rawRecipient) else Pair("", "")
        val resolvedNameOrQuery = contactMatch.first.ifBlank { rawRecipient }
        val translitRecipient = transliterateBnToLatin(rawRecipient)

        // Copy message body (or friend name for quick 1-tap paste in friend-search mode)
        val clipboardContent = textToSend.ifBlank { resolvedNameOrQuery }
        if (clipboardContent.isNotBlank()) {
            try {
                val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Sathi Draft", clipboardContent))
            } catch (_: Exception) {
            }
        }

        val targetPackages = when {
            lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস") ->
                listOf("com.whatsapp", "com.whatsapp.w4b")
            lower.contains("messenger") || lower.contains("মেসেঞ্জার") || lower.contains("ম্যাসেঞ্জার") ->
                listOf("com.facebook.orca", "com.facebook.mlite")
            lower.contains("facebook") || lower.contains("ফেসবুক") ->
                listOf("com.facebook.katana", "com.facebook.lite")
            lower.contains("instagram") || lower.contains("ইনস্টাগ্রাম") ->
                listOf("com.instagram.android")
            lower.contains("tiktok") || lower.contains("টিকটক") ->
                listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
            else -> listOf("com.whatsapp", "com.facebook.orca")
        }

        // Stage Accessibility two-step friend search + compose-box write (+ optional Send click)
        SathiScreenReaderService.stagePendingSocialFlow(
            targetPackages = targetPackages,
            recipientName = resolvedNameOrQuery,
            transliteratedRecipient = translitRecipient,
            messageText = textToSend,
            sendAfterType = sendAfterType
        )

        val pm = appContext.packageManager
        val installedPkg = targetPackages.firstOrNull { pkg ->
            pm.getLaunchIntentForPackage(pkg) != null
        }

        // 1. WhatsApp direct contact chat launch when phone number / Android contact is matched
        val isWhatsApp = lower.contains("whatsapp") || lower.contains("হোয়াটস") || lower.contains("হোয়াটস")
        if (isWhatsApp && rawRecipient.isNotBlank()) {
            val waPhone = normalizeWhatsAppNumber(contactMatch.second.ifBlank { resolvePhoneDigits(rawRecipient) })
            if (waPhone.length >= 10) {
                try {
                    val waUrl = if (textToSend.isNotBlank()) {
                        val encoded = URLEncoder.encode(textToSend, "UTF-8")
                        "https://api.whatsapp.com/send?phone=$waPhone&text=$encoded"
                    } else {
                        "https://api.whatsapp.com/send?phone=$waPhone"
                    }
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                        if (installedPkg != null) setPackage(installedPkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(waIntent)
                    return ActionOutcome.Executed(
                        if (language == SathiLanguage.BN) {
                            if (textToSend.isNotBlank()) {
                                "$resolvedNameOrQuery-এর WhatsApp চ্যাটে লেখা প্রস্তুত করে খোলা হয়েছে।"
                            } else {
                                "$resolvedNameOrQuery-এর WhatsApp চ্যাট খোলা হয়েছে।"
                            }
                        } else {
                            if (textToSend.isNotBlank()) {
                                "Opened WhatsApp chat for $resolvedNameOrQuery with your message ready."
                            } else {
                                "Opened WhatsApp chat for $resolvedNameOrQuery."
                            }
                        }
                    )
                } catch (_: Exception) {
                }
            }
        }

        // 2. Friend search only (no message text): launch app directly (never empty ACTION_SEND)
        val accessibilityActive = SathiScreenReaderService.isAccessibilityEnabled(appContext)
        if (textToSend.isBlank()) {
            if (installedPkg != null) {
                pm.getLaunchIntentForPackage(installedPkg)?.let { launch ->
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launch)
                    return ActionOutcome.Executed(
                        if (language == SathiLanguage.BN) {
                            if (accessibilityActive && resolvedNameOrQuery.isNotBlank()) {
                                "$platform খোলা হয়েছে — '$resolvedNameOrQuery'-কে স্বয়ংক্রিয়ভাবে খোঁজা হচ্ছে।"
                            } else {
                                "$platform খোলা হয়েছে — সার্চ বক্সে পেস্ট করতে বন্ধুর নাম '$resolvedNameOrQuery' কপি করা আছে।"
                            }
                        } else {
                            if (accessibilityActive && resolvedNameOrQuery.isNotBlank()) {
                                "Opened $platform — searching for '$resolvedNameOrQuery'."
                            } else {
                                "Opened $platform — copied friend name '$resolvedNameOrQuery' so you can paste in Search."
                            }
                        }
                    )
                }
            }
            return openInstalledApp(platform, language)
        }

        // 3. When Accessibility is enabled & recipient is known in WhatsApp/Messenger, launch app directly so two-step flow searches friend & types text
        if (accessibilityActive && resolvedNameOrQuery.isNotBlank() && installedPkg != null &&
            (installedPkg.contains("whatsapp") || installedPkg.contains("orca") || installedPkg.contains("mlite"))
        ) {
            pm.getLaunchIntentForPackage(installedPkg)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launch)
                return ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) {
                        "$platform খোলা হয়েছে — '$resolvedNameOrQuery'-কে খুঁজে মেসেজটি লিখে দেওয়া হচ্ছে।"
                    } else {
                        "Opened $platform — locating '$resolvedNameOrQuery' and writing your message."
                    }
                )
            }
        }

        // 4. Direct Share Intent for WhatsApp & Messenger when Accessibility is off (or no recipient specified)
        if (installedPkg != null && (installedPkg.contains("whatsapp") || installedPkg.contains("orca") || installedPkg.contains("mlite"))) {
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textToSend)
                    setPackage(installedPkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(sendIntent)
                val friendHintBn = if (resolvedNameOrQuery.isNotBlank()) " '$resolvedNameOrQuery' নির্বাচন করুন।" else ""
                val friendHintEn = if (resolvedNameOrQuery.isNotBlank()) " Pick '$resolvedNameOrQuery' to open chat." else ""
                return ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) {
                        "$platform-এ লেখা প্রস্তুত করে খোলা হয়েছে (ক্লিপবোর্ডেও কপি আছে)।" + friendHintBn
                    } else {
                        "Opened $platform with your text ready (also copied).$friendHintEn"
                    }
                )
            } catch (_: Exception) {
            }
        }

        if (installedPkg != null) {
            pm.getLaunchIntentForPackage(installedPkg)?.let { launch ->
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launch)
                return ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) {
                        "$platform খোলা হয়েছে ও লেখাটি কপি করা আছে — কমেন্ট বা মেসেজ বক্সে পেস্ট করুন।"
                    } else {
                        "Opened $platform and copied your text — paste it into the message box."
                    }
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

    private fun resolvePhoneDigits(targetOrName: String): String {
        return resolveContactMatch(targetOrName).second
    }

    private fun resolveContactMatch(targetOrName: String): Pair<String, String> {
        val directDigits = targetOrName.filter { it.isDigit() || it == '+' }
        if (directDigits.count { it.isDigit() } >= 6) {
            return Pair(targetOrName.trim(), directDigits)
        }
        val queryName = targetOrName.trim()
        if (queryName.length < 2) return Pair("", "")

        val hasContactPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactPerm) return Pair("", "")

        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            // First try direct SQL LIKE match
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$queryName%"),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val disp = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val num = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().filter { it.isDigit() || it == '+' } else ""
                    if (num.isNotEmpty()) return Pair(disp, num)
                }
            }

            // Second pass: transliteration & phonetic match across device contacts (e.g., "রহিম" -> "MD Rahim")
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val disp = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                    val num = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty().filter { it.isDigit() || it == '+' } else ""
                    if (disp.isNotBlank() && num.isNotBlank() && matchesContactName(disp, queryName)) {
                        return Pair(disp, num)
                    }
                }
            }
            Pair("", "")
        } catch (_: Exception) {
            Pair("", "")
        }
    }

    private fun normalizeWhatsAppNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return when {
            digits.startsWith("880") && digits.length >= 13 -> digits
            digits.startsWith("01") && digits.length == 11 -> "88$digits"
            else -> digits
        }
    }

    private fun openYouTube(query: String, language: SathiLanguage): ActionOutcome {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return openInstalledApp("YouTube", language)
        }
        return try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            val pm = appContext.packageManager
            val ytPkg = "com.google.android.youtube"
            val hasYtApp = pm.getLaunchIntentForPackage(ytPkg) != null
            val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                if (hasYtApp) setPackage(ytPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            ActionOutcome.Executed(
                if (language == SathiLanguage.BN) "ইউটিউবে '$cleanQuery' খোঁজা হচ্ছে" else "Searching YouTube for '$cleanQuery'"
            )
        } catch (_: Exception) {
            try {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                appContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                ActionOutcome.Executed(
                    if (language == SathiLanguage.BN) "ইউটিউবে '$cleanQuery' খোঁজা হচ্ছে" else "Searching YouTube for '$cleanQuery'"
                )
            } catch (_: Exception) {
                ActionOutcome.Failed(
                    if (language == SathiLanguage.BN) "ইউটিউব খোলা যায়নি।" else "Could not open YouTube."
                )
            }
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

    companion object {
        private val bnConsonants = mapOf(
            'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng",
            'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
            'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
            'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
            'প' to "p", 'ফ' to "f", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
            'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
            'স' to "s", 'হ' to "h", '\u09DC' to "r", '\u09DD' to "rh",
            '\u09DF' to "y", 'ৎ' to "t"
        )
        private val bnIndependentVowels = mapOf(
            'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i",
            'উ' to "u", 'ঊ' to "u", 'ঋ' to "ri", 'এ' to "e",
            'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou"
        )
        private val bnMatras = mapOf(
            'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u",
            'ৃ' to "ri", 'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou"
        )

        fun transliterateBnToLatin(input: String): String {
            val trimmed = input
                .replace("\u09A1\u09BC", "\u09DC")
                .replace("\u09A2\u09BC", "\u09DD")
                .replace("\u09AF\u09BC", "\u09DF")
                .trim()
            if (trimmed.isEmpty()) return ""
            val out = StringBuilder()
            var lastWasConsonant = false
            for (i in trimmed.indices) {
                val ch = trimmed[i]
                when {
                    ch in bnConsonants -> {
                        if (lastWasConsonant && trimmed.getOrNull(i - 1) != '্') {
                            out.append('o')
                        }
                        out.append(bnConsonants[ch])
                        lastWasConsonant = true
                    }
                    ch == '্' -> {
                        lastWasConsonant = false
                    }
                    ch in bnMatras -> {
                        out.append(bnMatras[ch])
                        lastWasConsonant = false
                    }
                    ch in bnIndependentVowels -> {
                        out.append(bnIndependentVowels[ch])
                        lastWasConsonant = false
                    }
                    ch == 'ং' || ch == 'ঁ' -> {
                        out.append('n')
                        lastWasConsonant = false
                    }
                    ch == 'ঃ' -> {
                        out.append('h')
                        lastWasConsonant = false
                    }
                    ch.isLetterOrDigit() || ch == ' ' -> {
                        out.append(ch.lowercaseChar())
                        lastWasConsonant = false
                    }
                }
            }
            return out.toString().replace(Regex("\\s+"), " ").trim()
        }

        fun phoneticSkeleton(latin: String): String {
            val s = latin.lowercase()
                .replace("ph", "f")
                .replace("bh", "v")
                .replace("dh", "d")
                .replace("th", "t")
                .replace("kh", "k")
                .replace("gh", "g")
                .replace("jh", "j")
                .replace("sh", "s")
                .replace("chh", "c")
                .replace("ch", "c")
                .replace("ee", "i")
                .replace("oo", "u")
                .replace('z', 'j')
                .replace('q', 'k')
                .replace('w', 'v')
                .filter { it.isLetterOrDigit() }
            if (s.isEmpty()) return ""
            val sb = StringBuilder()
            var prev = '\u0000'
            for (idx in s.indices) {
                val c = s[idx]
                val isVowel = c in charArrayOf('a', 'e', 'i', 'o', 'u', 'y')
                // Keep initial vowel if word starts with one; drop interior vowels to tolerate Bengali o/a/i/ee variance
                if (idx == 0 || !isVowel) {
                    if (c != prev) {
                        sb.append(c)
                        prev = c
                    }
                }
            }
            return sb.toString()
        }

        fun matchesContactName(displayName: String, queryName: String): Boolean {
            val disp = displayName.trim()
            val query = queryName.trim()
            if (disp.isEmpty() || query.length < 2) return false
            if (disp.contains(query, ignoreCase = true)) return true

            val queryLatin = if (query.any { it in '\u0980'..'\u09FF' }) {
                transliterateBnToLatin(query)
            } else {
                query.lowercase()
            }
            if (queryLatin.length < 2) return false
            if (disp.lowercase().contains(queryLatin)) return true

            val querySkel = phoneticSkeleton(queryLatin)
            if (querySkel.length < 2) return false

            val dispWords = disp.split(Regex("[\\s._-]+")).filter { it.isNotBlank() }
            return dispWords.any { word ->
                val wordLatin = if (word.any { it in '\u0980'..'\u09FF' }) transliterateBnToLatin(word) else word.lowercase()
                wordLatin.contains(queryLatin) || phoneticSkeleton(wordLatin) == querySkel ||
                    (querySkel.length >= 3 && phoneticSkeleton(wordLatin).contains(querySkel))
            }
        }
    }
}
