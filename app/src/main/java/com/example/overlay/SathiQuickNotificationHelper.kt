package com.example.overlay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.SathiLanguage

object SathiQuickNotificationHelper {
    private const val CHANNEL_ID = "sathi_quick_panel"
    private const val NOTIFICATION_ID = 4209

    fun syncNotification(context: Context, enabled: Boolean, language: SathiLanguage) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!enabled) {
            mgr.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (language == SathiLanguage.BN) "সাথী কুইক অ্যাক্সেস" else "Sathi Quick Access",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = if (language == SathiLanguage.BN) {
                    "নোটিফিকেশন প্যানেল থেকে এক ট্যাপে সাথীর সাথে কথা বলুন"
                } else {
                    "Tap from notification shade to talk to Sathi anytime"
                }
            }
            mgr.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(SathiWakeWordService.EXTRA_WAKE_TRIGGERED, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (language == SathiLanguage.BN) "Sathi AI • সবসময় প্রস্তুত" else "Sathi AI • Always Ready"
        val text = if (language == SathiLanguage.BN) {
            "কথা বলতে বা ফোন কন্ট্রোল করতে ট্যাপ করুন বস"
        } else {
            "Tap to talk to Sathi anytime, Boss"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        }
    }
}
