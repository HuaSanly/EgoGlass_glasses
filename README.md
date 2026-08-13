# EgoGlass_glasses

Production Android application for Rokid Glass3 Enterprise. It owns SDK
lifecycle, NV21 camera capture, and direct WebRTC publishing to the Windows
ingest gateway.

## Runtime baseline

- Application ID: `com.egoglass.glasses`
- Launcher: `com.egoglass.glasses.MainActivity`
- Rokid client ID: `EgoGlassGlasses`
- Glasses SDK: `com.rokid.security:glass3.open.sdk:2.2.0-E`
- Android compile/target SDK: 34
- Android minimum SDK: 29
- JVM toolchain target: 17
- Reference display: 480 x 640, portrait

Only `platform/rokid` imports vendor SDK classes. UI and future application
features consume the project-owned `SdkConnection` contract.

## Direct WebRTC streaming

The default profile requests pure-camera 640 x 480 NV21 at 30 FPS to expose the
camera's horizontal 4:3 preview mode and publishes
H.264 with an adaptive 3-8 Mbps LAN bitrate policy that starts at 6 Mbps.
WebRTC retains congestion control but prefers maintaining 640 x 480 instead of
silently reducing spatial resolution. The SDK callback copies into a small pool
of reusable NV21 buffers; overwritten, stopped, and encoded frames return their
buffers so long streams do not allocate another frame-sized array for every frame.
Capture and WebRTC adaptation use the same `CaptureConfig`. Frame metadata uses
the `frame-metadata-v1` DataChannel, while
the reliable ordered `stream-control-v1` DataChannel carries validated start and
stop commands plus device status acknowledgements. A separate reliable ordered
`recording-control-v1` DataChannel carries validated wearer start/stop intents
from a temple double-tap and authoritative recording status from the client.
On current Glass3 firmware Linux `KEY_DASHBOARD` scan code 204 is translated by
`Generic.kl` to Android `KEYCODE_NOTIFICATION`; the activity consumes that key
and applies a 100-500 ms double-tap window. The display is an edge HUD with a
relative game-rotation-vector Y/P/R values. The optical coverage guide is
removed because it cannot be represented faithfully on the narrow display:
it does not draw a misleading interior 4:3 rectangle. Rokid's public
consumer hardware table lists the same camera family as Sony IMX681 with 109
degrees diagonal FOV and the display as 30 degrees FOV. A true
boundary requires camera intrinsics, camera-to-display extrinsics, display
projection and wearer-fit measurements. It does not write orientation into the
raw IMU telemetry stream.
Each frame preserves the raw Rokid millisecond timestamp and uses one Android
`elapsedRealtimeNanos()` callback sample for both metadata and WebRTC/RTP time.
This gives camera and IMU callbacks a common device-clock anchor without
claiming that callback time is the camera exposure time.
Frame metadata also includes an application-local `camera_start_generation`
that increments on every camera start, allowing the client to split clock
mappings even when a stop/start interval is shorter than its gap threshold.
Start the Windows workspace client first:

```powershell
cd ..\EgoGlass_client
.\scripts\start-client.ps1
```

Then open EgoGlass directly from the Glass3 application list. The app discovers
the client on UDP port 8771, receives a nonce-bound process-only configuration,
and starts streaming without ADB extras. Runtime endpoint and pairing secrets
are never stored. Intent extras remain available only for device evals and
diagnostics. Initial streaming remains automatic. After the WebRTC connection is
established, the Windows client can stop camera capture and start it again
without closing or renegotiating the peer connection.

V1 signaling uses HTTP on a trusted LAN. Media remains encrypted by
DTLS-SRTP. The application keeps the Glass3 display awake while streaming and
stops capture when its Activity leaves the foreground.

## Build

The wrapper JAR is intentionally not versioned because repository policy
forbids committed binaries. Bootstrap the checksum-pinned Gradle 8.6 wrapper
once, then build normally:

```powershell
.\scripts\bootstrap-gradle-wrapper.ps1
.\gradlew.bat testDebugUnitTest assembleDebug
```

`local.properties`, APKs, signing material, IDE state, and build output are
ignored and must not be committed.

## Verification

Fast repository and JVM gates:

```powershell
.\scripts\check-repository.ps1
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleRelease
```

Real Glass3 readiness eval:

```powershell
.\gradlew.bat assembleDebug
.\scripts\run-device-eval.ps1
```

See `evals/device/sdk-readiness.md` for pass criteria and manual lifecycle
coverage.

Direct LAN streaming gate:

```powershell
.\scripts\run-webrtc-device-eval.ps1 `
  -ClientHost 192.168.1.20 `
  -PairingToken <runtime-token> `
  -DurationSeconds 60
```

See `evals/device/webrtc-lan-streaming.md` for the no-phone, no-USB-forwarding
acceptance criteria.

## Official Demo provenance

This clean baseline was derived after reviewing the official Glass3 SDK Demo:

- Repository: `https://gitee.com/as_pixar/glass3sdkdemo`
- Reviewed commit: `20af0e445894e944a64437a5d3ce0d08b09f5a66`
- Source project: `glassdemo`

The copied demonstration menus, recognition experiments, sample media, and
unused assets are not part of this application. Refer back to the pinned Demo
commit when adding a verified Glass3 capability instead of restoring the Demo
as production code.
