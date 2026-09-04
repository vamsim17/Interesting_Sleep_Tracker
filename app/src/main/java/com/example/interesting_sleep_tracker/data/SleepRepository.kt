package com.example.interesting_sleep_tracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.interesting_sleep_tracker.model.Phase
import com.example.interesting_sleep_tracker.model.SleepState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleep_prefs")

/**
 * Single source of truth for sleep-tracking state.
 *
 * Holds the live [state] the UI observes and mirrors it to DataStore so a session survives the
 * process being killed (a pending [android.app.AlarmManager] alarm re-launches the service, which
 * reads the restored state back).
 */
object SleepRepository {

    private object Keys {
        val INTERVAL = intPreferencesKey("interval_minutes")
        val LAST_MINUTES = intPreferencesKey("last_session_minutes")
        val LAST_SCORE = intPreferencesKey("last_session_score")
        val PHASE = intPreferencesKey("phase_ordinal")
        val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
        val ASLEEP_COUNT = intPreferencesKey("asleep_count")
        val TOTAL_ANSWERS = intPreferencesKey("total_answers")
        val PROMPT_DEADLINE = longPreferencesKey("prompt_deadline_epoch")
        val HAS_LAST = booleanPreferencesKey("has_last_session")
    }

    private lateinit var appContext: Context

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    /** Call once from [android.app.Application.onCreate]. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        runBlocking { _state.value = readFromDisk() }
    }

    /** Apply a transform to the live state and persist the result. */
    fun update(transform: (SleepState) -> SleepState) {
        val next = transform(_state.value)
        _state.value = next
        runBlocking { writeToDisk(next) }
    }

    private suspend fun readFromDisk(): SleepState {
        val prefs = appContext.dataStore.data.first()
        val phase = Phase.entries.getOrElse(prefs[Keys.PHASE] ?: 0) { Phase.IDLE }
        // A session that was mid-flight when the process died resumes as TRACKING; a fired alarm
        // will drive it forward. Anything else collapses to IDLE.
        val restoredPhase = when (phase) {
            Phase.TRACKING, Phase.AWAITING_ANSWER -> Phase.TRACKING
            else -> Phase.IDLE
        }
        val hasLast = prefs[Keys.HAS_LAST] ?: false
        return SleepState(
            phase = restoredPhase,
            sleepMinutes = if (restoredPhase == Phase.IDLE) 0 else prefs[Keys.SLEEP_MINUTES] ?: 0,
            score = if (restoredPhase == Phase.IDLE) 0 else {
                SleepState.scoreOf(prefs[Keys.ASLEEP_COUNT] ?: 0, prefs[Keys.TOTAL_ANSWERS] ?: 0)
            },
            asleepCount = if (restoredPhase == Phase.IDLE) 0 else prefs[Keys.ASLEEP_COUNT] ?: 0,
            totalAnswers = if (restoredPhase == Phase.IDLE) 0 else prefs[Keys.TOTAL_ANSWERS] ?: 0,
            intervalMinutes = SleepState.coerceInterval(
                prefs[Keys.INTERVAL] ?: SleepState.DEFAULT_INTERVAL_MINUTES
            ),
            promptDeadlineEpoch = null,
            lastSessionMinutes = if (hasLast) prefs[Keys.LAST_MINUTES] else null,
            lastSessionScore = if (hasLast) prefs[Keys.LAST_SCORE] else null,
        )
    }

    private suspend fun writeToDisk(s: SleepState) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.INTERVAL] = s.intervalMinutes
            prefs[Keys.PHASE] = s.phase.ordinal
            prefs[Keys.SLEEP_MINUTES] = s.sleepMinutes
            prefs[Keys.ASLEEP_COUNT] = s.asleepCount
            prefs[Keys.TOTAL_ANSWERS] = s.totalAnswers
            prefs[Keys.PROMPT_DEADLINE] = s.promptDeadlineEpoch ?: 0L
            if (s.lastSessionMinutes != null && s.lastSessionScore != null) {
                prefs[Keys.HAS_LAST] = true
                prefs[Keys.LAST_MINUTES] = s.lastSessionMinutes
                prefs[Keys.LAST_SCORE] = s.lastSessionScore
            }
        }
    }
}
