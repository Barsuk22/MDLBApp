package com.app.mdlbapp.habits.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.habits.data.updateHabitsNextDueDate
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime

class HabitUpdateWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Log.d("HabitUpdate", "Worker fired at ${LocalDateTime.now()}")

        val mommyUid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return Result.success()
        val snap = Firebase.firestore
            .collection("habits")
            .whereEqualTo("mommyUid", mommyUid)
            .get()
            .await()

        val habits = snap.documents.mapNotNull { doc ->
            doc.data?.plus("id" to doc.id)
        }
        // ваша существующая логика обновления дат
        updateHabitsNextDueDate(habits)

        // сразу планируем следующий запуск на следующую полночь
        HabitUpdateScheduler.scheduleNext(applicationContext)

        return Result.success()
    }
}