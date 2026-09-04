package com.example.interesting_sleep_tracker.model

/** Lifecycle phase of a sleep-tracking session. */
enum class Phase {
    /** No session running. */
    IDLE,

    /** Session running, waiting for the next scheduled prompt. */
    TRACKING,

    /** A prompt has fired and we are waiting for the user to answer. */
    AWAITING_ANSWER,

    /** The user tapped "I'm awake"; a finished summary is available. */
    FINISHED,
}

/**
 * Snapshot of everything the UI needs to render. Immutable; the service publishes a new copy on
 * every transition.
 *
 * @param sleepMinutes accumulated sleep time in minutes (only grows on an "asleep" answer)
 * @param score 0..100, share of answers that were "asleep"
 * @param intervalMinutes current prompt interval (1..60), editable any time
 * @param promptDeadlineEpoch when the open prompt auto-expires (only set in [Phase.AWAITING_ANSWER])
 * @param lastSessionMinutes sleep time of the most recently finished session, or null
 * @param lastSessionScore score of the most recently finished session, or null
 */
data class SleepState(
    val phase: Phase = Phase.IDLE,
    val sleepMinutes: Int = 0,
    val score: Int = 0,
    val asleepCount: Int = 0,
    val totalAnswers: Int = 0,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val promptDeadlineEpoch: Long? = null,
    val lastSessionMinutes: Int? = null,
    val lastSessionScore: Int? = null,
) {
    val isSessionRunning: Boolean
        get() = phase == Phase.TRACKING || phase == Phase.AWAITING_ANSWER

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 15
        const val MIN_INTERVAL_MINUTES = 1
        const val MAX_INTERVAL_MINUTES = 60

        /** Minutes the user has to answer a prompt before the session resets. */
        const val ANSWER_TIMEOUT_MINUTES = 5

        /** Pure score calculation, shared by the service and unit tests. */
        fun scoreOf(asleepAnswers: Int, totalAnswers: Int): Int {
            if (totalAnswers <= 0) return 0
            return Math.round(asleepAnswers * 100f / totalAnswers)
        }

        fun coerceInterval(minutes: Int): Int =
            minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }
}
