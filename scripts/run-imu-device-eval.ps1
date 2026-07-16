[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ClientHost,
    [Parameter(Mandatory = $true)]
    [string]$PairingToken,
    [int]$ClientPort = 8770,
    [int]$DurationSeconds = 10,
    [string]$Adb = 'adb',
    [string]$ApkPath = 'app\build\outputs\apk\debug\app-debug.apk'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'webrtc-device-eval-utils.ps1')
. (Join-Path $PSScriptRoot 'imu-device-eval-utils.ps1')
if ($PairingToken.Length -lt 16) {
    throw 'PairingToken must contain at least 16 characters.'
}
if ($DurationSeconds -lt 10) {
    throw 'DurationSeconds must be at least 10.'
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedApk = Join-Path $repositoryRoot $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk)) {
    throw "Debug APK not found: $resolvedApk"
}

$devices = @(& $Adb devices | Select-String '\tdevice$')
if ($devices.Count -ne 1) {
    throw "GLASS-EVAL-IMU-001 requires exactly one connected ADB device; found $($devices.Count)."
}
$model = (& $Adb shell getprop ro.product.model).Trim()
if ($model -ne 'RG-glasses') {
    throw "Expected Rokid Glass3 model RG-glasses, found '$model'."
}
$route = ConvertTo-SingleLineAdbOutput (& $Adb shell ip route get $ClientHost)
if (-not (Test-DirectWlanRoute -Route $route -ClientHost $ClientHost)) {
    throw "Direct LAN eval requires a wlan0 route to $ClientHost. Actual route: $route"
}

$baseUrl = "http://${ClientHost}:$ClientPort"
$statusUrl = Get-ImuLoopbackStatusUrl -ClientPort $ClientPort
$health = Invoke-RestMethod -Uri "http://127.0.0.1:$ClientPort/api/v1/health" -TimeoutSec 5
if ($health.status -ne 'ok') {
    throw 'Ingest gateway health check failed.'
}

$outputDirectory = Join-Path $repositoryRoot 'app\build\evals\imu'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& $Adb install -r $resolvedApk | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $Adb shell pm grant com.egoglass.glasses android.permission.CAMERA
$sensorService = & $Adb shell dumpsys sensorservice
Set-Content -LiteralPath (Join-Path $outputDirectory 'sensorservice.txt') -Value $sensorService

$signalingUrl = "$baseUrl/api/v1/webrtc/sessions"
$roundResults = @()
foreach ($round in 1..2) {
    & $Adb shell input keyevent KEYCODE_WAKEUP
    & $Adb shell wm dismiss-keyguard
    & $Adb logcat -c
    & $Adb shell am force-stop com.egoglass.glasses
    & $Adb shell am start -W -n com.egoglass.glasses/.MainActivity `
        --es signaling_url $signalingUrl `
        --es pairing_token $PairingToken | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Application launch $round failed." }

    $deadline = (Get-Date).AddSeconds($DurationSeconds)
    $lastStatus = $null
    do {
        Start-Sleep -Seconds 2
        $lastStatus = Invoke-RestMethod -Uri $statusUrl -TimeoutSec 5
        Write-Output (
            "GLASS-EVAL-IMU-001 round=$round state=$($lastStatus.channel_state) " +
            "samples=$($lastStatus.samples_received) malformed=$($lastStatus.malformed_messages)"
        )
    } while ((Get-Date) -lt $deadline)

    if ($lastStatus.channel_state -ne 'receiving') {
        throw "GLASS-EVAL-IMU-001 round $round failed: IMU channel is not receiving."
    }
    if ($null -eq $lastStatus.capabilities) {
        throw "GLASS-EVAL-IMU-001 round $round failed: capabilities were not received."
    }
    if (@($lastStatus.capabilities.missing_sensor_types).Count -ne 0) {
        $missing = @($lastStatus.capabilities.missing_sensor_types) -join ', '
        throw "GLASS-EVAL-IMU-001 round $round failed: missing sensors: $missing"
    }
    foreach ($sensorType in @('accelerometer', 'gyroscope')) {
        $sensorStatus = $lastStatus.sensors.$sensorType
        if ($null -eq $sensorStatus -or $sensorStatus.sample_count -lt 20) {
            throw (
                "GLASS-EVAL-IMU-001 round $round failed: $sensorType received " +
                "$($sensorStatus.sample_count) samples; expected at least 20."
            )
        }
        if ($null -eq $sensorStatus.observed_rate_hz -or $sensorStatus.observed_rate_hz -le 0) {
            throw "GLASS-EVAL-IMU-001 round $round failed: $sensorType rate was not measurable."
        }
    }
    if ($lastStatus.malformed_messages -ne 0) {
        throw "GLASS-EVAL-IMU-001 round $round failed: malformed telemetry was received."
    }

    $logs = & $Adb logcat -d -s 'EgoGlassImu:I' 'EgoGlassWebRtc:I' 'AndroidRuntime:E' '*:S'
    $logText = $logs -join "`n"
    if ($logText -match 'FATAL EXCEPTION') {
        throw "GLASS-EVAL-IMU-001 round $round failed: AndroidRuntime crash detected."
    }
    if (-not (Test-TwoImuSensorsRegistered -LogLines $logs)) {
        throw "GLASS-EVAL-IMU-001 round $round failed: two sensors were not registered."
    }
    Set-Content -LiteralPath (Join-Path $outputDirectory "device-round-$round.log") -Value $logs
    $lastStatus | ConvertTo-Json -Depth 10 |
        Set-Content -LiteralPath (Join-Path $outputDirectory "status-round-$round.json")
    $roundResults += $lastStatus
}

$firmware = (& $Adb shell getprop ro.build.fingerprint).Trim()
$capabilities = $roundResults[-1].capabilities
Write-Output 'GLASS-EVAL-IMU-001 PASSED'
Write-Output "Model: $model"
Write-Output "Firmware: $firmware"
Write-Output "Route: $route"
foreach ($descriptor in @($capabilities.sensors)) {
    $status = $roundResults[-1].sensors.($descriptor.sensor_type)
    Write-Output (
        "$($descriptor.sensor_type): name=$($descriptor.name) vendor=$($descriptor.vendor) " +
        "samples=$($status.sample_count) observed_hz=$($status.observed_rate_hz) " +
        "delta_ns=$($status.last_event_to_callback_delta_ns)"
    )
}
Write-Output "Evidence: $outputDirectory"
