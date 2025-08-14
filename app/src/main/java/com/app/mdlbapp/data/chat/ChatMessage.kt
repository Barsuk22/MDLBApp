package com.app.mdlbapp.data.chat

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val text: String = "",
    val at: Timestamp? = null,
    val seen: Boolean = false
)
