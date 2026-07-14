[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$wrapperJar = Join-Path $repositoryRoot 'gradle\wrapper\gradle-wrapper.jar'
$expectedSha256 = 'E996D452D2645E70C01C11143CA2D3742734A28DA2BF61F25C82BDC288C9E637'
$downloadUrl = 'https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradle/wrapper/gradle-wrapper.jar'

if (Test-Path -LiteralPath $wrapperJar) {
    $actualSha256 = (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash
    if ($actualSha256 -eq $expectedSha256) {
        Write-Output 'Gradle wrapper JAR is present and verified.'
        exit 0
    }
}

$temporaryJar = "$wrapperJar.download"
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $temporaryJar
    $actualSha256 = (Get-FileHash -LiteralPath $temporaryJar -Algorithm SHA256).Hash
    if ($actualSha256 -ne $expectedSha256) {
        throw "Gradle wrapper checksum mismatch. Expected $expectedSha256, got $actualSha256."
    }
    Move-Item -LiteralPath $temporaryJar -Destination $wrapperJar -Force
    Write-Output 'Gradle wrapper JAR downloaded and verified.'
} finally {
    if (Test-Path -LiteralPath $temporaryJar) {
        Remove-Item -LiteralPath $temporaryJar -Force
    }
}
