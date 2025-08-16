package com.app.mdlbapp.data.chat


import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.collections.hashMapOf
import kotlin.collections.mutableMapOf

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
            val list = snap?.documents?.mapNotNull { d ->
                runCatching { d.toObject(ChatMessage::class.java)?.copy(id = d.id) }.getOrNull()
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
        text: String,
        reply: ReplyPayload? = null,
        forward: ForwardPayload? = null
    ) {
        val chatId = "${mommyUid}_${babyUid}"
        val ref = Firebase.firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()

        // формируем ровно те поля, которые разрешены правилами
        val data = mutableMapOf<String, Any>(
            "fromUid" to fromUid,
            "toUid"   to toUid,
            "text"    to text,                       // можно пустую строку — это всё ещё string
            "at"      to FieldValue.serverTimestamp(),
            "seen"    to false
        )

        // reply добавляем ТОЛЬКО если он есть — без null
        reply?.let {
            data["reply"] = hashMapOf(
                "mid"     to it.mid,
                "fromUid" to it.fromUid,
                "text"    to it.text
            )
        }
        forward?.let { f ->
            val m = hashMapOf<String, Any>(
                "fromUid"  to f.fromUid,
                "fromName" to f.fromName,
                "text"     to f.text
            )
            f.fromPhoto?.let { m["fromPhoto"] = it }
            data["forward"] = m
        }

        // один-единственный set -> это "create", правила пропускают
        ref.set(data).await()
    }


    suspend fun markAllIncomingAsSeen(
        mommyUid: String,
        babyUid: String,
        meUid: String
    ) {
        val root = Firebase.firestore.collection("chats")
            .document("${mommyUid}_${babyUid}")
            .collection("messages")

        val q = root
            .whereEqualTo("toUid", meUid)
            .whereEqualTo("seen", false)
            .get()
            .await()

        if (!q.isEmpty) {
            val batch = Firebase.firestore.batch()
            q.documents.forEach { d ->
                batch.update(d.reference, mapOf(
                    "seen" to true,
                    "seenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }
            batch.commit().await()
        }
    }

    fun pinsFlow(mommyUid: String, babyUid: String) = callbackFlow<List<ChatPin>> {
        val chatId = "${mommyUid}_${babyUid}"
        val ref = Firebase.firestore.collection("chats")
            .document(chatId).collection("pins")
            .orderBy("at", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.mapNotNull { d ->
                runCatching { d.toObject(ChatPin::class.java)?.copy(id = d.id) }.getOrNull()
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun addPin(mommyUid: String, babyUid: String, mid: String, by: String) {
        val chatId = "${mommyUid}_${babyUid}"
        val ref = Firebase.firestore.collection("chats")
            .document(chatId).collection("pins").document()
        ref.set(
            mapOf(
                "mid" to mid,
                "by"  to by,
                "at"  to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun removePin(mommyUid: String, babyUid: String, mid: String) {
        val chatId = "${mommyUid}_${babyUid}"
        val coll = Firebase.firestore.collection("chats")
            .document(chatId).collection("pins")
        val snap = coll.whereEqualTo("mid", mid).get().await()
        val batch = Firebase.firestore.batch()
        snap.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}