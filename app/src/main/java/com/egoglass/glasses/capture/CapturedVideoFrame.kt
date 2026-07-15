package com.egoglass.glasses.capture

data class CapturedVideoFrame(
    val frameId: Long,
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val capturedAtRokidSdkMs: Long,
    val receivedAtElapsedRealtimeNs: Long,
    val videoAtMonotonicNs: Long,
    val rotationDegrees: Int,
    val captureConfigId: String,
) {
    init {
        require(frameId >= 0)
        require(width > 0 && height > 0)
        require(nv21.size >= width * height * 3 / 2)
        require(capturedAtRokidSdkMs >= 0)
        require(receivedAtElapsedRealtimeNs >= 0)
        require(videoAtMonotonicNs >= 0)
        require(rotationDegrees in setOf(0, 90, 180, 270))
    }
}
