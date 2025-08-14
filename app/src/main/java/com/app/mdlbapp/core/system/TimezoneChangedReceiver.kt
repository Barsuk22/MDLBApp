package com.app.mdlbapp.core.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

class TimezoneChangedReceiver: BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            pending.finish(); return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { d ->
                if (d.getString("role") != "Baby") {
                    pending.finish(); return@addOnSuccessListener
                }
                val tz = d.getString("timezone") ?: ZoneId.systemDefault().id
                HabitUpdateScheduler
                    .scheduleNext(ctx.applicationContext, ZoneId.of(tz))
                pending.finish()
            }
            .addOnFailureListener { pending.finish() }
    }
}