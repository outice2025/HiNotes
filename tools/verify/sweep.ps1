# Full verification sweep for a delivered build: the JVM checks, the resource audit, the icon
# geometry check, and a look at the archived APK itself.
#
#   pwsh -File tools/verify/sweep.ps1
#
# Every section's exit code is collected rather than printed and forgotten: the sweep ends non-zero
# when any of them failed, and names them again at the end. The output is still worth reading in
# full - each section says what it checked, and a section that fails says why.
#
# What the sweep needs is looked up rather than demanded, so a clone can run it with little set up:
#
#   * Python 3 with numpy, Pillow and fontTools: HINOTES_PYTHON, else `python` on PATH (the Windows
#     Store alias is skipped - it is a stub that opens the Store), else `py -3`.
#   * An Android SDK: ANDROID_HOME, else ANDROID_SDK_ROOT, else the `sdk.dir` in local.properties,
#     which is how the build itself finds it. Each candidate is checked, because a machine can
#     carry an ANDROID_HOME left over from another install that is no SDK at all.
#   * A JDK 17+: JAVA_HOME or HINOTES_JAVA_HOME, else a `javac` on PATH, else a JDK under one of the
#     usual install roots (or Android Studio's own). Nothing here names a path on any one machine.
$ErrorActionPreference = 'Continue'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent

# ---------------------------------------------------------------------------- python
$python = $env:HINOTES_PYTHON
$pythonPrefix = @()
function Test-PythonCandidate($candidate) {
    $command = Get-Command $candidate -ErrorAction SilentlyContinue
    if (-not $command) { return $false }
    # The Store's `python.exe` is an alias that opens the Store rather than running anything.
    return $command.Source -notlike '*\WindowsApps\*'
}
if (-not $python -and (Test-PythonCandidate 'python')) { $python = 'python' }
if (-not $python -and (Get-Command 'py' -ErrorAction SilentlyContinue)) {
    $python = 'py'
    $pythonPrefix = @('-3')
}
if (-not $python) {
    Write-Host "no Python found - set HINOTES_PYTHON to one with numpy, Pillow and fontTools" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------- android sdk
# An SDK without build-tools cannot run the checks below, so every candidate is looked at before it
# is taken rather than the first non-empty one being trusted.
function Test-SdkCandidate($candidate) {
    if (-not $candidate) { return $false }
    return Test-Path (Join-Path $candidate 'build-tools')
}
$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) | Where-Object { Test-SdkCandidate $_ } |
    Select-Object -First 1
if (-not $sdk) {
    $properties = Join-Path $repo 'local.properties'
    if (Test-Path $properties) {
        $setting = Select-String -Path $properties -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
        if ($setting) {
            # A Java properties file escapes the colon in a Windows path.
            $fromFile = ($setting.Line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
            $fromFile = $fromFile -replace '\\:', ':' -replace '\\\\', '\'
            if (Test-SdkCandidate $fromFile) { $sdk = $fromFile }
        }
    }
}
if (-not $sdk) {
    Write-Host "no Android SDK found - set ANDROID_HOME, or sdk.dir in local.properties" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------------------- jdk
$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = $env:HINOTES_JAVA_HOME }
if (-not $javaHome -and (Get-Command 'javac' -ErrorAction SilentlyContinue)) {
    $javaHome = Split-Path (Split-Path (Get-Command 'javac').Source -Parent) -Parent
}
if (-not $javaHome) {
    $roots = @(
        "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
        '/usr/lib/jvm'
    )
    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $found = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($found) { $javaHome = $found.FullName; break }
    }
}
if (-not $javaHome) {
    # Android Studio ships the JDK it builds with, which is a 17+ one and is not under a Java root.
    $studios = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
    )
    foreach ($studio in $studios) {
        if (Test-Path (Join-Path $studio 'bin\javac.exe')) { $javaHome = $studio; break }
    }
}
if (-not $javaHome) {
    Write-Host "no JDK found - set JAVA_HOME to a JDK 17+" -ForegroundColor Red
    exit 1
}
$javaVersion = (& (Join-Path $javaHome 'bin\java.exe') -version 2>&1 | Select-Object -First 1)
if ($javaVersion -match 'version "(\d+)' -and [int]$Matches[1] -lt 17) {
    Write-Host "the JDK at $javaHome is Java $($Matches[1]) - set JAVA_HOME to a JDK 17+" -ForegroundColor Red
    exit 1
}
$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdk

# Failures are collected by name so the run can end with the list rather than with whatever the
# last section happened to print. A section whose command returned non-zero is a failed sweep: a
# check that fails has to fail the command that ran it, or a broken build can be signed off by a
# script that exited 0.
$failed = @()
function Record-Section($name, $code) {
    if ($code -ne 0) { $script:failed += $name }
}

Write-Host '=== checks ==='
& "$repo\tools\verify\run.ps1" 2>&1 | Select-String -Pattern 'FAIL|passed|failed'
Record-Section 'JVM checks' $LASTEXITCODE

Write-Host '=== migrations ==='
& $python @pythonPrefix "$repo\tools\verify\check_migrations.py" | Select-Object -Last 3
Record-Section 'database migrations' $LASTEXITCODE

Write-Host '=== resource audit ==='
& $python @pythonPrefix "$repo\tools\verify\audit_resources.py" | Select-Object -Last 5
Record-Section 'resource audit' $LASTEXITCODE

Write-Host '=== icon geometry ==='
& $python @pythonPrefix "$repo\tools\verify\check_icon_geometry.py" | Select-Object -Last 3
Record-Section 'icon geometry' $LASTEXITCODE

Write-Host '=== apk ==='
$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$apk = Get-ChildItem "$repo\releases" -Recurse -Filter 'HiNotes-*-release.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    Write-Host 'no archived release APK found - build one with :app:assembleRelease' -ForegroundColor Red
    Record-Section 'apk' 1
} else {
    Write-Host ("file: {0}" -f $apk.FullName)
    Write-Host ("size: {0} MB" -f [math]::Round($apk.Length / 1MB, 2))
    Write-Host ("sha256: {0}" -f (Get-FileHash $apk.FullName -Algorithm SHA256).Hash)

    & "$($buildTools.FullName)\aapt2.exe" dump badging $apk.FullName 2>&1 |
        Select-String -Pattern "^package|^minSdkVersion|^targetSdkVersion|uses-permission|launchable-activity"
    Record-Section 'apk manifest' $LASTEXITCODE

    & "$($buildTools.FullName)\apksigner.bat" verify --verbose $apk.FullName 2>&1 |
        Select-String -Pattern 'v2 scheme|v3 scheme'
    Record-Section 'apk signature' $LASTEXITCODE
}

Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host 'sweep: every section passed' -ForegroundColor Green
    exit 0
}
Write-Host ("sweep: {0} section(s) failed - {1}" -f $failed.Count, ($failed -join ', ')) -ForegroundColor Red
exit 1
