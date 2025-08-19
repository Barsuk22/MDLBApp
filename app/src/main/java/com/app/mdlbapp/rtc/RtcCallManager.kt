package com.app.mdlbapp.rtc


import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
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
import kotlin.getValue


class RtcCallManager(
    private val ctx: Context,
    private val tid: String,
    private val meUid: String,
    private val peerUid: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
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

    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory by lazy {
        ensureInit(ctx) // <<< ВАЖНО: инициализируем один раз на процесс
        val enc = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val dec = DefaultVideoDecoderFactory(egl.eglBaseContext)
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(enc)
            .setVideoDecoderFactory(dec)
            .createPeerConnectionFactory()
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

    private val _remoteHasVideo = kotlinx.coroutines.flow.MutableStateFlow(false)
    val remoteHasVideo: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _remoteHasVideo

    private val pc: PeerConnection = factory.createPeerConnection(
        PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),

//                // 👇 добавь свой TURN:
//                PeerConnection.IceServer.builder("turn:YOUR_TURN_HOST:3478")
//                    .setUsername("YOUR_USER")
//                    .setPassword("YOUR_PASS")
//                    .createIceServer()

                // — публичный TURN OpenRelay (UDP через 80) —
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80?transport=udp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer(),

                // — тот же TURN по TCP через 443 (на случай злых сетей) —
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                    .setUsername("openrelayproject")
                    .setPassword("openrelayproject")
                    .createIceServer()
            )
        ),
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
            private val _remoteHasVideo = kotlinx.coroutines.flow.MutableStateFlow(false)
            val remoteHasVideo: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _remoteHasVideo

            override fun onTrack(t: RtpTransceiver) {
                android.util.Log.d("CALL","onTrack: ${t.receiver.track()?.kind()}")
                (t.receiver.track() as? VideoTrack)?.let { vt ->
                    vt.addSink(remoteView)
                    _remoteHasVideo.value = true
                }
            }
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.addSink(remoteView)
            }

            // остальное — пустышки
            @Deprecated("Deprecated in WebRTC") override fun onAddStream(stream: MediaStream) {}
            @Deprecated("Deprecated in WebRTC") override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) { /* no-op */ }

            override fun onRenegotiationNeeded() {}
        }
    )!!

    private var currentCallId: String? = null

    fun startLocalVideo(w: Int = 720, h: Int = 1280, fps: Int = 30) {
        capturer?.initialize(surfaceHelper, ctx, videoSource.capturerObserver)
        capturer?.startCapture(w, h, fps)
        videoTrack.setEnabled(true)

        // КРИТИЧЕСКОЕ: подвешиваем трек к обоим локальным рендерам
        try {
            videoTrack.addSink(localPreviewView)
            videoTrack.addSink(localPipView)
        } catch (_: Exception) { }
    }
    private var videoSender: RtpSender? = null
    private var mediaAttached = false


    fun attachMedia(sendVideoInitially: Boolean) {
        if (mediaAttached) return
        mediaAttached = true

        try { pc.addTrack(audioTrack) } catch (_: Exception) {}

        try {
            // Добавляем видео-трек, получаем sender (для управления отправкой)
            videoSender = pc.addTrack(videoTrack)

            // Если начинать нужно «в тихом режиме» — не отправляем (превью локально будет жить)
            if (!sendVideoInitially) {
                videoSender?.setTrack(null, false /* takeOwnership параметр не нужен в Android API */)
                android.util.Log.d("CALL", "video sending = false (detached)")
            } else {
                android.util.Log.d("CALL", "video sending = true (attached)")
            }
        } catch (_: Exception) {}
    }

    // ✅ Новая функция — включать/выключать ТОЛЬКО отправку (не влияем на превью)
    fun setVideoSending(enabled: Boolean) {
        try {
            val s = videoSender ?: return
            if (enabled) {
                s.setTrack(videoTrack, false)
                android.util.Log.d("CALL", "video sending = true")
            } else {
                s.setTrack(null, false)
                android.util.Log.d("CALL", "video sending = false")
            }
        } catch (e: Exception) {
            android.util.Log.e("CALL", "setVideoSending failed: ${e.message}")
        }
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


    fun acceptOffer(callId: String, offer: SdpBlob, sendVideo: Boolean) {
        currentCallId = callId
        attachMedia(sendVideo)  // добавим треки (с антидублем см. Патч B ниже)

        val remote = SessionDescription(SessionDescription.Type.OFFER, offer.sdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                // теперь создавать ответ МОЖНО
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
                }, MediaConstraints())
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

    fun setRemoteAnswer(answer: SdpBlob) {
        pc.setRemoteDescription(sdpStub(), SessionDescription(SessionDescription.Type.ANSWER, answer.sdp))
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
}