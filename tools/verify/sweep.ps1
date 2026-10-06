# Full verification sweep for a delivered build.
$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'D:\tools\jdk'
$repo = 'D:\HiNotes'
$python = 'C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'

Write-Host '=== harnesses ==='
& "$repo\tools\verify\run.ps1" 2>&1 | Select-String -Pattern 'FAIL|passed|failed'

Write-Host '=== resource audit ==='
& $python "$repo\tools\verify\audit_resources.py" | Select-Object -Last 5

Write-Host '=== icon geometry ==='
& $python "$repo\tools\verify\check_icon_geometry.py" | Select-Object -Last 3

Write-Host '=== apk ==='
$buildTools = Get-ChildItem 'D:\AndroidSDK\build-tools' -Directory | Sort-Object Name -Descending | Select-Object -First 1
$apk = Get-ChildItem "$repo\releases" -Recurse -Filter 'hinotes-*-release.apk' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ("file: {0}" -f $apk.FullName)
Write-Host ("size: {0} MB" -f [math]::Round($apk.Length / 1MB, 2))
Write-Host ("sha256: {0}" -f (Get-FileHash $apk.FullName -Algorithm SHA256).Hash)

& "$($buildTools.FullName)\aapt2.exe" dump badging $apk.FullName 2>&1 |
    Select-String -Pattern "^package|^minSdkVersion|^targetSdkVersion|uses-permission|launchable-activity"
& "$($buildTools.FullName)\apksigner.bat" verify --verbose $apk.FullName 2>&1 |
    Select-String -Pattern 'v2 scheme|v3 scheme'
