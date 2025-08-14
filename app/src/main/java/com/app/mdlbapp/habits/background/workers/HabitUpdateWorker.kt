package com.app.mdlbapp.habits.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.habits.data.updateHabitsNextDueDate
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HabitUpdateWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val udoc = Firebase.firestore.collection("users").document(me).get().await()
        if (udoc.getString("role") != "Baby") return Result.success()   // ← маме нельзя
        val babyUid = me

        // 1) читаем пояс Малыша (или дефолтимся на системный)
        val userDoc = Firebase.firestore.collection("users").document(babyUid).get().await()
        val zone = runCatching { ZoneId.of(userDoc.getString("timezone") ?: "") }.getOrNull()
            ?: ZoneId.systemDefault()

        // 2) грузим активные привычки, как и раньше
        val snap = Firebase.firestore.collection("habits")
            .whereEqualTo("babyUid", babyUid).whereEqualTo("status", "on").get().await()
        val habits = snap.documents.mapNotNull { it.data?.plus("id" to it.id) }

        // 3) обновляем с «сегодня» по часовому поясу Малыша
        updateHabitsNextDueDate(
            habits = habits,
            fromDate = LocalDate.now(zone),
            rollTime = LocalTime.MIDNIGHT
        )

        Log.d("TZ", "worker: nextDueDate updated, scheduling today's deadlines…")

        val today = LocalDate.now(zone)
        habits.forEach { h ->
            if ((h["status"] as? String ?: "on") == "on" &&
                (h["nextDueDate"] as? String) == today.toString()
            ) {
                HabitDeadlineScheduler
                    .scheduleForHabit(applicationContext, h, zone)
            }
        }

        // 4) пересаживаем полуночный будильник на следующий раз — ТАКЖЕ по поясу Малыша
        HabitUpdateScheduler.scheduleNext(applicationContext, zone)

        Log.d("TZ", "worker: zone=$zone today=${LocalDate.now(zone)}")

        return Result.success()
    }
}