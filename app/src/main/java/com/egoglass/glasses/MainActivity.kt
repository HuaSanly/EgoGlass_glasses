package com.egoglass.glasses

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.egoglass.glasses.domain.connection.SdkConnectionListener
import com.egoglass.glasses.domain.connection.SdkConnectionState
import com.egoglass.glasses.input.TempleDoubleTapDetector
import com.egoglass.glasses.orientation.GameRotationOrientationSource
import com.egoglass.glasses.orientation.RelativeOrientation
import com.egoglass.glasses.streaming.StreamingSession
import com.egoglass.glasses.streaming.StreamingSessionListener
import com.egoglass.glasses.streaming.StreamingSessionState
import com.egoglass.glasses.capture.CameraFrameGuideView
import com.egoglass.glasses.transport.discovery.ClientDiscovery
import com.egoglass.glasses.transport.discovery.ClientDiscoveryListener
import com.egoglass.glasses.transport.webrtc.RecordingControlState
import com.egoglass.glasses.transport.webrtc.RecordingControlStatus
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig

private const val CAMERA_PERMISSION_REQUEST = 1001
private const val EXTRA_SIGNALING_URL = "signaling_url"
private const val EXTRA_PAIRING_TOKEN = "pairing_token"

class MainActivity : Activity() {
    private val applicationState get() = application as EgoGlassApplication
    private val connection get() = applicationState.sdkConnection
    private val streamingSession: StreamingSession get() = applicationState.streamingSession
    private val clientDiscovery: ClientDiscovery get() = applicationState.clientDiscovery
    private lateinit var sdkStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var recordingStatus: TextView
    private lateinit var recordingTimer: TextView
    private lateinit var streamStats: TextView
    private lateinit var runtimeIdentity: TextView
    private lateinit var orientationView: TextView
    private lateinit var frameGuide: CameraFrameGuideView
    private lateinit var retryButton: Button

    private val tapDetector = TempleDoubleTapDetector()
    private lateinit var orientationSource: GameRotationOrientationSource
    private val hudHandler = Handler(Looper.getMainLooper())
    private var sdkState = SdkConnectionState.IDLE
    private var streamState = StreamingSessionState.IDLE
    private var stats = WebRtcPublisherStats()
    private var recording = RecordingControlStatus.UNAVAILABLE
    private var orientation: RelativeOrientation? = null
    private var sessionConfig: WebRtcSessionConfig? = null
    private var configError: String? = null
    private var discoveryState = DiscoveryState.IDLE
    private var discoveryError: String? = null
    private var permissionRequested = false

    private val sdkListener = SdkConnectionListener { state ->
        runOnUiThread {
            sdkState = state
            if (state == SdkConnectionState.READY) maybeStartStreaming()
            render()
        }
    }
    private val streamingListener = object : StreamingSessionListener {
        override fun onStateChanged(state: StreamingSessionState, detail: String?) {
            runOnUiThread { streamState = state; render() }
        }

        override fun onStatsChanged(stats: WebRtcPublisherStats) {
            runOnUiThread { this@MainActivity.stats = stats; render() }
        }

        override fun onCaptureSizeChanged(width: Int, height: Int) {
            runOnUiThread { frameGuide.setCameraSize(width, height) }
        }

        override fun onRecordingStatusChanged(status: RecordingControlStatus) {
            runOnUiThread {
                if (status.state == RecordingControlState.COUNTDOWN &&
                    recording.state != RecordingControlState.COUNTDOWN
                ) {
                    orientationSource.reset()
                }
                recording = status
                render()
            }
        }
    }
    private val discoveryListener = object : ClientDiscoveryListener {
        override fun onDiscovered(config: WebRtcSessionConfig) {
            runOnUiThread {
                sessionConfig = config
                discoveryState = DiscoveryState.READY
                discoveryError = null
                maybeStartStreaming()
                render()
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                discoveryState = DiscoveryState.ERROR
                discoveryError = message
                render()
            }
        }
    }

