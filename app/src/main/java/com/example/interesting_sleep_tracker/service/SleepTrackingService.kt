package com.example.interesting_sleep_tracker.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.interesting_sleep_tracker.R
import com.example.interesting_sleep_tracker.data.SleepRepository
import com.example.interesting_sleep_tracker.model.Phase
import com.example.interesting_sleep_tracker.model.SleepState
import com.example.interesting_sleep_tracker.ui.PromptActivity

/**
 * Owns the sleep-session state machine. Timing is delegated to [AlarmScheduler]; this service just
 * reacts to alarms and to user actions forwarded from the UI, publishing each transition through
 * [SleepRepository].
 */
class SleepTrackingService : Service() {

    private val repo get() = SleepRepository
    private lateinit var scheduler: AlarmScheduler
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Wall-clock anchor for the pending prompt, so an interval change can reschedule it. */
    private var scheduleAnchorMillis: Long = 0L

    private var ringtone: Ringtone? = null
    private val stopAlertRunnable = Runnable { stopAlert() }

    private val notifications by lazy { NotificationManagerCompat.from(this) }

    private fun postNotification(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifications.notify(id, notification)
    }

    override fun onCreate() {
        super.onCreate()
        scheduler = AlarmScheduler(this)
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always hold the foreground slot first; terminal actions release it below.
        promoteToForeground()

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PROMPT_DUE -> handlePromptDue()
            ACTION_ANSWER -> handleAnswer(intent.getBooleanExtra(EXTRA_ASLEEP, false))
            ACTION_TIMEOUT -> handleTimeout()
            ACTION_WAKE -> handleWake()
            ACTION_SET_INTERVAL -> handleSetInterval(
                intent.getIntExtra(EXTRA_INTERVAL, SleepState.DEFAULT_INTERVAL_MINUTES)
            )
            else -> if (!repo.state.value.isSessionRunning) stopSelfAndForeground()
        }
        return START_STICKY
    }

    // region actions

    private fun handleStart() {
        val interval = repo.state.value.intervalMinutes
        scheduleAnchorMillis = System.currentTimeMillis()
        repo.update {
            SleepState(
                phase = Phase.TRACKING,
                intervalMinutes = interval,
                lastSessionMinutes = it.lastSessionMinutes,
                lastSessionScore = it.lastSessionScore,
            )
        }
        scheduler.schedulePrompt(scheduleAnchorMillis + interval.minutesToMillis())
        refreshForeground()
    }

    private fun handlePromptDue() {
        if (!repo.state.value.isSessionRunning) {
            stopSelfAndForeground()
            return
        }
        val deadline = System.currentTimeMillis() +
            SleepState.ANSWER_TIMEOUT_MINUTES.minutesToMillis()
        repo.update { it.copy(phase = Phase.AWAITING_ANSWER, promptDeadlineEpoch = deadline) }
        scheduler.scheduleTimeout(deadline)

        postNotification(SleepNotifications.ID_PROMPT, SleepNotifications.buildPrompt(this, deadline))
        startActivity(PromptActivity.intent(this, deadline).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        startAlert()
        refreshForeground()
    }

    private fun handleAnswer(asleep: Boolean) {
        val current = repo.state.value
        if (current.phase != Phase.AWAITING_ANSWER) return
        scheduler.cancelTimeout()
        stopAlert()
        notifications.cancel(SleepNotifications.ID_PROMPT)

        val asleepCount = current.asleepCount + if (asleep) 1 else 0
        val totalAnswers = current.totalAnswers + 1
        val sleepMinutes = current.sleepMinutes + if (asleep) current.intervalMinutes else 0
        repo.update {
            it.copy(
                phase = Phase.TRACKING,
                asleepCount = asleepCount,
                totalAnswers = totalAnswers,
                sleepMinutes = sleepMinutes,
                score = SleepState.scoreOf(asleepCount, totalAnswers),
                promptDeadlineEpoch = null,
            )
        }
        scheduleAnchorMillis = System.currentTimeMillis()
        scheduler.schedulePrompt(
            scheduleAnchorMillis + repo.state.value.intervalMinutes.minutesToMillis()
        )
        refreshForeground()
    }

    private fun handleTimeout() {
        stopAlert()
        scheduler.cancelAll()
        notifications.cancel(SleepNotifications.ID_PROMPT)
        repo.update {
            SleepState(
                phase = Phase.IDLE,
                intervalMinutes = it.intervalMinutes,
                lastSessionMinutes = it.lastSessionMinutes,
                lastSessionScore = it.lastSessionScore,
            )
        }
        postNotification(
            SleepNotifications.ID_INFO,
            SleepNotifications.buildInfo(this, getString(R.string.reset_notif_text)),
        )
        stopSelfAndForeground()
    }

    private fun handleWake() {
        stopAlert()
        scheduler.cancelAll()
        notifications.cancel(SleepNotifications.ID_PROMPT)
        val s = repo.state.value
        repo.update {
            it.copy(
                phase = Phase.FINISHED,
                promptDeadlineEpoch = null,
                lastSessionMinutes = s.sleepMinutes,
                lastSessionScore = s.score,
            )
        }
        stopSelfAndForeground()
    }

    private fun handleSetInterval(minutesRaw: Int) {
        val minutes = SleepState.coerceInterval(minutesRaw)
        val wasTracking = repo.state.value.phase == Phase.TRACKING
        repo.update { it.copy(intervalMinutes = minutes) }
        if (wasTracking) {
            val nextTrigger = (scheduleAnchorMillis + minutes.minutesToMillis())
                .coerceAtLeast(System.currentTimeMillis() + 1_000L)
            scheduler.schedulePrompt(nextTrigger)
        }
        if (!repo.state.value.isSessionRunning) stopSelfAndForeground() else refreshForeground()
    }

    // endregion

    // region foreground + alert

    private fun promoteToForeground() {
        val s = repo.state.value
        val notification = SleepNotifications.buildTracking(
            this, s.sleepMinutes, s.score, s.phase == Phase.AWAITING_ANSWER,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, SleepNotifications.ID_TRACKING, notification, type)
    }

    private fun refreshForeground() {
        val s = repo.state.value
        postNotification(
            SleepNotifications.ID_TRACKING,
            SleepNotifications.buildTracking(
                this, s.sleepMinutes, s.score, s.phase == Phase.AWAITING_ANSWER,
            ),
        )
    }

    private fun stopSelfAndForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAlert() {
        mainHandler.removeCallbacks(stopAlertRunnable)
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        }
        val vibrator = getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 400, 250, 400, 250, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
        // Safety cap: never let the alarm ring longer than the answer window.
        mainHandler.postDelayed(
            stopAlertRunnable,
            SleepState.ANSWER_TIMEOUT_MINUTES.minutesToMillis(),
        )
    }

    private fun stopAlert() {
        mainHandler.removeCallbacks(stopAlertRunnable)
        runCatching { ringtone?.stop() }
        ringtone = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    // endregion

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }

    private fun Int.minutesToMillis(): Long = this * 60_000L

    companion object {
        const val ACTION_START = "com.example.interesting_sleep_tracker.service.START"
        const val ACTION_PROMPT_DUE = "com.example.interesting_sleep_tracker.service.PROMPT_DUE"
        const val ACTION_ANSWER = "com.example.interesting_sleep_tracker.service.ANSWER"
        const val ACTION_TIMEOUT = "com.example.interesting_sleep_tracker.service.TIMEOUT"
        const val ACTION_WAKE = "com.example.interesting_sleep_tracker.service.WAKE"
        const val ACTION_SET_INTERVAL = "com.example.interesting_sleep_tracker.service.SET_INTERVAL"

        const val EXTRA_ASLEEP = "extra_asleep"
        const val EXTRA_INTERVAL = "extra_interval"

        private fun base(context: Context, action: String) =
            Intent(context, SleepTrackingService::class.java).setAction(action)

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, base(context, ACTION_START))

        fun wake(context: Context) =
            ContextCompat.startForegroundService(context, base(context, ACTION_WAKE))

        fun answer(context: Context, asleep: Boolean) = ContextCompat.startForegroundService(
            context, base(context, ACTION_ANSWER).putExtra(EXTRA_ASLEEP, asleep),
        )

        fun setInterval(context: Context, minutes: Int) = ContextCompat.startForegroundService(
            context, base(context, ACTION_SET_INTERVAL).putExtra(EXTRA_INTERVAL, minutes),
        )
    }
}
