package com.app.mdlbapp.habits.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // Перепланируем полуночный апдейт
        HabitUpdateScheduler.scheduleNext(c)
        // Дедлайны на сегодня переустановятся, когда откроется экран с привычками
        // (у тебя уже есть логика постановки при подписке/рендере).
    }
}