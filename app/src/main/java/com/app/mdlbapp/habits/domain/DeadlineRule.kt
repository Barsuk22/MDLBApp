package com.app.mdlbapp.habits.domain

import java.time.LocalDate
import kotlin.math.abs

data class PenaltyDecision(
    val shouldApply: Boolean,
    val newCurrentStreak: Int,
    val pointsDelta: Int // обычно отрицательное число или 0
)

/**
 * Когда пришёл дедлайн:
 * - если статус "on", не выполнено, dueDate == today, и сегодня ещё не штрафовали — применяем.
 * - серия -> 0, баллы -> -abs(penalty).
 */
object DeadlineRule {
    @JvmStatic
    fun decide(
        status: String?,
        completedToday: Boolean,
        nextDueDate: LocalDate?,
        lastPenaltyDate: LocalDate?,
        penaltyConfigured: Int, // ожидаем положительное число "сколько снять"
        today: LocalDate
    ): PenaltyDecision {
        if (status != "on") return PenaltyDecision(false, -1, 0)
        if (completedToday)   return PenaltyDecision(false, -1, 0)
        if (nextDueDate != today) return PenaltyDecision(false, -1, 0)
        if (lastPenaltyDate == today) return PenaltyDecision(false, -1, 0)

        val delta = -abs(penaltyConfigured)
        return PenaltyDecision(true, 0, delta)
    }
}