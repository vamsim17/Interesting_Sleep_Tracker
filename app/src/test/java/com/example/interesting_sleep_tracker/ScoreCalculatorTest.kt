package com.example.interesting_sleep_tracker

import com.example.interesting_sleep_tracker.model.SleepState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun noAnswers_scoreIsZero() {
        assertEquals(0, SleepState.scoreOf(asleepAnswers = 0, totalAnswers = 0))
    }

    @Test
    fun allAsleep_scoreIs100() {
        assertEquals(100, SleepState.scoreOf(asleepAnswers = 6, totalAnswers = 6))
    }

    @Test
    fun threeOfFourAsleep_scoreIs75() {
        assertEquals(75, SleepState.scoreOf(asleepAnswers = 3, totalAnswers = 4))
    }

    @Test
    fun oneOfThreeAsleep_roundsTo33() {
        assertEquals(33, SleepState.scoreOf(asleepAnswers = 1, totalAnswers = 3))
    }

    @Test
    fun sleepTimeAccruesOnlyOnAsleepAnswersAtIntervalAtAnswerTime() {
        // Simulate the service's accrual rule: +interval on an "asleep" answer, using the
        // interval active when that answer was given.
        var sleepMinutes = 0
        var asleep = 0
        var total = 0

        fun answer(isAsleep: Boolean, intervalNow: Int) {
            total++
            if (isAsleep) {
                asleep++
                sleepMinutes += intervalNow
            }
        }

        answer(isAsleep = true, intervalNow = 15)   // +15
        answer(isAsleep = false, intervalNow = 15)  // +0
        answer(isAsleep = true, intervalNow = 30)   // +30 (user widened the interval)

        assertEquals(45, sleepMinutes)
        assertEquals(67, SleepState.scoreOf(asleep, total))
    }
}
