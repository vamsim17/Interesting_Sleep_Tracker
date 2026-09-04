package com.example.interesting_sleep_tracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Turns an [android.app.AlarmManager] broadcast into a foreground-service start. */
class SleepAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_PROMPT_DUE -> SleepTrackingService.ACTION_PROMPT_DUE
            ACTION_TIMEOUT -> SleepTrackingService.ACTION_TIMEOUT
            else -> return
        }
        val serviceIntent = Intent(context, SleepTrackingService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        const val ACTION_PROMPT_DUE = "com.example.interesting_sleep_tracker.alarm.PROMPT_DUE"
        const val ACTION_TIMEOUT = "com.example.interesting_sleep_tracker.alarm.TIMEOUT"
    }
}
