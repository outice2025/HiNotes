# HiNotes

[English](#english) · [简体中文](#简体中文)

---

## English

An open source, 100% FOSS note-taking app for Android, built with Jetpack Compose and Material 3
Expressive.

Local-first: notes live in SQLite on the device, settings in DataStore, and nothing is sent
anywhere. The app asks for no INTERNET permission — "Check for updates" only opens the project's
releases page in your browser.

### Features

**Notes**
- Create, edit and delete notes; search titles and bodies; sort by updated, created or title.
- A single-column list or a two-column staggered grid.
- Markdown rendering in the list and in the editor's read-only preview.
- Long-press to select several notes and delete them together; share a note as text or as a
  Markdown attachment.
- Notes can be marked as locked (a badge, stored with the note). It is a label rather than a
  second password — the app-wide password on the Unlock screen is what keeps others out.

**Editor**
- Markdown source editing with undo/redo, bold, italic, checkbox and list buttons, and find in
  note.
- Autosave every 20 s (opt-in) plus an explicit save. Leaving with unsaved changes prompts, and
  leaving still saves.
- A properties sheet with the note's length and its created / modified times.

**Appearance**
- Seven Material 3 accents — blue by default — each a full light and dark scheme.
- Wallpaper-derived dynamic colour on Android 12+, OLED dark, and app-wide font size and weight.

**Backup**
- Export settings as JSON and notes as a zip of Markdown files, both through the system share
  sheet; import either back through the file picker.
- Restore defaults, and export a diagnostics report (counts and environment only, never note
  text).

**Unlock**
- Password protection with PBKDF2-HMAC-SHA1, and face unlock through `androidx.biometric`.

**Language**
- English and Simplified Chinese, through Android's per-app language picker.

### Screens

Home · Editor · Settings (Appearance, Editor, Backup, Unlock) · About. Forward navigation slides in
from the right; every back — button, system back, gesture — plays the exact reverse.

### Building

JDK 17+ and an Android SDK with platform 37 and build-tools 36+.

```bash
./gradlew :app:assembleDebug      # debug
./gradlew :app:assembleRelease    # release; signed when keystore.properties exists
```

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/AndroidSDK
```

#### Release signing

`app/build.gradle.kts` reads `keystore.properties` from the project root (git-ignored):

```properties
storeFile=keystore/release.jks
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
├── MainActivity.kt      single activity, edge-to-edge, theme wiring
├── data/                notes, SQLite store, archive, settings, lock, biometrics
└── ui/                  navigation, view model, lock gate, components, screens, theme
```

`tools/` holds the generators for the app icon, the symbol vectors and the accent palettes;
`tools/verify/` holds the checks:

```bash
pwsh -File tools/verify/run.ps1          # archive round trip and Markdown verbs, on a JVM
python tools/verify/audit_resources.py   # string, symbol and drawable references
python tools/verify/check_icon_geometry.py
```

### Privacy

No analytics, no tracking, no accounts. Notes and settings stay on the device; the app makes no
network request of its own.

### Licence

Apache License 2.0 — see [LICENSE](LICENSE). The icon artwork is derived from Material Symbols
Rounded (Apache-2.0); no typeface is redistributed.

---

## 简体中文

一个开源且 100% FOSS 的 Android 笔记应用，使用 Jetpack Compose 与 Material 3 Expressive 构建。

本地优先：笔记存在设备的 SQLite 中，设置存在 DataStore 中，不会发送到任何地方。应用不申请
INTERNET 权限 ——「检查更新」只是在浏览器中打开项目的 releases 页面。

### 功能

**笔记**
- 新建、编辑、删除笔记；按标题与正文搜索；按最近修改、创建时间或标题排序。
- 首页支持单列列表或两列瀑布流网格。
- 开启 Markdown 后，列表与编辑器预览都按 Markdown 渲染。
- 长按进入多选，可批量删除；笔记可分享为文本或 Markdown 附件。
- 笔记可以标记为「锁定」（卡片显示锁形标记，随笔记保存）。这是标记而不是第二道密码 ——
  真正防止他人查看的是「解锁」页中的应用密码。

**编辑器**
- Markdown 源码编辑，支持撤销/重做、粗体、斜体、复选框、项目符号与笔记内查找。
- 每 20 秒自动保存（可选），也可显式保存。带着未保存的修改返回会提示，选择离开也会先保存。
- 属性面板显示笔记字数与创建、修改时间。

**外观**
- 七种 Material 3 强调色（默认蓝色），每种都是完整的浅色与深色方案。
- Android 12 及以上支持壁纸动态取色；另有 OLED 深色模式与应用级字体大小、字重。

**备份**
- 设置导出为 JSON，笔记导出为 Markdown 文件的 zip 包，都通过系统分享面板；两者都能用文件选择器
  导入回来。
- 可恢复默认设置，也可导出诊断报告（只含计数与环境信息，绝不含笔记正文）。

**解锁**
- 使用 PBKDF2-HMAC-SHA1 的密码保护，以及通过 `androidx.biometric` 的人脸解锁。

**语言**
- 英文与简体中文，使用 Android 原生的应用语言选择器。

### 屏幕

首页 · 编辑器 · 设置（外观、编辑器、备份、解锁）· 关于。前进导航从右侧滑入；所有返回 ——
按钮、系统返回键、手势 —— 都播放完全相同的反向动画。

### 构建

需要 JDK 17+，以及包含 platform 37 与 build-tools 36+ 的 Android SDK。

```bash
./gradlew :app:assembleDebug      # 调试版
./gradlew :app:assembleRelease    # 发布版；存在 keystore.properties 时自动签名
```

`local.properties` 必须指向你的 SDK：

```properties
sdk.dir=/path/to/AndroidSDK
```

#### Release 签名

`app/build.gradle.kts` 会读取项目根目录下的 `keystore.properties`（已被 gitignore）：

```properties
storeFile=keystore/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

缺少该文件时 release 变体会构建为未签名版本，而不是直接失败。每次发布还会把 APK 复制到
`build/` 之外的 `releases/v<版本号>/`，因此 `clean` 不会毁掉上一版交付物。

### 目录结构

```
app/src/main/java/com/hiapps/hinotes/
├── MainActivity.kt      单 Activity，边到边，主题装配
├── data/                笔记、SQLite 存储、归档、设置、密码存储、生物识别
└── ui/                  路由、ViewModel、锁屏、组件、屏幕、主题
```

`tools/` 存放应用图标、符号矢量图与强调色配色的生成脚本；`tools/verify/` 存放检查：

```bash
pwsh -File tools/verify/run.ps1          # 归档往返与 Markdown 动词，在 JVM 上执行
python tools/verify/audit_resources.py   # 字符串、图标、矢量图引用审计
python tools/verify/check_icon_geometry.py
```

### 隐私

无统计、无追踪、无账号。笔记与设置都留在设备上；应用不发起任何网络请求。

### 许可证

Apache License 2.0 —— 见 [LICENSE](LICENSE)。图标素材派生自 Material Symbols Rounded
（Apache-2.0）；不重新分发任何字体文件。
