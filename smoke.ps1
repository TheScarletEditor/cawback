# Build the smoke copy of Cawback, install it and open it, so /finalize can drive it over adb.
#
# Lyrics Smoke (com.thescarletcoder.lyrics.smoke) is release-optimized exactly like the public build (R8, not
# debuggable) but has its own app id and the debug key. It installs next to the regular Lyrics with its own database,
# settings and key, and can never see or change its data. The regular Cawback is never installed, changed or
# uninstalled by this. Remove the smoke copy when done.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1                  (the one connected device or emulator)
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1 -Serial XYZ      (pick one when several are connected)
#   powershell -ExecutionPolicy Bypass -File .\smoke.ps1 -Remove          (uninstall the smoke copy)

param([string]$Serial, [switch]$Remove)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

# The only package this script ever installs or removes.
$app = 'com.thescarletcoder.lyrics.smoke'

if (-not $Serial) {
    $serials = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
    if ($serials.Count -eq 0) { Write-Host "No device connected. Plug it in, unlock it, allow USB debugging, or start an emulator."; exit 1 }
    if ($serials.Count -gt 1) { Write-Host "More than one device connected. Run again with -Serial (one of: $($serials -join ', '))."; exit 1 }
    $Serial = $serials[0]
}

if ($Remove) {
    & $adb -s $Serial uninstall $app | Out-Null
    Write-Host "Lyrics Smoke removed from $Serial."
    exit 0
}

Write-Host "Building Lyrics Smoke..."
cmd /c ".\gradlew.bat :lyrics:assembleSmoke --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

Write-Host "Installing Lyrics Smoke on $Serial..."
& $adb -s $Serial install -r '.\lyrics\build\outputs\apk\smoke\lyrics-smoke.apk' | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }

& $adb -s $Serial shell monkey -p $app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "Lyrics Smoke ($app) is open on $Serial. Remove it with -Remove when done."
