<a id="user-content-hinotes"></a>

<div align="center">
    <img width="180" height="180" src="logo.png">
</div>

<div align="center">
    <h1>HiNotes</h1>
    <p>简单易用的 100% FOSS 笔记应用</p>
</div>

[English](README.md) · 简体中文

---

<details>
<summary><b>截图</b></summary>

| 首页 | 笔记 | 设置 | 关于 |
| :---: | :---: | :---: | :---: |
| <img src="screenshots/home-empty.png" width="190"> | <img src="screenshots/home-notes.png" width="190"> | <img src="screenshots/settings.png" width="190"> | <img src="screenshots/about.png" width="190"> |
| 空状态 | 列表与网格 | 设置 | 关于 |

</details>

---

简单易用的 100% FOSS Android 笔记应用，使用 Jetpack Compose 与 Material 3 Expressive 构建。

本地优先：笔记存在设备的 SQLite 中，设置存在 DataStore 中。应用不申请 INTERNET 权限。

### 功能

**笔记**
- 新建、编辑、删除笔记。
- 按标题与正文搜索；按最近修改、创建时间或标题排序。
- 首页支持单列列表或两列瀑布流网格。
- 支持以 Markdown 格式书写笔记：标题、有序与嵌套列表、任务列表、引用、表格、公式、带圆角背景的代码、可点击跳转浏览器的链接、强调、删除线、注释与转义。
- 可从相册导入图片：浏览模式与微件会显示图片，笔记本身保存的是图片地址，首页卡片显示该地址。
- 长按进入多选，可批量删除，也可只导出所选笔记。
- 笔记可分享为文本或 Markdown 附件。
- 可锁定笔记：锁定后以浏览模式显示、收起格式工具栏、编辑/浏览按钮变灰，笔记仍可被选中和删除。
- 可置顶笔记：置顶的笔记单独排在最前，并带有“置顶”标题，与“未置顶”的其余笔记分开。

**微件**
- 桌面微件显示一篇笔记，添加时为每个微件单独选择笔记。
- 标题固定，正文在下方滚动，不显示滚动条。
- 显示笔记中的图片，任务列表画成复选框。
- 可隐藏标题，此时正文占满整张卡片。
- 配色跟随应用配色方案，并跟随 Markdown 开关。
- 可单独设置微件的字号与字重。
- 微件所显示的笔记被删除后，卡片显示为空，点击它不会有任何动作。

**编辑器**
- 支持 Markdown 源码编辑；笔记内查找可按回车逐处定位并高亮，走到末尾后回到第一处。
- 书写笔记时显示格式工具栏；笔记内查找位于其上方的保存按钮左侧。
- 支持以 Markdown 格式预览笔记。
- 支持自动保存笔记。
- 属性面板显示笔记字数与创建、修改时间。

**外观**
- 包含 7 种 Material 3 配色方案。
- 支持壁纸动态取色。
- 支持 OLED 深色模式与应用级字体（0.8 到 1.5 倍）与字重设置。

**备份**
- 可导出/导入设置文件、笔记文件；笔记在 Markdown 开启时导出为 `.md`，关闭时导出为 `.txt`，笔记中的图片一并导出与还原。
- 可恢复默认设置。
- 可导出诊断报告。

**隐私**
- 可禁止应用内截图、录屏与投屏。
- 使用 PBKDF2-HMAC-SHA1 的密码保护。

**语言**
- 支持英文与简体中文，在应用内切换。

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
pwsh -File tools/verify/run.ps1          # 归档、Markdown、微件文本、主题与文档选择的检查，在 JVM 上执行
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
