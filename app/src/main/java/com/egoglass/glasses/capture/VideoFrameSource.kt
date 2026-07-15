package com.egoglass.glasses.capture

interface VideoFrameSourceListener {
    fun onCameraOpened(width: Int, height: Int, appliedFramesPerSecond: Int?)

    fun onFrame(frame: CapturedVideoFrame)

    fun onCameraClosed()

    fun onError(message: String)
}

interface VideoFrameSource {
    fun start(config: CaptureConfig, listener: VideoFrameSourceListener)

    fun stop()
}
