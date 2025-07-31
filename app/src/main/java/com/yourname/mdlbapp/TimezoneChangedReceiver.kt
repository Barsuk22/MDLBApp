package com.yourname.mdlbapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

class TimezoneChangedReceiver: BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val newTz = ZoneId.systemDefault().id
            Firebase.firestore
                .collection("users")
                .document(uid)
                .update("timezone", newTz)
        }
    }
}