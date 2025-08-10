package com.app.mdlbapp.habits.data

import com.app.mdlbapp.reward.changePoints
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlin.math.max

object AtomicUpdates {
    // Плюсуем привычку атомарно, а баллы кладём в points/totalPoints
    suspend fun completeHabitAtomically(habit: Map<String, Any>): Boolean {
        val id = habit["id"] as? String ?: return false
        val babyUid = habit["babyUid"] as? String ?: return false
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
            val doneToday = snap.getBoolean("completedToday") ?: false
            if (doneToday) return@runTransaction false

            val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
            val long = (snap.getLong("longestStreak") ?: 0L).toInt()
            val newCurrent = cur + 1
            val newLongest = max(long, newCurrent)

            tx.update(habitRef, mapOf(
                "completedToday" to true,
                "currentStreak" to newCurrent,
                "longestStreak" to newLongest
            ))
            true
        }.await()

        if (ok && points != 0) {
            // единый кошелёк
            changePoints(babyUid, points)
        }
        return ok
    }
}