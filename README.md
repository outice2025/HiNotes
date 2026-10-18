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

Local-first: notes live in SQLite on the device, settings in DataStore. The app asks for no
INTERNET permission.

### Features

**Notes**
- Create, edit and delete notes.
- Search titles and bodies; sort by updated, created or title.
- A single-column list or a two-column staggered grid on the home screen.
- Write notes in Markdown: headings, ordered and nested lists, task lists, quotes, tables, formulas,
  code on a rounded background, links that open in the browser, emphasis, strikethrough, comments
  and escapes.
- Insert a picture from the gallery: the preview and the widget draw it, while the note itself holds
  the address it is kept at, which is what the list cards show.
- Long-press to select several notes and delete them together, or export just those.
- Share a note as text or as a Markdown attachment.
- A note can be locked to keep it from being edited: it is then shown the way a preview is shown,
  the formatting toolbar is dropped and the mode switch is greyed out, while the note can still be
  selected and deleted.
- Pin a note to keep it in a run of its own above the rest, under a heading of its own, whatever the
  sort order is.

**Widget**
- A home screen widget showing one note, chosen for each widget as it is added.
- The title stays put and the note scrolls underneath it, without a scrollbar.
- Shows the pictures in a note, and draws its task lists as checkboxes.
- Its title can be hidden, and the note then fills the whole card.
- Drawn in the app's colour scheme, and follows the Markdown switch.
- Its own font size and weight.
- A card whose note has been deleted is drawn empty, and a press on it does nothing.

**Editor**
- Markdown source editing, and find in note: enter jumps to the next match and marks it, wrapping
  round at the end.
- The formatting toolbar is drawn while a note is being written; find in note sits beside Save,
  above it.
- Preview a note as Markdown.
- Autosave.
- A properties sheet with the note's length and its created / modified times.

**Appearance**
- Seven Material 3 colour schemes.
- Wallpaper-derived dynamic colour.
- OLED dark, and app-wide font size - 0.8x to 1.5x - and weight.

**Backup**
- Export and import settings files and note files - notes leave as `.md` files while Markdown is
  on and as `.txt` files while it is off, together with the pictures they hold.
- Restore defaults.
- Export a diagnostics report.

**Privacy**
- Screenshots, screen recording and casting can be blocked inside the app.
- Password protection with PBKDF2-HMAC-SHA1.

**Language**
- English and Simplified Chinese, chosen inside the app.

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

### Privacy

- No analytics, no tracking, no accounts.
- Notes and settings stay on the device.
- The app makes no network request of its own.

### Licence

Apache License 2.0.
