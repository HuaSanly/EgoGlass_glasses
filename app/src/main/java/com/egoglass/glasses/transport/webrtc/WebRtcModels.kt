package com.egoglass.glasses.transport.webrtc

import java.net.URI

data class WebRtcSessionConfig(
    val signalingUrl: String,
    val pairingToken: String,
    val targetBitrateBps: Int = 5_000_000,
) {
    init {
        val uri = URI(signalingUrl)
        require(uri.scheme == "http" || uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        require(uri.path == "/api/v1/webrtc/sessions")
        require(pairingToken.length in 16..256)
        require(targetBitrateBps in 500_000..10_000_000)
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
)

interface WebRtcPublisherListener {
    fun onStateChanged(state: WebRtcPublisherState, detail: String?)

    fun onStatsChanged(stats: WebRtcPublisherStats)
}
