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
if (-not (Test-ValidEvalSignalingMode -UseDiscovery $true -PairingToken '')) {
    throw 'Discovery-only signaling mode was rejected.'
}
if (-not (Test-ValidEvalSignalingMode `
        -UseDiscovery $false `
        -PairingToken 'runtime-pairing-token-123456')) {
    throw 'Explicit pairing-token signaling mode was rejected.'
}
if (Test-ValidEvalSignalingMode `
        -UseDiscovery $true `
        -PairingToken 'runtime-pairing-token-123456') {
    throw 'Ambiguous discovery plus pairing-token mode was accepted.'
}
if (Test-ValidEvalSignalingMode -UseDiscovery $false -PairingToken 'short') {
    throw 'A short explicit pairing token was accepted.'
}
if (-not (Test-LogContains -Lines @(
            'unrelated log line',
            'I EgoGlassWebRtc: video_bitrate_bps min=800000 start=3000000 max=6000000 degradation=balanced',
            'another unrelated log line'
        ) -Pattern 'video_bitrate_bps min=800000 start=3000000 max=6000000 degradation=balanced')) {
    throw 'A matching line inside a log array was not detected.'
}
if (Test-LogContains -Lines @('video_bitrate_bps min=800000 start=8000000 max=8000000 degradation=balanced') `
        -Pattern 'video_bitrate_bps min=800000 start=3000000 max=6000000 degradation=balanced') {
    throw 'A mismatched bitrate log was accepted.'
}
$hardwareEncoderLog = @(
    'HardwareVideoEncoder: Format: {color-format=19, width=640, bitrate-mode=2,',
    'bitrate=2000000, frame-rate=30.0, height=480}'
)
if (-not (Test-LogContains -Lines $hardwareEncoderLog `
        -Pattern '(?s)HardwareVideoEncoder: Format: .*width=640.*bitrate=[0-9]+.*height=480')) {
    throw 'A valid multiline hardware encoder configuration was not detected.'
}

Write-Output 'WebRTC device eval route regression test passed.'
