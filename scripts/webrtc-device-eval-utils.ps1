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
