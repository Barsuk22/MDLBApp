package com.app.mdlbapp.habits.background.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.app.mdlbapp.habits.data.updateHabitsNextDueDate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object HabitUpdateScheduler {
    private const val TAG = "TZ"
    private const val ACTION_ROLL_DAY = "com.app.mdlbapp.ROLL_DAY"

    fun scheduleNext(context: Context, zone: ZoneId) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nowZ = ZonedDateTime.now(zone)
        val nextMidnight = nowZ.toLocalDate().plusDays(1).atStartOfDay(zone)
        val triggerAt = nextMidnight.toInstant().toEpochMilli()

        val pi = PendingIntent.getBroadcast(
            context,
            1001,
            Intent(context, MidnightReceiver::class.java)
                .setAction(ACTION_ROLL_DAY)
                .putExtra("zone", zone.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    // запасной вариант, если точные запрещены
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (se: SecurityException) {
            // на всякий случай fallback
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        Log.d(TAG, "scheduleNext: zone=$zone nextMidnight=$nextMidnight")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            1001,
            Intent(context, MidnightReceiver::class.java).setAction(ACTION_ROLL_DAY),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val zoneId = intent.getStringExtra("zone")?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { me ->
                if (me.getString("role") != "Baby") return@addOnSuccessListener   // ← мама не трогает
                val babyUid = uid

                Firebase.firestore.collection("habits")
                    .whereEqualTo("status","on")
                    .whereEqualTo("babyUid", babyUid)      // ← фильтр по Малышу
                    .get()
                    .addOnSuccessListener { snap ->
                        val list = snap.documents.map { doc -> hashMapOf("id" to doc.id) + doc.data!! }
                        updateHabitsNextDueDate(
                            list,
                            fromDate = LocalDate.now(zoneId),
                            rollTime = LocalTime.MIDNIGHT
                        )
                        HabitDeadlineScheduler.rescheduleAllForToday(context, zoneId)
                        HabitUpdateScheduler.scheduleNext(context, zoneId)
                    }
            }
    }
}