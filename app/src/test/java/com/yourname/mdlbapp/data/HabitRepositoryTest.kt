package com.yourname.mdlbapp.data

import com.google.common.base.Verify.verify


import com.yourname.mdlbapp.data.HabitRepo
import com.yourname.mdlbapp.data.HabitRepository

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.any
import java.time.LocalDate
import java.time.LocalTime

class HabitRepositoryTest {
    private lateinit var repoMock: HabitRepo
    private lateinit var repository: HabitRepository

    @Before fun setup() {
        repoMock = mock()
        repository = HabitRepository(repoMock)
    }

    @Test
    fun `updates only when newDueDate differs from old`() {
        val from = LocalDate.of(2025, 7, 24)
        val now  = LocalTime.of(0, 0)

        val habits = listOf<Map<String, Any?>>(
            mapOf<String, Any?>(
                "id"           to "h1",
                "repeat"       to "daily",
                "nextDueDate"  to "2025-07-24",
                "deadline"     to null         // null теперь имеет тип Any?
            ),
            mapOf<String, Any?>(
                "id"           to "h2",
                "repeat"       to "daily",
                "nextDueDate"  to "2025-07-23",
                "deadline"     to null
            )
        )

        repository.updateHabitsNextDueDate(habits, from, now)

        verify(repoMock, never()).updateNextDueDate("h1", "2025-07-24")
        verify(repoMock, times(1)).updateNextDueDate("h2", "2025-07-24")
    }

    @Test
    fun `updates weekly when schedule matches`() {
        val from = LocalDate.of(2025, 7, 23) // Wed
        val now  = LocalTime.MIDNIGHT

        val habits = listOf<Map<String, Any?>>(
            mapOf<String, Any?>(
                "id"           to "w1",
                "repeat"       to "weekly",
                "daysOfWeek"   to listOf("Чт", "Пн"),
                "nextDueDate"  to "2025-07-22",
                "deadline"     to null
            )
        )

        repository.updateHabitsNextDueDate(habits, from, now)

        // Next from Wed(23) in ["Чт","Пн"] = Thu(24)
        verify(repoMock).updateNextDueDate("w1", "2025-07-24")
    }

    @Test
    fun `does not crash on once`() {
        val from = LocalDate.of(2025, 7, 24)
        val now  = LocalTime.MIDNIGHT

        val habits = listOf<Map<String, Any?>>(
            mapOf<String, Any?>(
                "id"           to "o1",
                "repeat"       to "once",
                "oneTimeDate"  to "2025-07-28",
                "nextDueDate"  to "2025-07-28",
                "deadline"     to null
            )
        )

        repository.updateHabitsNextDueDate(habits, from, now)

        // once-привычки не обновляются
        verify(repoMock, never()).updateNextDueDate("o1", any())
    }
}