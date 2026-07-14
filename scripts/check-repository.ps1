[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$candidateFiles = & git -C $repositoryRoot ls-files --cached --others --exclude-standard
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to list repository files.'
}

$forbiddenPatterns = @(
    '(^|/)local\.properties$',
    '\.(apk|aab|jks|keystore)$',
    '(^|/)(build|captures)/',
    '(^|/)gradle/wrapper/gradle-wrapper\.jar$'
)

$forbiddenFiles = @($candidateFiles | Where-Object {
    $path = $_ -replace '\\', '/'
    $forbiddenPatterns | Where-Object { $path -match $_ }
})
if ($forbiddenFiles.Count -gt 0) {
    throw "Forbidden generated or local files found:`n$($forbiddenFiles -join "`n")"
}

$sourceFiles = Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'app') -Recurse -File |
    Where-Object { $_.Extension -in @('.kt', '.java', '.xml', '.gradle') }
$legacyNames = @('com.rokid.glesse', 'com.rokid.glass.', 'GlassSample', 'glassdemo')
$legacyMatches = @($sourceFiles | Select-String -SimpleMatch -Pattern $legacyNames)
if ($legacyMatches.Count -gt 0) {
    throw "Legacy Demo identifiers remain:`n$($legacyMatches -join "`n")"
}

$scriptErrors = @()
Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1' -File | ForEach-Object {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile(
        $_.FullName,
        [ref]$tokens,
        [ref]$errors
    ) | Out-Null
    $scriptErrors += $errors
}
if ($scriptErrors.Count -gt 0) {
    throw "PowerShell syntax errors found:`n$($scriptErrors -join "`n")"
}

Write-Output "Repository gate passed for $($candidateFiles.Count) files."
