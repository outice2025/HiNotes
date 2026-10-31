# Runs the JVM checks in this folder against the app's real source files.
#
# These are not unit tests inside the Gradle build: they compile a couple of pure-logic sources
# with the Kotlin compiler Gradle already has cached, plus the Compose text classes the editor
# depends on, and run them on the desktop JVM. That keeps the app free of a test dependency while
# still executing - rather than reasoning about - the logic that is easiest to get subtly wrong and
# hardest to see on a phone: the Markdown verbs behind the toolbar buttons, the notes archive behind
# export and import, and the handoff of a picked document behind backup and restore.
#
#   pwsh -File tools/verify/run.ps1
#
# Exits non-zero when any check fails.

$ErrorActionPreference = 'Stop'

$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$cache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
# Two runs started at the same time would delete and rewrite the same folders under each other,
# and the second one's result would be the one nobody meant to read. A caller that needs its own
# result sets VERIFY_WORK to a directory of its own; the shared folder stays the default so the
# command in the header keeps working as it is written there.
$work = if ($env:VERIFY_WORK) { $env:VERIFY_WORK } else { Join-Path $repo 'build\verify' }

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
    # The renderer leaves a task's box as inline content, so the foundation artifact that defines
    # that API has to be on the checks' classpath along with the text it is written into.
    (Unpack-Aar 'androidx.compose.foundation' 'foundation-android' 'foundation.aar'),
    # The theme check compares a scheme's roles, so it needs the artifact that defines them.
    (Unpack-Aar 'androidx.compose.material3' 'material3-android' 'material3.aar'),
    (Find-Jar 'androidx.annotation' 'annotation-jvm' 'annotation-jvm-*.jar')
) | Where-Object { $_ }
$libCp = (($libJars + $stdlib) -join ';')

$src = Join-Path $repo 'app\src\main\java\com\hiapps\hinotes'
$failures = 0

# The platform classes, for the checks that touch an Android type on the way past. They are the
# stubs, so every method body throws if it is called - the checks that use this compile against the
# types and read constants, never call them.
#
# A machine can carry an ANDROID_HOME left over from another install, so a candidate is checked for
# a platforms folder before it is trusted; `local.properties`, which is how the build itself finds
# the SDK, is the fallback.
function Get-AndroidSdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $properties = Join-Path $repo 'local.properties'
    if (Test-Path $properties) {
        $setting = Select-String -Path $properties -Pattern '^\s*sdk\.dir\s*=' |
            Select-Object -First 1
        if ($setting) {
            # A Java properties file escapes the colon in a Windows path.
            $fromFile = ($setting.Line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
            $candidates += ($fromFile -replace '\\:', ':' -replace '\\\\', '\')
        }
    }
    $candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'platforms')) } |
        Select-Object -First 1
}

$androidJar = $null
$sdk = Get-AndroidSdk
if ($sdk) {
    $platform = Get-ChildItem (Join-Path $sdk 'platforms') -Directory |
        Sort-Object Name -Descending | Select-Object -First 1
    $candidate = Join-Path $platform.FullName 'android.jar'
    if (Test-Path $candidate) { $androidJar = $candidate }
}

function Invoke-Check($name, $checkFile, $sources, $extraCp) {
    Write-Host ""
    Write-Host "=== $name ==="
    $out = Join-Path $work $name
    Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $out | Out-Null

    $cp = if ($extraCp) { "$libCp;$extraCp" } else { $libCp }
    $args = @('-cp', $compilerCp, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
        '-no-stdlib', '-jvm-target', '17', '-classpath', $cp, '-d', $out, $checkFile) + $sources
    # A check reads the app's own files by paths relative to the repository root (see
    # DocumentPickChecks), so both halves of it are started from there rather than from wherever the
    # caller happened to be standing.
    $p = Start-Process -FilePath $java -ArgumentList $args -Wait -NoNewWindow -PassThru `
        -WorkingDirectory $repo `
        -RedirectStandardOutput "$out\compile.out" -RedirectStandardError "$out\compile.err"
    if ($p.ExitCode -ne 0) {
        Write-Host "compile failed:" -ForegroundColor Red
        Get-Content "$out\compile.err" | Select-Object -Last 15
        $script:failures++
        return
    }

    $runArgs = @('-cp', "$out;$cp", "com.hiapps.hinotes.$name")
    $r = Start-Process -FilePath $java -ArgumentList $runArgs -Wait -NoNewWindow -PassThru `
        -WorkingDirectory $repo `
        -RedirectStandardOutput "$out\run.out" -RedirectStandardError "$out\run.err"
    Get-Content "$out\run.out"
    if ($r.ExitCode -ne 0) {
        Get-Content "$out\run.err" | Select-Object -Last 8
        $script:failures++
    }
}

Invoke-Check 'data.ArchiveChecks' (Join-Path $PSScriptRoot 'ArchiveChecks.kt') @(
    "$src\data\NotesArchive.kt", "$src\data\Note.kt",
    # The archive carries the pictures a note refers to, so this check reads the addresses out of a
    # note's text with the same code the export does - and that source names the platform's picture
    # types on the way past, which is why the stubs above come with it.
    "$src\data\NoteImages.kt"
) $androidJar

Invoke-Check 'ui.editor.MarkdownChecks' (Join-Path $PSScriptRoot 'MarkdownChecks.kt') @(
    "$src\ui\editor\Markdown.kt"
)

# Cutting a note's rendered text into the items a widget's list is built from is the one part of a
# widget's drawing that touches nothing but characters, so it is run here in the package it lives in.
# The platform's spanned text is what a chunk is cut out of when the body carries a picture, so that
# source names the platform's own types on the way past and the stubs above come with it.
Invoke-Check 'widget.WidgetTextChecks' (Join-Path $PSScriptRoot 'WidgetTextChecks.kt') @(
    "$src\widget\WidgetChunks.kt", "$src\ui\editor\Markdown.kt"
) $androidJar

Invoke-Check 'ui.theme.ThemeChecks' (Join-Path $PSScriptRoot 'ThemeChecks.kt') @(
    "$src\ui\theme\ColorMatch.kt", "$src\ui\theme\ColorTokens.kt",
    "$src\ui\theme\ColorSchemes.kt", "$src\ui\theme\AccentPalettes.kt",
    # A widget's card is painted from a scheme the app has adjusted, so the check reads both the
    # adjustment and what the card's colours come to.
    "$src\ui\theme\DarkSurfaces.kt"
)

Invoke-Check 'ui.screens.DocumentPickChecks' (Join-Path $PSScriptRoot 'DocumentPickChecks.kt') @(
    "$src\ui\screens\DocumentPick.kt"
) $androidJar

Invoke-Check 'ui.screens.EditorChecks' (Join-Path $PSScriptRoot 'EditorChecks.kt') @() $androidJar

# The app shell around the widget: the build the checks were revised for, the picker frame both
# choice lists share, the room a settings page keeps around its heading, the wallpaper signals a
# card follows, and the lifecycle triggers that repaint a card the process missed a change for.
Invoke-Check 'AppShellChecks' (Join-Path $PSScriptRoot 'AppShellChecks.kt') @()

Write-Host ""
if ($failures -eq 0) {
    Write-Host "verify: all checks passed" -ForegroundColor Green
    exit 0
}
Write-Host "verify: $failures check group(s) failed" -ForegroundColor Red
exit 1
