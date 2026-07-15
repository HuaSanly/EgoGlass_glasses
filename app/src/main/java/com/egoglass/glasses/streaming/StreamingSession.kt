package com.egoglass.glasses.streaming

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig

enum class StreamingSessionState {
    IDLE,
    SIGNALING,
    CONNECTING,
    CAPTURING,
    STREAMING,
    DISCONNECTED,
    ERROR,
}

interface StreamingSessionListener {
    fun onStateChanged(state: StreamingSessionState, detail: String?)

    fun onStatsChanged(stats: WebRtcPublisherStats)
}

interface StreamingSession {
    val state: StreamingSessionState

    fun addListener(listener: StreamingSessionListener)

    fun removeListener(listener: StreamingSessionListener)

    fun start(
        sessionConfig: WebRtcSessionConfig,
        captureConfig: CaptureConfig = CaptureConfig(),
    )

    fun stop()
}
