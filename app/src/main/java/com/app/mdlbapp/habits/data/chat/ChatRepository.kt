package com.app.mdlbapp.habits.data.chat

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object ChatRepository {

    private fun pairId(mommyUid: String, babyUid: String) =
        "${mommyUid}_${babyUid}"

    fun messagesFlow(mommyUid: String, babyUid: String) = callbackFlow<List<ChatMessage>> {
        val ref = Firebase.firestore.collection("chats")
            .document(pairId(mommyUid, babyUid))
            .collection("messages")
            .orderBy("at", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                d.toObject(ChatMessage::class.java)!!.copy(id = d.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(
        mommyUid: String,
        babyUid: String,
        fromUid: String,
        toUid: String,
        text: String
    ) {
        val root = Firebase.firestore.collection("chats")
            .document(pairId(mommyUid, babyUid))

        // создаём чат при первом сообщении
        root.set(mapOf(
            "mommyUid" to mommyUid,
            "babyUid" to babyUid,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        ), com.google.firebase.firestore.SetOptions.merge()).await()

        root.collection("messages").add(
            mapOf(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "text" to text.trim(),
                "seen" to false,
                "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        ).await()
    }

}