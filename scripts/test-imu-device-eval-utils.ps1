[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'imu-device-eval-utils.ps1')

$statusUrl = Get-ImuLoopbackStatusUrl -ClientPort 8770
if ($statusUrl -ne 'http://127.0.0.1:8770/api/v1/webrtc/imu/status') {
    throw "Unexpected IMU status URL: $statusUrl"
}
if ($statusUrl -match '192\.168\.') {
    throw 'IMU status URL must never use a LAN address.'
}

$splitLog = @(
    'EgoGlassWebRtc: webrtc_state=STREAMING',
    'EgoGlassImu: imu_state=started registered_sensors=2',
    'EgoGlassWebRtc: imu_channel_state=open'
)
if (-not (Test-TwoImuSensorsRegistered -LogLines $splitLog)) {
    throw 'A registration marker split across logcat output was not detected.'
}
if (Test-TwoImuSensorsRegistered -LogLines @('imu_state=started registered_sensors=1')) {
    throw 'A one-sensor registration marker was incorrectly accepted.'
}

Write-Output 'IMU device eval loopback URL regression test passed.'
