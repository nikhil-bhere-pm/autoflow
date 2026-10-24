# AutoFlow Verification Script
# Read-only verification of repository state, hygiene, tests, and security invariants.

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " AutoFlow Compact Verification Report" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$allPass = $true

# 1. GIT STATUS & RECENT COMMITS
Write-Host "`n--- [1] Working Tree & Recent Commits ---" -ForegroundColor Yellow
$gitStatus = git status --short
if ([string]::IsNullOrWhiteSpace($gitStatus)) {
    Write-Host "[PASS] Git working tree is clean" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Git working tree is dirty:" -ForegroundColor Red
    Write-Host $gitStatus
    $allPass = $false
}

Write-Host "`nLast 5 commits:"
git log -n 5 --oneline

# 2. FILES CHANGED IN HEAD
Write-Host "`n--- [2] Files Changed in HEAD ---" -ForegroundColor Yellow
git show --stat --oneline HEAD

# 3. FORBIDDEN TRACKED FILES
Write-Host "`n--- [3] Tracked Forbidden Files (*.db, keys, local.properties, etc.) ---" -ForegroundColor Yellow
$forbiddenPatterns = @(
    "*.db", "*.db-wal", "*.db-shm", "*.sqlite",
    "local.properties", "*.jks", "*.keystore", "*.sms",
    "ondevice*", "*ondevice*"
)
$trackedForbidden = @()
foreach ($pat in $forbiddenPatterns) {
    $matches = git ls-files $pat
    if ($matches) {
        $trackedForbidden += $matches
    }
}
$trackedForbidden = $trackedForbidden | Select-Object -Unique
if ($trackedForbidden.Count -eq 0) {
    Write-Host "[PASS] No tracked database, keystore, or local properties files" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Found tracked forbidden files:" -ForegroundColor Red
    $trackedForbidden | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    $allPass = $false
}

# 4. RUN GRADLEW TEST & PARSE XML REPORTS
Write-Host "`n--- [4] Unit Tests & XML Report Counts ---" -ForegroundColor Yellow
Write-Host "Running: .\gradlew.bat test ..."

$gradlew = Join-Path $repoRoot "gradlew.bat"
& $gradlew test
if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] gradlew test failed with exit code $LASTEXITCODE" -ForegroundColor Red
    $allPass = $false
}

$moduleDirs = [ordered]@{
    "parser-core" = Join-Path $repoRoot "parser-core/build/test-results/test"
    "app debug"   = Join-Path $repoRoot "app/build/test-results/testDebugUnitTest"
    "app release" = Join-Path $repoRoot "app/build/test-results/testReleaseUnitTest"
}

$totalTests = 0
$totalFailures = 0
$totalErrors = 0
$totalSkipped = 0

foreach ($entry in $moduleDirs.GetEnumerator()) {
    $modName = $entry.Key
    $dirPath = $entry.Value
    $mTests = 0
    $mFailures = 0
    $mErrors = 0
    $mSkipped = 0

    if (Test-Path $dirPath) {
        Get-ChildItem -Path $dirPath -Filter "TEST-*.xml" | ForEach-Object {
            try {
                [xml]$xml = Get-Content $_.FullName
                $mTests    += [int]$xml.testsuite.tests
                $mFailures += [int]$xml.testsuite.failures
                $mErrors   += [int]$xml.testsuite.errors
                $mSkipped  += [int]$xml.testsuite.skipped
            } catch {
                # ignore malformed xml
            }
        }
    }

    $totalTests += $mTests
    $totalFailures += $mFailures
    $totalErrors += $mErrors
    $totalSkipped += $mSkipped

    $statusStr = if ($mFailures -eq 0 -and $mErrors -eq 0 -and $mTests -gt 0) { "PASS" } else { "FAIL" }
    $color = if ($statusStr -eq "PASS") { "Green" } else { "Red" }
    Write-Host ("  [{0}] {1,-12}: tests={2,4}, failures={3,2}, errors={4,2}, skipped={5,2}" -f $statusStr, $modName, $mTests, $mFailures, $mErrors, $mSkipped) -ForegroundColor $color
}

