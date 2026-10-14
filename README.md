<a id="user-content-hinotes"></a>

<div align="center">
    <img width="180" height="180" src="logo.png">
</div>

<div align="center">
    <h1>HiNotes</h1>
    <p>简单易用的 100% FOSS 笔记应用</p>
    <p>A simple, easy-to-use 100% FOSS note app</p>
    <p><a href="#user-content-english">English</a> · <a href="#user-content-简体中文">简体中文</a></p>
</div>

---

<details>
<summary><b>Screenshots</b> · 截图</summary>

| Home | Notes | Settings | About |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/home-empty.png" width="190"> | <img src="screenshots/home-notes.png" width="190"> | <img src="screenshots/settings.png" width="190"> | <img src="screenshots/about.png" width="190"> |
| Empty state · 空状态 | List and grid · 列表与网格 | Settings · 设置 | About · 关于 |

</details>

---

## English

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
  code, emphasis, strikethrough, comments and escapes.
- Long-press to select several notes and delete them together, or export just those.
- Share a note as text or as a Markdown attachment.
- A note can be locked to keep it from being edited: it is then shown the way a preview is shown,
  the formatting toolbar is dropped and the mode switch is greyed out, while the note can still be
  selected and deleted.
- Pin a note to keep it in a run of its own above the rest, above a rule, whatever the sort order is.

**Widget**
- A home screen widget showing one note, chosen for each widget as it is added.
- The title stays put and the note scrolls underneath it, without a scrollbar.
- Drawn in the app's colour scheme, and follows the Markdown switch.
- Its own font size and weight.
- A card whose note has been deleted is drawn empty, and a press on it does nothing.

**Editor**
- Markdown source editing, and find in note: enter jumps to the next match and marks it, wrapping
  round at the end.
- Preview a note as Markdown.
- Autosave.
- A properties sheet with the note's length and its created / modified times.

**Appearance**
- Seven Material 3 colour schemes.
- Wallpaper-derived dynamic colour.
- OLED dark, and app-wide font size and weight.

**Backup**
- Export and import settings files and note files - notes leave as `.md` files while Markdown is
  on and as `.txt` files while it is off.
- Restore defaults.
- Export a diagnostics report.

**Privacy**
- Screenshots, screen recording and casting can be blocked inside the app.
- Password protection with PBKDF2-HMAC-SHA1.

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
pwsh -File tools/verify/run.ps1          # archive, Markdown, theme and document-pick checks, on a JVM
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

---

## 简体中文

简单易用的 100% FOSS Android 笔记应用，使用 Jetpack Compose 与 Material 3 Expressive 构建。

本地优先：笔记存在设备的 SQLite 中，设置存在 DataStore 中。应用不申请 INTERNET 权限。

### 功能

**笔记**
- 新建、编辑、删除笔记。
- 按标题与正文搜索；按最近修改、创建时间或标题排序。
- 首页支持单列列表或两列瀑布流网格。
- 支持以 Markdown 格式书写笔记：标题、有序与嵌套列表、任务列表、引用、表格、公式、代码、强调、删除线、注释与转义。
- 长按进入多选，可批量删除，也可只导出所选笔记。
- 笔记可分享为文本或 Markdown 附件。
- 可锁定笔记：锁定后以浏览模式显示、收起格式工具栏、编辑/浏览按钮变灰，笔记仍可被选中和删除。
- 可置顶笔记：置顶的笔记单独排在最前，与其余笔记之间以一条分割线相隔。

**微件**
- 桌面微件显示一篇笔记，添加时为每个微件单独选择笔记。
- 标题固定，正文在下方滚动，不显示滚动条。
- 配色跟随应用配色方案，并跟随 Markdown 开关。
- 可单独设置微件的字号与字重。
- 微件所显示的笔记被删除后，卡片显示为空，点击它不会有任何动作。

**编辑器**
- 支持 Markdown 源码编辑；笔记内查找可按回车逐处定位并高亮，走到末尾后回到第一处。
- 支持以 Markdown 格式预览笔记。
- 支持自动保存笔记。
- 属性面板显示笔记字数与创建、修改时间。

**外观**
- 包含 7 种 Material 3 配色方案。
- 支持壁纸动态取色。
- 支持 OLED 深色模式与应用级字体大小、字重。

**备份**
- 可导出/导入设置文件、笔记文件；笔记在 Markdown 开启时导出为 `.md`，关闭时导出为 `.txt`。
- 可恢复默认设置。
- 可导出诊断报告。

**隐私**
- 可禁止应用内截图、录屏与投屏。
- 使用 PBKDF2-HMAC-SHA1 的密码保护。

**语言**
- 支持英文与简体中文。

### 构建

需要 JDK 17+，以及包含 platform 37 的 Android SDK（build-tools 版本由 Android Gradle Plugin 决定）。

```bash
./gradlew :app:assembleDebug      # 调试版
./gradlew :app:assembleRelease    # 发布版；存在 keystore.properties 时自动签名
```

`local.properties` 必须指向你的 SDK：

```properties
sdk.dir=/path/to/AndroidSDK
```

### Release 签名

`app/build.gradle.kts` 会读取项目根目录下的 `keystore.properties`（已被 gitignore）：

```properties
storeFile=keystore/hinotes-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

缺少该文件时 release 变体会构建为未签名版本，而不是直接失败。每次发布还会把 APK 复制到
`build/` 之外的 `releases/v<版本号>/`，因此 `clean` 不会毁掉上一版交付物。

### 目录结构

```
app/src/main/java/com/hiapps/hinotes/
├── MainActivity.kt      应用主 Activity：边到边、主题装配
├── data/                笔记、SQLite 存储、归档、设置、密码存储、生物识别
├── ui/                  路由、ViewModel、锁屏、组件、屏幕、主题
└── widget/              桌面微件：提供者、集合服务、选择器
```

`widget/` 里还有应用的第二个 Activity，也是唯一导出的入口：添加微件时由启动器打开的笔记选择器。

`tools/` 存放应用图标、符号矢量图与强调色配色的生成脚本，以及用来看字形、圆角半径的渲染脚本；
`tools/verify/` 存放各项检查，`sweep.ps1` 会依次运行全部检查并报告归档的 APK：

```bash
pwsh -File tools/verify/sweep.ps1        # 下列全部检查，外加 APK 的签名与哈希
pwsh -File tools/verify/run.ps1          # 归档、Markdown、主题与文档选择的检查，在 JVM 上执行
python tools/verify/check_migrations.py  # 每一条笔记数据库升级路径，用 SQLite 验证
python tools/verify/audit_resources.py   # 字符串、图标、矢量图引用审计
python tools/verify/check_icon_geometry.py
```

### 隐私

- 无统计、无追踪、无账号。
- 笔记与设置都留在设备上。
- 应用不发起任何网络请求。

### 许可证

本项目采用 Apache License 2.0 。
