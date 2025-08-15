package com.app.mdlbapp.data.chat

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val text: String = "",
    val at: Timestamp? = null,     // серверное "отправлено"
    val seen: Boolean = false,     // прочитано?
    val seenAt: Timestamp? = null  // когда прочитали (для будущих вкусняшек)
)