$grandStatus = if ($totalFailures -eq 0 -and $totalErrors -eq 0 -and $totalTests -gt 0) { "PASS" } else { "FAIL" }
$grandColor = if ($grandStatus -eq "PASS") { "Green" } else { "Red" }
Write-Host ("  [{0}] {1,-12}: tests={2,4}, failures={3,2}, errors={4,2}, skipped={5,2}" -f $grandStatus, "TOTAL", $totalTests, $totalFailures, $totalErrors, $totalSkipped) -ForegroundColor $grandColor
if ($grandStatus -ne "PASS") {
    $allPass = $false
}

# 5. HARDCODED COLOR VALUES OUTSIDE THEME
Write-Host "`n--- [5] Hardcoded Color Values Outside Theme ---" -ForegroundColor Yellow
$themeFile = "app/src/main/java/com/autoflow/app/presentation/theme/Theme.kt"
$colorMatches = git grep -n -E "Color\(|0xFF" app/src/main/java ":!$themeFile"
if ([string]::IsNullOrWhiteSpace($colorMatches)) {
    Write-Host "[PASS] Zero hardcoded color values outside Theme.kt" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Found hardcoded color values outside Theme.kt:" -ForegroundColor Red
    $colorMatches | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    $allPass = $false
}

# 6. PERMISSIONS IN ANDROIDMANIFEST.XML
Write-Host "`n--- [6] Declared Android Permissions ---" -ForegroundColor Yellow
$manifestPath = Join-Path $repoRoot "app/src/main/AndroidManifest.xml"
[xml]$manifestXml = Get-Content $manifestPath
$permissions = @($manifestXml.SelectNodes("//uses-permission") | ForEach-Object { $_.GetAttribute("android:name") })

Write-Host "Declared permissions in manifest:"
$hasReadSms = $false
foreach ($perm in $permissions) {
    Write-Host "  - $perm"
    if ($perm -match "READ_SMS") {
        $hasReadSms = $true
    }
}

if ($hasReadSms) {
    Write-Host "[FAIL] Prohibited permission android.permission.READ_SMS declared in manifest" -ForegroundColor Red
    $allPass = $false
} else {
    Write-Host "[PASS] READ_SMS is not declared (RECEIVE_SMS only)" -ForegroundColor Green
}

# 7. LOG.* CALLS MENTIONING BODY, SMS OR MESSAGE TEXT
Write-Host "`n--- [7] Log Calls Auditing for SMS/Body Leakage ---" -ForegroundColor Yellow
$ktFiles = Get-ChildItem -Path (Join-Path $repoRoot "app/src/main/java") -Recurse -Filter "*.kt"
$leakingLogs = @()

foreach ($f in $ktFiles) {
    $lines = Get-Content $f.FullName
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match 'Log\.[vdiwe]\s*\(') {
            # Check if arguments mention body, sms variable/content, or message text
            if ($line -cmatch '\bbody\b|\bsms\b|\bmessage\s+text\b|\bfullBody\b|\bsmsBody\b|\$body|\$sms\b') {
                $leakingLogs += [PSCustomObject]@{
                    File = $f.FullName.Replace($repoRoot.Path, "").TrimStart("\/")
                    Line = $i + 1
                    Code = $line.Trim()
                }
            }
        }
    }
}

if ($leakingLogs.Count -eq 0) {
    Write-Host "[PASS] Zero Log.* calls logging message body, sms, or message text" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Found Log.* calls with sensitive arguments:" -ForegroundColor Red
    foreach ($m in $leakingLogs) {
        Write-Host "  - $($m.File):$($m.Line): $($m.Code)" -ForegroundColor Red
    }
    $allPass = $false
}

Write-Host "`n============================================================" -ForegroundColor Cyan
if ($allPass) {
    Write-Host " VERIFICATION RESULT: ALL CHECKS PASSED" -ForegroundColor Green
} else {
    Write-Host " VERIFICATION RESULT: ONE OR MORE CHECKS FAILED" -ForegroundColor Red
}
Write-Host "============================================================" -ForegroundColor Cyan

if (-not $allPass) {
    exit 1
}
