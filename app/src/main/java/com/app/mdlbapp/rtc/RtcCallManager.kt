package com.app.mdlbapp.rtc


import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.app.mdlbapp.data.call.CallRepository
import com.app.mdlbapp.data.call.CallRepository.getOrCreateRtcKeyB64
import com.app.mdlbapp.data.call.IceBlob
import com.app.mdlbapp.data.call.SdpBlob
import com.app.mdlbapp.data.call.notifyCallViaAppsScript
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.getValue


class RtcCallManager(
    private val ctx: Context,
    private val tid: String,
    private val meUid: String,
    private val peerUid: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    val pcState = kotlinx.coroutines.flow.MutableStateFlow(PeerConnection.PeerConnectionState.NEW)
    val iceState = kotlinx.coroutines.flow.MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    var onFatalDisconnect: (() -> Unit)? = null

    // аудиофокус
    private var audioFocus: Int = AudioManager.AUDIOFOCUS_LOSS
    private var focusRequest: AudioFocusRequest? = null

    private fun enterCallAudioMode() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener { /* ignore */ }
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocus = am.requestAudioFocus(focusRequest!!)
        } else {
            audioFocus = am.requestAudioFocus(null,
                AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        // спикер держим как раньше через setSpeakerphone(), тут не трогаем
    }

    private fun leaveCallAudioMode() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { am.abandonAudioFocusRequest(it) }
            else am.abandonAudioFocus(null)
        } catch (_: Throwable) {}
        am.mode = AudioManager.MODE_NORMAL
        am.isSpeakerphoneOn = false
        focusRequest = null
        audioFocus = AudioManager.AUDIOFOCUS_LOSS
    }

    companion object {
        @Volatile private var inited = false
        private fun ensureInit(ctx: Context) {
            if (!inited) synchronized(this) {
                if (!inited) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions
                            .builder(ctx).createInitializationOptions()
                    )
                    inited = true
                }
            }
        }
    }

    private var videoSender: RtpSender? = null
    private var mediaAttached = false
    fun attachMedia(sendVideoInitially: Boolean) {
        if (mediaAttached) return
        mediaAttached = true

        // ===== АУДИО =====
        // ВАЖНО: добавляем с streamId → корректный msid/ssrc в SDP
        try {
            audioSender = pc.addTrack(audioTrack, listOf("ARDAMS"))
            audioTransceiver = findTransceiverFor(audioSender)
        } catch (_: Throwable) {
            // совсем старые билды: на крайний случай — через transceiver без streamId
            try {
                audioTransceiver = pc.addTransceiver(
                    audioTrack,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                )
                audioSender = audioTransceiver?.sender
            } catch (_: Exception) { }
        }

        // ===== ВИДЕО =====
        try {
            videoSender = pc.addTrack(videoTrack, listOf("ARDAMS"))
            videoTransceiver = findTransceiverFor(videoSender)
        } catch (_: Throwable) {
            try {
                videoTransceiver = pc.addTransceiver(
                    videoTrack,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                )
                videoSender = videoTransceiver?.sender
            } catch (_: Exception) { }
        }

        // На всякий — явно держим SEND_RECV на обоих
        try {
            audioTransceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            videoTransceiver?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
        } catch (_: Throwable) {}

        // Локальные превью уже подключены в startLocalVideo()

        // Важный момент: отправку видео контролируем ТОЛЬКО через sender.setTrack/null
        setVideoActiveInternal(sendVideoInitially)

        dumpDirections("attachMedia")

        android.util.Log.d("CALL", "video sending = $sendVideoInitially (encodings/replaceTrack)")
    }

    private fun dumpDirections(tag: String) {
        try {
            val aDir = audioTransceiver?.direction?.name ?: "null"
            val vDir = videoTransceiver?.direction?.name ?: "null"
            val vHasTrack = (videoSender?.track() != null)
            val aHasTrack = (audioSender?.track() != null)
            android.util.Log.d("CALL", "[$tag] A=$aDir hasTrack=$aHasTrack; V=$vDir hasTrack=$vHasTrack")
        } catch (_: Throwable) {}
    }

    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory by lazy {
        ensureInit(ctx)
        val enc = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val dec = DefaultVideoDecoderFactory(egl.eglBaseContext)
        PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(enc)
            .setVideoDecoderFactory(dec)
            .createPeerConnectionFactory()
    }

    private val adm: JavaAudioDeviceModule by lazy {
        JavaAudioDeviceModule.builder(ctx)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
    }

    private val audioSource = factory.createAudioSource(MediaConstraints())
    private val audioTrack = factory.createAudioTrack("AUDIO", audioSource)

    private val videoSource = factory.createVideoSource(false)
    private val videoTrack = factory.createVideoTrack("VIDEO", videoSource)
    private val surfaceHelper = SurfaceTextureHelper.create("cap", egl.eglBaseContext)
    private val capturer: VideoCapturer? = Camera2Enumerator(ctx).run {
        deviceNames.firstOrNull { isFrontFacing(it) }?.let { createCapturer(it, null) }
    }

    val localPreviewView = SurfaceViewRenderer(ctx).apply {
        init(egl.eglBaseContext, null)
        setMirror(true)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        setZOrderMediaOverlay(false)   // полноэкранный — не overlay
    }
    val localPipView = SurfaceViewRenderer(ctx).apply {
        init(egl.eglBaseContext, null)
        setMirror(true)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        setZOrderMediaOverlay(true)
    }

    val remoteView = SurfaceViewRenderer(ctx).apply {
        init(egl.eglBaseContext, null)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        setZOrderMediaOverlay(false)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _remoteHasVideo = kotlinx.coroutines.flow.MutableStateFlow(false)
    val remoteHasVideo: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _remoteHasVideo
    private inner class FirstFrameSink : VideoSink {
        @Volatile private var fired = false
        override fun onFrame(frame: VideoFrame) {
            if (!fired) {
                fired = true
                // Флажочек поднимаем на главном потоке → Compose точно увидит
                mainHandler.post { _remoteHasVideo.value = true }
            }
        }
    }
    private val firstFrameSink = FirstFrameSink()
    private val pc: PeerConnection = factory.createPeerConnection(
        PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=udp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        },
        object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                currentCallId?.let { cid ->
                    scope.launch {
                        ensureRtcKey()
                        CallRepository.addIceEncrypted(
                            tid, cid,
                            IceBlob(meUid, c.sdpMid ?: "", c.sdpMLineIndex, c.sdp),
                            rtcKeyB64!! // <—
                        )
                    }
                }
            }

            // ок, пусть будет — некоторых версиях обязателен
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}

            // — показываем мамочку в удалённом окне —
            override fun onTrack(t: RtpTransceiver) {
                val track = t.receiver.track()
                android.util.Log.d("CALL", "onTrack: kind=${track?.kind()}")
                (track as? VideoTrack)?.let { vt ->
                    vt.addSink(remoteView)
                    vt.addSink(firstFrameSink)
                    android.util.Log.d("CALL", "remote video track attached; waiting first frame…")
                }
            }

            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = receiver.track()
                android.util.Log.d("CALL", "onAddTrack: kind=${track?.kind()}")
                (track as? VideoTrack)?.let { vt ->
                    vt.addSink(remoteView)
                    vt.addSink(firstFrameSink)
                    android.util.Log.d("CALL", "remote video track attached (legacy); waiting first frame…")
                }
            }

            // остальное — пустышки
            @Deprecated("Deprecated in WebRTC") override fun onAddStream(stream: MediaStream) {}
            @Deprecated("Deprecated in WebRTC") override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) { /* no-op */ }

            override fun onRenegotiationNeeded() {}

            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                pcState.value = state
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        // in-call аудио как только реально соединились
                        enterCallAudioMode()
                    }
                    PeerConnection.PeerConnectionState.CLOSED,
                    PeerConnection.PeerConnectionState.FAILED -> {
                        onFatalDisconnect?.invoke()
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                iceState.value = state
                when (state) {
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> onFatalDisconnect?.invoke()
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        // небольшой grace-period: если за 10с не вернулись — падаем
                        mainHandler.postDelayed({
                            if (iceState.value == PeerConnection.IceConnectionState.DISCONNECTED) {
                                onFatalDisconnect?.invoke()
                            }
                        }, 10_000)
                    }
                    else -> {}
                }
            }
        }
    )!!

    private var currentCallId: String? = null

    fun startLocalVideo(w: Int = 720, h: Int = 1280, fps: Int = 30) {
        capturer?.initialize(surfaceHelper, ctx, videoSource.capturerObserver)
        capturer?.startCapture(w, h, fps)
        videoTrack.setEnabled(true)
        android.util.Log.d("CALL", "startLocalVideo: capture started")


        // КРИТИЧЕСКОЕ: подвешиваем трек к обоим локальным рендерам
        try {
            videoTrack.addSink(localPreviewView)
            videoTrack.addSink(localPipView)
        } catch (_: Exception) { }
    }

    // ✅ Новая функция — включать/выключать ТОЛЬКО отправку (не влияем на превью)
    fun setVideoSending(enabled: Boolean) {
        android.util.Log.d("CALL", "setVideoSending($enabled) — begin")
        setVideoActiveInternal(enabled)
        android.util.Log.d("CALL", "setVideoSending($enabled) — end")
    }



    fun setVideoEnabled(enabled: Boolean) { videoTrack.setEnabled(enabled) }

    fun makeOffer(sendVideo: Boolean, onCreated: (String) -> Unit) {
        attachMedia(sendVideo) // ← ВАЖНО: треки добавляем всегда
        val mc = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(object : SdpObserver by sdpStub() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(sdpStub(), desc)
                scope.launch {
                    runCatching {
                        ensureRtcKey()
                        val cid = CallRepository.createOutgoingCallEncrypted(
                            tid, meUid, peerUid,
                            SdpBlob(desc.type.canonical(), desc.description),
                            rtcKeyB64!!
                        )
                        currentCallId = cid
                        onCreated(cid)

                        val callerName = runCatching {
                            val meDoc = com.google.firebase.ktx.Firebase.firestore
                                .collection("users").document(meUid).get().await()
                            meDoc.getString("displayName") ?: "Мамочка"
                        }.getOrElse { "Мамочка" }

                        notifyCallViaAppsScript(
                            webHookUrl = com.app.mdlbapp.data.call.PushConfig.WEBHOOK_URL,
                            calleeUid  = peerUid,
                            callerUid  = meUid,
                            callerName = callerName,
                            hookSecret = com.app.mdlbapp.data.call.PushConfig.WEBHOOK_SECRET.ifBlank { null }
                        )

                        listenRemoteIce(cid)
                    }.onFailure { e ->
                        android.util.Log.e("CALL", "webhook failed", e)
                        android.widget.Toast.makeText(
                            ctx, "Не смогла создать звонок: ${e.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, mc)
    }



    // RtcCallManager.kt
    private fun setVideoActiveInternal(active: Boolean) {
        val s = videoSender ?: return

        // 1) НАДЁЖНЫЙ способ: replaceTrack (sender.setTrack)
        val replaced = runCatching {
            if (active) {
                if (s.track() != videoTrack) s.setTrack(videoTrack, /*takeOwnership=*/false)
            } else {
                if (s.track() != null) s.setTrack(null, /*takeOwnership=*/false)
            }
            true
        }.getOrElse { false }

        if (replaced) {
            android.util.Log.d("CALL", "video TX ${if (active) "ON" else "OFF"}; via sender.setTrack")
            return
        }

        // 2) Запасной путь: encodings.active
        runCatching {
            val params = s.parameters
            if (params.encodings.isNotEmpty()) {
                var changed = false
                params.encodings.forEach { enc ->
                    if (enc.active != active) { enc.active = active; changed = true }
                }
                if (changed) s.parameters = params
            }
        }.onSuccess {
            android.util.Log.d("CALL", "video TX ${if (active) "ON" else "OFF"}; via encodings.active")
        }.onFailure {
            android.util.Log.w("CALL", "video TX toggle failed: ${it.message}")
        }
        dumpDirections("setVideoActiveInternal")
    }

    fun acceptOffer(callId: String, offer: SdpBlob, sendVideo: Boolean) {
        currentCallId = callId
        attachMedia(sendVideo)  // добавим треки (с антидублем см. Патч B ниже)

        val remote = SessionDescription(SessionDescription.Type.OFFER, offer.sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                // теперь создавать ответ МОЖНО

                val answerConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        // сначала локально применяем answer…
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                // …и только затем отправляем в Firestore
                                scope.launch {
                                    runCatching {
                                        ensureRtcKey()
                                        CallRepository.setAnswerEncrypted(
                                            tid, callId,
                                            SdpBlob(desc.type.canonical(), desc.description),
                                            rtcKeyB64!! // <—
                                        )
                                        CallRepository.setState(tid, callId, "connected")
                                    }.onFailure { e ->
                                        android.widget.Toast.makeText(
                                            ctx, "Не смогла отдать answer: ${e.message}", android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                listenRemoteIce(callId)
                            }
                            override fun onSetFailure(p0: String?) {
                                android.widget.Toast.makeText(
                                    ctx, "Не смогла применить локальный answer: $p0",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, desc)
                    }

                    override fun onCreateFailure(err: String?) {
                        android.widget.Toast.makeText(
                            ctx, "Не смогла создать answer: $err",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onSetSuccess() {}
                    override fun onSetFailure(p0: String?) {}
                }, answerConstraints)
            }

            override fun onSetFailure(err: String?) {
                android.widget.Toast.makeText(
                    ctx, "Не смогла применить offer: $err",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, remote)
    }



    // когда я — зовущий
    fun setRemoteAnswer(answer: SdpBlob) {
        pc.setRemoteDescription(sdpStub(),
            SessionDescription(SessionDescription.Type.ANSWER, answer.sdp))
        // заранее включим аудио-режим — соединение вот-вот будет
    }

    private fun listenRemoteIce(callId: String) {
        scope.launch {
            ensureRtcKey()
            CallRepository.watchIceDecrypted(tid, callId, rtcKeyB64!!).collect { ice ->
                if (ice.fromUid != meUid) {
                    pc.addIceCandidate(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate))
                }
            }
        }
    }

    private var rtcKeyB64: String? = null

    private suspend fun ensureRtcKey() {
        if (rtcKeyB64 == null) {
            rtcKeyB64 = getOrCreateRtcKeyB64(tid, meUid, peerUid)
        }
    }

    @Volatile private var closed = false
    fun endCall() {
        if (closed) return
        closed = true
        leaveCallAudioMode()
        try {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).apply {
                mode = android.media.AudioManager.MODE_NORMAL
                isSpeakerphoneOn = false
            }
        } catch (_: Exception) {}

        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        try { videoSource.dispose() } catch (_: Exception) {}
        try { audioSource.dispose() } catch (_: Exception) {}
        try { pc.close() } catch (_: Exception) {}
        try { localPreviewView.release() } catch (_: Exception) {}
        try { localPipView.release() } catch (_: Exception) {}
        try { remoteView.release() } catch (_: Exception) {}
        try { egl.release() } catch (_: Exception) {}
        try { adm.release() } catch (_: Exception) {}
    }

    private fun sdpStub() = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
    private fun SessionDescription.Type.canonical() =
        if (this == SessionDescription.Type.OFFER) "offer" else "answer"

    fun setMicEnabled(enabled: Boolean) { audioTrack.setEnabled(enabled) }
    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun setSpeakerphone(on: Boolean) {
        val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }

    private var videoTransceiver: RtpTransceiver? = null
    private var audioTransceiver: RtpTransceiver? = null

    private var audioSender: RtpSender? = null
    // videoSender уже есть
    private fun findTransceiverFor(sender: RtpSender?): RtpTransceiver? {
        if (sender == null) return null
        return pc.transceivers.firstOrNull { it.sender.id() == sender.id() }
    }
}