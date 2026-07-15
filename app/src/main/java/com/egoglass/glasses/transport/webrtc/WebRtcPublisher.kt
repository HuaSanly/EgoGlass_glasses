package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CapturedVideoFrame

interface WebRtcPublisher {
    val state: WebRtcPublisherState

    fun addListener(listener: WebRtcPublisherListener)

    fun removeListener(listener: WebRtcPublisherListener)

    fun connect(config: WebRtcSessionConfig, captureConfig: CaptureConfig)

    fun offerFrame(frame: CapturedVideoFrame)

    fun close()
}
