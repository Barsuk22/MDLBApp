package com.app.mdlbapp.data.call

import android.content.Context
import android.os.PowerManager

object WakeHelper {
    fun pokeScreen(ctx: Context, ms: Long = 5000L) {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "mdlbapp:incoming_call"
        )
        try { wl.acquire(ms) } catch (_: Throwable) {}
    }
}