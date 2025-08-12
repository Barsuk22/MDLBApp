package com.app.mdlbapp.habits.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

object HabitDeadlineScheduler {
    private const val ACTION = "com.app.mdlbapp.ACTION_HABIT_DEADLINE"

    fun scheduleForHabit(ctx: Context, habit: Map<String, Any>) {
        val id = habit["id"] as? String ?: return
        val status = habit["status"] as? String ?: "on"
        if (status != "on") return

        val dateStr = habit["nextDueDate"] as? String ?: return
        val deadlineStr = habit["deadline"] as? String ?: return

        val today = LocalDate.now()
        val dueDate = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return
        if (dueDate != today) return

        val time = runCatching {
            LocalTime.parse(deadlineStr, DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrNull() ?: return
        if (!LocalTime.now().isBefore(time)) return

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(ctx, HabitDeadlineReceiver::class.java).apply {
            action = ACTION
            putExtra("habitId", id)
        }

        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pi = PendingIntent.getBroadcast(ctx, id.hashCode(), intent, flags)
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        val triggerAt = cal.timeInMillis

        if (Build.VERSION.SDK_INT >= 31) {
            if (alarm?.canScheduleExactAlarms() == true) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarm?.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else {
            alarm?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelForHabit(ctx: Context, habitId: String) {
        val intent = Intent(ctx, HabitDeadlineReceiver::class.java).apply { action = ACTION }
        val flags = if (Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT

        val pi = PendingIntent.getBroadcast(ctx, habitId.hashCode(), intent, flags)
        val alarm = ctx.getSystemService(AlarmManager::class.java)
        alarm?.cancel(pi)
    }
}