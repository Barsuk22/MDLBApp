package com.app.mdlbapp.data.chat

data class ReplyPayload(
    val mid: String = "",
    val fromUid: String = "",
    val text: String = ""
)

data class ChatMessage(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val text: String = "",
    val at: com.google.firebase.Timestamp? = null,
    val seen: Boolean = false,
    val seenAt: com.google.firebase.Timestamp? = null,
    val reply: ReplyPayload? = null,
    val forward: ForwardPayload? = null,
    val edited: Boolean? = null,
    val editedAt: com.google.firebase.Timestamp? = null
)

data class ForwardPayload(
    val fromUid: String = "",
    val fromName: String = "",
    val fromPhoto: String? = null,
    val text: String = ""
)

data class ChatPin(
    val id: String = "",
    val mid: String = "", // id сообщения
    val by: String = "",  // кто закрепил
    val at: com.google.firebase.Timestamp? = null // когда
)