package com.app.mdlbapp.data.call

import kotlinx.coroutines.flow.MutableStateFlow

object CallRuntime {
    @Volatile var rtc: com.app.mdlbapp.rtc.RtcCallManager? = null

    @Volatile var tid: String? = null
    @Volatile var callId: String? = null
    @Volatile var peerUid: String? = null
    @Volatile var peerName: String? = null

    // когда реально соединились — ставим сюда эпоху (System.currentTimeMillis)
    @Volatile var callStartedAtUptimeMs: Long? = null
    // сервис следит за состоянием и обновляет уведомление
    val connected = MutableStateFlow(false)
    @Volatile var asCaller: Boolean? = null
}