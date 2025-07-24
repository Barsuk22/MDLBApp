package com.yourname.mdlbapp.data

import com.yourname.mdlbapp.computeNextDueDateForHabit
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HabitRepository(
    private val repo: HabitRepo
) {
    fun updateHabitsNextDueDate(
        habits: List<Map<String, Any?>>,
        fromDate: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now()
    ) {
        val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        habits.forEach { habit ->
            val habitId     = habit["id"] as? String ?: return@forEach
            val repeat      = habit["repeat"] as? String ?: "daily"
            val daysOfWeek  = habit["daysOfWeek"] as? List<String>
            val oneTimeDate = habit["oneTimeDate"] as? String
            val deadline    = habit["deadline"] as? String

            val newDue = computeNextDueDateForHabit(
                repeatMode  = repeat,
                daysOfWeek  = daysOfWeek,
                oneTimeDate = oneTimeDate,
                deadline    = deadline,
                fromDate    = fromDate,
                nowTime     = nowTime
            ) ?: return@forEach

            val oldDateStr = habit["nextDueDate"] as? String
            val oldDate    = oldDateStr?.let { LocalDate.parse(it, isoFmt) }

            if (oldDate != newDue) {
                repo.updateNextDueDate(habitId, newDue.format(isoFmt))
            }
        }
    }
}
