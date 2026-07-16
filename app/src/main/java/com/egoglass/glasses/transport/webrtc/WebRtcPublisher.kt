package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.sensors.ImuCapabilities
import com.egoglass.glasses.sensors.ImuSample

interface WebRtcPublisher {
    val state: WebRtcPublisherState

    fun addListener(listener: WebRtcPublisherListener)

    fun removeListener(listener: WebRtcPublisherListener)

    fun connect(config: WebRtcSessionConfig, captureConfig: CaptureConfig)

    fun offerFrame(frame: CapturedVideoFrame)

    fun sendControlStatus(status: StreamControlStatus): Boolean

    fun sendImuCapabilities(capabilities: ImuCapabilities): Boolean

    fun offerImuSample(sample: ImuSample)

    fun close()
}
