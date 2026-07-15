package com.egoglass.glasses

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.egoglass.glasses.domain.connection.SdkConnection
import com.egoglass.glasses.domain.connection.SdkConnectionListener
import com.egoglass.glasses.domain.connection.SdkConnectionState
import com.egoglass.glasses.streaming.StreamingSession
import com.egoglass.glasses.streaming.StreamingSessionListener
import com.egoglass.glasses.streaming.StreamingSessionState
import com.egoglass.glasses.transport.webrtc.WebRtcPublisherStats
import com.egoglass.glasses.transport.webrtc.WebRtcSessionConfig

private const val CAMERA_PERMISSION_REQUEST = 1001
private const val EXTRA_SIGNALING_URL = "signaling_url"
private const val EXTRA_PAIRING_TOKEN = "pairing_token"

class MainActivity : Activity() {
    private val connection: SdkConnection
        get() = (application as EgoGlassApplication).sdkConnection
    private val streamingSession: StreamingSession
        get() = (application as EgoGlassApplication).streamingSession

    private lateinit var sdkStatus: TextView
    private lateinit var statusIndicator: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var streamStats: TextView
    private lateinit var runtimeIdentity: TextView
    private lateinit var retryButton: Button

    private var sdkState = SdkConnectionState.IDLE
    private var streamState = StreamingSessionState.IDLE
    private var streamDetail: String? = null
    private var stats = WebRtcPublisherStats()
    private var sessionConfig: WebRtcSessionConfig? = null
    private var configError: String? = null
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
            runOnUiThread {
                streamState = state
                streamDetail = detail
                render()
            }
        }

        override fun onStatsChanged(stats: WebRtcPublisherStats) {
            runOnUiThread {
                this@MainActivity.stats = stats
                render()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        sdkStatus = findViewById(R.id.sdk_status)
        statusIndicator = findViewById(R.id.status_indicator)
        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        streamStats = findViewById(R.id.stream_stats)
        runtimeIdentity = findViewById(R.id.runtime_identity)
        retryButton = findViewById(R.id.retry_button)
        retryButton.setOnClickListener { retry() }
        readSessionConfig(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        streamingSession.stop()
        readSessionConfig(intent)
        maybeStartStreaming()
        render()
    }

    override fun onStart() {
        super.onStart()
        connection.addListener(sdkListener)
        streamingSession.addListener(streamingListener)
        connection.start()
    }

    override fun onStop() {
        streamingSession.stop()
        streamingSession.removeListener(streamingListener)
        connection.removeListener(sdkListener)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) connection.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            permissionRequested = true
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                maybeStartStreaming()
            }
            render()
        }
    }

    private fun readSessionConfig(sourceIntent: Intent) {
        val signalingUrl = sourceIntent.getStringExtra(EXTRA_SIGNALING_URL)
        val pairingToken = sourceIntent.getStringExtra(EXTRA_PAIRING_TOKEN)
        sourceIntent.removeExtra(EXTRA_PAIRING_TOKEN)
        configError = null
        sessionConfig = if (signalingUrl.isNullOrBlank() || pairingToken.isNullOrBlank()) {
            null
        } else {
            runCatching { WebRtcSessionConfig(signalingUrl, pairingToken) }
                .onFailure { configError = "Invalid client configuration" }
                .getOrNull()
        }
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
        if (sdkState.canRetry || sdkState == SdkConnectionState.IDLE) {
            connection.start()
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            return
        }
        streamingSession.stop()
        maybeStartStreaming()
    }

    private fun render() {
        sdkStatus.text = getString(
            if (sdkState == SdkConnectionState.READY) R.string.sdk_ready else R.string.sdk_pending
        )
        val model = screenModel()
        statusIndicator.backgroundTintList = ColorStateList.valueOf(getColor(model.color))
        statusTitle.setText(model.title)
        statusDetail.text = model.detail ?: getString(model.detailResource)
        streamStats.text = if (streamState == StreamingSessionState.STREAMING) {
            getString(
                R.string.stream_stats,
                stats.framesPublished,
                stats.framesDropped,
                stats.metadataSent,
            )
        } else {
            ""
        }
        runtimeIdentity.text = sessionConfig?.displayEndpoint ?: getString(R.string.runtime_identity)
        retryButton.visibility = if (model.canRetry) View.VISIBLE else View.INVISIBLE
        if (model.canRetry) retryButton.requestFocus()
    }

    private fun screenModel(): ScreenModel {
        configError?.let {
            return ScreenModel(
                R.string.stream_config_error,
                0,
                it,
                R.color.status_error,
                true,
            )
        }
        if (sessionConfig == null) {
            return ScreenModel(
                R.string.stream_waiting,
                R.string.stream_waiting_detail,
                null,
                R.color.status_inactive,
                false,
            )
        }
        if (sdkState != SdkConnectionState.READY) return sdkModel()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return ScreenModel(
                R.string.camera_permission,
                R.string.camera_permission_detail,
                null,
                R.color.status_pending,
                permissionRequested,
            )
        }
        return when (streamState) {
            StreamingSessionState.IDLE,
            StreamingSessionState.SIGNALING,
            -> ScreenModel(
                R.string.stream_signaling,
                R.string.stream_signaling_detail,
                streamDetail,
                R.color.status_pending,
                false,
            )
            StreamingSessionState.CONNECTING -> ScreenModel(
                R.string.stream_connecting,
                R.string.stream_connecting_detail,
                streamDetail,
                R.color.status_pending,
                false,
            )
            StreamingSessionState.CAPTURING -> ScreenModel(
                R.string.stream_capturing,
                R.string.stream_capturing_detail,
                streamDetail,
                R.color.status_pending,
                false,
            )
            StreamingSessionState.STREAMING -> ScreenModel(
                R.string.stream_live,
                R.string.stream_live_detail,
                streamDetail,
                R.color.status_ready,
                false,
            )
            StreamingSessionState.DISCONNECTED -> ScreenModel(
                R.string.stream_disconnected,
                R.string.stream_disconnected_detail,
                streamDetail,
                R.color.status_error,
                true,
            )
            StreamingSessionState.ERROR -> ScreenModel(
                R.string.stream_error,
                R.string.stream_error_detail,
                streamDetail,
                R.color.status_error,
                true,
            )
        }
    }

    private fun sdkModel(): ScreenModel = when (sdkState) {
        SdkConnectionState.IDLE,
        SdkConnectionState.CONNECTING,
        SdkConnectionState.REGISTERING,
        -> ScreenModel(
            R.string.status_connecting,
            R.string.status_connecting_detail,
            null,
            R.color.status_pending,
            false,
        )
        SdkConnectionState.READY -> error("READY is handled before sdkModel")
        SdkConnectionState.DISCONNECTED -> ScreenModel(
            R.string.status_disconnected,
            R.string.status_disconnected_detail,
            null,
            R.color.status_error,
            true,
        )
        SdkConnectionState.ERROR -> ScreenModel(
            R.string.status_error,
            R.string.status_error_detail,
            null,
            R.color.status_error,
            true,
        )
    }

    private data class ScreenModel(
        val title: Int,
        val detailResource: Int,
        val detail: String?,
        val color: Int,
        val canRetry: Boolean,
    )
}
