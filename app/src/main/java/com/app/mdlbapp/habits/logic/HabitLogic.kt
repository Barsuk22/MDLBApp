package com.app.mdlbapp.habits.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Плюсик активен, если:
 *  - ещё не выполнено сегодня,
 *  - сегодня тот самый день,
 *  - текущее время до дедлайна.
 */
object HabitLogic {
    @JvmStatic
    fun canComplete(
        completedToday: Boolean,
        nextDueDate: LocalDate?,
        deadline: LocalTime?,
        now: LocalDateTime
    ): Boolean {
        if (completedToday) return false
        val isToday = nextDueDate == now.toLocalDate()
        val beforeDeadline = deadline?.let { now.toLocalTime().isBefore(it) } ?: true
        return isToday && beforeDeadline
    }
}