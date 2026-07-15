package com.egoglass.glasses.streaming

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.capture.VideoFrameSource
import com.egoglass.glasses.capture.VideoFrameSourceListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisher
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherState
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import java.util.concurrent.CopyOnWriteArraySet

class DefaultStreamingSession(
    private val frameSource: VideoFrameSource,
    private val publisher: WebRtcPublisher,
) : StreamingSession, WebRtcPublisherListener, VideoFrameSourceListener {
    private val listeners = CopyOnWriteArraySet<StreamingSessionListener>()

    @Volatile
    override var state: StreamingSessionState = StreamingSessionState.IDLE
        private set

    @Volatile
    private var captureStarted = false

    private var captureConfig = CaptureConfig()

    init {
        publisher.addListener(this)
    }

    override fun addListener(listener: StreamingSessionListener) {
        listeners.add(listener)
        listener.onStateChanged(state, null)
    }

    override fun removeListener(listener: StreamingSessionListener) {
        listeners.remove(listener)
    }

    @Synchronized
    override fun start(sessionConfig: WebRtcSessionConfig, captureConfig: CaptureConfig) {
        if (state in setOf(
                StreamingSessionState.SIGNALING,
                StreamingSessionState.CONNECTING,
                StreamingSessionState.CAPTURING,
                StreamingSessionState.STREAMING,
            )
        ) {
            return
        }
        this.captureConfig = captureConfig
        publisher.connect(sessionConfig, captureConfig)
    }

    @Synchronized
    override fun stop() {
        captureStarted = false
        frameSource.stop()
        publisher.close()
        updateState(StreamingSessionState.IDLE, null)
    }

    override fun onStateChanged(state: WebRtcPublisherState, detail: String?) {
        when (state) {
            WebRtcPublisherState.IDLE -> updateState(StreamingSessionState.IDLE, detail)
            WebRtcPublisherState.SIGNALING -> updateState(StreamingSessionState.SIGNALING, detail)
            WebRtcPublisherState.CONNECTING -> updateState(StreamingSessionState.CONNECTING, detail)
            WebRtcPublisherState.STREAMING -> startCapture()
            WebRtcPublisherState.DISCONNECTED -> {
                stopCapture()
                updateState(StreamingSessionState.DISCONNECTED, detail)
            }
            WebRtcPublisherState.ERROR -> {
                stopCapture()
                updateState(StreamingSessionState.ERROR, detail)
            }
        }
    }

    override fun onStatsChanged(stats: WebRtcPublisherStats) {
        listeners.forEach { listener -> listener.onStatsChanged(stats) }
    }

    override fun onCameraOpened(width: Int, height: Int, appliedFramesPerSecond: Int?) {
        val fps = appliedFramesPerSecond?.let { " at $it FPS" }.orEmpty()
        updateState(StreamingSessionState.STREAMING, "${width}x$height$fps")
    }

    override fun onFrame(frame: CapturedVideoFrame) {
        publisher.offerFrame(frame)
    }

    override fun onCameraClosed() {
        if (captureStarted) {
            updateState(StreamingSessionState.DISCONNECTED, "Glass3 camera closed")
        }
    }

    override fun onError(message: String) {
        stopCapture()
        updateState(StreamingSessionState.ERROR, message)
    }

    @Synchronized
    private fun startCapture() {
        if (captureStarted) return
        captureStarted = true
        updateState(StreamingSessionState.CAPTURING, "Opening Glass3 camera")
        frameSource.start(captureConfig, this)
    }

    @Synchronized
    private fun stopCapture() {
        if (!captureStarted) return
        captureStarted = false
        frameSource.stop()
    }

    private fun updateState(newState: StreamingSessionState, detail: String?) {
        state = newState
        listeners.forEach { listener -> listener.onStateChanged(newState, detail) }
    }
}
