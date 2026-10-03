package com.churchdrive.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import com.churchdrive.app.CrashReport
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A live view over WebRTC, set up the way Home Assistant's own player does it: our offer goes to
 * `camera/webrtc/offer`, and the answer and the network candidates come back (and go out) over the
 * same connection. [onStatus] gets null once the picture is playing, or a message if it can't be.
 */
@Composable
fun WebRtcPlayer(entityId: String, muted: Boolean, modifier: Modifier = Modifier, onStatus: (String?) -> Unit) {
    val context = LocalContext.current
    val host = LocalCameraHost.current
    val egl = remember { EglBase.create() }
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(egl.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
        }
    }
    var audio by remember { mutableStateOf<AudioTrack?>(null) }
    LaunchedEffect(muted, audio) { audio?.setEnabled(!muted) }

    DisposableEffect(entityId) {
        val session = host?.let { RtcSession(context, egl, it, entityId, renderer, { track -> audio = track }, onStatus) }
        if (session == null) onStatus("Live view isn't available.") else session.start()
        onDispose {
            session?.close()
            renderer.release()
            egl.release()
        }
    }
    AndroidView(factory = { renderer }, modifier = modifier)
}

/**
 * The media sections the offer is set up with, tried in turn if a camera rejects the answer's order. Home Assistant's
 * cameras here answer with just video then audio (no data channel), so that comes first.
 */
private val ORDERS = listOf(
    listOf("video", "audio"),
    listOf("audio", "video"),
    listOf("video"),
    listOf("video", "audio", "data"),
    listOf("data", "audio", "video"),
)

