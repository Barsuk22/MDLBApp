package com.app.mdlbapp.reward

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Utility functions for updating the baby's point balance in Firestore.
 *
 * Points are stored in a collection called "points" where each document's ID is the baby's UID.
 * The document contains a single field `totalPoints` which holds the current balance.
 */

/**
 * Atomically adjusts the point balance for a given baby UID by the provided delta.
 *
 * This function runs a Firestore transaction to ensure that increments and decrements
 * are applied safely even under concurrent updates. If the resulting balance would drop
 * below zero, it is clamped to zero.
 *
 * @param babyUid the UID of the baby whose points should be adjusted
 * @param delta the number of points to add (positive) or subtract (negative)
 */
suspend fun changePoints(babyUid: String, delta: Int) {
    val db = FirebaseFirestore.getInstance()
    val ref = db.collection("points").document(babyUid)
    db.runTransaction { transaction ->
        val snapshot = transaction.get(ref)
        val current = snapshot.getLong("totalPoints") ?: 0L
        var newPoints = current + delta
        if (newPoints < 0L) {
            newPoints = 0L
        }
        // Merge so other unknown fields on the document are preserved
        transaction.set(ref, mapOf("totalPoints" to newPoints), SetOptions.merge())
    }.await()
}

/**
 * Adjusts the point balance for a given baby UID without suspending.
 *
 * This version uses Firestore's transaction API but does not await the result. It is
 * suitable for non-suspend contexts such as background schedulers where the caller
 * does not need to block on completion.
 *
 * @param babyUid the UID of the baby whose points should be adjusted
 * @param delta the number of points to add (positive) or subtract (negative)
 */
fun changePointsAsync(babyUid: String, delta: Int) {
    val db = FirebaseFirestore.getInstance()
    val ref = db.collection("points").document(babyUid)
    db.runTransaction { transaction ->
        val snapshot = transaction.get(ref)
        val current = snapshot.getLong("totalPoints") ?: 0L
        var newPoints = current + delta
        if (newPoints < 0L) {
            newPoints = 0L
        }
        transaction.set(ref, mapOf("totalPoints" to newPoints), SetOptions.merge())
    }
}