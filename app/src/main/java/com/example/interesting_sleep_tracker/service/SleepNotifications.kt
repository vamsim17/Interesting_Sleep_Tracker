package com.example.interesting_sleep_tracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.interesting_sleep_tracker.MainActivity
import com.example.interesting_sleep_tracker.R
import com.example.interesting_sleep_tracker.ui.PromptActivity

/** Notification channels, ids and builders for sleep tracking. */
object SleepNotifications {

    const val CHANNEL_TRACKING = "channel_tracking"
    const val CHANNEL_PROMPT = "channel_prompt"
    const val CHANNEL_INFO = "channel_info"

    const val ID_TRACKING = 1
    const val ID_PROMPT = 2
    const val ID_INFO = 3

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val tracking = NotificationChannel(
            CHANNEL_TRACKING,
            context.getString(R.string.channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_tracking_desc)
            setShowBadge(false)
        }

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val prompt = NotificationChannel(
            CHANNEL_PROMPT,
            context.getString(R.string.channel_prompt_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_prompt_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 400)
            enableLights(true)
            setBypassDnd(true)
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

        val info = NotificationChannel(
            CHANNEL_INFO,
            context.getString(R.string.channel_info_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_info_desc)
        }

        manager.createNotificationChannels(listOf(tracking, prompt, info))
    }

    /** The ongoing notification shown while a session runs. */
    fun buildTracking(context: Context, sleepMinutes: Int, score: Int, awaiting: Boolean): Notification {
        val contentPi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (awaiting) {
            context.getString(R.string.tracking_notif_awaiting)
        } else {
            context.getString(R.string.tracking_notif_text, formatDuration(sleepMinutes), score)
        }
        return NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.tracking_notif_title))
            .setContentText(text)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** High-priority full-screen-intent notification that opens [PromptActivity]. */
    fun buildPrompt(context: Context, deadlineEpoch: Long): Notification {
        val fullScreenPi = PendingIntent.getActivity(
            context,
            0,
            PromptActivity.intent(context, deadlineEpoch),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_PROMPT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.prompt_question))
            .setContentText(context.getString(R.string.prompt_notif_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
    }

    fun buildInfo(context: Context, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
    }

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
