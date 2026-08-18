function ConvertTo-SingleLineAdbOutput {
    param([string[]]$Lines)

    return (($Lines -join ' ') -replace '\s+', ' ').Trim()
}

function Test-DirectWlanRoute {
    param(
        [string]$Route,
        [string]$ClientHost
    )

    return $Route -match "^$([Regex]::Escape($ClientHost))\s+.*\bdev wlan0\b"
}

function Test-LogContains {
    param(
        [string[]]$Lines,
        [string]$Pattern
    )

    return ($Lines -join [Environment]::NewLine) -match $Pattern
}

function Get-LatestPublisherPairingStats {
    param([string[]]$Lines)

    $pattern = '(?s)frames_published=(\d+).*?metadata_sent=(\d+).*?metadata_pair_drops=(\d+)'
    $match = [Regex]::Matches(($Lines -join [Environment]::NewLine), $pattern) |
        Select-Object -Last 1
    if ($null -eq $match) {
        return $null
    }
    return [pscustomobject]@{
        FramesPublished = [long]$match.Groups[1].Value
        MetadataSent = [long]$match.Groups[2].Value
        MetadataPairDrops = [long]$match.Groups[3].Value
    }
}

function Test-ValidEvalSignalingMode {
    param(
        [bool]$UseDiscovery,
        [string]$PairingToken
    )

    if ($UseDiscovery) {
        return [string]::IsNullOrEmpty($PairingToken)
    }
    return -not [string]::IsNullOrEmpty($PairingToken) -and $PairingToken.Length -ge 16
}
