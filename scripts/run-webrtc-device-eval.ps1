[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ClientHost,
    [Parameter(Mandatory = $true)]
    [string]$PairingToken,
    [int]$ClientPort = 8770,
    [int]$DurationSeconds = 60,
    [string]$Adb = 'adb',
    [string]$ApkPath = 'app\build\outputs\apk\debug\app-debug.apk'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'webrtc-device-eval-utils.ps1')
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

$route = ConvertTo-SingleLineAdbOutput (& $Adb shell ip route get $ClientHost)
if (-not (Test-DirectWlanRoute -Route $route -ClientHost $ClientHost)) {
    throw "Direct LAN eval requires a wlan0 route to $ClientHost. Actual route: $route"
}

$baseUrl = "http://${ClientHost}:$ClientPort"
$health = Invoke-RestMethod -Uri "$baseUrl/api/v1/health" -TimeoutSec 5
if ($health.status -ne 'ok') {
    throw 'Ingest gateway health check failed.'
}

$outputDirectory = Join-Path $repositoryRoot 'app\build\evals\webrtc'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$progressDirectory = Join-Path $env:TEMP 'egoglass-webrtc-eval'
New-Item -ItemType Directory -Force -Path $progressDirectory | Out-Null
$progressLog = Join-Path $progressDirectory 'progress.log'
Set-Content -LiteralPath $progressLog -Value ''
Write-Output "Progress: Get-Content -Wait '$progressLog'"

& $Adb install -r $resolvedApk | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $Adb shell pm grant com.egoglass.glasses android.permission.CAMERA
& $Adb shell input keyevent KEYCODE_WAKEUP
& $Adb shell wm dismiss-keyguard
& $Adb logcat -c
& $Adb shell am force-stop com.egoglass.glasses
$signalingUrl = "$baseUrl/api/v1/webrtc/sessions"
& $Adb shell am start -W -n com.egoglass.glasses/.MainActivity `
    --es signaling_url $signalingUrl `
    --es pairing_token $PairingToken | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Application launch failed.' }

$startedAt = Get-Date
$deadline = $startedAt.AddSeconds($DurationSeconds)
$initialStatus = $null
$lastStatus = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $lastStatus = Invoke-RestMethod -Uri "$baseUrl/api/v1/webrtc/status" -TimeoutSec 5
    if ($null -eq $initialStatus -and $lastStatus.phase -eq 'streaming') {
        $initialStatus = $lastStatus
    }
    $elapsed = ((Get-Date) - $startedAt).TotalSeconds
    $percent = [Math]::Min(100, [Math]::Round($elapsed * 100 / $DurationSeconds, 1))
    $remaining = [Math]::Max(0, [Math]::Round($DurationSeconds - $elapsed))
    $line = "GLASS-EVAL-WEBRTC-001 ${percent}% ETA ${remaining}s frames=$($lastStatus.frames_received) metadata=$($lastStatus.metadata_received) matched=$($lastStatus.metadata_matched) phase=$($lastStatus.phase)"
    $timestamped = "$(Get-Date -Format o) $line"
    Write-Output $line
    Add-Content -LiteralPath $progressLog -Value $timestamped
    if ($lastStatus.phase -eq 'failed') {
        throw "Ingest gateway failed: $($lastStatus.last_error)"
    }
}

if ($null -eq $initialStatus -or $lastStatus.phase -ne 'streaming') {
    throw 'GLASS-EVAL-WEBRTC-001 failed: stream never reached or retained streaming state.'
}
if ($lastStatus.frames_received -lt 10) {
    throw 'GLASS-EVAL-WEBRTC-001 failed: too few decoded frames.'
}
$matchRatio = $lastStatus.metadata_matched / [Math]::Max(1, $lastStatus.frames_received)
if ($matchRatio -lt 0.95) {
    throw "GLASS-EVAL-WEBRTC-001 failed: metadata match ratio $matchRatio is below 0.95."
}
if ($lastStatus.average_fps -lt 27 -or $lastStatus.average_fps -gt 33) {
    throw "GLASS-EVAL-WEBRTC-001 failed: average FPS $($lastStatus.average_fps) is outside 27..33."
}
if ($lastStatus.width -ne 1920 -or $lastStatus.height -ne 1080) {
    throw "GLASS-EVAL-WEBRTC-001 failed: decoded size is $($lastStatus.width)x$($lastStatus.height)."
}
if ($lastStatus.video_codec -ne 'H264') {
    throw "GLASS-EVAL-WEBRTC-001 failed: negotiated codec is $($lastStatus.video_codec)."
}
if ($lastStatus.max_timestamp_match_error_90khz -gt 90) {
    throw "GLASS-EVAL-WEBRTC-001 failed: timestamp error exceeds 90 ticks."
}
if ($lastStatus.first_frame_latency_ms -gt 2000) {
    throw "GLASS-EVAL-WEBRTC-001 failed: first-frame latency is $($lastStatus.first_frame_latency_ms) ms."
}

$remoteScreenshot = '/sdcard/egoglass-webrtc-eval.png'
$localScreenshot = Join-Path $outputDirectory 'streaming.png'
& $Adb shell screencap -p $remoteScreenshot
& $Adb pull $remoteScreenshot $localScreenshot | Out-Host
$firmware = (& $Adb shell getprop ro.build.fingerprint).Trim()
$glassAddress = (& $Adb shell ip -4 addr show wlan0 | Select-String -Pattern 'inet ').ToString().Trim()
$logs = & $Adb logcat -d -s 'EgoGlassWebRtc:I' 'EgoGlassCapture:I' 'AndroidRuntime:E' '*:S'
if ($logs -match 'FATAL EXCEPTION') {
    throw 'GLASS-EVAL-WEBRTC-001 failed: AndroidRuntime crash detected.'
}
if ($logs -notmatch 'video_bitrate_bps=10000000') {
    throw 'GLASS-EVAL-WEBRTC-001 failed: 10 Mbps WebRTC bitrate was not applied.'
}
Set-Content -LiteralPath (Join-Path $outputDirectory 'device.log') -Value $logs
$lastStatus | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputDirectory 'status.json')

Write-Output 'GLASS-EVAL-WEBRTC-001 PASSED'
Write-Output "Glass route: $route"
Write-Output "Glass address: $glassAddress"
Write-Output "Firmware: $firmware"
Write-Output "Frames: $($lastStatus.frames_received)"
Write-Output "Average FPS: $($lastStatus.average_fps)"
Write-Output "Codec: $($lastStatus.video_codec)"
Write-Output "Decoded size: $($lastStatus.width)x$($lastStatus.height)"
Write-Output "Metadata match ratio: $([Math]::Round($matchRatio, 4))"
Write-Output "Max timestamp error: $($lastStatus.max_timestamp_match_error_90khz) ticks"
Write-Output "First-frame latency: $($lastStatus.first_frame_latency_ms) ms"
Write-Output "Screenshot: $localScreenshot"
