# scripts/build-signed-release.ps1
# Secure release build script for AutoFlow testers

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$releasesDir = [System.IO.Path]::GetFullPath((Join-Path $repoRoot "..\releases"))

if (-not (Test-Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir -Force | Out-Null
}

$targetApk = Join-Path $releasesDir "AutoFlow-0.1.0-test1.apk"
if (Test-Path $targetApk) {
    Write-Error "Destination file already exists: $targetApk. STOP: will not overwrite existing release."
    exit 1
}

$ksPath = "C:\Users\solop\Downloads\AutoFlow\keys\autoflow-release.jks"
if (-not (Test-Path $ksPath)) {
    Write-Error "Keystore not found at $ksPath. STOP."
    exit 1
}

$securePass = Read-Host -Prompt "Enter keystore password" -AsSecureString
$plainPass = [System.Net.NetworkCredential]::new("", $securePass).Password

$builtApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"

try {
    # Set session environment variables
    $env:AUTOFLOW_KS_PATH = $ksPath
    $env:AUTOFLOW_KS_PASS = $plainPass
    $env:AUTOFLOW_KEY_ALIAS = "autoflow"
    $env:AUTOFLOW_KEY_PASS = $plainPass

    Write-Host "Building signed release APK..."
    $gradlew = Join-Path $repoRoot "gradlew.bat"
    & $gradlew assembleRelease
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle assembleRelease failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }

    if (-not (Test-Path $builtApk)) {
        Write-Error "Expected build output not found at $builtApk"
        exit 1
    }

    Copy-Item -Path $builtApk -Destination $targetApk
    Write-Host "`nSigned APK successfully copied to: $targetApk"
}
finally {
    # Clear environment variables from current session
    Remove-Item Env:\AUTOFLOW_KS_PATH -ErrorAction SilentlyContinue
    Remove-Item Env:\AUTOFLOW_KS_PASS -ErrorAction SilentlyContinue
    Remove-Item Env:\AUTOFLOW_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:\AUTOFLOW_KEY_PASS -ErrorAction SilentlyContinue
    $plainPass = $null
    $securePass = $null
}

# Run apksigner verify if available
$apksigner = Get-Command apksigner.bat, apksigner -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if (-not $apksigner) {
    $sdkBuildTools = Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"
    if (Test-Path $sdkBuildTools) {
        $apksignerItem = Get-ChildItem -Path $sdkBuildTools -Filter "apksigner.bat" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($apksignerItem) {
            $apksigner = $apksignerItem.FullName
        }
    }
}

if ($apksigner) {
    Write-Host "`n--- Signature Verification ---"
    & $apksigner verify --verbose $targetApk
} else {
    Write-Warning "apksigner not found; skipping signature verification step."
}

$fileItem = Get-Item $targetApk
$sha256 = (Get-FileHash -Path $targetApk -Algorithm SHA256).Hash

Write-Host "`n============================================================"
Write-Host " AutoFlow Release Build Summary"
Write-Host "============================================================"
Write-Host "APK:     $($fileItem.FullName)"
Write-Host "Size:    $($fileItem.Length) bytes"
Write-Host "SHA-256: $sha256"
Write-Host "============================================================`n"
