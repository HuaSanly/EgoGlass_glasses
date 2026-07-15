package com.egoglass.glasses.transport.webrtc

import android.content.Context
import android.util.Log
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.capture.CaptureConfig
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.NV21Buffer
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "EgoGlassWebRtc"
private const val METADATA_CHANNEL = "frame-metadata-v1"
private const val MAX_SIGNALING_RESPONSE_BYTES = 1_048_576
private const val MAX_METADATA_BUFFERED_BYTES = 262_144L
private const val MIN_VIDEO_BITRATE_BPS = 4_000_000
private const val MAX_VIDEO_BITRATE_BPS = 10_000_000

fun createAndroidWebRtcPublisher(context: Context): WebRtcPublisher =
    AndroidWebRtcPublisher(context.applicationContext)

private class AndroidWebRtcPublisher(
    private val applicationContext: Context,
) : WebRtcPublisher {
    private val listeners = CopyOnWriteArraySet<WebRtcPublisherListener>()
    private val generation = AtomicLong(0)
    private val offerPosted = AtomicBoolean(false)
    private val localDescriptionReady = AtomicBoolean(false)
    private val frameQueue = LatestFrameQueue<CapturedVideoFrame>()
    private val framesOffered = AtomicLong(0)
    private val framesPublished = AtomicLong(0)
    private val framesDropped = AtomicLong(0)
    private val metadataSent = AtomicLong(0)

    @Volatile
    override var state: WebRtcPublisherState = WebRtcPublisherState.IDLE
        private set

    @Volatile
    private var peerConnection: PeerConnection? = null

    @Volatile
    private var peerConnectionFactory: PeerConnectionFactory? = null

    @Volatile
    private var videoSource: VideoSource? = null

    @Volatile
    private var videoTrack: VideoTrack? = null

    @Volatile
    private var dataChannel: DataChannel? = null

    @Volatile
    private var eglBase: EglBase? = null

    @Volatile
    private var signalingExecutor: ExecutorService? = null

    @Volatile
    private var frameExecutor: ExecutorService? = null

    @Volatile
    private var frameWorkerRunning = false

    private val deviceSessionId = UUID.randomUUID().toString().replace("-", "")

    override fun addListener(listener: WebRtcPublisherListener) {
        listeners.add(listener)
        listener.onStateChanged(state, null)
        listener.onStatsChanged(currentStats())
    }

    override fun removeListener(listener: WebRtcPublisherListener) {
        listeners.remove(listener)
    }

    @Synchronized
    override fun connect(config: WebRtcSessionConfig, captureConfig: CaptureConfig) {
        if (state in setOf(
                WebRtcPublisherState.SIGNALING,
                WebRtcPublisherState.CONNECTING,
                WebRtcPublisherState.STREAMING,
            )
        ) {
            return
        }

        closeResources()
        resetCounters()
        val sessionGeneration = generation.incrementAndGet()
        offerPosted.set(false)
        localDescriptionReady.set(false)
        updateState(WebRtcPublisherState.SIGNALING, "Creating WebRTC offer")
        signalingExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "egoglass-webrtc-signaling")
        }.also { executor ->
            executor.execute {
                runCatching { initializePeer(sessionGeneration, config, captureConfig) }
                    .onFailure { error -> fail(sessionGeneration, error) }
            }
        }
        frameExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "egoglass-webrtc-frames")
        }
        frameWorkerRunning = true
        frameExecutor?.execute { drainFrames(sessionGeneration) }
    }

    override fun offerFrame(frame: CapturedVideoFrame) {
        if (state != WebRtcPublisherState.STREAMING) return
        framesOffered.incrementAndGet()
        if (frameQueue.offerLatest(frame)) {
            framesDropped.incrementAndGet()
        }
    }

    @Synchronized
    override fun close() {
        generation.incrementAndGet()
        closeResources()
        updateState(WebRtcPublisherState.IDLE, null)
    }

    private fun initializePeer(
        sessionGeneration: Long,
        config: WebRtcSessionConfig,
        captureConfig: CaptureConfig,
    ) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )
        val sessionEglBase = EglBase.create()
        eglBase = sessionEglBase
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    sessionEglBase.eglBaseContext,
                    false,
                    true,
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(sessionEglBase.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val source = factory.createVideoSource(false, false)
        source.adaptOutputFormat(
            captureConfig.width,
            captureConfig.height,
            captureConfig.framesPerSecond,
        )
        source.capturerObserver.onCapturerStarted(true)
        videoSource = source
        val track = factory.createVideoTrack("camera-v1", source)
        videoTrack = track

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val peer = factory.createPeerConnection(
            rtcConfig,
            createPeerObserver(sessionGeneration, config),
        ) ?: error("Unable to create PeerConnection")
        peerConnection = peer

        val transceiver = peer.addTransceiver(
            track,
            RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.SEND_ONLY,
                listOf("egoglass-camera"),
            ),
        )
        preferH264(factory, transceiver)
        configureHighQualityVideoSender(transceiver, captureConfig.framesPerSecond)
        check(
            peer.setBitrate(
                MIN_VIDEO_BITRATE_BPS,
                config.targetBitrateBps,
                MAX_VIDEO_BITRATE_BPS,
            )
        ) {
            "Unable to set WebRTC bitrate"
        }

        val channelInit = DataChannel.Init().apply {
            ordered = true
        }
        dataChannel = peer.createDataChannel(METADATA_CHANNEL, channelInit)
        peer.createOffer(
            object : BaseSdpObserver() {
                override fun onCreateSuccess(description: SessionDescription) {
                    peer.setLocalDescription(
                        object : BaseSdpObserver() {
                            override fun onSetSuccess() {
                                localDescriptionReady.set(true)
                                maybePostOffer(sessionGeneration, config)
                            }

                            override fun onSetFailure(error: String) {
                                fail(sessionGeneration, IllegalStateException("local SDP rejected"))
                            }
                        },
                        description,
                    )
                }

                override fun onCreateFailure(error: String) {
                    fail(sessionGeneration, IllegalStateException("offer creation failed"))
                }
            },
            MediaConstraints(),
        )
    }

    private fun createPeerObserver(
        sessionGeneration: Long,
        config: WebRtcSessionConfig,
    ): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED,
                -> updateStateIfCurrent(sessionGeneration, WebRtcPublisherState.STREAMING, null)

                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.CLOSED,
                -> updateStateIfCurrent(
                    sessionGeneration,
                    WebRtcPublisherState.DISCONNECTED,
                    "ICE connection closed",
                )

                PeerConnection.IceConnectionState.FAILED -> fail(
                    sessionGeneration,
                    IllegalStateException("ICE connection failed"),
                )
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                maybePostOffer(sessionGeneration, config)
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) = Unit

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

        override fun onAddStream(stream: MediaStream) = Unit

        override fun onRemoveStream(stream: MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) = Unit

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) = Unit
    }

    private fun maybePostOffer(sessionGeneration: Long, config: WebRtcSessionConfig) {
        val peer = peerConnection ?: return
        if (!localDescriptionReady.get()) return
        if (peer.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) return
        if (!offerPosted.compareAndSet(false, true)) return
        signalingExecutor?.execute {
            runCatching {
                val localDescription = peer.localDescription ?: error("Local SDP is unavailable")
                val answerSdp = exchangeSdp(config, localDescription.description)
                peer.setRemoteDescription(
                    object : BaseSdpObserver() {
                        override fun onSetSuccess() {
                            updateStateIfCurrent(
                                sessionGeneration,
                                WebRtcPublisherState.CONNECTING,
                                "Waiting for ICE connection",
                            )
                        }

                        override fun onSetFailure(error: String) {
                            fail(
                                sessionGeneration,
                                IllegalStateException("remote SDP rejected"),
                            )
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, answerSdp),
                )
            }.onFailure { error -> fail(sessionGeneration, error) }
        }
    }

    private fun exchangeSdp(config: WebRtcSessionConfig, offerSdp: String): String {
        val connection = URL(config.signalingUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${config.pairingToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject()
                .put("schema_version", "1.0")
                .put("device_session_id", deviceSessionId)
                .put("type", "offer")
                .put("sdp", offerSdp)
                .toString()
            connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(payload)
            }

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Signaling HTTP ${connection.responseCode}")
            }
            val response = readLimited(connection.inputStream)
            val answer = JSONObject(response.toString(StandardCharsets.UTF_8))
            check(answer.getString("schema_version") == "1.0")
            check(answer.getString("type") == "answer")
            return answer.getString("sdp")
        } finally {
            connection.disconnect()
        }
    }

    private fun drainFrames(sessionGeneration: Long) {
        while (frameWorkerRunning && sessionGeneration == generation.get()) {
            val frame = frameQueue.poll(250) ?: continue
            publishFrame(sessionGeneration, frame)
        }
    }

    private fun publishFrame(sessionGeneration: Long, frame: CapturedVideoFrame) {
        if (sessionGeneration != generation.get()) return
        val source = videoSource ?: return
        val buffer = NV21Buffer(frame.nv21, frame.width, frame.height, null)
        val videoFrame = VideoFrame(buffer, frame.rotationDegrees, frame.videoAtMonotonicNs)
        source.capturerObserver.onFrameCaptured(videoFrame)
        videoFrame.release()
        val published = framesPublished.incrementAndGet()
        sendMetadata(frame)
        if (published % 20L == 0L) notifyStats()
    }

    private fun sendMetadata(frame: CapturedVideoFrame) {
        val channel = dataChannel ?: return
        if (channel.state() != DataChannel.State.OPEN) return
        if (channel.bufferedAmount() > MAX_METADATA_BUFFERED_BYTES) return
        val payload = JSONObject()
            .put("schema_version", "1.0")
            .put("message_type", "video_frame")
            .put("stream_id", "camera")
            .put("frame_id", frame.frameId)
            .put("captured_at_rokid_sdk_ms", frame.capturedAtRokidSdkMs)
            .put("received_at_elapsed_realtime_ns", frame.receivedAtElapsedRealtimeNs)
            .put("video_at_monotonic_ns", frame.videoAtMonotonicNs)
            .put("rtp_timestamp_90khz", monotonicNsToRtpTimestamp90Khz(frame.videoAtMonotonicNs))
            .put("width", frame.width)
            .put("height", frame.height)
            .put("rotation_degrees", frame.rotationDegrees)
            .put("capture_config_id", frame.captureConfigId)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        if (channel.send(DataChannel.Buffer(ByteBuffer.wrap(payload), false))) {
            metadataSent.incrementAndGet()
        }
    }

    private fun preferH264(factory: PeerConnectionFactory, transceiver: RtpTransceiver) {
        val h264Codecs = factory
            .getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
            .codecs
            .filter { codec -> codec.mimeType.equals("video/H264", ignoreCase = true) }
        check(h264Codecs.isNotEmpty()) { "No H.264 encoder is available" }
        transceiver.setCodecPreferences(h264Codecs)
    }

    private fun configureHighQualityVideoSender(
        transceiver: RtpTransceiver,
        framesPerSecond: Int,
    ) {
        val parameters = transceiver.sender.parameters
        parameters.degradationPreference =
            RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
        parameters.encodings.forEach { encoding ->
            encoding.minBitrateBps = MIN_VIDEO_BITRATE_BPS
            encoding.maxBitrateBps = MAX_VIDEO_BITRATE_BPS
            encoding.maxFramerate = framesPerSecond
            encoding.scaleResolutionDownBy = 1.0
        }
        check(transceiver.sender.setParameters(parameters)) {
            "Unable to configure high-quality WebRTC video"
        }
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_SIGNALING_RESPONSE_BYTES) {
                throw IllegalStateException("Signaling response is too large")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun updateStateIfCurrent(
        sessionGeneration: Long,
        newState: WebRtcPublisherState,
        detail: String?,
    ) {
        if (sessionGeneration == generation.get()) updateState(newState, detail)
    }

    private fun updateState(newState: WebRtcPublisherState, detail: String?) {
        state = newState
        Log.i(TAG, "webrtc_state=${newState.name}")
        listeners.forEach { listener -> listener.onStateChanged(newState, detail) }
    }

    private fun fail(sessionGeneration: Long, error: Throwable) {
        if (sessionGeneration != generation.get()) return
        Log.e(TAG, "webrtc_failure=${error::class.java.simpleName}", error)
        updateState(WebRtcPublisherState.ERROR, error.message ?: "WebRTC failed")
    }

    private fun currentStats() = WebRtcPublisherStats(
        framesOffered = framesOffered.get(),
        framesPublished = framesPublished.get(),
        framesDropped = framesDropped.get(),
        metadataSent = metadataSent.get(),
    )

    private fun notifyStats() {
        val stats = currentStats()
        Log.i(
            TAG,
            "frames_published=${stats.framesPublished} frames_dropped=${stats.framesDropped} " +
                "metadata_sent=${stats.metadataSent}",
        )
        listeners.forEach { listener -> listener.onStatsChanged(stats) }
    }

    private fun resetCounters() {
        framesOffered.set(0)
        framesPublished.set(0)
        framesDropped.set(0)
        metadataSent.set(0)
    }

    private fun closeResources() {
        frameWorkerRunning = false
        frameQueue.clear()
        frameExecutor?.shutdownNow()
        frameExecutor = null
        signalingExecutor?.shutdownNow()
        signalingExecutor = null
        dataChannel?.runCatching {
            close()
            dispose()
        }
        dataChannel = null
        peerConnection?.runCatching {
            close()
            dispose()
        }
        peerConnection = null
        videoSource?.capturerObserver?.onCapturerStopped()
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
    }

    private open class BaseSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit

        override fun onSetSuccess() = Unit

        override fun onCreateFailure(error: String) = Unit

        override fun onSetFailure(error: String) = Unit
    }
}
