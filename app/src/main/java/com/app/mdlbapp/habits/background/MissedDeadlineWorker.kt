package com.app.mdlbapp.habits.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.abs
import com.app.mdlbapp.reward.changePoints

class MissedDeadlineWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val habitId = inputData.getString("habitId") ?: return Result.failure()
        val db = Firebase.firestore
        val habitRef = db.collection("habits").document(habitId)

        var babyForPenalty: String? = null
        var penaltyToCharge = 0

        return try {
            val applied = db.runTransaction { tx ->
                val snap = tx.get(habitRef)
                if (!snap.exists()) return@runTransaction false

                val completed = snap.getBoolean("completedToday") ?: false
                val status = snap.getString("status") ?: "on"
                if (completed || status != "on") return@runTransaction false

                val penaltyAbs = kotlin.math.abs((snap.getLong("penalty") ?: 0L).toInt())
                val babyUid = snap.getString("babyUid")

                // важное: фиксируем штраф именно за due-день, а не за "сейчас"
                val dueStr = snap.getString("nextDueDate") ?: LocalDate.now().toString()

                // сброс серии и отметка, что за due-день штрафовали
                tx.update(habitRef, mapOf(
                    "currentStreak" to 0L,                // ← Long!
                    "lastPenaltyDate" to dueStr
                ))

                if (babyUid != null && penaltyAbs > 0) {
                    babyForPenalty = babyUid
                    penaltyToCharge = penaltyAbs
                }
                true
            }.await()

            if (applied && babyForPenalty != null && penaltyToCharge > 0) {
                changePoints(babyForPenalty!!, -penaltyToCharge)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}