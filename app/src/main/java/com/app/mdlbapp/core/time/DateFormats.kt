package com.app.mdlbapp.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun formatDateLabel(dateStr: String): String {
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val date = try {
        LocalDate.parse(dateStr, inputFormatter)
    } catch (e: Exception) {
        return dateStr
    }

    return when {
        date == today -> "Сегодня"
        date == tomorrow -> "Завтра"
        date.isAfter(today.plusDays(1)) && date <= today.with(DayOfWeek.SUNDAY) -> {
            // Только если внутри этой недели
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
            dayOfWeek.replaceFirstChar { it.uppercaseChar() }
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
            date.format(formatter).replaceFirstChar { it.uppercaseChar() }
        }
    }
}