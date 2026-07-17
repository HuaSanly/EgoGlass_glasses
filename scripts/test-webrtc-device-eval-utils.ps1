[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'webrtc-device-eval-utils.ps1')

$route = ConvertTo-SingleLineAdbOutput @(
    '192.168.1.20 dev wlan0 table wlan0 src 192.168.',
    '3.45 uid 2000 cache'
)
if (-not (Test-DirectWlanRoute -Route $route -ClientHost '192.168.1.20')) {
    throw "Split ADB route output was not accepted: $route"
}
if (Test-DirectWlanRoute -Route '192.168.1.20 dev rndis0 src 192.168.1.45' `
        -ClientHost '192.168.1.20') {
    throw 'USB route was incorrectly accepted as direct WLAN.'
}
if (-not (Test-LogContains -Lines @(
            'unrelated log line',
            'I EgoGlassWebRtc: video_bitrate_bps=8000000',
            'another unrelated log line'
        ) -Pattern 'video_bitrate_bps=8000000')) {
    throw 'A matching line inside a log array was not detected.'
}
if (Test-LogContains -Lines @('video_bitrate_bps=10000000') `
        -Pattern 'video_bitrate_bps=8000000') {
    throw 'A mismatched bitrate log was accepted.'
}
$hardwareEncoderLog = @(
    'HardwareVideoEncoder: Format: {color-format=19, width=1280, bitrate-mode=2,',
    'bitrate=8000000, frame-rate=30.0, height=720}'
)
if (-not (Test-LogContains -Lines $hardwareEncoderLog `
        -Pattern '(?s)HardwareVideoEncoder: Format: .*width=1280.*bitrate=8000000.*height=720')) {
    throw 'A valid multiline hardware encoder configuration was not detected.'
}

Write-Output 'WebRTC device eval route regression test passed.'
