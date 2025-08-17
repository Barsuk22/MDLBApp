package com.app.mdlbapp.data.call

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
}