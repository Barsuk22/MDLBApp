package com.app.mdlbapp.journal.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class RewardJournalItem(
    val docId: String,
    val rewardId: String,
    val rewardTitle: String,
    val status: String,         // bought | pending | approved | rejected
    val pointsDelta: Int,
    val source: String?,
    val at: Timestamp?
)

object RewardLogsRepository {
    fun allRewardLogsFlow(mommyUid: String, babyUid: String) =
        callbackFlow<List<RewardJournalItem>> {
            val ref = Firebase.firestore
                .collectionGroup("rewardLogs")
                .whereEqualTo("mommyUid", mommyUid)
                .whereEqualTo("babyUid", babyUid)
                .orderBy("at", Query.Direction.DESCENDING)
                .limit(200)

            val reg = ref.addSnapshotListener { snap, err ->
                if (err != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
                val items = snap.documents.map { d ->
                    RewardJournalItem(
                        docId = d.id,
                        rewardId = d.getString("rewardId") ?: "",
                        rewardTitle = d.getString("rewardTitle") ?: "",
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