# Build the release APK and sign it with The Scarlet Coder's app key.
# apksigner asks for the key password; type it here. It is never stored or passed on the command line.
#
# Run:
#   powershell -ExecutionPolicy Bypass -File .\release.ps1
#
# Output: dist\scarlet-lyrics-<version>.apk and its .sha256, ready for a GitHub release.

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat"
$keystore = "$env:USERPROFILE\scarlet-apps.keystore"
$alias = 'scarletapps'
if (-not (Test-Path $keystore)) { Write-Host "Signing key not found at $keystore."; exit 1 }

$version = [regex]::Match((Get-Content .\lyrics\build.gradle.kts -Raw), 'versionName = "([^"]+)"').Groups[1].Value
Write-Host "Building Scarlet Lyrics $version (release)..."
cmd /c ".\gradlew.bat :lyrics:assembleRelease --console=plain -q"
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed."; exit 1 }

New-Item -ItemType Directory -Force dist | Out-Null
$out = ".\dist\scarlet-lyrics-$version.apk"
Write-Host "Signing. Type the key password when asked."
& $apksigner sign --ks $keystore --ks-key-alias $alias --out $out ".\lyrics\build\outputs\apk\release\lyrics-release-unsigned.apk"
if ($LASTEXITCODE -ne 0) { Write-Host "Signing failed."; exit 1 }
Remove-Item "$out.idsig" -ErrorAction SilentlyContinue

& $apksigner verify --print-certs $out | Select-String 'SHA-256'
if ($LASTEXITCODE -ne 0) { Write-Host "Signature check failed."; exit 1 }
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
"$hash  scarlet-lyrics-$version.apk" | Set-Content -Encoding ascii "$out.sha256"
Write-Host "Done: $out"
Write-Host "SHA-256: $hash"
