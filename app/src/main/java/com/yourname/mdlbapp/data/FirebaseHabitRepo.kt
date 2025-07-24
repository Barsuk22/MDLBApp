package com.yourname.mdlbapp.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FirebaseHabitRepo: HabitRepo {
    private val db = Firebase.firestore
    override fun updateNextDueDate(habitId: String, isoDate: String) {
        db.collection("habits").document(habitId)
            .update("nextDueDate", isoDate)
    }
}