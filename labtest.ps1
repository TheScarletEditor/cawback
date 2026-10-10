# Run the on-device tests on a real device, against Lyrics Lab, the test copy.
#
# Lyrics Lab (com.thescarletcoder.lyrics.labtest) is a separate app: its own database, settings and key. The tests
# wipe and rewrite ITS Library, never Sam's. It has no network, no launcher icon and no notification access. It is
# uninstalled again when the run ends. Sam's Scarlet Lyrics is never installed, changed or uninstalled by this.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\labtest.ps1                 (the one connected device, every on-device test)
#   powershell -ExecutionPolicy Bypass -File .\labtest.ps1 -Serial XYZ     (pick one when several are plugged in)
#   powershell -ExecutionPolicy Bypass -File .\labtest.ps1 -Class LibraryDbTest

param([string]$Serial, [string]$Class)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }

# The only packages this script ever installs or removes.
$app = 'com.thescarletcoder.lyrics.labtest'
$tests = 'com.thescarletcoder.lyrics.labtest.test'

if (-not $Serial) {
    $serials = @(& $adb devices | Select-String '	device$' | ForEach-Object { ($_ -split '	')[0] })
    if ($serials.Count -eq 0) { Write-Host "No device connected. Plug it in, unlock it, allow USB debugging."; exit 1 }
    if ($serials.Count -gt 1) { Write-Host "More than one device connected. Run again with -Serial (one of: $($serials -join ', '))."; exit 1 }
    $Serial = $serials[0]
}

Write-Host "Building Lyrics Lab and its tests..."
cmd /c ".\gradlew.bat :lyrics:assembleLabtest :lyrics:assembleLabtestAndroidTest --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

Write-Host "Installing Lyrics Lab on $Serial..."
& $adb -s $Serial install -r -t '.\lyrics\build\outputs\apk\labtest\lyrics-labtest.apk' | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }
& $adb -s $Serial install -r -t '.\lyrics\build\outputs\apk\androidTest\labtest\lyrics-labtest-androidTest.apk' | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "Install failed."; exit 1 }

$run = @('-s', $Serial, 'shell', 'am', 'instrument', '-w')
if ($Class) { $run += @('-e', 'class', "com.thescarletcoder.lyrics.$Class") }
$run += "$tests/androidx.test.runner.AndroidJUnitRunner"
$out = (& $adb @run | Out-String)
Write-Host $out.Trim()

Write-Host "Removing Lyrics Lab..."
& $adb -s $Serial uninstall $tests | Out-Null
& $adb -s $Serial uninstall $app | Out-Null

if ($out -match 'OK \(\d+ tests?\)') { Write-Host "Passed."; exit 0 } else { Write-Host "FAILED."; exit 1 }
