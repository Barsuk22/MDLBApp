//Файл Rule.kt

package com.app.mdlbapp.rule

import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp


data class Rule(
    val title: String = "",
    val description: String = "",
    val createdBy: String = "",
    val targetUid: String = "",

    val reminder: String = "",
    val category: String = "",
    val status: String = "active", // или "disabled"

    @JvmField var id: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun addSampleRuleForBaby(mommyUid: String, babyUid: String) {
    val rule = mapOf(
        "title" to "УВАЖЕНИЕ",
        "description" to "Ты обязан проявлять уважение ко Мне всегда",
        "createdBy" to mommyUid,
        "targetUid" to babyUid,
        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )
    Firebase.firestore.collection("rules").add(rule)
}