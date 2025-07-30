package com.yourname.mdlbapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime

class HabitAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        Log.d("HabitAlarm", "Alarm fired at ${LocalDateTime.now()}")
        // запускаем воркер
        val req = OneTimeWorkRequestBuilder<HabitUpdateWorker>().build()
        WorkManager.getInstance(ctx).enqueue(req)
        // пересаживаем будильник на следующую полуночь
        HabitUpdateScheduler.scheduleNext(ctx)
    }
}