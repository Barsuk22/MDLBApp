package com.yourname.mdlbapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class HabitUpdateWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // 1) Берём все привычки этого пользователя
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
        // 2) Вызываем функцию из MainActivity
        updateHabitsNextDueDate(habits)
        return Result.success()
    }
}