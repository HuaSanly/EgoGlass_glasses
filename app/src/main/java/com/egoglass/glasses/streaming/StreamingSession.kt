package com.egoglass.glasses.streaming

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import com.egoglass.glasses.transport.webrtc.RecordingControlStatus

enum class StreamingSessionState {
    IDLE,
    SIGNALING,
    CONNECTING,
    CAPTURING,
    STREAMING,
    STOPPED,
    DISCONNECTED,
    ERROR,
}

interface StreamingSessionListener {
    fun onStateChanged(state: StreamingSessionState, detail: String?)

    fun onStatsChanged(stats: WebRtcPublisherStats)

    fun onRecordingStatusChanged(status: RecordingControlStatus) = Unit
}

interface StreamingSession {
    val state: StreamingSessionState

    val recordingStatus: RecordingControlStatus

    fun addListener(listener: StreamingSessionListener)

    fun removeListener(listener: StreamingSessionListener)

    fun start(
        sessionConfig: WebRtcSessionConfig,
        captureConfig: CaptureConfig = CaptureConfig(),
    )

    fun stop()

    fun requestRecordingToggle(triggeredAtElapsedRealtimeNs: Long): Boolean
}
