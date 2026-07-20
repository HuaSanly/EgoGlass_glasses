# GLASS-EVAL-IMU-001: experimental IMU telemetry

## Outcome

Determine whether a real Rokid Glass3 Enterprise exposes Android accelerometer
and gyroscope samples to EgoGlass and whether both streams reach the Windows
ingest gateway over the existing direct WebRTC session.

This eval does not verify camera alignment, pose, sensor calibration, a world
coordinate frame, or stable timestamp semantics. The SDK 2.2.0-E public API
does not document IMU access, and the public FAQ says Glass3 has no magnetic
field sensor.

## Procedure

Start the ingest gateway with a fixed runtime token, build the debug APK, then
run two 10-second device rounds:

```powershell
cd ..\EgoGlass_client\services\ingest-gateway
.\.venv\Scripts\python.exe -m egoglass_ingest_gateway.app `
  --host 0.0.0.0 `
  --port 8770 `
  --pairing-token <runtime-token>

cd ..\..\..\EgoGlass_glasses
.\gradlew.bat assembleDebug
.\scripts\run-imu-device-eval.ps1 `
  -ClientHost 192.168.1.20 `
  -PairingToken <runtime-token> `
  -DurationSeconds 10
```

The script requires exactly one `RG-glasses` ADB device and a `wlan0` route to
the client. Glasses signaling uses the client's LAN address, while the protected
IMU status endpoint is queried through `127.0.0.1`. It records
`dumpsys sensorservice`, Android logs, complete
capabilities, final bounded status, firmware, and observed rates under ignored
`app/build/evals/imu/` output.

## Pass criteria

- Android `SensorManager` registers both `TYPE_ACCELEROMETER` and
  `TYPE_GYROSCOPE`; no magnetometer is requested or inferred.
- The client receives an exact descriptor for both sensors and no requested
  sensor is reported missing.
- Both sensors deliver at least 20 valid samples per 10-second round and an
  observed positive arrival rate.
- The endpoint reports zero malformed messages and exposes only bounded
  counters plus the latest sample, not raw history.
- `SensorEvent.timestamp` and callback-time `elapsedRealtimeNanos()` remain
  separate fields; their delta is recorded as evidence only.
- Every video frame uses one `elapsedRealtimeNanos()` callback sample for both
  frame metadata and the WebRTC/RTP timestamp, so camera and IMU callback
  anchors share an explicit device clock without altering either raw source
  timestamp.
- Stop and restart the camera in one application run. The published
  `camera_start_generation` must increase and all frames from one camera run
  must keep the same value, so client clock fitting cannot cross the restart.
- A force-stop and immediate second launch still registers and streams both
  sensors, proving the old listener cannot unregister the replacement run.
- No AndroidRuntime crash or unbounded WebRTC DataChannel buffering occurs.

Promotion to a stable contract requires reviewing the captured device model,
firmware, sensor descriptors, actual rates, clock deltas, gaps, and reordered
samples from this eval.
