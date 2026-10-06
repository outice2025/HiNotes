# Full verification sweep for a delivered build: the JVM checks, the resource audit, the icon
# geometry check, and a look at the archived APK itself.
#
#   pwsh -File tools/verify/sweep.ps1
#
# The image checks need a Python 3 with numpy, Pillow and fontTools; point HINOTES_PYTHON at one,
# or have `python` on PATH. JAVA_HOME must name a JDK 17+, and ANDROID_HOME an SDK whose
# build-tools can inspect the APK.
$ErrorActionPreference = 'Continue'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$python = if ($env:HINOTES_PYTHON) { $env:HINOTES_PYTHON } else { 'python' }
if (-not (Get-Command $python -ErrorAction SilentlyContinue)) {
    Write-Host "python not found - set HINOTES_PYTHON to run the image checks" -ForegroundColor Red
    exit 1
}
if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME is not set - point it at a JDK 17+" -ForegroundColor Red
    exit 1
}
if (-not $env:ANDROID_HOME) {
    Write-Host "ANDROID_HOME is not set - point it at an Android SDK" -ForegroundColor Red
    exit 1
}

Write-Host '=== checks ==='
& "$repo\tools\verify\run.ps1" 2>&1 | Select-String -Pattern 'FAIL|passed|failed'

Write-Host '=== resource audit ==='
& $python "$repo\tools\verify\audit_resources.py" | Select-Object -Last 5

Write-Host '=== icon geometry ==='
& $python "$repo\tools\verify\check_icon_geometry.py" | Select-Object -Last 3

Write-Host '=== apk ==='
$buildTools = Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
    Sort-Object Name -Descending | Select-Object -First 1
$apk = Get-ChildItem "$repo\releases" -Recurse -Filter 'hinotes-*-release.apk' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ("file: {0}" -f $apk.FullName)
Write-Host ("size: {0} MB" -f [math]::Round($apk.Length / 1MB, 2))
Write-Host ("sha256: {0}" -f (Get-FileHash $apk.FullName -Algorithm SHA256).Hash)

& "$($buildTools.FullName)\aapt2.exe" dump badging $apk.FullName 2>&1 |
    Select-String -Pattern "^package|^minSdkVersion|^targetSdkVersion|uses-permission|launchable-activity"
& "$($buildTools.FullName)\apksigner.bat" verify --verbose $apk.FullName 2>&1 |
    Select-String -Pattern 'v2 scheme|v3 scheme'
