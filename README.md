# EgoGlass_glasses

Production Android application for Rokid Glass3 Enterprise. The current
baseline owns SDK lifecycle and device readiness; capture and transport
features will be added behind project-owned interfaces.

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

## Official Demo provenance

This clean baseline was derived after reviewing the official Glass3 SDK Demo:

- Repository: `https://gitee.com/as_pixar/glass3sdkdemo`
- Reviewed commit: `20af0e445894e944a64437a5d3ce0d08b09f5a66`
- Source project: `glassdemo`

The copied demonstration menus, recognition experiments, sample media, and
unused assets are not part of this application. Refer back to the pinned Demo
commit when adding a verified Glass3 capability instead of restoring the Demo
as production code.
