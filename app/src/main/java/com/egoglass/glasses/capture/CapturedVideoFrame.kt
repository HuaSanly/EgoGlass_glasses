package com.egoglass.glasses.capture

import java.util.concurrent.atomic.AtomicBoolean

data class CapturedVideoFrame(
    val frameId: Long,
    val cameraStartGeneration: Long,
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val capturedAtRokidSdkMs: Long,
    val receivedAtElapsedRealtimeNs: Long,
    val videoAtMonotonicNs: Long,
    val rotationDegrees: Int,
    val captureConfigId: String,
    private val releaseCallback: (() -> Unit)? = null,
) {
    private val released = AtomicBoolean(false)

    init {
        require(frameId >= 0)
        require(cameraStartGeneration >= 1)
        require(width > 0 && height > 0)
        require(nv21.size >= width * height * 3 / 2)
        require(capturedAtRokidSdkMs >= 0)
        require(receivedAtElapsedRealtimeNs >= 0)
        require(videoAtMonotonicNs >= 0)
        require(videoAtMonotonicNs == receivedAtElapsedRealtimeNs) {
            "WebRTC and callback timestamps must use the same elapsed-realtime sample"
        }
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }

    fun release() {
        if (released.compareAndSet(false, true)) releaseCallback?.invoke()
    }
}
