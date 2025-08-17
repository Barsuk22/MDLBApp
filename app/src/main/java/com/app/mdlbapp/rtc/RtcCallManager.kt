package com.app.mdlbapp.rtc


import android.content.Context
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
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
        )
        val enc = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val dec = DefaultVideoDecoderFactory(egl.eglBaseContext)
        PeerConnectionFactory.builder().setVideoEncoderFactory(enc).setVideoDecoderFactory(dec).createPeerConnectionFactory()
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
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
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

    fun attachMedia(sendVideo: Boolean) {
        pc.addTrack(audioTrack)
        if (sendVideo) pc.addTrack(videoTrack)
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
                    val cid = CallRepository.createOutgoingCall(
                        tid, meUid, peerUid, SdpBlob(desc.type.canonical(), desc.description)
                    )
                    currentCallId = cid
                    onCreated(cid)
                    listenRemoteIce(cid)
                }
            }
        }, mc)
    }

    fun acceptOffer(callId: String, offer: SdpBlob, sendVideo: Boolean) {
        currentCallId = callId
        attachMedia(sendVideo)
        pc.setRemoteDescription(sdpStub(), SessionDescription(SessionDescription.Type.OFFER, offer.sdp))
        pc.createAnswer(object : SdpObserver by sdpStub() {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(sdpStub(), desc)
                scope.launch { CallRepository.setAnswer(tid, callId, SdpBlob(desc.type.canonical(), desc.description)) }
                listenRemoteIce(callId)
            }
        }, MediaConstraints())
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

    fun endCall() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose(); videoSource.dispose(); audioSource.dispose(); pc.close()
        localView.release(); remoteView.release(); egl.release()
    }

    private fun sdpStub() = object : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
    private fun SessionDescription.Type.canonical() =
        if (this == SessionDescription.Type.OFFER) "offer" else "answer"
}