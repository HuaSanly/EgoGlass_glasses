# GLASS-EVAL-WEBRTC-001: direct LAN streaming

## Outcome

Glass3 sends pure-camera video and frame metadata directly to the Windows
ingest gateway over WebRTC without a phone, cloud relay, ADB reverse, or USB
network forwarding.

## Automated procedure

Start the ingest gateway on Windows and keep its runtime token private:

```powershell
cd ..\EgoGlass_client\services\ingest-gateway
uv run egoglass-ingest-gateway --host 0.0.0.0 --port 8770
```

In another terminal:

```powershell
.\gradlew.bat assembleDebug
.\scripts\run-webrtc-device-eval.ps1 `
  -ClientHost 192.168.1.20 `
  -PairingToken <runtime-token> `
  -DurationSeconds 60
```

The script refuses to run unless Android routes the Windows client address over
`wlan0`. Generated logs and screenshots stay under the ignored `app/build/`
directory and `%TEMP%\egoglass-webrtc-eval`.

## Pass criteria

- Client and Glass3 are on the same LAN and the route uses `wlan0`.
- WebRTC reaches `streaming` and the first decoded frame arrives in under two
  seconds after offer processing begins.
- Requested and applied capture is 1920 x 1080 at 30 FPS and the negotiated
  codec is H.264.
- The glasses log records `video_bitrate_bps=20000000`, and the hardware H.264
  encoder initializes with a 20,000,000 bps target. This fixed LAN trial profile
  must be reevaluated before use on a constrained or untrusted network.
- The decoded stream remains between 27 and 33 FPS for the 60-second gate.
- The device log records `supported_preview_sizes` and the requested
  `1920x1080@30` profile. The client verifies the applied cadence from decoded
  frames because SDK 2.2.0-E does not always emit a runtime-parameter callback
  for the initial camera open.
- Metadata is received, at least 95 percent of decoded frames match metadata,
  unmatched buffers remain bounded at 256 entries, and timestamp association
  error does not exceed 90 ticks (1 ms).
- The glasses latest-frame queue reports its drop count and never grows.
- Stopping or replacing a session interrupts the frame worker cleanly without
  an `AndroidRuntime` crash; the JVM interruption regression test passes.
- No camera, WebRTC, AndroidRuntime, or ingest process crash occurs.
- Glasses firmware, addresses, final counters, and a screenshot are recorded.

## Separate interruption check

After the stable run, interrupt Wi-Fi for five seconds. The client must enter a
disconnected state and a new authenticated offer must restore streaming within
ten seconds. Do not count this manual network mutation as part of the stable
run.

## 20 Mbps validation record

Validated on 2026-07-16 with the Glass3 and Windows client on the same Wi-Fi:

- the application logged `video_bitrate_bps=20000000`;
- `c2.qti.avc.encoder` initialized H.264 at 1920 x 1080, 30 FPS, and
  `bitrate=20000000`;
- the client received 2,842 frames at 29.576 FPS with no malformed metadata,
  unmatched-buffer drops, or stream error;
- the glasses published 2,880 frames with zero latest-frame queue drops and no
  Android or codec crash.

The 20 Mbps value is the encoder and sender target. WebRTC congestion control
may still lower instantaneous network throughput when the Wi-Fi path cannot
sustain that rate.
