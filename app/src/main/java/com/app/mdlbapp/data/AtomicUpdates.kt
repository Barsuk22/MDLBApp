package com.app.mdlbapp.data

import android.util.Log
import com.app.mdlbapp.reward.changePoints
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

object AtomicUpdates {
    // Плюсуем привычку атомарно, а баллы кладём в points/totalPoints
    suspend fun completeHabitAtomically(habit: Map<String, Any>): Boolean {
        val id = habit["id"] as? String ?: return false
        val babyUid = habit["babyUid"] as? String ?: return false
        val uDoc = Firebase.firestore.collection("users").document(babyUid).get().await()
        val zone = runCatching { ZoneId.of(uDoc.getString("timezone") ?: "") }.getOrNull()
            ?: ZoneId.systemDefault()
        val today = LocalDate.now(zone).toString()

        val points = when (val p = habit["points"]) {
            is Long -> p.toInt()
            is Int -> p
            else -> 0
        }

        val db = Firebase.firestore
        val habitRef = db.collection("habits").document(id)


        val ok = db.runTransaction { tx ->
            val snap = tx.get(habitRef)
            if (!snap.exists()) return@runTransaction false

            val status = snap.getString("status") ?: "on"
            if (status != "on") return@runTransaction false

            // 💡 если уже отмечали именно за СЕГОДНЯ — выходим
            val doneToday = snap.getBoolean("completedToday") ?: false
            val completedForDate = snap.getString("completedForDate")
            if (doneToday && completedForDate == today) return@runTransaction false

            val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
            val long = (snap.getLong("longestStreak") ?: 0L).toInt()
            val newCurrent = cur + 1
            val newLongest = max(long, newCurrent)

            tx.update(habitRef, mapOf(
                "completedToday" to true,
                "completedAt" to FieldValue.serverTimestamp(), // 🕒 когда сделали
                "completedForDate" to today,                   // 📅 за какой день
                "currentStreak" to newCurrent.toLong(),
                "longestStreak" to newLongest.toLong()
            ))
            true
        }.await()

        if (ok) {
            if (points != 0) changePoints(babyUid, points)

            val mommyUidInHabit = habit["mommyUid"] as? String
            val habitTitle = (habit["title"] as? String).orEmpty()

            try {
                Firebase.firestore.collection("habits").document(id)
                    .collection("logs").document(today)        // например "2025-08-12"
                    .set(
                        mapOf(
                            "status" to "done",
                            "pointsDelta" to points,
                            "source" to "complete",
                            "at" to FieldValue.serverTimestamp(),
                            "habitId" to id,
                            "habitTitle" to habitTitle,
                            "mommyUid" to (mommyUidInHabit ?: ""),
                            "babyUid" to babyUid
                        )
                    )
                    .await() // 👈 ждём, чтобы точно создалось/увидеть ошибку
                Log.d("HabitLogsRepo","done-log written for $id/$today")
            } catch (e: Exception) {
                Log.e("HabitLogsRepo","write done log failed", e)
            }
        }
        return ok
    }
}