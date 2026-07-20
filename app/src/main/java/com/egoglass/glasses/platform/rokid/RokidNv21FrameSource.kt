package com.egoglass.glasses.platform.rokid

import android.os.SystemClock
import android.util.Log
import com.egoglass.glasses.capture.CaptureConfig
import com.egoglass.glasses.capture.CameraStartGenerationCounter
import com.egoglass.glasses.capture.CapturedVideoFrame
import com.egoglass.glasses.capture.VideoFrameSource
import com.egoglass.glasses.capture.VideoFrameSourceListener
import com.rokid.security.glass3.open.sdk.GlassSdk
import com.rokid.security.glass3.open.sdk.camera.CameraShareHelper
import com.rokid.security.glass3.sdk.base.data.media.CameraShareConfig
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "EgoGlassCapture"

fun createRokidNv21FrameSource(): VideoFrameSource = RokidNv21FrameSource()

private class RokidNv21FrameSource : VideoFrameSource {
    private val nextFrameId = AtomicLong(0)
    private val cameraStartGenerations = CameraStartGenerationCounter()

    @Volatile
    private var helper: CameraShareHelper? = null

    @Synchronized
    override fun start(config: CaptureConfig, listener: VideoFrameSourceListener) {
        if (helper?.isNv21Active() == true) return
        if (!GlassSdk.isReady()) {
            listener.onError("Glass3 SDK is not ready")
            return
        }

        val activeHelper = CameraShareHelper()
        val cameraStartGeneration = cameraStartGenerations.next()
        helper = activeHelper
        runCatching { activeHelper.getSupportedPreviewSizes() }
            .onSuccess { sizes ->
                Log.i(
                    TAG,
                    "supported_preview_sizes=$sizes requested=" +
                        "${config.width}x${config.height}@${config.framesPerSecond}",
                )
            }
            .onFailure { error ->
                Log.w(TAG, "supported_preview_sizes_unavailable", error)
            }
        val rokidConfig = CameraShareConfig(
            previewWidth = config.width,
            previewHeight = config.height,
            previewTargetFps = config.framesPerSecond,
            enableVideoStabilization = config.enableVideoStabilization,
            zoomLevel = config.zoomLevel,
            useAsyncCallback = true,
        )
        activeHelper.initNv21ExportWithConfig(
            enableMix = false,
            config = rokidConfig,
            callback = object : CameraShareHelper.Nv21Callback {
                override fun onCameraOpened(width: Int, height: Int) {
                    Log.i(TAG, "camera_opened=${width}x$height")
                    listener.onCameraOpened(width, height, null)
                }

                override fun onNv21Frame(
                    nv21: ByteArray,
                    width: Int,
                    height: Int,
                    timestamp: Long,
                ) {
                    val expectedBytes = width * height * 3 / 2
                    if (nv21.size < expectedBytes) {
                        listener.onError("Invalid NV21 frame size")
                        return
                    }
                    val callbackAtElapsedRealtimeNs = SystemClock.elapsedRealtimeNanos()
                    listener.onFrame(
                        CapturedVideoFrame(
                            frameId = nextFrameId.getAndIncrement(),
                            cameraStartGeneration = cameraStartGeneration,
                            nv21 = nv21.copyOf(expectedBytes),
                            width = width,
                            height = height,
                            capturedAtRokidSdkMs = timestamp,
                            receivedAtElapsedRealtimeNs = callbackAtElapsedRealtimeNs,
                            videoAtMonotonicNs = callbackAtElapsedRealtimeNs,
                            rotationDegrees = config.rotationDegrees,
                            captureConfigId = config.captureConfigId,
                        )
                    )
                }

                override fun onCameraClosed() {
                    Log.i(TAG, "camera_closed")
                    listener.onCameraClosed()
                }

                override fun onError(code: Int, msg: String) {
                    Log.e(TAG, "camera_error=$code")
                    listener.onError("Glass3 camera error $code: $msg")
                }

                override fun onNv21ExportResolutionChanged(
                    width: Int,
                    height: Int,
                    appliedPreviewFps: Int,
                ) {
                    Log.i(TAG, "capture_applied=${width}x$height@$appliedPreviewFps")
                    listener.onCameraOpened(width, height, appliedPreviewFps)
                }

                override fun onNv21ExportRuntimeParamsChanged(
                    appliedPreviewFps: Int,
                    videoStabilizationEnabled: Boolean,
                ) {
                    Log.i(
                        TAG,
                        "capture_params_fps=$appliedPreviewFps stabilization=$videoStabilizationEnabled",
                    )
                }

                override fun onZoomLevelChanged(zoomLevel: Int) {
                    Log.i(TAG, "zoom_level=$zoomLevel")
                }
            },
        )
    }

    @Synchronized
    override fun stop() {
        helper?.releaseNv21Export()
        helper = null
    }
}
