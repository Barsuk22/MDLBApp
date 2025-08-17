package com.app.mdlbapp.rtc


import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import com.app.mdlbapp.data.call.CallRepository
import com.app.mdlbapp.data.call.IceBlob
import com.app.mdlbapp.data.call.SdpBlob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    val localView = SurfaceViewRenderer(ctx).apply { init(egl.eglBaseContext, null); setMirror(true) }
    val remoteView = SurfaceViewRenderer(ctx).apply { init(egl.eglBaseContext, null) }

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
                        CallRepository.addIce(
                            tid, cid, IceBlob(meUid, c.sdpMid ?: "", c.sdpMLineIndex, c.sdp)
                        )
                    }
                }
            }

            // ок, пусть будет — некоторых версиях обязателен
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState) {}

            // — показываем мамочку в удалённом окне —
            override fun onTrack(t: RtpTransceiver) {
                (t.receiver.track() as? VideoTrack)?.addSink(remoteView)
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
        videoTrack.addSink(localView)
    }

    private var mediaAttached = false
    fun attachMedia(sendVideo: Boolean) {
        if (mediaAttached) return
        mediaAttached = true
        try { pc.addTrack(audioTrack) } catch (_: Exception) { }
        if (sendVideo) try { pc.addTrack(videoTrack) } catch (_: Exception) { }
    }

    fun makeOffer(sendVideo: Boolean, onCreated: (String) -> Unit) {
        attachMedia(sendVideo)
        val mc = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", sendVideo.toString()))
        }
        pc.createOffer(object : SdpObserver by sdpStub() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(sdpStub(), desc)
                scope.launch {
                    runCatching {
                    val cid = CallRepository.createOutgoingCall(
                        tid, meUid, peerUid, SdpBlob(desc.type.canonical(), desc.description)
                    )
                    currentCallId = cid
                    onCreated(cid)
                    listenRemoteIce(cid)
                }.onFailure { e ->
                        android.widget.Toast
                            .makeText(ctx, "Не смогла создать звонок: ${e.message}", android.widget.Toast.LENGTH_LONG)
                            .show()
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
                                        CallRepository.setAnswer(tid, callId, SdpBlob(desc.type.canonical(), desc.description))
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
            CallRepository.watchIce(tid, callId).collect { ice ->
                if (ice.fromUid != meUid) pc.addIceCandidate(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate))
            }
        }
    }

    @Volatile private var closed = false
    fun endCall() {
        if (closed) return
        closed = true
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        try { videoSource.dispose() } catch (_: Exception) {}
        try { audioSource.dispose() } catch (_: Exception) {}
        try { pc.close() } catch (_: Exception) {}
        try { localView.release() } catch (_: Exception) {}
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
    fun setVideoEnabled(enabled: Boolean) { videoTrack.setEnabled(enabled) }
    fun switchCamera() { (capturer as? CameraVideoCapturer)?.switchCamera(null) }
    fun setSpeakerphone(on: Boolean) {
        val am = ctx.getSystemService(AUDIO_SERVICE) as AudioManager
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = on
    }
}