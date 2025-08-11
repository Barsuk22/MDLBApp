package com.app.mdlbapp.habits.logic

import java.time.DayOfWeek
import java.time.LocalDate

object NextDueDate {

    // соответствие русских сокращений дням недели
    private val ru2dow = mapOf(
        "Пн" to DayOfWeek.MONDAY,
        "Вт" to DayOfWeek.TUESDAY,
        "Ср" to DayOfWeek.WEDNESDAY,
        "Чт" to DayOfWeek.THURSDAY,
        "Пт" to DayOfWeek.FRIDAY,
        "Сб" to DayOfWeek.SATURDAY,
        "Вс" to DayOfWeek.SUNDAY
    )

    /**
     * Вернёт новую дату, если текущая уже наступила (или прошла).
     * Если до дедлайна ещё не дошли — вернём ту же дату.
     *
     * repeat: "daily" | "weekly" | "once"
     * daysOfWeek: список типа ["Пн","Ср","Пт"] — только для weekly
     * due: текущая nextDueDate (YYYY-MM-DD → LocalDate)
     * today: "сегодня" (для тестов можно подставлять любую)
     */
    fun advanceIfReached(
        repeat: String,
        daysOfWeek: List<String>,
        due: LocalDate,
        today: LocalDate
    ): LocalDate {
        if (due.isAfter(today)) return due // ещё не наступила — ничего не делаем

        return when (repeat) {
            "daily" -> due.plusDays(1)
            "weekly" -> nextWeekly(due, daysOfWeek)
            else -> due // "once" — не двигаем (разовая)
        }
    }

    // найти следующий выбранный день недели после 'due'
    private fun nextWeekly(due: LocalDate, daysOfWeek: List<String>): LocalDate {
        val set = daysOfWeek.mapNotNull { ru2dow[it] }.toSet()
        require(set.isNotEmpty()) { "daysOfWeek пустой для weekly" }

        var d = due.plusDays(1)
        while (true) {
            if (set.contains(d.dayOfWeek)) return d
            d = d.plusDays(1)
        }
    }
}