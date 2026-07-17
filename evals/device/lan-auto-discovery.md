# GLASS-EVAL-DISCOVERY-001: normal-launch LAN discovery

## Outcome

With the Windows client already running, the operator launches EgoGlass from
the Glass3 application list without ADB extras. The glasses discover the client
on the same Wi-Fi network and begin the direct 720p30 WebRTC stream.

## Procedure

1. Start the Windows client from `EgoGlass_client`:

   ```powershell
   .\scripts\start-client.ps1
   ```

2. Install the current debug APK once, then disconnect ADB or omit all launch
   extras.
3. Open EgoGlass from the Glass3 application list.
4. Confirm the glasses progress through `FINDING CLIENT`, `NEGOTIATING`, and
   `STREAMING LIVE`.
5. Confirm the Windows console displays 1280 x 720 video near 30 FPS.
6. Close and reopen EgoGlass once; discovery and streaming must repeat without
   restarting the Windows client.

## Pass criteria

- No `signaling_url` or `pairing_token` Intent extra is used.
- Discovery completes within six UDP attempts on the same private IPv4 LAN.
- The response nonce matches the request and the UDP source matches the
  signaling URL host.
- The pairing token does not appear in the request, repository, or glasses
  persistent storage.
- The client receives 1280 x 720 H.264 near 30 FPS.
- Closing the Windows application stops both managed client processes.

## Current validation record

Validated the 720p30 profile on 2026-07-17 with the same `RG-glasses` hardware
and Glasses SDK `2.2.0-E`:

- a normal launcher Intent contained no signaling URL or pairing token extras;
- discovery connected directly to the Windows client on the shared Wi-Fi LAN;
- the client decoded 1280 x 720 H.264 at 30.139 FPS;
- first-frame latency was 788.037 ms and no ingest error was reported;
- the glasses application was force-stopped after verification so capture did
  not remain active.

## Previous validation record

Validated on 2026-07-16 with an `RG-glasses` device running Android 12,
firmware `SKQ1.240613.001 release-keys`, and glasses SDK `2.2.0-E`:

- two consecutive cold launches used no Intent extras and produced different
  client and device session IDs;
- the second launch replaced the live first session without a 409 response or
  Windows client restart;
- first-frame latency after replacement was 896.584 ms;
- the client measured 1920 x 1080 H.264 at 29.967 FPS over 1,238 frames;
- the current glasses process published 1,240 frames with zero publisher drops
  and one metadata message per published frame;
- the application persisted no recording, configuration secret, or media, so
  local-storage growth was zero.
