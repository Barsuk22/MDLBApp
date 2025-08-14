package com.app.mdlbapp.journal.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class HabitJournalItem(
    val docId: String,
    val habitId: String,
    val habitTitle: String,
    val status: String,           // done | missed
    val pointsDelta: Int,
    val source: String?,
    val at: Timestamp?
)

object HabitLogsRepository {

    // общая лента по паре (мама ↔ малыш)
    fun allHabitLogsFlow(mommyUid: String, babyUid: String) = callbackFlow<List<HabitJournalItem>> {
        val ref = Firebase.firestore
            .collectionGroup("logs") // собираем все подколлекции logs
            .whereEqualTo("mommyUid", mommyUid)
            .whereEqualTo("babyUid", babyUid)
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(200)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                Log.e("HabitLogsRepo", "logs error", err)
                trySend(emptyList()); return@addSnapshotListener
            }

            val items = snap.documents.map { d ->
                HabitJournalItem(
                    docId = d.id,
                    habitId = d.getString("habitId") ?: "",
                    habitTitle = d.getString("habitTitle") ?: "",
                    status = d.getString("status") ?: "",
                    pointsDelta = (d.getLong("pointsDelta") ?: 0L).toInt(),
                    source = d.getString("source"),
                    at = d.getTimestamp("at")
                )
            }
            trySend(items)
        }
        awaitClose { reg.remove() }
    }
}