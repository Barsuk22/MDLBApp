package com.yourname.mdlbapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.provider.Settings


object HabitUpdateScheduler {
    fun scheduleNext(context: Context) {
        // Получаем AlarmManager
        val alarmMgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(AlarmManager::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }

        // рассчитываем время следующей «полночной» синхронизации
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // PendingIntent на наш ресивер
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, HabitAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ пытаемся точный
            if (alarmMgr.canScheduleExactAlarms()) {
                alarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pi
                )
            } else {
                // нет права — ставим inexact, но при этом не выходим сразу
                alarmMgr.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pi
                )
                // а пользователю показываем, как дать право, только если он сам захочет:
                // (опционально вызывайте это из UI, а не автоматически)
                val settings = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(settings)
            }
        } else {
            // для старых Android — точный без проблем
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pi
            )
        }
    }
}
