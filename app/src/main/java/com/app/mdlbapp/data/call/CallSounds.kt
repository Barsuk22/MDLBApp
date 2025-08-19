package com.app.mdlbapp.data.call

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.*

object CallSounds {
    @Volatile private var tg: ToneGenerator? = null
    private var endBeepJob: Job? = null

    @Synchronized fun startRingback() {
        stopAll()
        tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, /*volume*/ 80).apply {
            // Похоже на гудки ожидания
            startTone(ToneGenerator.TONE_SUP_RINGTONE)
        }
    }

    @Synchronized fun stopRingback() {
        runCatching { tg?.stopTone() }
    }

    /** Длинный «пиииип» при завершении. Сам остановится через ~1.2 сек. */
    @Synchronized fun playHangupBeep(scope: CoroutineScope) {
        stopRingback()
        runCatching { tg?.release() }
        tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).apply {
            startTone(ToneGenerator.TONE_SUP_CONGESTION, 1200) // ~длинный
        }
        endBeepJob?.cancel()
        endBeepJob = scope.launch {
            delay(1300)
            stopAll()
        }
    }

    @Synchronized fun stopAll() {
        runCatching { tg?.stopTone() }
        runCatching { tg?.release() }
        tg = null
        endBeepJob?.cancel()
        endBeepJob = null
    }
}
