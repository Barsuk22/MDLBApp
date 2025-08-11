package com.app.mdlbapp.habits.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DeadlineRuleTest {
    private val today = LocalDate.of(2025, 8, 11)

    @Test fun apply_penalty_basic() {
        val d = DeadlineRule.decide(
            status = "on",
            completedToday = false,
            nextDueDate = today,
            lastPenaltyDate = null,
            penaltyConfigured = 5,
            today = today
        )
        assertThat(d.shouldApply).isTrue()
        assertThat(d.newCurrentStreak).isEqualTo(0)
        assertThat(d.pointsDelta).isEqualTo(-5)
    }

    @Test fun no_penalty_if_completed() {
        val d = DeadlineRule.decide("on", true, today, null, 5, today)
        assertThat(d.shouldApply).isFalse()
    }

    @Test fun no_penalty_if_wrong_day() {
        val d = DeadlineRule.decide("on", false, today.plusDays(1), null, 5, today)
        assertThat(d.shouldApply).isFalse()
    }

    @Test fun no_double_penalty_same_day() {
        val d = DeadlineRule.decide("on", false, today, today, 5, today)
        assertThat(d.shouldApply).isFalse()
    }

    @Test fun no_penalty_if_off() {
        val d = DeadlineRule.decide("off", false, today, null, 5, today)
        assertThat(d.shouldApply).isFalse()
    }
}