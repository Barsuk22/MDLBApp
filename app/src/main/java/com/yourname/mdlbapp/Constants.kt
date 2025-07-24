//Файл Constants.kt

package com.yourname.mdlbapp

import java.time.DayOfWeek


object Constants {
    val RuleCategories = listOf(
        "Поведение",
        "Речь",
        "Контакт",
        "Физика",
        "Здоровье",
        "Дисциплина",
        "Прочее"
    )

    val ruToDayOfWeek: Map<String, DayOfWeek> = mapOf(
        "Пн" to DayOfWeek.MONDAY,
        "Вт" to DayOfWeek.TUESDAY,
        "Ср" to DayOfWeek.WEDNESDAY,
        "Чт" to DayOfWeek.THURSDAY,
        "Пт" to DayOfWeek.FRIDAY,
        "Сб" to DayOfWeek.SATURDAY,
        "Вс" to DayOfWeek.SUNDAY
    )

    /** DayOfWeek → русская аббревиатура */
    val dayOfWeekToRu: Map<DayOfWeek, String> = ruToDayOfWeek.entries
        .associate { (ru, dow) -> dow to ru }
}