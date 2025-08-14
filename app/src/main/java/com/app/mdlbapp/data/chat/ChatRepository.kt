package com.app.mdlbapp.data.chat

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object ChatRepository {

    // ===== Модель присутствия (для "печатает", галочки и пр.)
    data class ChatPresence(
        val lastDeliveredAt: Timestamp? = null,
        val lastReadAt: Timestamp? = null,
        val typing: Boolean = false
    )

    // ===== Вспомогалки =====
    private fun pairId(mommyUid: String, babyUid: String) = "${mommyUid}_${babyUid}"

    private fun chatDoc(mommyUid: String, babyUid: String) =
        Firebase.firestore.collection("chats").document(pairId(mommyUid, babyUid))

    private fun participantsCol(mommyUid: String, babyUid: String) =
        chatDoc(mommyUid, babyUid).collection("participants")

    // ===== Поток сообщений =====
    fun messagesFlow(mommyUid: String, babyUid: String) = callbackFlow<List<ChatMessage>> {
        val ref = chatDoc(mommyUid, babyUid)
            .collection("messages")
            .orderBy("at", Query.Direction.ASCENDING)

        val reg = ref.addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { d ->
                d.toObject(ChatMessage::class.java)?.copy(id = d.id)
            } ?: emptyList()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    // ===== Гарантируем корневой документ чата (если стёрли/нет) =====
    suspend fun ensureChat(mommyUid: String, babyUid: String) {
        val ref = chatDoc(mommyUid, babyUid)
        Firebase.firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) {
                tx.set(
                    ref,
                    mapOf(
                        "mommyUid" to mommyUid,
                        "babyUid" to babyUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            null
        }.await()
    }

    // ===== Отправка текста =====
    suspend fun sendText(
        mommyUid: String,
        babyUid: String,
        fromUid: String,
        toUid: String,
        text: String
    ) {
        val root = chatDoc(mommyUid, babyUid)
        // создадим чат, если нет
        if (!root.get().await().exists()) {
            root.set(
                mapOf(
                    "mommyUid" to mommyUid,
                    "babyUid" to babyUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        root.collection("messages").add(
            mapOf(
                "fromUid" to fromUid,
                "toUid" to toUid,
                "text" to text.trim(),
                "seen" to false,
                "at" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ===== Присутствие: "печатает", галочки и пр. =====
    fun presenceFlow(mommyUid: String, babyUid: String, uid: String) =
        callbackFlow<ChatPresence> {
            val doc = participantsCol(mommyUid, babyUid).document(uid)
            val reg = doc.addSnapshotListener { s, _ ->
                trySend(s?.toObject(ChatPresence::class.java) ?: ChatPresence())
            }
            awaitClose { reg.remove() }
        }

    suspend fun setTyping(mommyUid: String, babyUid: String, uid: String, value: Boolean) {
        participantsCol(mommyUid, babyUid).document(uid)
            .set(mapOf("typing" to value), SetOptions.merge())
            .await()
    }

    suspend fun markDelivered(mommyUid: String, babyUid: String, uid: String) {
        participantsCol(mommyUid, babyUid).document(uid)
            .set(mapOf("lastDeliveredAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
    }

    suspend fun markRead(mommyUid: String, babyUid: String, uid: String) {
        participantsCol(mommyUid, babyUid).document(uid)
            .set(mapOf("lastReadAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
    }

    suspend fun ensurePresence(mommyUid: String, babyUid: String, uid: String) {
        // создаёт/сливает doc участника, чтобы был куда писать typing/галочки
        participantsCol(mommyUid, babyUid).document(uid)
            .set(mapOf("typing" to false), SetOptions.merge())
            .await()
    }
}