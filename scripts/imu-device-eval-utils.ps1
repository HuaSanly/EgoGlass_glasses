function Get-ImuLoopbackStatusUrl {
    param([int]$ClientPort)

    if ($ClientPort -lt 1 -or $ClientPort -gt 65535) {
        throw 'ClientPort must be between 1 and 65535.'
    }
    return "http://127.0.0.1:$ClientPort/api/v1/webrtc/imu/status"
}

function Test-TwoImuSensorsRegistered {
    param([string[]]$LogLines)

    $logText = $LogLines -join "`n"
    return $logText -match 'imu_state=started registered_sensors=2'
}
