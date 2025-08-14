package com.app.mdlbapp.habits.background.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val pending = goAsync()
        val appCtx = ctx.applicationContext
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            pending.finish(); return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { d ->
                // если это не Малыш — просто закрываемся
                if (d.getString("role") != "Baby") {
                    pending.finish(); return@addOnSuccessListener
                }

                val tzStr = d.getString("timezone")
                val zone = runCatching { ZoneId.of(tzStr ?: "") }.getOrNull()
                    ?: ZoneId.systemDefault()

                HabitUpdateScheduler.scheduleNext(appCtx, zone)
                Log.d("TZ", "time-change: rescheduled with zone=$zone")
                pending.finish()
            }
            .addOnFailureListener { pending.finish() }
    }
}