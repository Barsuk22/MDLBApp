package com.app.mdlbapp.habits.logic

import com.app.mdlbapp.habits.domain.NextDueDate
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class NextDueDateTest {

    private val mon = LocalDate.of(2025, 8, 11) // Пн (реально понедельник)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val fri = mon.plusDays(4)

    @Test fun daily_moves_to_tomorrow_when_due_today() {
        val out = NextDueDate.advanceIfReached(
            repeat = "daily",
            daysOfWeek = emptyList(),
            due = mon,
            today = mon
        )
        assertThat(out).isEqualTo(tue)
    }

    @Test fun weekly_moves_to_next_selected_day_in_same_week() {
        val out = NextDueDate.advanceIfReached(
            repeat = "weekly",
            daysOfWeek = listOf("Пн","Ср","Пт"),
            due = mon,     // был срок в понедельник
            today = mon    // наступил — двигаем
        )
        assertThat(out).isEqualTo(wed) // следующий — среда
    }

    @Test fun weekly_wraps_to_next_week_if_needed() {
        val out = NextDueDate.advanceIfReached(
            repeat = "weekly",
            daysOfWeek = listOf("Пн","Пт"),
            due = fri,     // был срок в пятницу
            today = fri
        )
        assertThat(out).isEqualTo(mon.plusDays(7)) // следующий понедельник
    }

    @Test fun once_does_not_move() {
        val out = NextDueDate.advanceIfReached(
            repeat = "once",
            daysOfWeek = emptyList(),
            due = mon,
            today = mon
        )
        assertThat(out).isEqualTo(mon)
    }

    @Test fun not_reached_yet_keeps_same_date() {
        val out = NextDueDate.advanceIfReached(
            repeat = "daily",
            daysOfWeek = emptyList(),
            due = tue,
            today = mon
        )
        assertThat(out).isEqualTo(tue) // ещё не наступила — не трогаем
    }
}