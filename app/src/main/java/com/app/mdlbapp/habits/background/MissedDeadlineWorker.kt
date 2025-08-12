package com.app.mdlbapp.habits.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.math.abs
import com.app.mdlbapp.reward.changePoints
import com.google.firebase.firestore.FieldValue

class MissedDeadlineWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val habitId = inputData.getString("habitId") ?: return Result.failure()
        val db = Firebase.firestore
        val habitRef = db.collection("habits").document(habitId)

        var babyForPenalty: String? = null
        var penaltyToCharge = 0

        var penalizedDateCaptured: String? = null

        var mommyUidSnap: String? = null
        var habitTitle: String = ""

        return try {
            val applied = db.runTransaction { tx ->
                val snap = tx.get(habitRef)

                mommyUidSnap = snap.getString("mommyUid")
                habitTitle = snap.getString("title") ?: ""

                if (!snap.exists()) return@runTransaction false

                val completed = snap.getBoolean("completedToday") ?: false
                val status = snap.getString("status") ?: "on"
                if (completed || status != "on") return@runTransaction false

                val dueStr = snap.getString("nextDueDate") ?: LocalDate.now().toString()
                val lastPenalized = snap.getString("lastPenaltyDate")
                if (lastPenalized == dueStr) return@runTransaction false

// сохраняем день, за который наказываем
                penalizedDateCaptured = dueStr

                val penaltyAbs = kotlin.math.abs((snap.getLong("penalty") ?: 0L).toInt())
                val babyUid = snap.getString("babyUid")

// один раз сбрасываем серию и отмечаем дату штрафа
                tx.update(habitRef, mapOf(
                    "currentStreak" to 0L,     // придерживаемся int
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

                val dateForLog = penalizedDateCaptured ?: LocalDate.now().toString()
                try {
                    Firebase.firestore.collection("habits").document(habitId)
                        .collection("logs").document(dateForLog)
                        .set(
                            mapOf(
                                "status" to "missed",
                                "pointsDelta" to -penaltyToCharge,
                                "source" to "deadline",
                                "at" to FieldValue.serverTimestamp(),
                                "habitId" to habitId,
                                "habitTitle" to habitTitle,
                                "mommyUid" to (mommyUidSnap ?: ""),
                                "babyUid" to babyForPenalty!!
                            )
                        ).await()
                    Log.d("HabitLogsRepo","missed-log written for $habitId/$dateForLog")
                } catch (e: Exception) {
                    Log.e("HabitLogsRepo","write missed log failed", e)
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}