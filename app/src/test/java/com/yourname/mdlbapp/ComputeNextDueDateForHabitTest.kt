package com.yourname.mdlbapp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ComputeNextDueDateForHabitTest {

    // Ежедневные сценарии
    @Test fun daily_before_deadline() {
        val got = computeNextDueDateForHabit(
            repeatMode = "daily",
            daysOfWeek = null,
            oneTimeDate= null,
            deadline   = "12:00",
            fromDate   = LocalDate.of(2025,7,24),
            nowTime    = LocalTime.of(10,0)
        )
        assertEquals(LocalDate.of(2025,7,24), got)
    }
    @Test fun daily_after_deadline() {
        val got = computeNextDueDateForHabit(
            "daily", null, null, "00:00",
            LocalDate.of(2025,7,24), LocalTime.of(1,0)
        )
        assertEquals(LocalDate.of(2025,7,25), got)
    }

    // Weekly
    @Test fun weekly_wrap_around() {
        val got = computeNextDueDateForHabit(
            "weekly",
            daysOfWeek = listOf("Пн","Ср"),
            oneTimeDate= null,
            deadline   = null,
            fromDate   = LocalDate.of(2025,7,27), // Вс
            nowTime    = LocalTime.MIDNIGHT
        )
        assertEquals(LocalDate.of(2025,7,28), got) // Пн
    }

    // Once
    @Test fun once_valid() {
        val got = computeNextDueDateForHabit(
            "once", null, "2025-07-30", null,
            LocalDate.now(), LocalTime.now()
        )
        assertEquals(LocalDate.of(2025,7,30), got)
    }
    @Test fun once_invalid() {
        val got = computeNextDueDateForHabit(
            "once", null, "30.07.2025", null,
            LocalDate.now(), LocalTime.now()
        )
        assertNull(got)
    }

    // Неподдерживаемый repeat
    @Test fun unknown_repeat() {
        val got = computeNextDueDateForHabit(
            "foobar", null, null, null,
            LocalDate.now(), LocalTime.now()
        )
        assertNull(got)
    }
}