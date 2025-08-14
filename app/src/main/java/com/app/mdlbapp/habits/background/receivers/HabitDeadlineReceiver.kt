package com.app.mdlbapp.habits.background.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.mdlbapp.habits.background.workers.MissedDeadlineWorker

class HabitDeadlineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("habitId") ?: return
        val data = Data.Builder().putString("habitId", habitId).build()
        val work = OneTimeWorkRequestBuilder<MissedDeadlineWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }
}