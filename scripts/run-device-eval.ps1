[CmdletBinding()]
param(
    [string]$Adb = 'adb',
    [string]$ApkPath = 'app\build\outputs\apk\debug\app-debug.apk'
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedApk = Join-Path $repositoryRoot $ApkPath
if (-not (Test-Path -LiteralPath $resolvedApk)) {
    throw "Debug APK not found: $resolvedApk"
}

$devices = @(& $Adb devices | Select-String '\tdevice$')
if ($devices.Count -ne 1) {
    throw "GLASS-EVAL-SDK-001 requires exactly one connected ADB device; found $($devices.Count)."
}

$model = (& $Adb shell getprop ro.product.model).Trim()
$fingerprint = (& $Adb shell getprop ro.build.fingerprint).Trim()
$displaySize = (& $Adb shell wm size | Select-String 'Physical size').ToString().Trim()
if ($model -ne 'RG-glasses') {
    throw "Expected Rokid Glass3 model RG-glasses, found '$model'."
}

& $Adb install -r $resolvedApk | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw 'APK installation failed.'
}

& $Adb shell input keyevent KEYCODE_WAKEUP
foreach ($attempt in 1..3) {
    & $Adb logcat -c
    & $Adb shell am force-stop com.egoglass.glasses
    & $Adb shell am start -W -n com.egoglass.glasses/.MainActivity | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Application launch $attempt failed."
    }

    Start-Sleep -Seconds 5
    $logs = & $Adb logcat -d -s 'EgoGlassSdk:I' '*:S'
    $connecting = $logs | Select-String -SimpleMatch 'sdk_state=CONNECTING'
    $ready = $logs | Select-String -SimpleMatch 'sdk_state=READY'
    if (-not $connecting -or -not $ready) {
        $logs | Out-Host
        throw "GLASS-EVAL-SDK-001 failed on launch ${attempt}: SDK did not complete CONNECTING -> READY."
    }
    Write-Output "Cold launch $attempt/3 reached READY."
}

$remoteUiDump = '/sdcard/egoglass-sdk-readiness.xml'
& $Adb shell uiautomator dump $remoteUiDump | Out-Host
$uiDump = & $Adb exec-out cat $remoteUiDump
if ($uiDump -notmatch 'text="SYSTEM READY"' -or
    $uiDump -notmatch 'text="EGOGLASS / GLASSES"') {
    throw 'GLASS-EVAL-SDK-001 failed: the ready UI was not visible.'
}

$evalOutput = Join-Path $repositoryRoot 'app\build\evals'
New-Item -ItemType Directory -Force -Path $evalOutput | Out-Null
$remoteScreenshot = '/sdcard/egoglass-sdk-readiness.png'
$localScreenshot = Join-Path $evalOutput 'sdk-readiness.png'
& $Adb shell screencap -p $remoteScreenshot
& $Adb pull $remoteScreenshot $localScreenshot | Out-Host

Write-Output 'GLASS-EVAL-SDK-001 PASSED'
Write-Output "Model: $model"
Write-Output "Display: $displaySize"
Write-Output "Firmware: $fingerprint"
Write-Output 'Observed: 3/3 cold launches completed CONNECTING -> READY'
Write-Output 'Visible UI: EGOGLASS / GLASSES, SYSTEM READY'
Write-Output "Screenshot: $localScreenshot"
