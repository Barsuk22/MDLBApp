package com.yourname.mdlbapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertNull
import org.junit.Assert.assertNull

class HabitDateUtilsTest {

    // 1.1.1 До дедлайна → сегодня
    @Test
    fun `daily before deadline returns today`() {
        val today = LocalDate.of(2025, 7, 24)  // Четверг
        val nowTime = LocalTime.of(10, 0)        // 10:00
        val deadline = "12:00"

        val got = getNextDueDate(
            repeatMode = "daily",
            daysOfWeek = null,
            oneTimeDate = null,
            deadline = deadline,
            today = today,
            nowTime = nowTime
        )

        assertEquals("2025-07-24", got)
    }

    // 1.1.2 Точно в дедлайн → всё ещё сегодня
    @Test
    fun `daily exactly at deadline returns today`() {
        val today = LocalDate.of(2025, 7, 24)
        val nowTime = LocalTime.of(12, 0)        // ровно дедлайн
        val deadline = "12:00"

        val got = getNextDueDate("daily", null, null, deadline, today, nowTime)
        assertEquals("2025-07-24", got)
    }

    // 1.1.3 Сразу после дедлайна → завтра
    @Test
    fun `daily after deadline returns tomorrow`() {
        val today = LocalDate.of(2025, 7, 24)
        val nowTime = LocalTime.of(12, 1)        // 12:01
        val deadline = "12:00"

        val got = getNextDueDate("daily", null, null, deadline, today, nowTime)
        assertEquals("2025-07-25", got)
    }

    // 1.1.4 Без дедлайна → всегда сегодня
    @Test
    fun `daily without deadline returns today`() {
        val today = LocalDate.of(2025, 7, 24)
        val nowTime = LocalTime.of(23, 59)
        val got = getNextDueDate("daily", null, null, null, today, nowTime)
        assertEquals("2025-07-24", got)
    }

    // 1.2.1 Сегодня входит в расписание и до дедлайна → сегодня
    @Test
    fun `weekly today in schedule before deadline returns today`() {
        val today = LocalDate.of(2025, 7, 24)  // Четверг
        val nowTime = LocalTime.of(10, 0)        // 10:00
        val deadline = "12:00"
        val days = listOf("Чт", "Пн", "Вс")

        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = days,
            oneTimeDate = null,
            deadline = deadline,
            today = today,
            nowTime = nowTime
        )

        assertEquals("2025-07-24", got)  // сегодня
    }

    // 1.2.2 Сегодня входит, но уже после дедлайна → следующий из списка
    @Test
    fun `weekly today in schedule after deadline returns next`() {
        val today = LocalDate.of(2025, 7, 24)  // Четверг
        val nowTime = LocalTime.of(13, 0)        // 13:00
        val deadline = "12:00"
        val days = listOf("Чт", "Пн", "Вс")

        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = days,
            oneTimeDate = null,
            deadline = deadline,
            today = today,
            nowTime = nowTime
        )

        // После "Чт" идёт "Вс" (через 3 дня)
        assertEquals("2025-07-27", got)
    }

    // 1.2.3 Сегодня не входит → ближайший в будущем
    @Test
    fun `weekly today not in schedule returns next in same week`() {
        val today = LocalDate.of(2025, 7, 23)  // Среда
        val nowTime = LocalTime.of(8, 0)
        val deadline: String? = null
        val days = listOf("Чт", "Пн", "Вс")

        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = days,
            oneTimeDate = null,
            deadline = deadline,
            today = today,
            nowTime = nowTime
        )

        // Ближайший из ["Чт","Пн","Вс"] после среды — четверг (24 июля)
        assertEquals("2025-07-24", got)
    }

    // 1.2.4 Wrap-around (конец недели → начало следующей)
    @Test
    fun `weekly wrap around to next week`() {
        val today = LocalDate.of(2025, 7, 27)  // Воскресенье
        val nowTime = LocalTime.of(9, 0)
        val deadline: String? = null
        val days = listOf("Пн", "Ср")         // Пн и Ср

        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = days,
            oneTimeDate = null,
            deadline = deadline,
            today = today,
            nowTime = nowTime
        )

        // После Вс ожидаем Пн, но это уже на следующей неделе (28 июля)
        assertEquals("2025-07-28", got)
    }

    // 1.2.5 Пустой список → null
    @Test
    fun `weekly empty days returns null`() {
        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = emptyList(),
            oneTimeDate = null,
            deadline = null,
            today = LocalDate.now(),
            nowTime = LocalTime.now()
        )
        assertNull(got)
    }

    // 1.2.6 Неверная аббревиатура → null
    @Test
    fun `weekly invalid abbreviations returns null`() {
        val got = getNextDueDate(
            repeatMode = "weekly",
            daysOfWeek = listOf("XX", "YY"),
            oneTimeDate = null,
            deadline = null,
            today = LocalDate.now(),
            nowTime = LocalTime.now()
        )
        assertNull(got)
    }

    // 1.3.1 Once: date == today
    @Test
    fun `once when date equals today returns today`() {
        val today      = LocalDate.of(2025, 7, 24)
        val nowTime    = LocalTime.of(0, 0)
        val oneTimeIso = "2025-07-24"

        val got = getNextDueDate(
            repeatMode  = "once",
            daysOfWeek  = null,
            oneTimeDate = oneTimeIso,
            deadline    = null,
            today       = today,
            nowTime     = nowTime
        )

        assertEquals("2025-07-24", got)
    }

    // 1.3.2 Once: date in future
    @Test
    fun `once when date in future returns that date`() {
        val today      = LocalDate.of(2025, 7, 24)
        val nowTime    = LocalTime.of(0, 0)
        val oneTimeIso = "2025-07-26"

        val got = getNextDueDate(
            repeatMode  = "once",
            daysOfWeek  = null,
            oneTimeDate = oneTimeIso,
            deadline    = null,
            today       = today,
            nowTime     = nowTime
        )

        assertEquals("2025-07-26", got)
    }

    // 1.3.3 Once: date in past
    @Test
    fun `once when date in past returns that date`() {
        val today      = LocalDate.of(2025, 7, 24)
        val nowTime    = LocalTime.of(0, 0)
        val oneTimeIso = "2025-07-22"

        val got = getNextDueDate(
            repeatMode  = "once",
            daysOfWeek  = null,
            oneTimeDate = oneTimeIso,
            deadline    = null,
            today       = today,
            nowTime     = nowTime
        )

        assertEquals("2025-07-22", got)
    }

    // 1.3.4 Once: invalid ISO format
    @Test
    fun `once when oneTimeDate invalid format returns null`() {
        val today      = LocalDate.of(2025, 7, 24)
        val nowTime    = LocalTime.of(0, 0)
        val oneTimeIso = "24-07-2025"  // не "YYYY-MM-DD"

        val got = getNextDueDate(
            repeatMode  = "once",
            daysOfWeek  = null,
            oneTimeDate = oneTimeIso,
            deadline    = null,
            today       = today,
            nowTime     = nowTime
        )

        assertNull(got)
    }

}