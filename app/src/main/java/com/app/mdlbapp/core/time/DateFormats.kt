package com.app.mdlbapp.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatDateLabel(dateStr: String, today: LocalDate = LocalDate.now()): String {
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val date = try {
        LocalDate.parse(dateStr, inputFormatter)
    } catch (_: Exception) {
        return dateStr
    }

    val tomorrow = today.plusDays(1)

    return when {
        date == today -> "Сегодня"
        date == tomorrow -> "Завтра"
        // внутри текущей недели (до воскресенья включительно) — показываем день недели
        date.isAfter(tomorrow) && !date.isAfter(today.with(DayOfWeek.SUNDAY)) -> {
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
            dayOfWeek.replaceFirstChar { it.uppercaseChar() }
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
            date.format(formatter).replaceFirstChar { it.uppercaseChar() }
        }
    }
}