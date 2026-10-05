# Runs the JVM harnesses in this folder against the app's real source files.
#
# These are not unit tests inside the Gradle build: they compile a couple of pure-logic sources
# with the Kotlin compiler Gradle already has cached, plus the Compose text classes the editor
# depends on, and run them on the desktop JVM. That keeps the app free of a test dependency while
# still executing - rather than reasoning about - the two pieces of logic that are easiest to get
# subtly wrong and hardest to see on a phone: the Markdown verbs behind the toolbar buttons, and
# the notes archive behind export/import.
#
#   pwsh -File tools/verify/run.ps1
#
# Exits non-zero when any check fails.

$ErrorActionPreference = 'Stop'

$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
$work = Join-Path $repo 'build\verify'

if (-not (Test-Path $cache)) { throw "Gradle cache not found at $cache - build the app once first." }
New-Item -ItemType Directory -Force -Path $work | Out-Null

function Find-Jar($group, $artifact, $filter) {
    $dir = Join-Path $cache "$group\$artifact"
    if (-not (Test-Path $dir)) { return $null }
    $jar = Get-ChildItem $dir -Recurse -Filter $filter -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*sources*' } |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($jar) { $jar.FullName } else { $null }
}

# The compiler itself.
$compilerJars = @(
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable' 'kotlin-compiler-embeddable-*.jar'),
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib' 'kotlin-stdlib-2*.jar'),
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-reflect' 'kotlin-reflect-2*.jar'),
    (Find-Jar 'org.jetbrains.kotlin' 'kotlin-script-runtime' 'kotlin-script-runtime-*.jar'),
    (Find-Jar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm' '*.jar'),
    (Find-Jar 'org.jetbrains' 'annotations' 'annotations-2*.jar')
) | Where-Object { $_ }
$compilerCp = $compilerJars -join ';'
$stdlib = Find-Jar 'org.jetbrains.kotlin' 'kotlin-stdlib' 'kotlin-stdlib-2*.jar'
if (-not $stdlib) { throw 'Kotlin stdlib not found in the Gradle cache.' }

# Unpack the Compose Android artifacts so their classes.jar can sit on a plain JVM classpath.
function Unpack-Aar($group, $artifact, $filter) {
    $aar = Find-Jar $group $artifact $filter
    if (-not $aar) { Write-Host "  (missing $artifact - some checks will be skipped)"; return $null }
    $dest = Join-Path $work $artifact
    $zip = "$dest.zip"
    if (-not (Test-Path "$dest\classes.jar")) {
        Remove-Item -Recurse -Force $dest -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $dest | Out-Null
        Copy-Item $aar $zip -Force
        Expand-Archive -Path $zip -DestinationPath $dest -Force
    }
    "$dest\classes.jar"
}

$libJars = @(
    (Unpack-Aar 'androidx.compose.ui' 'ui-text-android' 'ui-text.aar'),
    (Unpack-Aar 'androidx.compose.ui' 'ui-unit-android' 'ui-unit.aar'),
    (Unpack-Aar 'androidx.compose.ui' 'ui-geometry-android' 'ui-geometry.aar'),
    (Unpack-Aar 'androidx.compose.ui' 'ui-util-android' 'ui-util.aar'),
    (Unpack-Aar 'androidx.compose.ui' 'ui-graphics-android' 'ui-graphics.aar'),
    (Unpack-Aar 'androidx.compose.runtime' 'runtime-android' 'runtime.aar'),
    (Unpack-Aar 'androidx.compose.runtime' 'runtime-saveable-android' 'runtime-saveable.aar'),
    (Unpack-Aar 'androidx.compose.runtime' 'runtime-annotation-android' 'runtime-annotation.aar'),
    (Find-Jar 'androidx.annotation' 'annotation-jvm' 'annotation-jvm-*.jar')
) | Where-Object { $_ }
$libCp = (($libJars + $stdlib) -join ';')

$src = Join-Path $repo 'app\src\main\java\com\hiapps\hinotes'
$failures = 0

function Invoke-Harness($name, $harnessFile, $sources) {
    Write-Host ""
    Write-Host "=== $name ==="
    $out = Join-Path $work $name
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    $args = @('-cp', $compilerCp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-jvm-target', '17', '-classpath', $libCp, '-d', $out, $harnessFile) + $sources
    $p = Start-Process -FilePath $java -ArgumentList $args -Wait -NoNewWindow -PassThru `
        -RedirectStandardOutput "$out\compile.out" -RedirectStandardError "$out\compile.err"
    if ($p.ExitCode -ne 0) {
        Write-Host "compile failed:" -ForegroundColor Red
        Get-Content "$out\compile.err" | Select-Object -Last 15
        $script:failures++
        return
    }

    $runArgs = @('-cp', "$out;$libCp", "com.hiapps.hinotes.$name")
    $r = Start-Process -FilePath $java -ArgumentList $runArgs -Wait -NoNewWindow -PassThru `
        -RedirectStandardOutput "$out\run.out" -RedirectStandardError "$out\run.err"
    Get-Content "$out\run.out"
    if ($r.ExitCode -ne 0) {
        Get-Content "$out\run.err" | Select-Object -Last 8
        $script:failures++
    }
}

Invoke-Harness 'data.ArchiveHarness' (Join-Path $PSScriptRoot 'ArchiveHarness.kt') @(
    "$src\data\NotesArchive.kt", "$src\data\Note.kt"
)

Invoke-Harness 'ui.editor.MarkdownHarness' (Join-Path $PSScriptRoot 'MarkdownHarness.kt') @(
    "$src\ui\editor\Markdown.kt"
)

Write-Host ""
if ($failures -eq 0) {
    Write-Host "verify: all harnesses passed" -ForegroundColor Green
    exit 0
}
Write-Host "verify: $failures harness(es) failed" -ForegroundColor Red
exit 1
