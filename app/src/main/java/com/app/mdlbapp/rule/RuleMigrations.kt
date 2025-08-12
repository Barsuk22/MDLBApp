package com.app.mdlbapp.rule

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

suspend fun migrateRulesCreatedAt(mommyUid: String): Int {
    val db = Firebase.firestore
    val snaps = db.collection("rules")
        .whereEqualTo("createdBy", mommyUid)
        .get().await()

    var changed = 0
    db.runBatch { b ->
        snaps.documents.forEach { d ->
            val v = d.get("createdAt")
            when (v) {
                null -> { // не было — поставим серверный
                    b.update(d.reference, "createdAt", FieldValue.serverTimestamp()); changed++
                }
                is Number -> { // старый long → Timestamp (секунды, без наносек)
                    b.update(d.reference, "createdAt", Timestamp(v.toLong() / 1000, 0)); changed++
                }
            }
        }
    }.await()
    return changed
}