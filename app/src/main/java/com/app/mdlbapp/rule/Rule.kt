//Файл Rule.kt

package com.app.mdlbapp.rule

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


data class Rule(
    val title: String = "",
    val description: String = "",
    val createdBy: String = "",
    val targetUid: String = "",
    val timestamp: Long = 0L,
    @JvmField var id: String? = null,
    var createdAt: Long? = null,

    val reminder: String = "",
    val category: String = "",
    val status: String = "active" // или "disabled"
)

fun addSampleRuleForBaby(mommyUid: String, babyUid: String) {
    val rule = mapOf(
        "title" to "УВАЖЕНИЕ",
        "description" to "Ты обязан проявлять уважение ко Мне всегда",
        "createdBy" to mommyUid,
        "targetUid" to babyUid,
        "timestamp" to System.currentTimeMillis()
    )
    Firebase.firestore.collection("rules").add(rule)
}