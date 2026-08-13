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
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig
import com.egoglass.glasses.transport.webrtc.StreamControlAction
import com.egoglass.glasses.transport.webrtc.StreamControlCommand
import com.egoglass.glasses.transport.webrtc.StreamControlState
import com.egoglass.glasses.transport.webrtc.StreamControlStatus
import com.egoglass.glasses.transport.webrtc.RecordingControlAction
import com.egoglass.glasses.transport.webrtc.RecordingControlCommand
import com.egoglass.glasses.transport.webrtc.RecordingControlState
import com.egoglass.glasses.transport.webrtc.RecordingControlStatus
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

class DefaultStreamingSession(
    private val frameSource: VideoFrameSource,
    private val publisher: WebRtcPublisher,
    private val imuSource: ImuSource,
) : StreamingSession, WebRtcPublisherListener, VideoFrameSourceListener, ImuSourceListener {
    private val listeners = CopyOnWriteArraySet<StreamingSessionListener>()

    @Volatile
    override var state: StreamingSessionState = StreamingSessionState.IDLE
        private set

    @Volatile
    override var recordingStatus: RecordingControlStatus = RecordingControlStatus.UNAVAILABLE
        private set

    @Volatile
    private var captureStarted = false

    @Volatile
    private var cameraOpened = false

    @Volatile
    private var captureDesired = true

    @Volatile
    private var imuStarted = false

    private var captureConfig = CaptureConfig()

    init {
        publisher.addListener(this)
    }

    override fun addListener(listener: StreamingSessionListener) {
        listeners.add(listener)
        listener.onStateChanged(state, null)
        listener.onRecordingStatusChanged(recordingStatus)
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
        stopImu()
        publisher.close()
        updateRecordingStatus(RecordingControlStatus.UNAVAILABLE)
        updateState(StreamingSessionState.IDLE, null)
    }

    @Synchronized
    override fun requestRecordingToggle(triggeredAtElapsedRealtimeNs: Long): Boolean {
        val action = when (recordingStatus.state) {
            RecordingControlState.READY,
            RecordingControlState.ERROR,
            RecordingControlState.UNAVAILABLE,
            -> RecordingControlAction.START
            RecordingControlState.COUNTDOWN,
            RecordingControlState.RECORDING,
            -> RecordingControlAction.STOP
            RecordingControlState.STARTING_STREAM,
            RecordingControlState.FINALIZING,
            -> return false
        }
        val command = RecordingControlCommand(
            commandId = UUID.randomUUID().toString().replace("-", ""),
            action = action,
            requestedAtElapsedRealtimeNs = triggeredAtElapsedRealtimeNs,
        )
        return publisher.sendRecordingControlCommand(command)
    }

    @Synchronized
    override fun onStateChanged(state: WebRtcPublisherState, detail: String?) {
        when (state) {
            WebRtcPublisherState.IDLE -> {
                stopImu()
                updateState(StreamingSessionState.IDLE, detail)
            }
            WebRtcPublisherState.SIGNALING -> updateState(StreamingSessionState.SIGNALING, detail)
            WebRtcPublisherState.CONNECTING -> updateState(StreamingSessionState.CONNECTING, detail)
            WebRtcPublisherState.STREAMING -> {
                startImu()
                if (captureDesired) {
                    startCapture()
                } else {
                    updateState(StreamingSessionState.STOPPED, "Video stopped by client")
                    sendControlStatus(null, StreamControlState.STOPPED)
                }
            }
            WebRtcPublisherState.DISCONNECTED -> {
                stopCapture()
                stopImu()
                updateState(StreamingSessionState.DISCONNECTED, detail)
                sendControlStatus(null, StreamControlState.ERROR, detail)
                updateRecordingStatus(RecordingControlStatus.UNAVAILABLE)
            }
            WebRtcPublisherState.ERROR -> {
                stopCapture()
                stopImu()
                updateState(StreamingSessionState.ERROR, detail)
                sendControlStatus(null, StreamControlState.ERROR, detail)
                updateRecordingStatus(RecordingControlStatus.UNAVAILABLE)
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

    override fun onCapabilities(capabilities: ImuCapabilities) {
        if (imuStarted) publisher.sendImuCapabilities(capabilities)
    }

    override fun onSample(sample: ImuSample) {
        if (imuStarted) publisher.offerImuSample(sample)
    }

    override fun onImuError(message: String) = Unit

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

    override fun onRecordingControlChannelReady() = Unit

    override fun onRecordingControlStatus(status: RecordingControlStatus) {
        updateRecordingStatus(status)
    }

    override fun onRecordingControlProtocolError(detail: String) {
        updateRecordingStatus(
            RecordingControlStatus.UNAVAILABLE.copy(
                state = RecordingControlState.ERROR,
                detail = detail.take(256),
            )
        )
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

    @Synchronized
    private fun startImu() {
        if (imuStarted) return
        imuStarted = true
        imuSource.start(this)
    }

    @Synchronized
    private fun stopImu() {
        if (!imuStarted) return
        imuStarted = false
        imuSource.stop()
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

    private fun updateRecordingStatus(status: RecordingControlStatus) {
        recordingStatus = status
        listeners.forEach { listener -> listener.onRecordingStatusChanged(status) }
    }
}
