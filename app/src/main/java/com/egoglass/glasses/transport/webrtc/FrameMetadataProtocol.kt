package com.egoglass.glasses.transport.webrtc

import com.egoglass.glasses.capture.CapturedVideoFrame
import org.json.JSONObject

internal fun encodeVideoFrameMetadata(frame: CapturedVideoFrame): String =
    JSONObject()
        .put("schema_version", "1.0")
        .put("message_type", "video_frame")
        .put("stream_id", "camera")
        .put("camera_start_generation", frame.cameraStartGeneration)
        .put("frame_id", frame.frameId)
        .put("captured_at_rokid_sdk_ms", frame.capturedAtRokidSdkMs)
        .put("received_at_elapsed_realtime_ns", frame.receivedAtElapsedRealtimeNs)
        .put("video_at_monotonic_ns", frame.videoAtMonotonicNs)
        .put("rtp_timestamp_90khz", monotonicNsToRtpTimestamp90Khz(frame.videoAtMonotonicNs))
        .put("width", frame.width)
        .put("height", frame.height)
        .put("rotation_degrees", frame.rotationDegrees)
        .put("capture_config_id", frame.captureConfigId)
        .toString()
