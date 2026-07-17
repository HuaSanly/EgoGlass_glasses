package com.egoglass.glasses.streaming

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.capture.VideoFrameSource
import com.egoglass.glasses.capture.VideoFrameSourceListener
import com.egoglass.glasses.sensors.ImuCapabilities
import com.egoglass.glasses.sensors.ImuSample
import com.egoglass.glasses.sensors.ImuSource
import com.egoglass.glasses.sensors.ImuSourceListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisher
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherListener
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherState
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import com.egoglass.glasses.transport.webrtc.StreamControlAction
import com.egoglass.glasses.transport.webrtc.StreamControlCommand
import com.egoglass.glasses.transport.webrtc.StreamControlState
import com.egoglass.glasses.transport.webrtc.StreamControlStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStreamingSessionTest {
    @Test
    fun opensCameraOnlyAfterPeerConnectsAndStopsOnDisconnect() {
        val source = FakeSource()
        val publisher = FakePublisher()
        val imuSource = FakeImuSource()
        val session = DefaultStreamingSession(source, publisher, imuSource)
        val config = WebRtcSessionConfig(
            "http://192.168.1.20:8770/api/v1/webrtc/sessions",
            "runtime-pairing-token-123456",
        )

        session.start(config)
        assertFalse(source.started)

        publisher.emitState(WebRtcPublisherState.STREAMING)
        assertTrue(source.started)
        assertTrue(imuSource.started)
        assertEquals(CaptureConfig(), publisher.captureConfig)
        assertEquals(1280, publisher.captureConfig?.width)
        assertEquals(720, publisher.captureConfig?.height)
        assertEquals(30, publisher.captureConfig?.framesPerSecond)
        assertEquals("720p30", publisher.captureConfig?.captureConfigId)
        assertEquals(StreamingSessionState.CAPTURING, session.state)

        val capabilities = ImuCapabilities(10_000, emptyList(), emptyList())
        val sample = ImuSample(
            sensorType = com.egoglass.glasses.sensors.ImuSensorType.ACCELEROMETER,
            sequenceNumber = 0,
            sensorEventMonotonicNs = 100,
            receivedAtElapsedRealtimeNs = 110,
            accuracy = 3,
            values = listOf(1.0, 2.0, 3.0),
        )
        imuSource.emitCapabilities(capabilities)
        imuSource.emitSample(sample)
        assertEquals(listOf(capabilities), publisher.imuCapabilities)
        assertEquals(listOf(sample), publisher.imuSamples)

        source.emitFrame()
        assertEquals(1, publisher.frames.size)

        publisher.emitState(WebRtcPublisherState.DISCONNECTED)
        assertFalse(source.started)
        assertFalse(imuSource.started)
        assertEquals(1, imuSource.stopCount)
        assertEquals(StreamingSessionState.DISCONNECTED, session.state)
    }

    @Test
    fun remoteStopKeepsPeerConnectedAndRemoteStartResumesCaptureIdempotently() {
        val source = FakeSource()
        val publisher = FakePublisher()
        val imuSource = FakeImuSource()
        val session = DefaultStreamingSession(source, publisher, imuSource)
        val config = WebRtcSessionConfig(
            "http://192.168.1.20:8770/api/v1/webrtc/sessions",
            "runtime-pairing-token-123456",
        )

        session.start(config)
        publisher.emitState(WebRtcPublisherState.STREAMING)
        source.emitOpened()
        assertEquals(1, source.startCount)

        val stopId = "0123456789abcdef0123456789abcdef"
        publisher.emitControl(StreamControlCommand(stopId, StreamControlAction.STOP))
        assertFalse(source.started)
        assertTrue(imuSource.started)
        assertEquals(0, imuSource.stopCount)
        assertEquals(1, source.stopCount)
        assertEquals(0, publisher.closeCount)
        assertEquals(WebRtcPublisherState.STREAMING, publisher.state)
        assertEquals(StreamingSessionState.STOPPED, session.state)
        assertEquals(
            StreamControlStatus(stopId, StreamControlState.STOPPED),
            publisher.statuses.last(),
        )

        publisher.emitControl(StreamControlCommand(stopId, StreamControlAction.STOP))
        assertEquals(1, source.stopCount)
        assertEquals(0, publisher.closeCount)

        val startId = "abcdef0123456789abcdef0123456789"
        publisher.emitControl(StreamControlCommand(startId, StreamControlAction.START))
        assertTrue(source.started)
        assertEquals(2, source.startCount)
        assertEquals(
            StreamControlStatus(
                startId,
                StreamControlState.STARTING,
                "Opening Glass3 camera",
            ),
            publisher.statuses.last(),
        )

        publisher.emitControl(StreamControlCommand(startId, StreamControlAction.START))
        assertEquals(2, source.startCount)
        assertEquals(StreamControlState.STARTING, publisher.statuses.last().state)
        source.emitOpened()
        assertEquals(StreamingSessionState.STREAMING, session.state)
        assertEquals(StreamControlState.STREAMING, publisher.statuses.last().state)
    }

    @Test
    fun controlChannelReportsCurrentStateAndProtocolErrors() {
        val source = FakeSource()
        val publisher = FakePublisher()
        DefaultStreamingSession(source, publisher, FakeImuSource())

        publisher.emitControlReady()
        assertEquals(StreamControlStatus(null, StreamControlState.READY), publisher.statuses.last())

        publisher.emitControlError("Malformed command")
        assertEquals(
            StreamControlStatus(null, StreamControlState.ERROR, "Malformed command"),
            publisher.statuses.last(),
        )
    }

    private class FakeSource : VideoFrameSource {
        var started = false
        var startCount = 0
        var stopCount = 0
        private var listener: VideoFrameSourceListener? = null

        override fun start(config: CaptureConfig, listener: VideoFrameSourceListener) {
            started = true
            startCount += 1
            this.listener = listener
        }

        override fun stop() {
            if (started) stopCount += 1
            started = false
        }

        fun emitOpened() {
            listener?.onCameraOpened(1280, 720, 30)
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

    private class FakeImuSource : ImuSource {
        var started = false
        var startCount = 0
        var stopCount = 0
        private var listener: ImuSourceListener? = null

        override fun start(listener: ImuSourceListener) {
            if (started) return
            started = true
            startCount += 1
            this.listener = listener
        }

        override fun stop() {
            if (!started) return
            started = false
            stopCount += 1
            listener = null
        }

        fun emitCapabilities(capabilities: ImuCapabilities) {
            listener?.onCapabilities(capabilities)
        }

        fun emitSample(sample: ImuSample) {
            listener?.onSample(sample)
        }
    }

    private class FakePublisher : WebRtcPublisher {
        private val listeners = mutableSetOf<WebRtcPublisherListener>()
        val frames = mutableListOf<CapturedVideoFrame>()
        var captureConfig: CaptureConfig? = null
        var closeCount = 0
        val statuses = mutableListOf<StreamControlStatus>()
        val imuCapabilities = mutableListOf<ImuCapabilities>()
        val imuSamples = mutableListOf<ImuSample>()
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

        override fun sendControlStatus(status: StreamControlStatus): Boolean {
            statuses += status
            return true
        }

        override fun sendImuCapabilities(capabilities: ImuCapabilities): Boolean {
            imuCapabilities += capabilities
            return true
        }

        override fun offerImuSample(sample: ImuSample) {
            imuSamples += sample
        }

        override fun close() {
            closeCount += 1
            emitState(WebRtcPublisherState.IDLE)
        }

        fun emitControlReady() {
            listeners.forEach(WebRtcPublisherListener::onControlChannelReady)
        }

        fun emitControl(command: StreamControlCommand) {
            listeners.forEach { it.onControlCommand(command) }
        }

        fun emitControlError(detail: String) {
            listeners.forEach { it.onControlProtocolError(detail) }
        }

        fun emitState(newState: WebRtcPublisherState) {
            state = newState
            listeners.forEach { it.onStateChanged(newState, null) }
        }
    }
}
