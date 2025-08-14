package com.app.mdlbapp.habits.logic

import com.app.mdlbapp.habits.domain.HabitLogic
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.*

class HabitLogicTest {
    private val base = LocalDate.of(2025, 8, 11)
    private val nine = LocalTime.of(9, 0)
    private val ten  = LocalTime.of(10, 0)

    @Test fun plus_active_before_deadline() {
        val ok = HabitLogic.canComplete(false, base, ten, LocalDateTime.of(base, nine))
        assertThat(ok).isTrue()
    }

    @Test fun plus_inactive_after_deadline() {
        val ok = HabitLogic.canComplete(false, base, ten, LocalDateTime.of(base, LocalTime.of(10,1)))
        assertThat(ok).isFalse()
    }

    @Test fun plus_inactive_already_completed() {
        val ok = HabitLogic.canComplete(true, base, ten, LocalDateTime.of(base, nine))
        assertThat(ok).isFalse()
    }

    @Test fun plus_inactive_wrong_day() {
        val ok = HabitLogic.canComplete(false, base.plusDays(1), ten, LocalDateTime.of(base, nine))
        assertThat(ok).isFalse()
    }
}