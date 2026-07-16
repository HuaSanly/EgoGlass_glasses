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
import com.egoglass.glasses.transport.webrtc.StreamControlAction
import com.egoglass.glasses.transport.webrtc.StreamControlCommand
import com.egoglass.glasses.transport.webrtc.StreamControlState
import com.egoglass.glasses.transport.webrtc.StreamControlStatus
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

    @Volatile
    private var cameraOpened = false

    @Volatile
    private var captureDesired = true

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
                StreamingSessionState.STOPPED,
            )
        ) {
            return
        }
        captureDesired = true
        captureStarted = false
        cameraOpened = false
        this.captureConfig = captureConfig
        publisher.connect(sessionConfig, captureConfig)
    }

    @Synchronized
    override fun stop() {
        captureDesired = false
        captureStarted = false
        cameraOpened = false
        frameSource.stop()
        publisher.close()
        updateState(StreamingSessionState.IDLE, null)
    }

    @Synchronized
    override fun onStateChanged(state: WebRtcPublisherState, detail: String?) {
        when (state) {
            WebRtcPublisherState.IDLE -> updateState(StreamingSessionState.IDLE, detail)
            WebRtcPublisherState.SIGNALING -> updateState(StreamingSessionState.SIGNALING, detail)
            WebRtcPublisherState.CONNECTING -> updateState(StreamingSessionState.CONNECTING, detail)
            WebRtcPublisherState.STREAMING -> {
                if (captureDesired) {
                    startCapture()
                } else {
                    updateState(StreamingSessionState.STOPPED, "Video stopped by client")
                    sendControlStatus(null, StreamControlState.STOPPED)
                }
            }
            WebRtcPublisherState.DISCONNECTED -> {
                stopCapture()
                updateState(StreamingSessionState.DISCONNECTED, detail)
                sendControlStatus(null, StreamControlState.ERROR, detail)
            }
            WebRtcPublisherState.ERROR -> {
                stopCapture()
                updateState(StreamingSessionState.ERROR, detail)
                sendControlStatus(null, StreamControlState.ERROR, detail)
            }
        }
    }

    override fun onStatsChanged(stats: WebRtcPublisherStats) {
        listeners.forEach { listener -> listener.onStatsChanged(stats) }
    }

    @Synchronized
    override fun onCameraOpened(width: Int, height: Int, appliedFramesPerSecond: Int?) {
        if (!captureStarted) return
        cameraOpened = true
        val fps = appliedFramesPerSecond?.let { " at $it FPS" }.orEmpty()
        updateState(StreamingSessionState.STREAMING, "${width}x$height$fps")
        sendControlStatus(null, StreamControlState.STREAMING, "${width}x$height$fps")
    }

    override fun onFrame(frame: CapturedVideoFrame) {
        if (captureStarted) publisher.offerFrame(frame)
    }

    override fun onCameraClosed() {
        if (captureStarted) {
            updateState(StreamingSessionState.DISCONNECTED, "Glass3 camera closed")
        }
    }

    @Synchronized
    override fun onError(message: String) {
        if (!captureStarted && !captureDesired) return
        stopCapture()
        updateState(StreamingSessionState.ERROR, message)
        sendControlStatus(null, StreamControlState.ERROR, message)
    }

    @Synchronized
    override fun onControlChannelReady() {
        sendControlStatus(null, currentControlState())
    }

    @Synchronized
    override fun onControlCommand(command: StreamControlCommand) {
        when (command.action) {
            StreamControlAction.START -> {
                captureDesired = true
                startCapture(command.commandId)
            }
            StreamControlAction.STOP -> stopCaptureFromControl(command.commandId)
        }
    }

    override fun onControlProtocolError(detail: String) {
        sendControlStatus(null, StreamControlState.ERROR, detail)
    }

    @Synchronized
    private fun startCapture(commandId: String? = null) {
        if (captureStarted) {
            sendControlStatus(commandId, currentControlState())
            return
        }
        if (publisher.state != WebRtcPublisherState.STREAMING) {
            sendControlStatus(
                commandId,
                StreamControlState.ERROR,
                "WebRTC peer is not ready",
            )
            return
        }
        captureStarted = true
        cameraOpened = false
        updateState(StreamingSessionState.CAPTURING, "Opening Glass3 camera")
        sendControlStatus(commandId, StreamControlState.STARTING, "Opening Glass3 camera")
        frameSource.start(captureConfig, this)
    }

    @Synchronized
    private fun stopCapture() {
        if (!captureStarted) return
        captureStarted = false
        cameraOpened = false
        frameSource.stop()
    }

    @Synchronized
    private fun stopCaptureFromControl(commandId: String) {
        captureDesired = false
        if (captureStarted) {
            sendControlStatus(null, StreamControlState.STOPPING)
            stopCapture()
        }
        updateState(StreamingSessionState.STOPPED, "Video stopped by client")
        sendControlStatus(commandId, StreamControlState.STOPPED)
    }

    private fun currentControlState(): StreamControlState = when {
        cameraOpened -> StreamControlState.STREAMING
        captureStarted -> StreamControlState.STARTING
        state == StreamingSessionState.STOPPED -> StreamControlState.STOPPED
        publisher.state == WebRtcPublisherState.STREAMING -> StreamControlState.READY
        state == StreamingSessionState.ERROR || state == StreamingSessionState.DISCONNECTED -> {
            StreamControlState.ERROR
        }
        else -> StreamControlState.READY
    }

    private fun sendControlStatus(
        commandId: String?,
        state: StreamControlState,
        detail: String? = null,
    ) {
        publisher.sendControlStatus(
            StreamControlStatus(
                commandId = commandId,
                state = state,
                detail = detail?.take(256),
            )
        )
    }

    private fun updateState(newState: StreamingSessionState, detail: String?) {
        state = newState
        listeners.forEach { listener -> listener.onStateChanged(newState, detail) }
    }
}
