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

                val penaltyAbs = abs((snap.getLong("penalty") ?: 0L).toInt())
                val babyUid = snap.getString("babyUid")

                // сброс серии и отметка, что сегодня штрафовали
                tx.update(habitRef, mapOf(
                    "currentStreak" to 0,
                    "lastPenaltyDate" to LocalDate.now().toString()
                ))

                if (babyUid != null && penaltyAbs > 0) {
                    babyForPenalty = babyUid
                    penaltyToCharge = penaltyAbs
                }
                true
            }.await()

            if (applied && babyForPenalty != null && penaltyToCharge > 0) {
                // единый кошелёк: points/totalPoints
                changePoints(babyForPenalty!!, -penaltyToCharge)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}