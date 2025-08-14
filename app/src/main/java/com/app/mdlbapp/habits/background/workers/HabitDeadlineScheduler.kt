package com.app.mdlbapp.habits.background.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.app.mdlbapp.habits.background.receivers.HabitDeadlineReceiver
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

object HabitDeadlineScheduler {
    private const val TAG = "TZ"
    private const val ACTION_DEADLINE = "com.app.mdlbapp.DEADLINE"

    private fun parseDeadlineLocalTime(deadline: String?): LocalTime =
        runCatching { LocalTime.parse(deadline ?: "23:59", DateTimeFormatter.ofPattern("HH:mm")) }
            .getOrDefault(LocalTime.of(23,59))



    fun scheduleForHabit(ctx: Context, habit: Map<String, Any>, zone: ZoneId) {
        val id = habit["id"] as? String ?: return
        if ((habit["status"] as? String ?: "on") != "on") return

        val dueDate = LocalDate.parse(habit["nextDueDate"] as? String ?: return)
        val deadline = LocalTime.parse(
            habit["deadline"] as? String ?: return,
            DateTimeFormatter.ofPattern("HH:mm")
        )

        val today = LocalDate.now(zone)
        if (dueDate != today) return
        if (!LocalTime.now(zone).isBefore(deadline)) return

        val triggerAt = ZonedDateTime.of(today, deadline, zone).toInstant().toEpochMilli()

        val intent = Intent(ctx, HabitDeadlineReceiver::class.java).putExtra("habitId", id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags)
        val alarm = ctx.getSystemService(AlarmManager::class.java)

        if (Build.VERSION.SDK_INT >= 31 && alarm?.canScheduleExactAlarms() == true) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarm?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        Log.d("TZ", "deadline: id=$id zone=$zone trigger=${Date(triggerAt)}")
    }

    fun cancelForHabit(ctx: Context, habitId: String) {
        // ДОЛЖНЫ совпасть и requestCode, и Intent (тот же receiver)
        val pi = PendingIntent.getBroadcast(
            ctx,
            habitId.hashCode(),
            Intent(ctx, HabitDeadlineReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm?.cancel(pi)

        Log.d(TAG, "deadline-cancel: id=$habitId")
    }

    suspend fun fetchBabyUidForCurrentUser(): String? {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val doc = Firebase.firestore.collection("users").document(me).get().await()
        val role = doc.getString("role")
        val paired = doc.getString("pairedWith")
        return when (role) {
            "Baby"  -> me
            "Mommy" -> paired
            else    -> null
        }
    }

    suspend fun loadOnHabitsForBaby(babyUid: String): List<Map<String, Any>> {
        val snap = Firebase.firestore.collection("habits")
            .whereEqualTo("babyUid", babyUid)
            .whereEqualTo("status", "on")
            .get().await()
        return snap.documents.mapNotNull { it.data?.plus("id" to it.id) }
    }

    /** Пересчитать ВСЕ будильники-дедлайны на сегодня по заданному поясу малыша. */
    fun rescheduleAllForToday(context: Context, zone: ZoneId) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // читать только СВОИ привычки
        val col = Firebase.firestore.collection("habits")

        val qMom = col.whereEqualTo("status", "on")
            .whereEqualTo("mommyUid", uid)
            .get()

        val qBab = col.whereEqualTo("status", "on")
            .whereEqualTo("babyUid", uid)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(qMom, qBab)
            .addOnSuccessListener { results ->
                val allDocs = results.flatMap { it.documents }.distinctBy { it.id }
                var count = 0
                val today = LocalDate.now(zone)
                for (doc in allDocs) {
                    val h = doc.data?.plus("id" to doc.id) ?: continue
                    try {
                        cancelForHabit(context, doc.id)
                        scheduleForHabit(context, h, zone)
                        count++
                    } catch (_: Exception) { /* пропускаем битые */ }
                }
                Log.d("TZ","rescheduleAllForToday: ok; count=$count zone=$zone")
            }
            .addOnFailureListener { e ->
                Log.e("TZ","rescheduleAllForToday: error", e)
            }
    }
}