    private val hudTick = object : Runnable {
        override fun run() {
            orientation = orientationSource.latest
            renderOrientation()
            hudHandler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        sdkStatus = findViewById(R.id.sdk_status)
        statusIndicator = findViewById(R.id.status_indicator)
        recordingStatus = findViewById(R.id.recording_status)
        recordingTimer = findViewById(R.id.recording_timer)
        streamStats = findViewById(R.id.stream_stats)
        runtimeIdentity = findViewById(R.id.runtime_identity)
        orientationView = findViewById(R.id.imu_orientation)
        frameGuide = findViewById(R.id.frame_guide)
        retryButton = findViewById(R.id.retry_button)
        retryButton.setOnClickListener { retry() }
        orientationSource = GameRotationOrientationSource(this)
        readSessionConfig(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        streamingSession.stop()
        readSessionConfig(intent)
        if (sessionConfig == null) discoverClient() else maybeStartStreaming()
    }

    override fun onStart() {
        super.onStart()
        connection.addListener(sdkListener)
        streamingSession.addListener(streamingListener)
        orientationSource.start()
        hudHandler.post(hudTick)
        connection.start()
        if (sessionConfig == null) discoverClient()
    }

    override fun onStop() {
        hudHandler.removeCallbacks(hudTick)
        orientationSource.stop()
        clientDiscovery.cancel()
        streamingSession.stop()
        streamingSession.removeListener(streamingListener)
        connection.removeListener(sdkListener)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) connection.close()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Glass3 Generic.kl maps Linux KEY_DASHBOARD (204) to Android NOTIFICATION.
        if (event.keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                if (tapDetector.onTap(event.eventTime)) {
                    val sent = streamingSession.requestRecordingToggle(
                        SystemClock.elapsedRealtimeNanos()
                    )
                    if (!sent && recording.state == RecordingControlState.UNAVAILABLE) retry()
                    render()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            permissionRequested = true
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) maybeStartStreaming()
            render()
        }
    }

    private fun readSessionConfig(sourceIntent: Intent) {
        val signalingUrl = sourceIntent.getStringExtra(EXTRA_SIGNALING_URL)
        val pairingToken = sourceIntent.getStringExtra(EXTRA_PAIRING_TOKEN)
        sourceIntent.removeExtra(EXTRA_PAIRING_TOKEN)
        configError = null
        discoveryError = null
        sessionConfig = if (signalingUrl.isNullOrBlank() || pairingToken.isNullOrBlank()) {
            discoveryState = DiscoveryState.IDLE
            null
        } else {
            runCatching { WebRtcSessionConfig(signalingUrl, pairingToken) }
                .onFailure { configError = "Invalid client configuration" }
                .onSuccess { discoveryState = DiscoveryState.READY }
                .getOrNull()
        }
    }

    private fun discoverClient() {
        clientDiscovery.cancel()
        sessionConfig = null
        configError = null
        discoveryError = null
        discoveryState = DiscoveryState.DISCOVERING
        render()
        clientDiscovery.discover(discoveryListener)
    }

    private fun maybeStartStreaming() {
        val config = sessionConfig ?: return
        if (sdkState != SdkConnectionState.READY) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequested) {
                permissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            }
            return
        }
        streamingSession.start(config)
    }

    private fun retry() {
        connection.start()
        streamingSession.stop()
        discoverClient()
    }

    private fun render() {
        sdkStatus.text = when {
            sdkState == SdkConnectionState.ERROR -> "DEVICE ERROR"
            discoveryState == DiscoveryState.DISCOVERING -> "FINDING CLIENT"
            streamState in setOf(StreamingSessionState.STREAMING, StreamingSessionState.STOPPED) -> "CLIENT READY"
            streamState in setOf(StreamingSessionState.ERROR, StreamingSessionState.DISCONNECTED) -> "CLIENT OFFLINE"
            else -> "CONNECTING"
        }
        runtimeIdentity.text = sessionConfig?.displayEndpoint ?: getString(if (discoveryState == DiscoveryState.DISCOVERING) R.string.runtime_discovery else R.string.runtime_identity)
        streamStats.text = if (streamState == StreamingSessionState.STREAMING) getString(R.string.stream_stats, stats.framesPublished, stats.framesDropped, stats.metadataSent) else ""
        recordingStatus.setText(recordingLabel())
        recordingTimer.text = when {
            recording.state == RecordingControlState.COUNTDOWN -> "${recording.countdownRemainingMs ?: 0} ms"
            recording.state == RecordingControlState.RECORDING -> formatDuration(recording.recordingDurationMs)
            recording.state == RecordingControlState.ERROR -> recording.detail.orEmpty()
            else -> ""
        }
        val active = recording.state == RecordingControlState.RECORDING || recording.state == RecordingControlState.COUNTDOWN
        frameGuide.visibility = if (streamState in setOf(
                StreamingSessionState.CAPTURING,
                StreamingSessionState.STREAMING,
                StreamingSessionState.STOPPED,
            )
        ) View.VISIBLE else View.INVISIBLE
        frameGuide.setRecording(active)
        statusIndicator.setBackgroundResource(if (active) R.drawable.recording_indicator else R.drawable.status_indicator)
        retryButton.visibility = if (shouldShowRetry()) View.VISIBLE else View.GONE
        renderOrientation()
    }

    private fun renderOrientation() {
        val value = orientation
        orientationView.text = if (value == null) getString(R.string.orientation_unavailable) else "Y ${value.yawDegrees.toInt().withSign()}   P ${value.pitchDegrees.toInt().withSign()}   R ${value.rollDegrees.toInt().withSign()}"
    }

    private fun recordingLabel(): Int = when (recording.state) {
        RecordingControlState.UNAVAILABLE -> R.string.recording_unavailable
        RecordingControlState.READY -> R.string.recording_ready
        RecordingControlState.STARTING_STREAM -> R.string.recording_starting_stream
        RecordingControlState.COUNTDOWN -> R.string.recording_countdown
        RecordingControlState.RECORDING -> R.string.recording_recording
        RecordingControlState.FINALIZING -> R.string.recording_finalizing
        RecordingControlState.ERROR -> R.string.recording_error
    }

    private fun shouldShowRetry() = configError != null || discoveryState == DiscoveryState.ERROR || sdkState == SdkConnectionState.ERROR || streamState == StreamingSessionState.ERROR || streamState == StreamingSessionState.DISCONNECTED

    private fun formatDuration(milliseconds: Long): String = "%02d:%02d".format(milliseconds / 60_000, (milliseconds / 1_000) % 60)

    private fun Int.withSign() = if (this >= 0) "+$this" else toString()

    private enum class DiscoveryState { IDLE, DISCOVERING, READY, ERROR }
}
