package com.app.mdlbapp.habits.background.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
import com.app.mdlbapp.habits.background.workers.HabitUpdateWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

class HabitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val appCtx = ctx.applicationContext
        val pending = goAsync()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // никто не залогинен — просто ставим на системную полночь, чтобы не потерять цикл
            HabitUpdateScheduler.scheduleNext(appCtx, ZoneId.systemDefault())
            Log.d("HabitAlarm", "No user, scheduled by systemDefault")
            pending.finish()
            return
        }

        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { d ->
                // пускаем дальше ТОЛЬКО если это Малыш
                if (d.getString("role") != "Baby") {
                    Log.d("HabitAlarm", "User is not Baby, ignore")
                    pending.finish()
                    return@addOnSuccessListener
                }

                // зона Малыша (или системная, если поле пусто/битое)
                val zone = runCatching { ZoneId.of(d.getString("timezone") ?: "") }
                    .getOrNull() ?: ZoneId.systemDefault()

                // 1) Переклад (воркер) — уникально, чтобы дубликаты не плодить
                val req = OneTimeWorkRequestBuilder<HabitUpdateWorker>().build()
                WorkManager.getInstance(appCtx).enqueueUniqueWork(
                    "habit-midnight-rollover",
                    ExistingWorkPolicy.REPLACE,
                    req
                )

                // 2) Сразу пересаживаем будильник на следующую полночь в пояске Малыша
                HabitUpdateScheduler.scheduleNext(appCtx, zone)

                Log.d("HabitAlarm", "Alarm handled & rescheduled with zone=$zone")
                pending.finish()
            }
            .addOnFailureListener {
                // на всякий случай не роняем цикл
                HabitUpdateScheduler.scheduleNext(appCtx, ZoneId.systemDefault())
                Log.e("HabitAlarm", "Failed to read user, fallback to system zone", it)
                pending.finish()
            }
    }
}