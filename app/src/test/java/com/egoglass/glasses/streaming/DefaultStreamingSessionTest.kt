package com.egoglass.glasses.streaming

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.capture.VideoFrameSource
import com.egoglass.glasses.capture.VideoFrameSourceListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisher
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherState
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStreamingSessionTest {
    @Test
    fun opensCameraOnlyAfterPeerConnectsAndStopsOnDisconnect() {
        val source = FakeSource()
        val publisher = FakePublisher()
        val session = DefaultStreamingSession(source, publisher)
        val config = WebRtcSessionConfig(
            "http://192.168.1.20:8770/api/v1/webrtc/sessions",
            "runtime-pairing-token-123456",
        )

        session.start(config)
        assertFalse(source.started)

        publisher.emitState(WebRtcPublisherState.STREAMING)
        assertTrue(source.started)
        assertEquals(CaptureConfig(), publisher.captureConfig)
        assertEquals(StreamingSessionState.CAPTURING, session.state)

        source.emitFrame()
        assertEquals(1, publisher.frames.size)

        publisher.emitState(WebRtcPublisherState.DISCONNECTED)
        assertFalse(source.started)
        assertEquals(StreamingSessionState.DISCONNECTED, session.state)
    }

    private class FakeSource : VideoFrameSource {
        var started = false
        private var listener: VideoFrameSourceListener? = null

        override fun start(config: CaptureConfig, listener: VideoFrameSourceListener) {
            started = true
            this.listener = listener
        }

        override fun stop() {
            started = false
        }

        fun emitFrame() {
            listener?.onFrame(
                CapturedVideoFrame(
                    frameId = 1,
                    nv21 = ByteArray(6),
                    width = 2,
                    height = 2,
                    capturedAtRokidSdkMs = 1,
                    receivedAtElapsedRealtimeNs = 2,
                    videoAtMonotonicNs = 3,
                    rotationDegrees = 0,
                    captureConfigId = "test",
                )
            )
        }
    }

    private class FakePublisher : WebRtcPublisher {
        private val listeners = mutableSetOf<WebRtcPublisherListener>()
        val frames = mutableListOf<CapturedVideoFrame>()
        var captureConfig: CaptureConfig? = null
        override var state = WebRtcPublisherState.IDLE

        override fun addListener(listener: WebRtcPublisherListener) {
            listeners += listener
        }

        override fun removeListener(listener: WebRtcPublisherListener) {
            listeners -= listener
        }

        override fun connect(config: WebRtcSessionConfig, captureConfig: CaptureConfig) {
            this.captureConfig = captureConfig
            emitState(WebRtcPublisherState.SIGNALING)
        }

        override fun offerFrame(frame: CapturedVideoFrame) {
            frames += frame
        }

        override fun close() {
            emitState(WebRtcPublisherState.IDLE)
        }

        fun emitState(newState: WebRtcPublisherState) {
            state = newState
            listeners.forEach { it.onStateChanged(newState, null) }
        }
    }
}
