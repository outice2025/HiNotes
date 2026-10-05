# HiNotes

[English](#english) · [简体中文](#简体中文)

---

## English

An open source, 100% FOSS note-taking app for Android, built with Jetpack Compose and
Material 3 Expressive.

**Version 0.0.5** — opens in light mode by default, regardless of the device setting; "Follow
system" remains available on the Appearance screen.

**Version 0.0.4** — icon and button alignment corrected (the 0.0.3 baseline compensation was
itself the bug); screen transitions now match the platform's standard push/pop; backup export no
longer crashes; the insert-image feature was removed.

**Version 0.0.3** — type size and weight are now four-stop sliders (smallest / small / medium /
large) for both the app and note content; the app and editor use the system font, so the bundled
Roboto face is gone; launcher icon orientation fixed.

**Version 0.0.2** — bug-fix release: list-icon centring and group corner continuity, home search
field alignment, backup export crash, unified back animations, editor split-button placement,
OLED dark usable alongside dynamic colour, image insertion, new themed launcher icon.

### What it is

A local-first notes app. Notes live in SQLite on the device, settings live in DataStore, and
nothing is sent anywhere. The only network request the app makes on its own is the optional
"Check for updates" action on the About screen.

### Features

**Notes**
- Create, edit, and delete notes; everything persists across restarts.
- Search across titles and bodies from the home screen's search field, with an empty state when
  nothing matches.
- Sort by last updated, date created, or title.
- A note can be locked behind the app password.
- Share a note as text, or as a Markdown attachment when it is long.
- Long-press a note card to delete it, with a confirmation dialog.

**Editor**
- Markdown source editing with an optional rendered preview.
- Undo / redo with coalesced typing history.
- Bold, italic, checkbox, bullet list, and image insertion from a connected toolbar.
- Inserted images are copied into app storage, so they survive the picker's permission expiring.
- Find in note.
- Autosave every 20 s (opt-in) plus explicit save from the split button.
- A properties panel with created/updated timestamps, word, character and line counts.
- Unsaved changes are never lost silently: leaving prompts, and the choice to leave still saves.

**Appearance**
- Dynamic colour from the wallpaper on Android 12+, with a Mono fallback palette below that.
  OLED dark composes with it rather than replacing it.
- Light and dark schemes; the app opens in light mode, and "Follow system" on the Appearance
  screen hands the choice back to the device.
- OLED dark mode (true black surfaces).
- App-wide font size and weight, and separate size/weight for note content.
- Bundles Roboto and a subset of Material Symbols Rounded so rendering is identical everywhere.
- Themed (monochrome) launcher icon that follows the wallpaper's colour on Android 13+.

**Backup**
- Export and import settings, and export and import notes, as readable JSON through the system
  file picker. Imports are confirmed first and reject files that are not HiNotes backups.
- Restore all settings to defaults.
- Export a diagnostics report (counts and environment only, never note text).
- Unhandled exceptions are recorded locally so a crash can be reported from that same export.

**Unlock**
- Password protection with PBKDF2-HMAC-SHA1 (120,000 iterations, per-password random salt).
- Fingerprint and face unlock through `androidx.biometric`, each enabled only when the device
  actually reports that biometric class.

**Language**
- Opens Android's own per-app language picker. English and Simplified Chinese are included.

### Screens

| Screen | Route |
| --- | --- |
| Home | `home` |
| Editor | `editor/{noteId}` |
| Settings | `settings` |
| Settings – Appearance | `settings/appearance` |
| Settings – Editor | `settings/editor` |
| Settings – Backup | `settings/backup` |
| Settings – Unlock | `settings/unlock` |
| About | `about` |

Every forward navigation slides in from the right; every back — the toolbar button, the system
back button, and the predictive back gesture — plays the exact reverse through the same pop.

### Design notes

- **Colour roles only.** The UI never uses a literal colour. The Mono palette lives in
  `ui/theme/ColorTokens.kt` and is installed through `lightColorScheme` / `darkColorScheme`;
  the alternative is `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
- **Motion.** `ui/theme/Motion.kt` reproduces Material 3's *standard* motion scheme from its
  published tokens (damped springs, `dampingRatio` 0.9 spatial / 1.0 effects). Material 3 1.4.0
  keeps `MotionScheme.standard()` internal, so the tokens are re-declared rather than reached
  for. Screen transitions use a plain tween for the same reason: a spring on a full-screen slide
  reads as a settle.
- **Expressive components.** Material 3 1.4.0 ships design tokens for `SplitButton` and
  `ButtonGroup` but not the composables. `ui/components/ExpressiveComponents.kt` assembles them
  from standard M3 building blocks (`Surface`, `IconButton`, M3 shape and motion) at the
  specified sizes, rather than redrawing anything the library already provides.
- **Icons.** `res/font/material_symbols_rounded.ttf` is a 127 KB subset of Google's Material
  Symbols Rounded variable font, cut to the 44 glyphs the app uses and rendered outlined
  (FILL=0). Codepoints live in `ui/icons/Symbols.kt`. The launcher icon is generated from the
  same font's `edit` glyph, so the app icon and the in-app mark are one shape.

### Building

Requires JDK 17+ and an Android SDK with platform 37 and build-tools 36+.

```bash
# Debug
./gradlew :app:assembleDebug

# Release (needs signing material, see below)
./gradlew :app:assembleRelease
```

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/AndroidSDK
```

#### Release signing

`app/build.gradle.kts` reads `keystore.properties` from the project root. It is git-ignored, so
create your own:

```properties
storeFile=keystore/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release variant builds unsigned instead of failing.

#### Archived builds

`assembleRelease` also copies its APK to `releases/v<version>/`, outside `build/`. Gradle wipes
`build/` on `clean`, so without this a rebuild would destroy the previous deliverable; the
archive keeps every version side by side:

```
releases/
├── v0.0.3/hinotes-0.0.3-release.apk
└── v0.0.4/hinotes-0.0.4-release.apk
```

Rebuilding the same version overwrites that version's copy rather than piling up duplicates.
The task is `:app:archiveReleaseArtifacts` if you ever want to run it on its own. The folder is
git-ignored.

### Project layout

```
app/src/main/java/com/hinotes/app/
├── MainActivity.kt              single activity, edge-to-edge, theme wiring
├── data/                        Note, SQLite store, DataStore settings, lock store,
│                                backups, biometrics, image store, crash logger
├── update/                      release-feed check
└── ui/
    ├── HiNotesApp.kt            routes and transitions
    ├── AppViewModel.kt          single source of truth for the running app
    ├── LockGate.kt              app lock screen
    ├── components/              shared M3 Expressive pieces
    ├── editor/                  editor state (undo/redo) and Markdown
    ├── icons/                   bundled symbol font + codepoints
    ├── screens/                 the eight screens
    └── theme/                   colour roles, type, motion, shapes
```

### Privacy

No analytics, no tracking, no accounts. Notes and settings stay on the device. The update check
sends a plain `GET` for the latest release tag and nothing else; no note content is ever
transmitted.

### Licence

Apache License 2.0. Bundles Roboto and Material Symbols Rounded (both Apache-2.0).

---

## 简体中文

一个开源且 100% FOSS 的 Android 笔记应用，使用 Jetpack Compose 与 Material 3 Expressive 构建。

**版本 0.0.5** —— 默认以浅色模式打开，不再跟随设备设置；「跟随系统」仍保留在外观页。

**版本 0.0.4** —— 修正图标与按钮错位（0.0.3 里那次"基线补偿"本身就是问题根源）；页面切换改用
平台标准的前进/返回动画；备份导出不再崩溃；移除插入图片功能。

**版本 0.0.3** —— 字号与字重改为四档滑块（极小 / 小 / 中 / 大），应用与笔记内容各一套；
应用与编辑器改用系统字体，因此不再内置 Roboto；修复桌面图标方向。

**版本 0.0.2** —— 修复版本：列表图标居中与分组圆角连贯性、首页搜索框对齐、备份导出崩溃、
统一返回动画、编辑器拆分按钮位置、OLED 深色可与动态取色同时生效、插入图片、全新的主题图标。

### 这是什么

一个本地优先的笔记应用。笔记存在设备的 SQLite 中，设置存在 DataStore 中，不会发送到任何地方。
应用唯一主动发起的网络请求，是「关于」页里可选的「检查更新」。

### 功能

**笔记**
- 新建、编辑、删除笔记；所有数据重启后仍然保留。
- 在首页搜索框按标题与正文搜索，无结果时显示空状态。
- 按最近修改、创建时间或标题排序。
- 单条笔记可用应用密码锁定。
- 分享笔记为文本，内容较长时改为 Markdown 附件。
- 长按笔记卡片删除，并弹出确认对话框。

**编辑器**
- Markdown 源码编辑，可选渲染预览。
- 撤销 / 重做，连续输入会合并为一步。
- 相连工具栏提供粗体、斜体、复选框、项目符号与插入图片。
- 插入的图片会复制到应用私有目录，不会因为选择器授权过期而失效。
- 在笔记内查找。
- 每 20 秒自动保存（可选），也可用拆分按钮显式保存。
- 属性面板显示创建/修改时间、词数、字符数与行数。
- 未保存的修改不会被静默丢弃：返回时会提示，即使选择离开也会先保存。

**外观**
- Android 12 及以上从壁纸动态取色，更低版本使用 Mono 备用配色。OLED 深色与动态取色是叠加
  关系，而不是互相取代。
- 浅色与深色方案；应用默认以浅色打开，外观页的「跟随系统」可把选择权交还给设备。
- OLED 深色模式（纯黑表面）。
- 应用级字体大小与字重，笔记内容另有独立的大小与字重。
- 内置 Roboto 与裁剪版 Material Symbols Rounded，保证各设备渲染一致。
- Android 13 及以上支持主题图标（单色），跟随壁纸颜色。

**备份**
- 通过系统文件选择器以可读 JSON 导出/导入设置与笔记。导入前会确认，并拒绝非 HiNotes 的备份文件。
- 恢复所有设置为默认值。
- 导出诊断报告（只含计数与环境信息，绝不含笔记正文）。
- 未捕获异常会记录到本地，可通过同一个导出入口提交崩溃报告。

**解锁**
- 密码保护使用 PBKDF2-HMAC-SHA1（120,000 次迭代，每个密码独立随机盐）。
- 通过 `androidx.biometric` 实现指纹与人脸解锁；只有设备确实报告该生物识别等级时才可开启。

**语言**
- 打开 Android 原生的应用语言选择器，内置英文与简体中文。

### 屏幕

| 屏幕 | 路由 |
| --- | --- |
| 首页 | `home` |
| 编辑器 | `editor/{noteId}` |
| 设置页 | `settings` |
| 设置页 - 外观 | `settings/appearance` |
| 设置页 - 编辑器 | `settings/editor` |
| 设置页 - 备份 | `settings/backup` |
| 设置页 - 解锁 | `settings/unlock` |
| 关于 | `about` |

所有前进导航都从右侧滑入；所有返回 —— 左上角按钮、系统返回键、手势返回 —— 都走同一次出栈，
播放完全相同的反向动画。

### 设计说明

- **只使用颜色角色。** UI 中不出现字面颜色值。Mono 配色集中在 `ui/theme/ColorTokens.kt`，
  通过 `lightColorScheme` / `darkColorScheme` 安装；另一条路径是
  `dynamicLightColorScheme` / `dynamicDarkColorScheme`。
- **动效。** `ui/theme/Motion.kt` 按 Material 3 公布的设计令牌复刻了*标准*动效方案
  （阻尼弹簧，空间 0.9 / 效果 1.0）。Material 3 1.4.0 把 `MotionScheme.standard()` 标为
  `internal`，因此这里是重新声明令牌而不是调用它。屏幕过渡同样出于这个原因使用普通 tween：
  整屏滑动上用弹簧会显得有回弹感。
- **Expressive 组件。** Material 3 1.4.0 只提供了 `SplitButton` 与 `ButtonGroup` 的设计令牌，
  没有对应的 composable。`ui/components/ExpressiveComponents.kt` 用标准 M3 构件
  （`Surface`、`IconButton`、M3 形状与动效）按规格尺寸把它们组装出来，而不是重画库里已有的东西。
- **图标。** `res/font/material_symbols_rounded.ttf` 是 Google Material Symbols Rounded 可变
  字体的 127 KB 子集，裁剪到应用实际使用的 44 个字形，并以描边形态（FILL=0）渲染。码位定义在
  `ui/icons/Symbols.kt`。应用图标由同一字体的 `edit` 字形生成，因此桌面图标与应用内标识是同一个形状。

### 构建

需要 JDK 17+，以及包含 platform 37 与 build-tools 36+ 的 Android SDK。

```bash
# Debug
./gradlew :app:assembleDebug

# Release（需要签名材料，见下）
./gradlew :app:assembleRelease
```

`local.properties` 必须指向你的 SDK：

```properties
sdk.dir=/path/to/AndroidSDK
```

#### Release 签名

`app/build.gradle.kts` 会读取项目根目录下的 `keystore.properties`。该文件已被 gitignore，
请自行创建：

```properties
storeFile=keystore/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

缺少该文件时，release 变体会构建为未签名版本，而不是直接失败。

#### 构建归档

`assembleRelease` 会把 APK 额外复制到 `build/` 之外的 `releases/v<版本号>/`。Gradle 执行
`clean` 时会清空 `build/`，没有这一步的话重新构建就会毁掉上一版交付物；有了归档，每个版本
都会并排保留：

```
releases/
├── v0.0.3/hinotes-0.0.3-release.apk
└── v0.0.4/hinotes-0.0.4-release.apk
```

重复构建同一版本会覆盖该版本目录，而不会堆积重复文件。这个任务名为
`:app:archiveReleaseArtifacts`，需要时也可以单独执行。该目录已加入 gitignore。

### 项目结构

```
app/src/main/java/com/hinotes/app/
├── MainActivity.kt              单 Activity，边到边，主题装配
├── data/                        Note、SQLite 存储、DataStore 设置、密码存储、
│                                备份、生物识别、图片存储、崩溃记录
├── update/                      发布源更新检查
└── ui/
    ├── HiNotesApp.kt            路由与过渡动画
    ├── AppViewModel.kt          运行期应用的唯一数据源
    ├── LockGate.kt              应用锁屏
    ├── components/              共享的 M3 Expressive 组件
    ├── editor/                  编辑器状态（撤销/重做）与 Markdown
    ├── icons/                   内置符号字体与码位
    ├── screens/                 八个屏幕
    └── theme/                   颜色角色、排版、动效、形状
```

### 隐私

无统计、无追踪、无账号。笔记与设置都留在设备上。更新检查只发送一个获取最新发布标签的普通
`GET` 请求，除此之外没有别的；笔记内容永远不会被传输。

### 许可证

Apache License 2.0。内置 Roboto 与 Material Symbols Rounded（均为 Apache-2.0）。
