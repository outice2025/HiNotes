# HiNotes

[English](#english) · [简体中文](#简体中文)

---

## English

An open source, 100% FOSS note-taking app for Android, built with Jetpack Compose and
Material 3 Expressive.

### What it is

A local-first notes app. Notes live in SQLite on the device, settings live in DataStore, and
nothing is sent anywhere. The app makes no network request of its own and asks for no INTERNET
permission: "Check for updates" on the About screen opens the project's releases page in your
browser, which is the browser's request rather than the app's.

### Features

**Notes**
- Create, edit, and delete notes; everything persists across restarts.
- Search across titles and bodies from the home screen's search field, with an empty state when
  nothing matches.
- Sort by last updated, date created, or title.
- Two home arrangements: a single-column list, or a two-column staggered grid where every card is
  exactly as tall as its own content.
- Note bodies are rendered as Markdown in the list and in the editor's preview when Markdown is
  enabled.
- Long-press a note to start a multi-select: select all, delete the selection, or leave the mode.
- A note can be marked as locked: it carries a lock badge and the mark is stored with the note.
  Note-level locking is a label rather than a second password — the app-wide password on the
  Unlock settings screen is what actually keeps other people out.
- Share a note as text, or as a Markdown attachment when it is long.

**Editor**
- Markdown source editing with a rendered preview. Preview is read-only: the title and the note
  are locked, the formatting toolbar steps aside, and the note is drawn as Markdown.
- Undo / redo with coalesced typing history.
- Bold, italic, checkbox, and bullet-list insertion from a connected toolbar.
- Find in note.
- Autosave every 20 s (opt-in) plus explicit save.
- A properties sheet with the note's text length (title and punctuation excluded) and its
  created and last-modified times.
- Unsaved changes are never lost silently: leaving prompts, and the choice to leave still saves.

**Appearance**
- Seven Material 3 accent palettes — Blue (the default), Tea green, Light gray, Red,
  Light purple, Orange and Yellow — each a full light and dark scheme built from a seed colour
  with the Material 3 HCT algorithm, so every role carries the tone MD3 prescribes.
- Dynamic colour from the wallpaper on Android 12+, taking precedence while it is switched on.
- Light and dark schemes; the app opens in light mode, and "Follow system" on the Appearance
  screen hands the choice back to the device.
- OLED dark mode (true black surfaces), composable with either colour source.
- App-wide font size and weight, and separate size/weight for note content.
- A themed (monochrome) launcher icon that follows the wallpaper's colour on Android 13+.

**Backup**
- Export settings as readable JSON, and export notes as a zip of Markdown files: unzip it and
  every note is a document any editor can open. Both are written to app storage and handed to the
  system share sheet — the same route the diagnostics export takes — so "Save to Files" (or any
  other destination the device offers) is one tap away and nothing needs a storage permission.
- Import settings or notes back through the system file picker. Notes import reads the zip the
  export writes, and still reads the JSON notes backup older builds wrote.
- Imports are confirmed first and reject files that are not HiNotes backups.
- Restore all settings to defaults.
- Export a diagnostics report (counts and environment only, never note text).
- Unhandled exceptions are recorded locally so a crash can be reported from that same export.

**Unlock**
- Password protection with PBKDF2-HMAC-SHA1 (120,000 iterations, per-password random salt).
- Face unlock through `androidx.biometric`, asking for a class-2 authenticator — the class face
  implementations carry, and the closest Android comes to letting an app name the sensor. The
  switch is only offered when the device reports that class.

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

- **Colour roles only.** The UI never uses a literal colour. Accent palettes live in
  `ui/theme/ColorTokens.kt` (the design's own Light purple) and `ui/theme/AccentPalettes.kt`
  (generated with the official Material 3 HCT implementation; Blue is the default, and the
  launcher icon and window background are painted from it); the third source is
  `dynamicLightColorScheme` / `dynamicDarkColorScheme`. Nothing else names a colour.
- **Motion.** `ui/theme/Motion.kt` reproduces Material 3's *standard* motion scheme from its
  published tokens (damped springs, `dampingRatio` 0.9 spatial / 1.0 effects). Material 3 1.4.0
  keeps `MotionScheme.standard()` internal, so the tokens are re-declared rather than reached
  for. Screen transitions use a plain tween for the same reason: a spring on a full-screen slide
  reads as a settle.
- **Expressive components.** Material 3 1.4.0 ships design tokens for connected button groups
  but not every composable. `ui/components/ExpressiveComponents.kt` assembles what the app needs
  from standard M3 building blocks (`Surface`, `IconButton`, M3 shape and motion) at the
  specified sizes, rather than redrawing anything the library already provides.
- **Icons.** Each glyph is a vector drawable (`res/drawable/sym_*.xml`) generated from the
  Material Symbols Rounded outline. One scale is shared by the whole set, taken from the union of
  their ink boxes, and each glyph is then centred on *its own* ink box — centring on the union
  instead left most glyphs about 0.4dp low and a few up to 1.5dp off, which is visible as an icon
  sitting off-centre in its button. `tools/verify/check_icon_geometry.py` rasterises every one of
  them and fails if any glyph is off-centre or leaves its viewport. Names live in
  `ui/icons/Symbols.kt`. The launcher icon is generated from the same font's `edit` glyph, so the
  app icon and the in-app mark are one shape.
- **Type.** Text uses the device's own font family. Only size and weight are configurable.

### Verifying

Two JVM check programs execute the app's real sources — the notes archive round trip, and the
Markdown verbs behind the editor's toolbar buttons — plus an audit of the string, icon and
drawable references, and the icon geometry check above:

```bash
pwsh -File tools/verify/run.ps1
python tools/verify/audit_resources.py
python tools/verify/check_icon_geometry.py
```

They exist because these are the parts that cannot be judged by reading them: a toggle that
removes a prefix it never added looks like a dead button, and a glyph that is half a dp off centre
looks fine until it is measured.

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
└── v1.0.0/hinotes-1.0.0-release.apk
```

Rebuilding the same version overwrites that version's copy rather than piling up duplicates.
The task is `:app:archiveReleaseArtifacts` if you ever want to run it on its own. The folder is
git-ignored.

### Project layout

```
app/src/main/java/com/hiapps/hinotes/
├── MainActivity.kt              single activity, edge-to-edge, theme wiring
├── data/                        Note, SQLite store, the Markdown notes archive,
│                                DataStore settings, lock store, biometrics, crash logger
└── ui/
    ├── HiNotesApp.kt            routes and transitions
    ├── AppViewModel.kt          single source of truth for the running app
    ├── LockGate.kt              app lock screen
    ├── components/              shared M3 Expressive pieces
    ├── editor/                  editor state (undo/redo) and Markdown
    ├── icons/                   generated symbol drawables and their names
    ├── screens/                 the eight screens
    └── theme/                   colour roles, type, motion, shapes
```

Generators for the derived artwork and palettes are kept in `tools/` at the repository root
(`gen_symbol_vectors.py`, `gen_icons.py`, `gen_accents.py`); each writes a file that says so in
its header and is not meant to be hand-edited. `tools/verify/` holds the checks described below.

### Privacy

No analytics, no tracking, no accounts. Notes and settings stay on the device. The app makes no
network request at all and does not ask for the INTERNET permission; no note content ever leaves
the device unless you share or export it yourself.

### Licence

Apache License 2.0. The icon artwork is derived from Material Symbols Rounded (Apache-2.0); no
typeface is redistributed.

---

## 简体中文

一个开源且 100% FOSS 的 Android 笔记应用，使用 Jetpack Compose 与 Material 3 Expressive 构建。

### 这是什么

一个本地优先的笔记应用。笔记存在设备的 SQLite 中，设置存在 DataStore 中，不会发送到任何地方。
应用不发起任何网络请求，也不申请 INTERNET 权限：「关于」页的「检查更新」是在浏览器中打开项目的
releases 页面，那是浏览器的请求，不是应用的。

### 功能

**笔记**
- 新建、编辑、删除笔记；所有数据重启后仍然保留。
- 在首页搜索框按标题与正文搜索，无结果时显示空状态。
- 按最近修改、创建时间或标题排序。
- 首页两种排版：单列列表，或两列瀑布流网格 —— 每张卡片的高度恰好等于自身内容。
- 开启 Markdown 后，列表与编辑器预览中的笔记正文都会按 Markdown 渲染。
- 长按笔记进入多选：可全选、删除所选，或退出多选。
- 单条笔记可以标记为「锁定」：卡片上会显示锁形标记，标记随笔记一起保存。这是**标记**而不是第二道
  密码 —— 真正防止他人查看的是「解锁」设置页中的应用密码。
- 分享笔记为文本，内容较长时改为 Markdown 附件。

**编辑器**
- Markdown 源码编辑，可选渲染预览。预览为只读：标题与正文都无法修改，格式化工具栏收起，
  笔记以 Markdown 渲染显示。
- 撤销 / 重做，连续输入会合并为一步。
- 相连工具栏提供粗体、斜体、复选框与项目符号。
- 在笔记内查找。
- 每 20 秒自动保存（可选），也可显式保存。
- 属性面板从底部滑出，显示笔记字数（不含标题与标点符号）、创建时间与最近修改时间。
- 未保存的修改不会被静默丢弃：返回时会提示，即使选择离开也会先保存。

**外观**
- 七种 Material 3 强调色 —— 蓝色（默认）、茶绿色、浅灰色、红色、淡紫色、橙色、黄色。每种配色都由一个
  种子颜色经 Material 3 的 HCT 算法生成完整的浅色与深色方案，各颜色角色都落在 MD3 规定的色调上。
- Android 12 及以上支持从壁纸动态取色；开启时优先生效。
- 浅色与深色方案；应用默认以浅色打开，外观页的「跟随系统」可把选择权交还给设备。
- OLED 深色模式（纯黑表面），可与上面两种取色方式叠加。
- 应用级字体大小与字重，笔记内容另有独立的大小与字重。
- Android 13 及以上支持主题图标（单色），跟随壁纸颜色。

**备份**
- 设置导出为易读的 JSON，笔记导出为 Markdown 文件的 zip 包：解压后每条笔记都是任何编辑器都能打开的
  文档。两者都先写入应用缓存再交给系统分享面板 —— 与「关于」页的日志导出同一条路径 —— 因此在面板里
  一步就能「保存到文件」（或任何设备提供的目的地），全程不需要存储权限。
- 设置与笔记都可以通过系统文件选择器导入回来。笔记导入可以读回导出的 zip，也仍然兼容旧版本写出的
  JSON 笔记备份。
- 导入前会确认，并拒绝非 HiNotes 的备份文件。
- 恢复所有设置为默认值。
- 导出诊断报告（只含计数与环境信息，绝不含笔记正文）。
- 未捕获异常会记录到本地，可通过同一个导出入口提交崩溃报告。

**解锁**
- 密码保护使用 PBKDF2-HMAC-SHA1（120,000 次迭代，每个密码独立随机盐）。
- 通过 `androidx.biometric` 实现人脸解锁，请求 class 2 生物识别 —— 这是人脸识别通常所属的等级，
  也是 Android 允许应用指定传感器类型的极限做法；只有设备确实报告该等级时才提供该开关。

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

- **只使用颜色角色。** UI 中不出现字面颜色值。强调色配色集中在 `ui/theme/ColorTokens.kt`
  （设计稿自带的淡紫色）与 `ui/theme/AccentPalettes.kt`（用官方 Material 3 HCT 实现生成；蓝色为默认色，
  桌面图标与窗口背景都取自它）；第三条路径是 `dynamicLightColorScheme` / `dynamicDarkColorScheme`。
  除此之外没有任何地方写死颜色。
- **动效。** `ui/theme/Motion.kt` 按 Material 3 公布的设计令牌复刻了*标准*动效方案
  （阻尼弹簧，空间 0.9 / 效果 1.0）。Material 3 1.4.0 把 `MotionScheme.standard()` 标为
  `internal`，因此这里是重新声明令牌而不是调用它。屏幕过渡同样出于这个原因使用普通 tween：
  整屏滑动上用弹簧会显得有回弹感。
- **Expressive 组件。** Material 3 1.4.0 提供了相连按钮组的设计令牌，但并非每个 composable 都有。
  `ui/components/ExpressiveComponents.kt` 用标准 M3 构件（`Surface`、`IconButton`、M3 形状与动效）
  按规格尺寸把应用需要的部分组装出来，而不是重画库里已有的东西。
- **图标。** 每个字形都是一个矢量图（`res/drawable/sym_*.xml`），由 Material Symbols Rounded 的轮廓
  生成。整套图标共用一个缩放比例（取自全部字形墨迹包围盒的并集），再让**每个字形按自身墨迹包围盒居中** ——
  若按并集居中，多数字形会整体偏低约 0.4dp，个别偏差可达 1.5dp，看起来就是图标在按钮里没对齐。
  `tools/verify/check_icon_geometry.py` 会逐个栅格化并测量，任何字形偏心或超出视口都会失败。名称定义在
  `ui/icons/Symbols.kt`。应用图标由同一字体的 `edit` 字形生成，因此桌面图标与应用内标识是同一个形状。
- **字体。** 文字使用设备自带字体，仅字号与字重可调。

### 验证

有两个 JVM 检查程序直接运行应用的真实源码 —— 笔记归档的往返、编辑器工具栏按钮背后的 Markdown 动词 ——
外加字符串 / 图标 / 矢量图引用的审计，以及上面的图标几何检查：

```bash
pwsh -File tools/verify/run.ps1
python tools/verify/audit_resources.py
python tools/verify/check_icon_geometry.py
```

它们存在的理由，正是这些部分无法靠"读代码"判断：一个删除了从未添加过的前缀的开关，看起来就是个坏按钮；
一个偏了半 dp 的字形，不量就看不出来。

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
└── v1.0.0/hinotes-1.0.0-release.apk
```

重复构建同一版本会覆盖该版本目录，而不会堆积重复文件。这个任务名为
`:app:archiveReleaseArtifacts`，需要时也可以单独执行。该目录已加入 gitignore。

### 项目结构

```
app/src/main/java/com/hiapps/hinotes/
├── MainActivity.kt              单 Activity，边到边，主题装配
├── data/                        Note、SQLite 存储、Markdown 笔记归档、
│                                DataStore 设置、密码存储、生物识别、崩溃记录
└── ui/
    ├── HiNotesApp.kt            路由与过渡动画
    ├── AppViewModel.kt          运行期应用的唯一数据源
    ├── LockGate.kt              应用锁屏
    ├── components/              共享的 M3 Expressive 组件
    ├── editor/                  编辑器状态（撤销/重做）与 Markdown
    ├── icons/                   生成的符号矢量图与名称
    ├── screens/                 八个屏幕
    └── theme/                   颜色角色、排版、动效、形状
```

生成派生素材的脚本放在仓库根目录的 `tools/` 下（`gen_symbol_vectors.py`、`gen_icons.py`、
`gen_accents.py`）；它们写出的文件都会在头部注明，请勿手改。`tools/verify/` 存放上面这些检查。

### 隐私

无统计、无追踪、无账号。笔记与设置都留在设备上。应用不发起任何网络请求，也不申请 INTERNET 权限；
除非你自己分享或导出，笔记内容永远不会离开设备。

### 许可证

Apache License 2.0。图标素材派生自 Material Symbols Rounded（Apache-2.0）；不重新分发任何字体文件。
