# GLASS-EVAL-IMU-001 acceptance record: 2026-07-16

## Verdict

PASS. Rokid Glass3 exposed both the Android accelerometer and gyroscope to the
EgoGlass application. Both streams reached the Windows ingest gateway over the
direct WebRTC DataChannel in two consecutive 10-second runs, including an
application force-stop and cold restart between runs.

## Tested system

- Glass model: `RG-glasses`
- Firmware: `1.19.e003-20260616-150201`
- Android build fingerprint:
  `Rokid/glasses/glasses:12/SKQ1.240613.001/1.19.e003-20260616-150201:user/release-keys`
- Glasses commit used for the APK: `65c8b20`
- Client commit: `41d06c8`
- Superproject contract commit: `efe2275`
- Glass address: `192.168.3.45`
- Client address: `192.168.3.185`
- Verified route: direct `wlan0`
- Requested sampling period: `10,000 us`

## Sensor capabilities

| Type | Android type | Sensor | Vendor | Resolution | Max range | Min delay |
| --- | ---: | --- | --- | ---: | ---: | ---: |
| Accelerometer | 1 | icm4x6xx Accelerometer Non-wakeup | TDK-Invensense | 0.0002992752 m/s^2 | 156.9064 m/s^2 | 5,000 us |
| Gyroscope | 4 | icm4x6xx Gyroscope Non-wakeup | TDK-Invensense | 0.0000665790 rad/s | 34.9066 rad/s | 5,000 us |

No requested sensor was missing. The experiment did not request or infer a
magnetometer.

## Client observations

| Run | Sensor | Samples | Arrival rate | Gaps | Out of order | Delta min | Delta max | Delta last |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | Accelerometer | 960 | 100.890 Hz | 0 | 0 | 1.104 ms | 50.339 ms | 3.933 ms |
| 1 | Gyroscope | 952 | 100.891 Hz | 0 | 0 | 1.157 ms | 44.604 ms | 5.136 ms |
| 2 | Accelerometer | 936 | 100.879 Hz | 0 | 0 | 1.114 ms | 49.443 ms | 6.283 ms |
| 2 | Gyroscope | 929 | 100.916 Hz | 0 | 0 | 1.288 ms | 45.900 ms | 8.048 ms |

- Run 1: 1,912 samples, 5 repeated capability messages, 0 malformed messages.
- Run 2: 1,865 samples, 4 repeated capability messages, 0 malformed messages.
- Periodic sender logs reported `imu_samples_dropped=0` in both runs.
- Both runs opened `imu-telemetry-experimental-v0` and registered two sensors.
- The second cold launch resumed both streams, covering the fast stop/start
  listener-unregistration regression.
- No `AndroidRuntime` crash was observed.

## Limits

The measured rate is the Windows client arrival rate, not a guarantee of the
sensor HAL's exact sampling cadence. `SensorEvent.timestamp` and callback-time
`elapsedRealtimeNanos()` were preserved separately. The measured delta includes
device scheduling and callback delay and does not establish camera alignment.
The contract remains experimental until camera/IMU clock behavior and longer
thermal runs are evaluated.

Raw evidence is stored in the ignored local directory
`app/build/evals/imu/`: `sensorservice.txt`, both final status JSON files, and
both filtered Android logs.
