package com.egoglass.glasses.transport.webrtc

import java.net.URI

internal const val MIN_VIDEO_BITRATE_BPS = 800_000
internal const val START_VIDEO_BITRATE_BPS = 3_000_000
internal const val DEFAULT_VIDEO_BITRATE_BPS = 6_000_000
internal const val MAX_VIDEO_BITRATE_BPS = 8_000_000

internal data class VideoBitratePolicy(
    val minimumBps: Int,
    val startBps: Int,
    val maximumBps: Int,
) {
    init {
        require(minimumBps > 0)
        require(minimumBps <= startBps)
        require(startBps <= maximumBps)
    }
}

internal fun videoBitratePolicy(maximumBps: Int): VideoBitratePolicy =
    VideoBitratePolicy(
        minimumBps = minOf(MIN_VIDEO_BITRATE_BPS, maximumBps),
        startBps = minOf(START_VIDEO_BITRATE_BPS, maximumBps),
        maximumBps = maximumBps,
    )

data class WebRtcSessionConfig(
    val signalingUrl: String,
    val pairingToken: String,
    val targetBitrateBps: Int = DEFAULT_VIDEO_BITRATE_BPS,
) {
    init {
        val uri = URI(signalingUrl)
        require(uri.scheme == "http" || uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.path == "/api/v1/webrtc/sessions")
        require(pairingToken.length in 16..256)
        require(targetBitrateBps in 500_000..MAX_VIDEO_BITRATE_BPS)
    }

    val displayEndpoint: String
        get() = URI(signalingUrl).let { uri ->
            if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
        }

    override fun toString(): String =
        "WebRtcSessionConfig(signalingUrl=$signalingUrl, pairingToken=<redacted>, " +
            "targetBitrateBps=$targetBitrateBps)"
}

enum class WebRtcPublisherState {
    IDLE,
    SIGNALING,
    CONNECTING,
    STREAMING,
    DISCONNECTED,
    ERROR,
}

data class WebRtcPublisherStats(
    val framesOffered: Long = 0,
    val framesPublished: Long = 0,
    val framesDropped: Long = 0,
    val metadataSent: Long = 0,
    val imuSamplesOffered: Long = 0,
    val imuSamplesSent: Long = 0,
    val imuSamplesDropped: Long = 0,
)

interface WebRtcPublisherListener {
    fun onStateChanged(state: WebRtcPublisherState, detail: String?)

    fun onStatsChanged(stats: WebRtcPublisherStats)

    fun onControlChannelReady() = Unit

    fun onControlCommand(command: StreamControlCommand) = Unit

    fun onControlProtocolError(detail: String) = Unit
}
