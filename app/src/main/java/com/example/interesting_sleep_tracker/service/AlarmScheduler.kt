package com.example.interesting_sleep_tracker.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Wraps [AlarmManager] exact alarms. A coroutine timer inside the service is not reliable across
 * Doze for 1..60 minute gaps, so wakeups go through the OS instead.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedulePrompt(triggerAtMillis: Long) =
        scheduleExact(triggerAtMillis, SleepAlarmReceiver.ACTION_PROMPT_DUE, REQ_PROMPT)

    fun scheduleTimeout(triggerAtMillis: Long) =
        scheduleExact(triggerAtMillis, SleepAlarmReceiver.ACTION_TIMEOUT, REQ_TIMEOUT)

    fun cancelPrompt() = cancel(SleepAlarmReceiver.ACTION_PROMPT_DUE, REQ_PROMPT)

    fun cancelTimeout() = cancel(SleepAlarmReceiver.ACTION_TIMEOUT, REQ_TIMEOUT)

    fun cancelAll() {
        cancelPrompt()
        cancelTimeout()
    }

    // USE_EXACT_ALARM is declared in the manifest (this is an alarm-clock-style app), so no
    // runtime SCHEDULE_EXACT_ALARM check is required.
    @SuppressLint("MissingPermission")
    private fun scheduleExact(triggerAtMillis: Long, action: String, requestCode: Int) {
        val pi = pendingIntent(action, requestCode)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    private fun cancel(action: String, requestCode: Int) {
        alarmManager.cancel(pendingIntent(action, requestCode))
    }

    private fun pendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SleepAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQ_PROMPT = 1001
        const val REQ_TIMEOUT = 1002
    }
}
