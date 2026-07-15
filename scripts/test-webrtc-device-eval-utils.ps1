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

Write-Output 'WebRTC device eval route regression test passed.'
