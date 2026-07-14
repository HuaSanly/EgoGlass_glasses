# GLASS-EVAL-SDK-001: SDK readiness lifecycle

## Outcome

The production launcher must bind the Glass3 system service, register the
`EgoGlassGlasses` client, and visibly reach `SYSTEM READY` on real hardware.

## Required hardware

- Rokid Glass3 Enterprise with model property `RG-glasses`.
- Glass3 data/debug cable.
- One ADB-visible device.
- Firmware fingerprint recorded in the result.

## Automated procedure

```powershell
.\gradlew.bat assembleDebug
.\scripts\run-device-eval.ps1
```

The script installs the debug APK, wakes the display, launches
`com.egoglass.glasses/.MainActivity`, requires the structured transition
`sdk_state=CONNECTING -> sdk_state=READY` within five seconds on three
consecutive cold launches, verifies the visible ready-state text, and saves a
screenshot under `app/build/evals/`.

## Pass criteria

- The APK installs and the launcher activity starts.
- The device reports model `RG-glasses`.
- Three consecutive cold launches emit `sdk_state=CONNECTING`, then reach
  `sdk_state=READY` within five seconds each.
- The UI shows `SYSTEM READY` with no crash or permission dialog.
- The model, display size, and firmware fingerprint are captured in the result.
- A 480 x 640 screenshot is saved as evidence without entering Git.

## Manual lifecycle checks

1. Stop the Glass3 security service or reboot the device; the application must
   show a recoverable disconnected/error state rather than crash.
2. Select `RETRY`; the application must either return to `SYSTEM READY` or keep
   the explicit error state.
