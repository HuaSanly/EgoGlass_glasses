package com.egoglass.glasses.capture

data class CaptureConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val framesPerSecond: Int = 30,
    val enableVideoStabilization: Boolean = false,
    val zoomLevel: Int = 1,
    val rotationDegrees: Int = 0,
    val captureConfigId: String = "720p30",
) {
    init {
        require(width > 0 && height > 0)
        require(framesPerSecond in 1..30)
        require(zoomLevel >= 1)
        require(rotationDegrees in setOf(0, 90, 180, 270))
        require(captureConfigId.matches(Regex("^[A-Za-z0-9_.-]{1,64}$")))
    }
}