/** One live view's connection. Everything is cleaned up by [close]. */
private class RtcSession(
    private val context: Context,
    private val egl: EglBase,
    private val host: CameraHost,
    private val entityId: String,
    private val renderer: SurfaceViewRenderer,
    private val onAudio: (AudioTrack) -> Unit,
    private val onStatus: (String?) -> Unit,
) : PeerConnection.Observer {
    private var factory: PeerConnectionFactory? = null
    private var connection: PeerConnection? = null
    private var subscription = -1
    private var sessionId: String? = null
    private var servers: List<IceServerSpec> = emptyList()
    private var generation = 0
    private var lastOffer = ""
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val closed = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)
    private var timeout: Thread? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var videoTrack: VideoTrack? = null
    private var audioModule: JavaAudioDeviceModule? = null

    fun start() {
        CrashReport.note("live view: $entityId")
        host.iceServers(entityId) { list ->
            if (closed.get()) return@iceServers
            servers = parseIceServers(list)
            CrashReport.note("ice servers: ${servers.size}")
            attempt(0)
        }
        // Give up if nothing is playing after a while, so the viewer says so instead of waiting for ever.
        timeout = Thread {
            try {
                Thread.sleep(25_000)
                if (!closed.get() && !playing.get()) onStatus("The camera didn't answer. Try again.")
            } catch (_: InterruptedException) {
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** One try at connecting, with the offer's media sections in [ORDERS] order number [n]. */
    private fun attempt(n: Int) {
        runCatching {
            teardown()
            val mine = ++generation
            CrashReport.note("try ${n + 1}: ${ORDERS[n].joinToString(",")}")
            ensureInitialised(context)
            val f = factory ?: PeerConnectionFactory.builder()
                // WebRTC's own Java audio output: the default native one crashed on a Pixel with Android 17.
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context.applicationContext).createAudioDeviceModule().also { audioModule = it })
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .createPeerConnectionFactory().also { factory = it }
            val ice = servers.map { s ->
                PeerConnection.IceServer.builder(s.urls).apply {
                    s.username?.let { setUsername(it) }
                    s.credential?.let { setPassword(it) }
                }.createIceServer()
            }
            val config = PeerConnection.RTCConfiguration(ice).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
            val pc = f.createPeerConnection(config, this) ?: error("No connection")
            connection = pc
            for (kind in ORDERS[n]) {
                when (kind) {
                    "audio" -> pc.addTransceiver(
                        org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
                    )
                    "video" -> pc.addTransceiver(
                        org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
                    )
                    // Home Assistant's own player opens a data channel too; some cameras need it.
                    else -> pc.createDataChannel("dataSendChannel", DataChannel.Init())
                }
            }
            pc.createOffer(object : Sdp() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(object : Sdp() {
                        override fun onSetSuccess() = sendOffer(sdp.description, mine)
                        override fun onSetFailure(error: String?) = fail("Couldn't start the live view ($error)")
                    }, sdp)
                }

                override fun onCreateFailure(error: String?) = fail("Couldn't start the live view ($error)")
            }, MediaConstraints())
            lastAttempt = n
        }.onFailure { fail("Couldn't start the live view (${it.message})") }
    }

    private var lastAttempt = 0

    private fun sendOffer(sdp: String, mine: Int) {
        lastOffer = sdp
        CrashReport.note("offer sent: ${mediaOrder(sdp).joinToString(",")}")
        subscription = host.webRtcOffer(entityId, sdp) { event -> if (mine == generation) handle(parseRtcEvent(event)) }
        if (subscription < 0) fail("Not connected to Home Assistant.")
    }

    private fun handle(event: RtcEvent?) {
        if (closed.get()) return
        CrashReport.note("from camera: ${event?.javaClass?.simpleName}")
        when (event) {
            is RtcEvent.Session -> {
                sessionId = event.id
                // Candidates found before the session id was known go now.
                synchronized(pendingCandidates) {
                    pendingCandidates.forEach { sendCandidate(it) }
                    pendingCandidates.clear()
                }
            }
            is RtcEvent.Answer -> connection?.setRemoteDescription(object : Sdp() {
                override fun onSetSuccess() = CrashReport.note("answer accepted: ${mediaOrder(event.sdp).joinToString(",")}")
                override fun onSetFailure(error: String?) {
                    // A camera that answers in another order: try the next way of setting up the offer.
                    if (error?.contains("m-lines") == true && lastAttempt + 1 < ORDERS.size && !closed.get()) {
                        // Not from inside this connection's own callback: closing it from here can crash.
                        val next = lastAttempt + 1
                        main.post { if (!closed.get()) attempt(next) }
                    } else {
                        fail(
                            "The camera's answer wasn't accepted ($error). " +
                                "Offer: ${mediaOrder(lastOffer).joinToString(",")}; answer: ${mediaOrder(event.sdp).joinToString(",")}.",
                        )
                    }
                }
            }, SessionDescription(SessionDescription.Type.ANSWER, event.sdp))
            is RtcEvent.Candidate -> connection?.addIceCandidate(IceCandidate(event.sdpMid ?: "0", event.sdpMLineIndex, event.candidate))
            is RtcEvent.Error -> {
                CrashReport.note("camera error: ${event.message}")
                fail(event.message)
            }
            null -> Unit
        }
    }

    private fun sendCandidate(c: IceCandidate) {
        val id = sessionId ?: return
        host.webRtcCandidate(entityId, id, candidateJson(c.sdp, c.sdpMid, c.sdpMLineIndex))
    }

    private fun fail(message: String) {
        if (closed.get() || playing.get()) return
        onStatus(message)
    }

    /** Ends the current try (the connection and its subscription), keeping the factory for the next one. */
    private fun teardown() {
        if (subscription >= 0) host.close(subscription)
        subscription = -1
        sessionId = null
        synchronized(pendingCandidates) { pendingCandidates.clear() }
        runCatching { connection?.close() }
        runCatching { connection?.dispose() }
        connection = null
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        timeout?.interrupt()
        runCatching { videoTrack?.removeSink(renderer) }
        videoTrack = null
        teardown()
        runCatching { factory?.dispose() }
        runCatching { audioModule?.release() }
    }

    private fun gotTrack(track: org.webrtc.MediaStreamTrack?) {
        if (closed.get()) return
        CrashReport.note("track: ${track?.kind()}")
        when (track) {
            is VideoTrack -> {
                if (videoTrack === track) return
                videoTrack = track
                track.setEnabled(true)
                track.addSink(renderer)
                if (playing.compareAndSet(false, true)) onStatus(null)
            }
            is AudioTrack -> {
                track.setEnabled(false) // muted until asked, so nothing plays at first
                onAudio(track)
            }
            else -> Unit
        }
    }

    // ---- PeerConnection.Observer
    override fun onIceCandidate(candidate: IceCandidate) {
        if (sessionId == null) synchronized(pendingCandidates) { if (sessionId == null) { pendingCandidates += candidate; return } }
        sendCandidate(candidate)
    }

    override fun onTrack(transceiver: RtpTransceiver) = gotTrack(transceiver.receiver.track())
    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
        CrashReport.note("ice: $state")
        if (state == PeerConnection.IceConnectionState.FAILED) fail("Couldn't reach the camera.")
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
    override fun onAddStream(stream: MediaStream) = Unit
    override fun onRemoveStream(stream: MediaStream) = Unit
    override fun onDataChannel(channel: DataChannel) = Unit
    override fun onRenegotiationNeeded() = Unit

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    companion object {
        @Volatile private var initialised = false

        private fun ensureInitialised(context: Context) {
            if (initialised) return
            synchronized(this) {
                if (initialised) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions(),
                )
                initialised = true
            }
        }
    }
}
