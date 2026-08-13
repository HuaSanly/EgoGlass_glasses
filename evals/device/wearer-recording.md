# Wearer recording device eval

Record the Glass3 serial, Android build, firmware and client commit before running.

1. Start the Windows client and wait for the HUD to show `READY`.
2. Double-tap the right temple ten times to alternate start and stop. A single tap must do nothing and the system launcher must not appear.
3. Verify each start shows countdown within 500 ms when video is already live, then `RECORDING`; verify each stop shows `SAVING`, then `READY`.
4. Confirm the client publishes ten independent recordings and validates the four-file recording contract.
5. Stop video, double-tap, and verify automatic stream recovery completes within five seconds.
6. During idle, countdown, recording and finalization, disconnect Wi-Fi and terminate the client in separate runs. The HUD must never claim recording without client confirmation.
7. Stand still, turn right, look up and tilt right. Record Y/P/R signs and cross-axis error; target non-commanded axis error is at most 8 degrees.
8. Record for 30 seconds and compare frame rate, dropped frames, control latency and memory to the prior build. Dropped-frame rate must not regress by more than one percentage point.
9. Confirm there is no camera frame overlay in the recording HUD. The public Rokid consumer hardware table reports a 109 degree diagonal camera FOV and a 30 degree display FOV; a full camera boundary cannot be represented faithfully on the display. Optical alignment, if needed later, requires measured camera intrinsics, camera-to-display extrinsics and display projection.

The ring is out of scope for this eval.
