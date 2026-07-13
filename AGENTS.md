# EgoGlass Glasses Repository Guide

This file is authoritative for the standalone `EgoGlass_glasses` repository. It
also extends the superproject `AGENTS.md` when checked out as a submodule.

## Repository workflow

- Use `main` as the protected integration branch and Conventional Commits with
  the `glasses` scope.
- Ship every feature or fix with deterministic tests and a device eval when
  hardware behavior is involved.
- Use Semantic Versioning for releases and push the submodule commit before the
  superproject pointer is updated.
- Never commit secrets, recordings, APKs, signing material, generated build
  output, or IDE state.

## Ownership

The subproject is the Android application running on Rokid Glass3 Enterprise.
It owns:

- RGB or SDK-native frame acquisition and explicit device timestamps.
- Optional sensor capture only after the API and clock are verified on-device.
- Local session persistence and recoverable upload state.
- The device side of phone, P2P, or future WebRTC transport adapters.
- Low-latency feedback rendering suitable for the glasses display.

It does not own data curation, dataset registration, training, model selection,
or cloud persistence policy.

## Platform rules

- Use Kotlin for new application code unless an SDK-only Java boundary requires
  Java. Keep the Java boundary isolated.
- Baseline against JDK 17 or newer and Android 8.0 or newer. Keep compile/target
  SDK decisions explicit in Gradle when the project is created.
- Pin Rokid dependencies centrally. The current glasses SDK baseline is
  `com.rokid.security:glass3.open.sdk:2.2.0-E` with the documented `org.slf4j`
  exclusion.
- Never commit AK/SK values, API keys, device identifiers, recordings, APKs, or
  Gradle signing material.
- Preserve raw capture timestamps. Conversion or alignment belongs in a named
  adapter and must never overwrite the source clock value.
- Do not label NV21 data as RGB or MP4. Format conversion must be explicit and
  covered by a deterministic test.

## Package boundaries

When implementation begins, separate `capture`, `storage`, `transport`,
`feedback`, and `platform/rokid` concerns. Only `platform/rokid` may depend
directly on vendor SDK APIs. Other packages consume project-owned interfaces.

## Verification

- Pure transforms and state machines require fast JVM unit tests.
- Android integration requires instrumentation tests where platform behavior is
  involved.
- Bluetooth, P2P, camera, microphone, lifecycle, thermal, and long-session
  behavior require a named real-device eval with hardware/firmware recorded.
- For SDK setup, verify the documented readiness signal before testing higher
  layers. Do not replace a missing device result with a mock claim.
- Every capture or transport change must report dropped frames, end-to-end
  latency, reconnect behavior, and local-storage growth.

Build and test commands belong in this directory once the Android project is
created. Do not make root checks depend on a connected device.
