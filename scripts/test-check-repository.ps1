[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$fixtureDirectory = Join-Path $repositoryRoot 'app\build\repository-gate-fixture'
$fixtureFile = Join-Path $fixtureDirectory 'LegacyDemo.kt'
New-Item -ItemType Directory -Force -Path $fixtureDirectory | Out-Null
Set-Content -LiteralPath $fixtureFile -Value 'val legacyClient = "GlassSample"'

try {
    & (Join-Path $PSScriptRoot 'check-repository.ps1') | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw 'Repository gate rejected an ignored Gradle build fixture.'
    }
} finally {
    Remove-Item -LiteralPath $fixtureFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $fixtureDirectory -Force -ErrorAction SilentlyContinue
}

Write-Output 'Repository gate build-directory regression test passed.'
