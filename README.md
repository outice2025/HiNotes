<a id="user-content-hinotes"></a>

<div align="center">
    <img width="180" height="180" src="logo.png">
</div>

<div align="center">
    <h1>HiNotes</h1>
    <p>A simple, easy-to-use 100% FOSS note app</p>
</div>

English · [简体中文](README.zh-CN.md)

---

<details>
<summary><b>Screenshots</b></summary>

| Home | Notes | Settings | About |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/home-empty.png" width="190"> | <img src="screenshots/home-notes.png" width="190"> | <img src="screenshots/settings.png" width="190"> | <img src="screenshots/about.png" width="190"> |
| Empty state | List and grid | Settings | About |

</details>

---

A simple, easy-to-use 100% FOSS note-taking app for Android, built with Jetpack Compose and
Material 3 Expressive.

Local-first: notes and settings are kept on the device. The app asks for no INTERNET permission.

### Features

**Notes**
- Create, edit and delete notes.
- Search titles and bodies; sort by updated, created or title.
- A single-column list or a two-column staggered grid on the home screen.
- Write notes in Markdown.
- Show pictures.
- Long-press to select several notes and act on them together.
- Share a note as text or as a Markdown attachment.
- Lock and pin notes.

**Widget**
- A home screen widget showing one note, chosen for each widget as it is added.
- The title stays put and the note scrolls underneath it, without a scrollbar.
- Shows the pictures in a note, and draws its task lists as checkboxes.
- Its title can be hidden, and the note then fills the whole card.
- Drawn in the app's colour scheme, and follows the Markdown switch.
- Its own font size and weight.

**Editor**
- Markdown editing and preview.
- Find in note.
- The formatting toolbar.
- Autosave.
- A properties sheet with the note's information.

**Appearance**
- Material You colour schemes / dynamic colour.
- OLED dark.
- Font size and weight.

**Backup**
- Export and import settings and notes.
- Restore defaults.
- Export a diagnostics report.

**Privacy**
- No analytics, no tracking, no accounts.
- Screen protection inside the app.
- Password unlock for the app.
- Notes and settings stay on the device.
- The app makes no network request.

**Language**
- English and Simplified Chinese.

### Building

JDK 17+ and an Android SDK with platform 37 and the build-tools revision the Android Gradle Plugin
asks for.

```bash
./gradlew :app:assembleDebug      # debug
./gradlew :app:assembleRelease    # release; signed when keystore.properties exists
```

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/AndroidSDK
```

### Release signing

`app/build.gradle.kts` reads `keystore.properties` from the project root (git-ignored):

```properties
storeFile=keystore/hinotes-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release variant builds unsigned instead of failing. Each release is also
copied to `releases/v<version>/`, outside `build/`, so a `clean` cannot destroy the previous
deliverable.

### Layout

```
app/src/main/java/com/hiapps/hinotes/
├── MainActivity.kt      the app's activity: edge-to-edge, theme wiring
├── data/                notes, SQLite store, archive, settings, lock, biometrics
├── ui/                  navigation, view model, lock gate, components, screens, theme
└── widget/              the home screen widget: provider, collection service, picker
```

`widget/` also holds the app's second activity and its only exported entry point: the note picker
the launcher opens when a widget is placed.

`tools/` holds the generators for the app icon, the symbol vectors and the accent palettes, and the
renderers used to look at a glyph or a corner radius; `tools/verify/` holds the checks, with
`sweep.ps1` running all of them and then reporting on the archived APK:

```bash
pwsh -File tools/verify/sweep.ps1        # everything below, plus the APK's signature and hash
pwsh -File tools/verify/run.ps1          # archive, Markdown, widget text, theme and document-pick checks, on a JVM
python tools/verify/check_migrations.py  # every notes-database upgrade path, against SQLite
python tools/verify/audit_resources.py   # string, symbol and drawable references
python tools/verify/check_icon_geometry.py
```

### Licence

Apache License 2.0.
