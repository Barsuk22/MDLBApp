package com.app.mdlbapp.data.call

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class SdpBlob(val type: String = "", val sdp: String = "")
data class CallDoc(
    val callerUid: String = "",
    val calleeUid: String = "",
    val state: String = "ringing", // ringing | connected | ended
    val offer: SdpBlob? = null,
    val answer: SdpBlob? = null,
)
data class IceBlob(
    val fromUid: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)

object CallRepository {
    private fun callDocRef(tid: String, callId: String) =
        Firebase.firestore.collection("chats").document(tid)
            .collection("calls").document(callId)

    suspend fun createOutgoingCall(
        tid: String, callerUid: String, calleeUid: String, offer: SdpBlob
    ): String {
        val calls = Firebase.firestore.collection("chats").document(tid).collection("calls")
        val ref = calls.document()
        ref.set(
            mapOf(
                "callerUid" to callerUid,
                "calleeUid" to calleeUid,
                "state" to "ringing",
                "offer" to mapOf("type" to offer.type, "sdp" to offer.sdp),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return ref.id
    }

    suspend fun setAnswer(tid: String, callId: String, answer: SdpBlob) {
        callDocRef(tid, callId).update("answer", mapOf("type" to answer.type, "sdp" to answer.sdp)).await()
    }
    suspend fun setState(tid: String, callId: String, state: String) {
        callDocRef(tid, callId).update("state", state).await()
    }

    suspend fun addIce(tid: String, callId: String, ice: IceBlob) {
        callDocRef(tid, callId).collection("candidates").add(
            mapOf(
                "fromUid" to ice.fromUid,
                "sdpMid" to ice.sdpMid,
                "sdpMLineIndex" to ice.sdpMLineIndex,
                "candidate" to ice.candidate,
                "at" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun watchCall(tid: String, callId: String) = callbackFlow<CallDoc?> {
        val reg = callDocRef(tid, callId).addSnapshotListener { s, _ ->
            trySend(s?.toObject(CallDoc::class.java))
        }
        awaitClose { reg.remove() }
    }

    fun watchIce(tid: String, callId: String) = callbackFlow<IceBlob> {
        val reg = callDocRef(tid, callId).collection("candidates")
            .addSnapshotListener { qs, _ ->
                qs?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { dc -> trySend(dc.document.toObject(IceBlob::class.java)) }
            }
        awaitClose { reg.remove() }
    }

    suspend fun getOrCreateRtcKeyB64(tid: String, myUid: String, peerUid: String): String {
        val root = com.google.firebase.ktx.Firebase.firestore
            .collection("chats").document(tid).collection("secrets").document("rtc")
        val snap = root.get().await()
        val cur = snap.getString("keyB64")
        if (!cur.isNullOrBlank()) return cur

        // генерим на лету 32 байта
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val keyB64 = android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)

        // создаёт любой из пары — важны правила доступа
        root.set(mapOf("keyB64" to keyB64, "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            .await()
        return keyB64
    }

    // === Шифрованные записи ===
    suspend fun createOutgoingCallEncrypted(
        tid: String, callerUid: String, calleeUid: String, offer: SdpBlob, keyB64: String
    ): String {
        val enc = CryptoBox.encryptMap(keyB64, mapOf("type" to offer.type, "sdp" to offer.sdp))
        val calls = Firebase.firestore.collection("chats").document(tid).collection("calls")
        val ref = calls.document()
        ref.set(
            mapOf(
                "callerUid" to callerUid,
                "calleeUid" to calleeUid,
                "state" to "ringing",
                "offerEnc" to enc, // <— кладём ШИФР
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        return ref.id
    }

    suspend fun setAnswerEncrypted(tid: String, callId: String, answer: SdpBlob, keyB64: String) {
        val enc = CryptoBox.encryptMap(keyB64, mapOf("type" to answer.type, "sdp" to answer.sdp))
        callDocRef(tid, callId).update("answerEnc", enc).await()
    }

    suspend fun addIceEncrypted(tid: String, callId: String, ice: IceBlob, keyB64: String) {
        val enc = CryptoBox.encryptMap(keyB64, mapOf(
            "fromUid" to ice.fromUid,
            "sdpMid" to ice.sdpMid,
            "sdpMLineIndex" to ice.sdpMLineIndex,
            "candidate" to ice.candidate
        ))
        callDocRef(tid, callId).collection("candidates").add(
            mapOf("enc" to enc, "at" to FieldValue.serverTimestamp())
        ).await()
    }

    fun watchCallDecrypted(tid: String, callId: String, keyB64: String) = callbackFlow<CallDoc?> {
        val reg = callDocRef(tid, callId).addSnapshotListener { s, _ ->
            if (s == null || !s.exists()) { trySend(null); return@addSnapshotListener }
            val caller = s.getString("callerUid") ?: ""
            val callee = s.getString("calleeUid") ?: ""
            val state  = s.getString("state") ?: "ringing"

            fun decOffer(): SdpBlob? = s.getString("offerEnc")?.let { b64 ->
                val m = CryptoBox.decryptMap(keyB64, b64)
                SdpBlob((m["type"] as? String) ?: "", (m["sdp"] as? String) ?: "")
            } ?: s.get("offer")?.let { // на всякий случай, если есть старые звонки
                val map = it as Map<*,*>
                SdpBlob((map["type"] as? String) ?: "", (map["sdp"] as? String) ?: "")
            }

            fun decAnswer(): SdpBlob? = s.getString("answerEnc")?.let { b64 ->
                val m = CryptoBox.decryptMap(keyB64, b64)
                SdpBlob((m["type"] as? String) ?: "", (m["sdp"] as? String) ?: "")
            } ?: s.get("answer")?.let {
                val map = it as Map<*,*>
                SdpBlob((map["type"] as? String) ?: "", (map["sdp"] as? String) ?: "")
            }

            trySend(CallDoc(caller, callee, state, decOffer(), decAnswer()))
        }
        awaitClose { reg.remove() }
    }

    // ——— один раз получить расшифрованное состояние звонка ———
    suspend fun watchCallDecryptedOnce(
        tid: String,
        callId: String,
        keyB64: String
    ): CallDoc? {
        // берём ПЕРВОЕ НЕ-NULL значение из watchCallDecrypted и возвращаем
        return watchCallDecrypted(tid, callId, keyB64)
            .filterNotNull()
            .firstOrNull()
    }

    // ——— (опционально) подождать именно оффер, с таймаутом ———
    suspend fun waitForDecryptedOffer(
        tid: String,
        callId: String,
        keyB64: String,
        timeoutMs: Long = 10_000
    ): SdpBlob? = withTimeout(timeoutMs) {
        watchCallDecrypted(tid, callId, keyB64)
            .filter { it?.offer != null }
            .map { it!!.offer }
            .first()
    }

    fun watchIceDecrypted(tid: String, callId: String, keyB64: String) = callbackFlow<IceBlob> {
        val reg = callDocRef(tid, callId).collection("candidates")
            .addSnapshotListener { qs, _ ->
                qs?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { dc ->
                        val enc = dc.document.getString("enc")
                        if (enc != null) {
                            val m = CryptoBox.decryptMap(keyB64, enc)
                            trySend(IceBlob(
                                (m["fromUid"] as? String) ?: "",
                                (m["sdpMid"] as? String) ?: "",
                                (m["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                                (m["candidate"] as? String) ?: ""
                            ))
                        } else {
                            // на случай старых незашифрованных кандидатов
                            trySend(dc.document.toObject(IceBlob::class.java))
                        }
                    }
            }
        awaitClose { reg.remove() }
    }
}