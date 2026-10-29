# Open Listener

**One-shot Android Engineering Brief · Product / Design / Architecture / Delivery Contract**  
Version: **1.0 · 2026-10-29**  
Project: **Open Listener** · Repository target: **`open-listener`** · Platform: **Android ONLY**  

> **给执行 Agent 的首要指令**：你正在一个仅包含本文件的空目录中。**立即把当前目录建设为完整、可编译、可安装、可持续升级、开源可发布的 Open Listener Android 项目，并将其推送到当前已认证 GitHub 账号的新仓库。**不要只给方案、伪代码、静态设计稿或空壳；不要另建嵌套项目目录；不要要求使用者在本机安装 Android Studio、Android SDK、NDK、模拟器或运行 Gradle；使用 GitHub Actions 在云端编译、自动测试、签名、打包、发布 APK。**执行期间自行查阅官方资料，连续实现、检查、修复并重新运行 CI，直至达到下面定义的验收条件。**
>
> 用户已经授权为此项目在当前文件夹创建源代码、Git、GitHub 仓库、GitHub Actions、首个 Release。遵守当前执行环境的工具授权边界；如 GitHub CLI 无认证或平台要求显式确认，不能伪称已完成，应继续完成可离线完成的工作并明确说明被阻塞的步骤。任何 API 密钥不向用户索取作为建仓或 CI 的先决条件；无密钥的第三方 API 使用协议级模拟测试，实际测试需用户填入自己的凭据。

---

## 0. 不可协商的结果

1. **原生 Android**：Kotlin + Jetpack Compose + Material 3（深度定制视觉）；不使用 Flutter、React Native、Electron、PWA、WebView 套壳。`minSdk 26`，`targetSdk` 使用实施时官方推荐且 CI 可用的较新稳定值；仅 Android。默认 `applicationId = io.github.vexpaer.openlistener`，若当前 GitHub owner 不为 `vexpaer`，可依实际 owner 调整一次，此后保持不变以确保升级兼容。
2. **三大顶部 Tab**：`录音 / 记录 / 设置`（英文：Listen / Library / Settings），顶部固定/可滚动折叠但不能变成底部导航；录音是首屏。
3. **连续实时识别 + 翻译**：在录音页点击主按钮开始，实时看到原文与目标语言译文；ASR 的 partial 和 final 必须区分，已确认段落持久化，翻译异步回填、保持顺序。
4. **绝不存储原始音频**：仅传递临时 PCM 内存帧到选定 ASR 引擎，不能生成/保存音频文件、磁盘音频缓存、音频日志、完整音频上传队列。文本记录可保存/导出/分享；云 API 若被选择，会向相应第三方发送音频/文字，界面必须诚实披露。
5. **息屏不中断（正常系统场景）**：从可见页面启动 `microphone` 类型 Foreground Service，熄屏、回桌面、切换 App 仍继续捕获及识别；常驻通知可暂停/继续/结束，通知状态与 UI 一致；异常断流/系统终止需可感知并尽量恢复**文本状态**。**不得承诺所有厂商极端省电/强制停止场景下绝对运行**，禁止用不合规方式绕过 Android 权限和后台限制。
6. **本地模型商店**：至少一个真正可用的 **sherpa-onnx Android Streaming ASR** 方案支持一键下载安装、SHA-256 验证、断点续传/重试、加载/使用/删除；本地翻译至少支持 **ML Kit 按需下载语言模型** 并真实翻译。必须准确标注 ML Kit 非开源。模型来源/许可证需核验。
7. **云端 API 配置**：优先完整实现 **豆包 / 火山引擎流式语音识别 2.0（WebSocket 双向流）**，支持用户自行配置 Key/Resource ID，实时音频、增量/最终结果、错误反馈；另有阿里云百炼及 OpenAI 的**可用**服务商预设/适配器；文本翻译至少豆包方舟、Qwen/OpenAI 兼容文本 API 适配，语音识别和翻译可独立选取。不得把非实时上传转写称作流式 ASR；不得将只有外观的选项标记为可用。
8. **历史与分享**：Room 持久化文本历史、标题/搜索/详情/删除；Markdown、PDF 导出和 Android 系统分享，支持中英混排与可阅读分页。关闭 App 或退出 UI 不丢已确认文本。
9. **更新**：设置里的 `检查新版本` / `下载新版本` 两个真实按钮，读取**本仓库**最新非草稿、非预发布 GitHub Release，版本比较、下载有进度、校验并交给 Android 系统安装；不能静默安装，不能跨签名直接覆盖。
10. **开源仓库**：当前目录初始化 Git，创建公开 GitHub 仓库（除非已有有效 remote），README 具备高质量开源项目审美且内容真实；Apache-2.0 或经核验适用的开源许可；完善 CONTRIBUTING、PRIVACY、SECURITY、CHANGELOG、LICENSE、.gitignore、GitHub Issue/PR 模板。首个可安装的、**签名保持稳定**的 APK 在 GitHub Release 可下载。
11. **无本地 Android 工具链**：本地允许使用 Git、GitHub CLI、Python、OpenSSL、文本编辑及网络；**不得**安装/下载 Android Studio、Android SDK、NDK、模拟器或为了项目在本地执行 Android Gradle build。构建和设备测试都放 GitHub Actions runner。
12. **完整交付**：最终汇报仓库 URL、成功 CI run、Release URL、APK 下载位置、测试结果、已知设备或厂商限制。禁止声称没有验证的功能“已在真机可用”。

### One-shot 作业原则

- 这是实现任务，不是方案咨询。**不要中途要求二次确认视觉风格、架构、命名或其它已明确的决定**，优先遵守此文件的默认值并持续完成。
- 不得以缺少 API Key 为理由退化为 Demo；按协议实现真实适配器，并使用 mock server/fixture 测试，运行时要求用户输入自己的密钥。
- 不允许 `TODO` / 假按钮 / 永远返回固定字符串的生产实现 / UI 假装识别成功 / 构建失败却贴出 APK 链接 / 存在用例静默跳过；如能力必须推迟，UI 禁止提供一个看似已完成的功能。
- 遇到某个特定模型库或远端 provider 无法通过验证时：**优先缩小次要型号范围，但一定保留完整的最小核心闭环**。把取舍写在 `docs/IMPLEMENTATION_STATUS.md`，不能谎称完全通过。
- 完成标准以能安装和操作为准，不以代码行数、README 字数或 Agent 自行宣称完成为准。

---

## 1. 产品定位、目标体验

**Open Listener**：面向上课、讲座、研讨会的轻量 Android 实时双语字幕软件。**Listen. Understand. Keep the words.**

使用路径：打开 → 录音页选定源语言/目标语言 → 轻触开始 → 双语同步显示 → 锁屏带走或回桌面继续识别 → 结束 → 保存文字记录 → 查看 / 搜索 / 导出 MD 或 PDF → 分享。

不需要账号、云数据库、录音文件或复杂的聊天/AI 总结。不要在首屏加入打卡、文章推荐、邮箱连接或侧边栏。第一启动向导控制在不超过两步：权限与模型模式（`离线下载` 或 `连接 API`），允许之后在设置改。

主语言中文与英文，跟随系统、可手动切换。源语言和目标语言是独立的两个设置；不能把 UI 语言当作翻译目标语言。

### 1.1 信息结构

- `录音` / **Listen**：唯一主操作、实时双语文本、状态和即时语言切换。
- `记录` / **Library**：历史、搜索、详情、复制、编辑标题、删除、导出、分享。
- `设置` / **Settings**：外观、软件语言、语音语言/目标语言、ASR/翻译引擎、模型商店、API 凭证、隐私、本机空间、关于、检查/下载更新。

底层所有会话均是 text-first：麦克风的 PCM 帧只存在于短时有界 RAM buffer 中。

---

## 2. 视觉研究：Hark Pro（**不是** hark-app.com 那款同名字幕软件）

### 2.1 正确的参考对象（已核对）

**Hark Pro by Hark Labs Inc.** 官方：
- https://hark.com/
- https://hark.com/articles/introducing-hark-pro
- https://hark.com/using-hark
- https://play.google.com/store/apps/details?id=com.hark.android
- 设计访谈（Fast Company）：https://www.fastcompany.com/91618831/how-hark-designed-an-ai-assistant-thats-actually-helpful
- Hark Pro 实际 App 截图集合（仅作观察）：https://www.appbrain.com/app/hark-pro/com.hark.android

官方表述与第三方设计报道一致：Hark Pro 的背景是**环境天空（ambient sky）**，以昼夜、霞光、云层和渐变构建沉静、个性化的空间；UI 以文字、单一主操作、短文本块和克制的信息层次为主，不靠密集导航/卡片堆叠。它在 Chat、Home、Projects 中有自己的一套组织方式，**不要复制那些页面结构**。对 Open Listener，只借鉴美学原则，保留我们独立的三个顶部 Tab。

**禁止直接复制**：Hark 的 logo、图标、源码、图像素材、天空纹理、产品文案、组件边框和逐像素布局。用户要求的是同等级的高级质感，不是仿冒。

### 2.2 Open Listener 独立设计方向

Design direction: **Ambient Precision** — “温柔的环境光 + 清晰到极致的实时文字”。视觉关键词：Calm / Air / Ink / Focus / Responsive / Editorial。

- 全屏柔和的色彩环境层。浅色模式以轻灰蓝/雾白/暖粉天空渐变为主；夜间过渡到深蓝紫/石墨色。**不读取地理定位**，只依据设备本地时间模拟一天四个阶段，不需要天气 API、位置权限，也不需要任何第三方壁纸。
- 天空只作为背景：可在 Compose Canvas/Brush 做 3–4 层渐变、超低频漂浮半透明光晕。其模糊/透明度需实现可控、渲染开销低。避免不断重采样 bitmap 或逐帧生成高分辨率噪声。
- 清晰的文字和温和的阅读区域覆盖在背景之上。录音期间文本对比度比氛围更重要：必要时增加微白/深色半透明底，但不要整屏高模糊玻璃。
- 整个应用最多一个主要 CTA，其他操作弱化。留白、字阶、段落间距优先于阴影和炫光。
- 精致不是“所有元素都动画化”：**动画只说明状态、层级和时间流动**，不能影响课堂阅读。

### 2.3 必须实际落地的设计令牌（可微调，需统一）

| Token | 浅色 | 深色 |
|---|---|---|
| `ink.primary` | `#182532` | `#F5F6FA` |
| `ink.secondary` | `#5C6B79` | `#ADB6C3` |
| `sky.top` | `#B7CDDC` | `#111B37` |
| `sky.mid` | `#E0E8ED` | `#253456` |
| `sky.bottom` | `#F8EEE8` | `#3F405A` |
| `surface.reading` | `#FFFFFF` 约 65–85% 不透明 | `#111827` 约 68–90% 不透明 |
| `accent` | `#406B88` | `#A5C8E3` |
| `signal.live` | `#D3646A` | `#F18F91` |
| `stroke.soft` | `#FFFFFF` 约 35–50% | `#FFFFFF` 约 10–15% |

具体设计用 `ColorScheme` + 自有 tokens，保证 WCAG 对比度目标正文 ≥ 4.5:1、主要控件 ≥ 3:1；在深色/浅色与系统大字体下不能溢出。手机安全边距 ≥ 20dp，顶部导航可触摸区域 ≥ 48dp，主按钮 ≥ 72dp。

排版：正文使用 Android 系统优良中英字体回退，无需下载收费/有专利限制的品牌字体；标题排印视觉可以偏 humanist/editorial，字重 450/500，正文 400；字幕原文约 16–17sp，译文约 17–19sp，时间戳 11–12sp；让中文字形、换行、标点和拉丁单词都舒服。可按屏幕和 Accessibility font scale 自适应。

组件：
- 顶部品牌 `open listener`（字标采用自己设计的类型排版），右侧小状态点；下方 capsule segmented tabs：录音 / 记录 / 设置，**禁止底部 Tab**。
- 主录音按钮：造型极简（柔和实心圆/环＋原创线性麦克风 glyph），只有它是高注意力组件。不要廉价拟物的 3D 麦克风。
- 文本区：段落而非气泡聊天；原文+译文同组，轻微留白分组和时间戳，分段左右齐头、随内容滚动。不要二十张矩形卡片。
- 设置与模型商店：列表行和面板层级清晰、圆角约 16–22dp；小标签如 `DEVICE` / `CLOUD` / `STREAMING`；下载按键展示进度。
- 空状态必须真实设计：无模型 / 未录音 / 无历史 / 下载失败 / 网络断开 / 权限不足 / 已是最新版本。不要只放空白页面。

### 2.4 动效规格（必须写代码实现并有可观察效果）

| 场景 | 行为 | 时间/曲线 | 必须满足 |
|---|---|---|---|
| 首次打开 | 天空逐步显现、页面内容淡入轻上移 | 450–650ms，ease-out | 切换后不重复闪白 |
| Tab 切换 | 导航胶囊滑动 + 内容轻微淡入 | 180–260ms spring/tween | 保留各页状态和滚动位置 |
| 点击开始 | 主按钮向内轻压再舒展，状态点开始低频呼吸 | press 90–130ms，呼吸周期约 2–2.8s | 录音在服务实际开始后才标红 |
| 音频反馈 | 低幅度音量环/波纹，幅度由 RMS 平滑控制 | ~100ms 平滑；不显示虚假跳动 | 无声时安静，不做大面积频繁绘制 |
| partial → final | 同一行修订；final 颜色/不透明度稳定 | 120–200ms | **绝不因为每个字出现而跳动整页** |
| 译文到达 | 小幅度渐显、位置不变/微移 | 150–250ms | 多段晚到按 segment ID 正确回填 |
| 结束 | 轻收缩，出现记录已保存提示 | 180–280ms | 真正保存成功才显示成功 |
| 下载模型/更新 | 细进度条平稳推进 + 完成状态 | 进度有真实 bytes | 不使用假进度 |
| 夜间环境 | 极慢背景漂移，色调按时间平滑变换 | 30–120s 级缓慢变化 | 电量低/Reduce Motion 时停止动态背景 |

技术实现：Compose `AnimatedContent` / `animate*AsState` / `updateTransition`、适当 `Canvas` 和 `drawWithCache`。使用 Android Accessibility 的动画缩放设置作为动效强度依据；有明确“减少动态效果”设置。**应用息屏或不在前台时立刻停止背景动画和 UI waveform 绘制，但 Foreground Service 的识别继续**。禁用全屏实时 GPU 粒子/重型 WebGL。动画必须用 Preview/UI Test 可见而非只写注释。

### 2.5 README 的视觉语言

像优秀的高星开源项目一样：第一屏只呈现名字、核心主张、简约原创 logo/hero SVG、少量关键徽章、真实产品截图/动画、下载入口；排版简洁，功能说明准确，版式留白恰当。**不伪造 10 万星、贡献者数量、企业背书或性能数据**。README 使用英语为主，可提供 `README.zh-CN.md`，README 开头显著给出语言切换。使用本仓库可维护的 SVG 图形；不要用 Hark 素材。真实截图用 GitHub Actions Android emulator 运行真实 App 自动采集，或在尚未采集前明确标注为界面预览，不能伪装真实设备图。

---

## 3. 三个页面的精确产品说明

### 3.1 Listen（录音页）

未录音：顶部 sky 与品牌、三个 Tab；小行 `English → 简体中文` 可点击切换；中间空状态短句 `Your lecture, in both languages.`（汉化按本地语言）；下方主按钮 `开始聆听`；旁边轻量提示 `Audio is never saved.`。如果没有可用的 ASR 或翻译引擎，展示清晰动作 `下载离线模型` / `连接 API`，禁止假装开始。

录音中：顶部 `● LIVE` + 用 `elapsedRealtime()` 计算时长 + `本地/云端` + 语言方向；段落间距有序滚动，标记时间戳，先原文后译文；仅最后一个未定稿 ASR 段有 partial，字体或不透明度略浅；完成段固定；允许 `双语 / 原文 / 译文` 切换（切换显示方式不重新识别）。底部浮动或固定主操作为 `暂停/继续`，旁边明确 `结束`。屏幕可滚动回看：只在列表原本接近末尾时自动滚动，有 `回到最新` 按钮；不能强抢浏览焦点。录音时点击历史 Tab 可查看历史，但录音必须在服务中持续。

停止：服务 flush 引擎最终部分并等待翻译队列有限时间，立即生成可查看文字记录；若队列超时，保存 `translation_pending`，在文本存在时允许稍后重新翻译。默认记录标题 `Lecture · 2026-10-29 14:30` 这种系统当前时间格式，也可重命名。

录音可从通知暂停、继续、结束。进入 App 看到同一个会话状态。若被 OS 杀进程，历史中已确认文本必须存在，未结束会话显示 `中断`，不能凭空补全已丢失音频。

### 3.2 Library（记录页）

- 最近优先，搜索标题、原文、译文；空列表/无结果态。
- 每项：标题、日期、时长、语言方向、文本摘要、已完成/已中断。
- 详情支持双语/原文/译文，长按复制段落与复制全文，修改标题、删除确认。
- `导出 Markdown`、`导出 PDF`：Android SAF `ACTION_CREATE_DOCUMENT` 可指定位置；`分享 Markdown`、`分享 PDF`：app 缓存中生成**文本产物**通过 `FileProvider` + `ACTION_SEND`，设置 URI 授权，不能给 `file://`；分享完按 TTL 清缓存。
- Markdown：标题、日期、语言、时长、依序每段时间戳/原文/翻译，UTF-8；PDF：页眉、正文、页码、自动分页、跨语言字体、长段落换行、字体缩放与溢出测试。
- 不支持音频回放，不显示音频文件大小，不提供虚构的录音下载。

### 3.3 Settings（设置页）

清晰分组：
1. **Experience**：应用语言（系统/中文/English）、主题（系统/明/暗）、减少动态、字幕字体尺寸、源语言和目标语言。
2. **Speech Recognition**：本地/云端切换，正在使用的引擎卡、模型商店 / 服务商配置、连接测试、下载/删除模型。
3. **Translation**：独立选本地/云端，语言包或引擎设置；以组合显示数据路径（`全离线` / `上传音频` / `上传文字` / `上传两者`）。
4. **Privacy & Storage**：音频从不保存（不可关闭）、已使用的文本和模型容量、清除记录/模型；API 密钥管理。
5. **Updates & About**：当前版本 `vX.Y.Z (versionCode)`、`检查新版本`、`下载新版本`（检查到新版本后才可用）、发布说明、下载进度、安装引导、开源地址、许可。

设置使用列表行/轻量弹层而非长表单。每个可点击项目有明确反馈；网络失败、认证失败、未开通服务/计费应给出可操作错误信息。

---

## 4. Native Android 架构（强制）

- Kotlin、Jetpack Compose、Material3、Navigation Compose、Hilt（或明确采用一种一致的手动 DI）、Coroutines/Flow、Room、DataStore、OkHttp WebSocket、WorkManager（仅用于非录音后台任务）、Android Keystore + Jetpack Security 等官方/维护良好的组件。
- 项目采用 **单 `app` 模块 + 分层 package** 或适量 feature module，避免 one-shot 过度拆分导致 CI 脆弱。优先 `ui/`, `domain/`, `data/`, `audio/`, `asr/`, `translation/`, `models/`, `updates/`, `service/`。
- 不在 Compose Composable 中直接操作麦克风、WebSocket 或 Room；UI 只观察 ViewModel `StateFlow`，事件驱动 Service/Repository。
- 建议工程大纲（能根据实测调整）：

```text
./
├── OPEN_LISTENER_ONESHOT.md       # 本文件，保留在仓库作为任务历史
├── README.md
├── README.zh-CN.md
├── LICENSE
├── PRIVACY.md
├── SECURITY.md
├── CONTRIBUTING.md
├── CHANGELOG.md
├── .gitignore
├── .github/
│   ├── workflows/
│   │   ├── android-ci.yml         # PR / push: lint + unit + assemble + artifact
│   │   ├── android-release.yml    # tag v*: 统一签名 + APK + SHA + Release
│   │   └── android-ui-tests.yml   # emulator UI test / screenshot (可并入 CI)
│   ├── ISSUE_TEMPLATE/
│   └── pull_request_template.md
├── gradlew
├── gradle/wrapper/               # 固定 wrapper 文件与 jar（可信来源+校验）
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/
│   ├── build.gradle.kts
│   ├── src/main/AndroidManifest.xml
│   └── src/main/java/io/github/vexpaer/openlistener/
│       ├── OpenListenerApp.kt
│       ├── MainActivity.kt
│       ├── service/MicrophoneRecognitionService.kt
│       ├── audio/AudioPcmCapture.kt
│       ├── asr/AsrEngine.kt
│       ├── asr/SherpaStreamingAsr.kt
│       ├── asr/DoubaoRealtimeAsr.kt
│       ├── asr/AliyunRealtimeAsr.kt
│       ├── asr/OpenAiRealtimeAsr.kt
│       ├── translation/TranslationEngine.kt
│       ├── translation/MlKitTranslation.kt
│       ├── translation/CloudTextTranslation.kt
│       ├── models/ModelStore.kt
│       ├── data/TranscriptDatabase.kt
│       ├── updates/GitHubReleaseUpdater.kt
│       └── ui/... (Listen, Library, Settings, DesignSystem)
├── app/src/test/...               # 纯 JVM 测试
├── app/src/androidTest/...        # Compose、Room、Service、SAF 测试
├── docs/
│   ├── DESIGN.md
│   ├── ARCHITECTURE.md
│   ├── PRIVACY_AND_PERMISSIONS.md
│   ├── MODEL_CATALOG.md
│   ├── RELEASE.md
│   ├── IMPLEMENTATION_STATUS.md
│   └── images/                    # 原创 SVG / 真机或 CI 模拟器截图
└── tools/                         # 开源、可复现的小型发布/元数据脚本
```

应用程序包名/目录按实际 owner 决定，一旦发布必须固定。不要为了漂亮创建几十个无实际用途的空类。

### 4.1 音频及录音服务

`AudioRecord` 获取单声道 PCM16（首选 16kHz，按实际设备/引擎要求重采样），有限内存 ring buffer，唯一生产者/消费者，无文件落盘。音频帧按照当前配置送入本地 ASR 或网络 ASR。录音 Service 持有 engine 和 session，不依赖 Activity 生命周期。需要时在会话期间持有**最小必要的 `PARTIAL_WAKE_LOCK`**，在暂停/结束/异常时可靠释放；避免后台 UI 绘制，优先低功耗；不能维持长时间 `SCREEN_ON` 作为解决方案。

AndroidManifest 至少：
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.POST_NOTIFICATIONS`（Android 13+ 适当请求；即使通知被拒绝也按平台支持情况处理 FGS 可见性）
- `android.permission.INTERNET`、`android.permission.WAKE_LOCK`（仅按实际用途）
- `<service android:foregroundServiceType="microphone" android:exported="false" ... />`
- `<provider ... androidx.core.content.FileProvider ...>` 分享内容、更新包。

**关键 Android 限制**：必须在用户点击开始且 Activity 可见、麦克风权限已授予时启动 microphone FGS，随后锁屏或后台时**继续同一个已经启动的服务**。不要在锁屏后从零启动新的 microphone FGS，也不要从 `BOOT_COMPLETED` 自动启动麦克风。系统或 OEM 强制结束/撤销麦克风权限时，停止并诚实标注；允许用户回到 App 后重新手动开始。

Service 状态机（处理旋转屏幕、进程重建、UI 离开）：`IDLE → PREPARING → RECORDING ↔ PAUSED → STOPPING → FINISHED`，异常 `ERROR/INTERRUPTED`。捕获异常、麦克风抢占、蓝牙路由更改、网络断连要有状态事件和持久化标记；通知上提供 Pause/Resume/Stop 的 `PendingIntent`，确保对应权限及后台限制合规。

### 4.2 端到端转录/翻译管线

```text
Visible Activity starts FGS (microphone)
      ↓
AudioRecord PCM  →  bounded in-memory queue (NO DISK AUDIO)
      ↓
selected ASR adapter (local / WebSocket cloud)
      ↓
TranscriptEvent { sessionId, segmentId, partial|final, text, timestamps, revision }
      ↓
SegmentAssembler (dedup/order/replace partial, no duplicate final)
      ↓                         ↘
Room text store               selected TranslationEngine (offline/cloud)
      ↑                              ↓
      └──── segmentId-based translated text / status updates
      ↓
StateFlow → Compose UI / Library / MD / PDF
```

`partial` 只在 UI 暂时更新；`final` 入 Room，并发翻译队列按 segment ID 补填，按顺序展示；翻译错误保留原文并提供重试。停止时 flush 与有限等待，不因网络服务卡死而丢本地已确定文字。**云端识别已发往服务商的音频不能因为应用没存音频就宣称“完全离线”**。

数据库：`Session(id, title, startedAt, endedAt, durationMs, sourceLang, targetLang, asrProvider, translatorProvider, state, createdAt)`、`Segment(id, sessionId, sequence, startOffsetMs, endOffsetMs, original, translated, translationStatus, revision)`，建立高频查询索引、Room 迁移和 FTS 搜索。只存文字；不存 PCM、音频字节数组、音频 URI。启动时检测 `recording` 且无活动 service 的 Session 并标中断，保留文字。

日志禁用 PCM/文本正文/API key 的直接输出；release 禁用不必要 debug 日志及分析；导出缓存仅用于文本文档/更新包并自动清理。

### 4.3 引擎接口（需要有真正的生产实现）

- `AsrEngine`：`capabilities`, `prepare`, `start`, `acceptPcm`, `events: Flow<TranscriptEvent>`, `pause/resume`（或由 capture 层处理）, `flush`, `stop`, `close`。
- `TranslationEngine`：`capabilities`, `prepare`, `translate(segmentId, text, source, target)`, `close`；必须区分超时、额度/认证错误和支持语言限制。
- `EngineRegistry`：只注册已实现且可用的引擎，缺少配置/模型时返回 `NeedsDownload/NeedsCredentials`；选择不会自动触发隐私级别更低的云端回退。
- 模型目录与 API Provider 是可独立扩展的，不把 provider endpoint 鞋盒式硬编码到 ViewModel。
- 测试使用 `FakeAsrEngine` / `FakeTranslator` **仅存在于 test/androidTest 源集或 debug 测试配置**，生产代码不得选择模拟引擎。

---

## 5. 本地模型中心（真正的一键下载）

### 5.1 ASR：首个真实模型必须运行

首批至少展示一款**真正增量流式** Android sherpa-onnx Zipformer 中英模型，首选：
- `sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`：https://k2-fsa.github.io/sherpa/onnx/android/apk.html
- 上游 sherpa-onnx 原生 Android Kotlin 示例：https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnx
- 上游 Android 文档：https://k2-fsa.github.io/sherpa/onnx/android/index.html
- 模型清单：https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models

**Agent 必须**先核对某个可下载的正式模型档案、真实文件结构/命名、SDK 运行时需要的文件、权重许可证、ARM64/可选 x86_64 原生库来源，并**获得真实 SHA-256**写进 `docs/MODEL_CATALOG.md` 与应用内模型 manifest。禁止虚构 URL、checksum、大小和授权。若候选已失效，换官方当前可用的同类 streaming 模型并记录选择依据。优先复用官方发布的 `.so` / JNI bridge，禁止为性能演示偷偷依赖网络服务。

模型安装必须：用户点击 → 显示体积/语言/类型/许可/流式状态 → HTTPS 下载到 App **私有模型临时文件** → 断点/重试、进度、足够磁盘空间 → checksum → 安全解压（防 Zip Slip / 防 zip bomb）→ 受信模型初始化验证 → 原子生效 → 可以卸载。**不要随 APK 打包大体积模型**；允许首次安装 APK 很轻。模型包的“录音禁止落盘”仅约束音频，不禁止下载权重文件。

加一个低配推荐小模型（仅当 checksum 与 Android 集成得到验证）。SenseVoice/Qwen ASR 可作为后续候选但要明确标记 VAD 分段/伪流式，不得冒称真流式，也不要为了堆型号牺牲首个可用方案。

### 5.2 翻译

- MVP：**Google ML Kit on-device Translation** 官方 Android SDK，按需下载语言包、检查已安装/删除、实际完成 `en→zh` 和 `zh→en`（根据官方支持）。官方：https://developers.google.com/ml-kit/language/translation/android
- 商店标记 `设备端 · 第三方非开源`，不把 ML Kit 写成开源模型；如未来接 OPUS-MT 需建立 Android 推理和 tokenizer、真实许可证验证。
- 模型/语言包下载应给出明确网络提示；翻译过程中若语言包缺失，操作必须被正确引导而不能假定成功。

### 5.3 管理结构

模型记录包括 ID、来源/许可证、runtime、语言、是否真流式、当前版本、bytes、SHA-256、安装状态、进度、错误原因。删除当前正在使用模型应被阻止或推迟到录音结束。模型目录可与 release 资产分离，但**不得依赖用户自己手动查仓库、解压、移动文件**。

---

## 6. 云端 API 预设（BYOK，豆包优先）

用户自己提供凭证，凭证保存在 Android Keystore 加密存储，不写数据库明文、不进日志、不入 GitHub Secrets（除发布签名凭据之外），不随分享文件导出。服务商切换不修改其他提供者配置。提供测试连接时明确可能计费；没有密钥不要发任何真实音频。

### 6.1 豆包流式 ASR 2.0（P0，必须实现）

**官方双向 WebSocket**：https://docs.volcengine.com/docs/doubao-voice/bidirectional-streaming-automatic-speech-recognition-websocket?lang=zh

已核对的主要配置：
- `wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async`（官方双向真流式，不是 `bigmodel_nostream`）。
- `X-Api-Key`、`X-Api-Resource-Id`、`X-Api-Request-Id`；2.0 小时版资源示例 `volc.seedasr.sauc.duration`、并发版 `volc.seedasr.sauc.concurrent`，具体必须以当前开通实例和文档为准。
- PCM16 mono 16000 Hz 或按官方协议协商的真实音频格式；正确实现**官方二进制帧头、负载、事件、序列号和最后一帧**，不能把普通文本 JSON 塞进 WebSocket 就算完成。
- 处理中间修正/最终分句、二遍修正、句子时间戳；遇网络断开禁止无限重放几分钟音频（原始音频根本没留存），只可从重连后的新音频继续并标明空窗。
- 按官方区分认证不正确、资源未开通、欠费/超配额、网络失败、格式不符。
- 写好协议解析器单元测试，用**脱敏的人工 fixture** 和本地 mock WebSocket server 校验握手、事件和停止收尾。没有真实用户 Key 的条件下如实标记 `协议测试通过，真实账户未联调`。

### 6.2 其他大厂预设

| 服务商 | ASR | 翻译 | 要求 |
|---|---|---|---|
| **豆包 / 火山引擎** | 上述豆包流式 ASR 2.0 | 火山方舟豆包文本模型 | 第一优先，生产可用适配器 |
| **阿里云百炼** | 官方 Fun-ASR-Realtime / 受支持实时 Qwen ASR | Qwen-MT（模型 ID/地域按官方文档） | 独立 WebSocket 协议/鉴权，不能套豆包 |
| **OpenAI** | 官方 Realtime transcription 可用模型（以当期官方文档为准） | OpenAI 文本模型 | Realtime session 的增量/最终事件正确解析 |
| **自定义 OpenAI 兼容** | **不宣称支持实时 ASR** | 文本翻译 | base URL / model ID / Key，兼容性错误必须清晰 |

官方参考：
- https://help.aliyun.com/zh/model-studio/real-time-speech-recognition-user-guide
- https://help.aliyun.com/zh/model-studio/machine-translation/
- https://developers.openai.com/api/docs/guides/realtime-transcription
- https://docs.volcengine.com/docs/ark/chat-api?lang=zh

**P0 必须实际完成豆包 + 本地识别；P1 在同次 one-shot 内尽可能完整交付阿里云/OpenAI。** Agent **不得**在设置页把尚未实现的厂商画成 `已就绪`；要么完成适配器和自动化协议测试，要么明确 disabled + `即将支持`，在 `docs/IMPLEMENTATION_STATUS.md` 中列为未交付。这条透明度规则优先于“全绿”的假象。

### 6.3 识别和翻译自由组合

组合必须支持：
- 本地 Zipformer + 本地 ML Kit（音频/文本都不上云）
- 豆包 ASR + 本地 ML Kit（音频上传豆包）
- 本地 Zipformer + 云端 Qwen/豆包/OpenAI 翻译（文本上传相应服务商）
- 豆包 ASR + 云端翻译（音频与文本可能分别发给不同服务商）

翻译服务不能逐字请求造成成本与抖动：按 ASR final 段落或稳定分组使用 debounced queue、并发限制、超时重试、术语不翻错等 prompt 约束。对账单/费用只能提供服务商官方链接，不许捏造价格。

---

## 7. 息屏和长时课堂的正确实现与验收

Android 官方依据：
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/about/versions/14/changes/fgs-types-required

**具体实现检查：**

1. 请求 `RECORD_AUDIO`，可见 Activity 发起 `startForegroundService`，在规定时间内调用 `startForeground` 并声明 `FOREGROUND_SERVICE_TYPE_MICROPHONE`。
2. `AudioRecord` 的生命周期归属于 Service，不属于 Activity 或 Compose。Service 进程在活动会话中维持通知，通知可控，系统允许时持续采集。
3. 当屏幕锁住、APP 不可见时只暂停 GPU/UI 背景；**音频采集 + ASR + 翻译 + Room 保存继续**。
4. 暂停暂停的是采集/识别数据流；`暂停`不写音频缓存。继续时应根据网络连接状态正确启动新流或恢复同一流。
5. 使用 `elapsedRealtime` 时间轴，不受系统时钟跳变影响。长会话防止无限缓存、内存 OOM；建立边界队列与背压机制。
6. 检测电话抢占、录音权限变化、网络断线、低内存杀进程；日志记录**无正文的事件元数据**，已确认文字可恢复，丢失音频不假装恢复。
7. 提供手机厂商省电限制的可选帮助说明，但不能强迫用户授予过度权限或无条件跳转电池白名单。

**测试项目**：Android 13/14/15/16 等 Actions 具备的 emulator API（至少 2 个不同 API levels）启动会话 → `adb shell input keyevent 26` 熄屏/模拟锁屏 → 等待合理窗口 → 用已授权 mock/模拟输入判断 Service、时间戳和文字仍处理；对 `AudioRecord` 实际麦克风锁屏延续性，若云模拟器不能提供可靠真实麦克风，必须在交付说明中标记需要物理机验证，**不得把 mock 成功等同真机验证**。至少要验证服务在锁屏后仍在且未被 Activity 停止。可以补充手工 30/60 分钟真机 checklist。

---

## 8. GitHub Releases 内更新（两按钮真实可用）

UI 在 `设置 → 更新与关于`：
- `检查新版本`：读取 `GET https://api.github.com/repos/<owner>/open-listener/releases/latest`（repo 常量从构建配置设置）。只接受非 draft / 非 prerelease，并比较当前 `versionCode` + release 的可信 `versionCode` 元数据；不要仅做字典序比较 `v1.10` 与 `v1.9`。无需用户登录即可检查公开仓库；处理 GitHub API 限流、网络错误和无发布版本。
- `下载新版本`：只有存在合法的新版本、正确 APK asset 和当前无会话冲突时启用，下载到 App 私有 `cacheDir/updates`，真实进度/暂停或取消、失败重试；发布流程同时生成 `update.json`（包含 tag/versionName/versionCode/apkName/sha256/最低支持 Android/API）及 `SHA256SUMS`；客户端校验 SHA-256 与 appId，条件允许额外检查 archive signing certificate 与当前签名一致。
- 下载成功后使用带授予临时读权限的 `FileProvider` content URI，交给系统 Package Installer。Android 不允许普通应用自动静默升级，需要用户确认安装/可能允许该来源安装未知应用；依据系统版本正确实现 `canRequestPackageInstalls` 和权限引导，必要时打开 GitHub Release 作为回退。
- 必须确保 **同一 packageName + 相同证书 + 递增 versionCode**。更新成功后用户记录/设置不能丢失。**不可以每次 GitHub Action 用全新的随机 keystore 签名**。
- 如果 Release 不含合法签名 APK，说明原因；不能把 AAB 当可直接安装 APK，不能下载 source zip 当升级包。

官方参考：
- https://developer.android.com/guide/app-bundle/app-signing
- https://developer.android.google.cn/google/play/app-updates
- https://docs.github.com/en/rest/releases/releases#get-the-latest-release

---

## 9. CI/CD 与可持续签名（绝不能省略）

**硬要求：本机零 Android 环境。** 使用 GitHub-hosted `ubuntu-latest` runner + `actions/setup-java`（按当前 AGP/Gradle 兼容矩阵固定稳定 JDK），`android-actions/setup-android`（如果有必要），Gradle Wrapper，Android SDK/NDK 全在 runner 上下载/使用。开启 Gradle 缓存。**仓库需保留正规的 Gradle Wrapper 文件和校验**，不能在执行者本机下载 Android/Gradle 工具链。选用实施时官方兼容的 AGP、Kotlin、Compose BOM、Gradle 版本并固定依赖，CI 不使用 `+` 浮动版本。

### 9.1 android-ci.yml

触发 `push` 到主分支、`pull_request`、`workflow_dispatch`。
- checkout、setup JDK、Gradle cache、安全依赖校验；`./gradlew --no-daemon lint testDebugUnitTest assembleDebug`，可构建性检测、静态/单元测试。
- UI tests 用 Android emulator 在 CI 运行（合适时独立 job，考虑 runner 硬件加速和执行时间），至少首页、导航、权限提示、离线模型无文件态、历史导出、设置更新按钮态的真实 Compose instrumentation tests。
- 上传可追踪的 unsigned/debug **测试用** APK 和报告为 Actions artifact（不得混淆正式 Release APK），如有失败自动定位并修复后再次提交、等待 CI 通过。
- 提供依赖/模型 checksum 校验、避免未授权执行可疑 release actions；依赖固定权限 `permissions: contents: read`。

### 9.2 android-release.yml

触发 `v*` tag push 或显式 workflow_dispatch（安全防错误发布）；先完成 lint/关键测试，再 `assembleRelease`，应用**固定的同一私有签名证书**，`apksigner verify`，创建 `OpenListener-vX.Y.Z-arm64-v8a.apk`（或者若 ABI 通用则文件名与实际一致）、SHA256SUMS 和 update.json，上传 GitHub Releases（`permissions: contents: write`，仅 release job 使用）。**先验证后公布 Release**，不允许把失败构建发布成正式版。

版本从 Gradle 单一来源派生，`versionCode` 随版本递增；Tag、`BuildConfig`、`update.json`、APK metadata 保持一致。首次 tag `v0.1.0`，只在 CI 完成验证、正式 release 流程可运行后创建；发布说明真实列出功能及限制。

### 9.3 一次性签名密钥自动初始化（Agent 在已授权的本机 shell 完成）

**安全性高于盲目自动化**：Agent 要在仓库外安全临时目录生成一次签名证书并上传到当前 GitHub 仓库的 Actions Secrets；绝不上传到 Git、workflow artifact、issue、log、公开云盘。**生成 keystore 不需要 Android SDK**，允许本机使用 OpenSSL 生成 `.p12`（PKCS#12），Gradle 配置 `storeType = "pkcs12"`，key alias `openlistener`：

1. 用 `mktemp -d`、`umask 077` 和 `openssl req -x509 -newkey rsa:3072` 生成私钥与自签名证书；`openssl pkcs12 -export -name openlistener` 输出 `.p12`，使用强随机 password。
2. **通过 `gh secret set` 的标准输入**设置 `ANDROID_KEYSTORE_BASE64`（P12 的 Base64）、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_PASSWORD`（PKCS#12 通常与 store password 相同）。用 `gh secret list` 仅验证存在，不显示密钥内容。
3. CI 把 Secret 放入 env 后在 `$RUNNER_TEMP` 解码 .p12，Gradle 加载后签名；`post` 清理；不要上传 .p12 到任何 artifact。
4. 在 `~/.config/open-listener/`（不在 Git 工作目录）可选保留加密备份与证书指纹，设置 0600 权限；如果运行环境是无法保证持久性和安全性的临时 Agent 环境，就不要留下明文私钥，向用户明确说明 Secret 丢失后无法升级、必须安全备份。**不能以每次 CI 重新生成新证书作为“自动化方案”。**
5. 若 GitHub 权限不足不能设置 Secrets，自动使用 debug APK 仅供测试，不得冒充正式可无损升级 Release；完成源码与 CI 后明确阻塞，不暴露私钥。

禁止依赖 `gh` 临时 token 在 Actions 内自改仓库 Secret，禁止把用于签名的私钥或密码 echo 到日志。Release 只发布签名 APK。

### 9.4 建仓 + 运行循环（One-shot 的真实操作）

在**当前目录**，先确认 git 状态和当前 GitHub 账户权限。若已有 `.git`，检查 remote，避免覆盖另一个项目。正常新建顺序：

```bash
# 仅表示目标操作顺序，不代表这些命令已经执行
pwd
ls -la
gh auth status
git init -b main
# 生成上述全部源文件 / .gitignore / CI workflow / 文档 / 测试
git add .
git commit -m "feat: initial native Android Open Listener"
gh repo create open-listener --public --source=. --remote=origin --push
# 安全上传签名 secret，然后触发 CI
# 使用 gh run list / gh run view / gh run watch 检查结果，修复并重新 push
# build/test 通过后创建 v0.1.0 tag 并 push
# 使用 gh release view v0.1.0 确认签名 APK、SHA256SUMS 和 update.json 存在
```

以上只是顺序示例。**Agent 必须真的执行并核对 GitHub 返回的 URL、Actions 成功状态和 release assets**，而不是复制命令到最终答复。出现编译错时修改 Kotlin/Gradle 代码、提交并重试直到绿；不能因为本地不能编译就跳过 CI。

---

## 10. README：像顶级开源项目，而不是营销模板

**README 首屏要求**：

```text
[small original Open Listener glyph]

Open Listener

Listen. Understand. Keep the words.
Native Android · Live transcription · Translation · Privacy-first

[Download APK]  [See features]  [Build]  [License]

[real product screenshot(s) from CI emulator]

One tap. Live bilingual notes. No audio files.
```

其他段落顺序：`Why`、`Highlights`（4–6 点而非 20 个特性）、`Screenshots`（真实）、`Get Started`（GitHub Release APK 安装和未知来源授权）、`Offline Models & Cloud Providers`（能力状态诚实）、`How it works`（5 行 diagram）、`Privacy`（区分离线/云端）、`Build`（仅 GitHub Actions、如需自行构建开发者可另行使用 SDK，但本项目不要求用户本机配置）、`Contributing`、`License`、`Acknowledgements`。中英文 README 清晰互链。不要在首屏放 10 个低质 badge，最多 3–4 个真正有效的 GitHub CI/Release/License 链接。Logo/hero 必须为原创、可轻量渲染的 SVG，不调用商业素材，不生成无法维护的 base64 巨图。

设计截图采用原生 Android emulator + Android Test 启动 App 后抓取实际页面，生产缺模型/Key 时呈现真实 onboarding/空状态；可以为截图搭建仅存在于测试构建中的示例段落数据，截图必须注明 `Illustrative transcript in test build`，不可让人误以为真实课堂录制。

开源治理：LICENSE 选择需兼容整个 repo/第三方 SDK（应用源代码可 Apache-2.0；模型权重须另列其许可证）。`PRIVACY.md` 用直白语言说明没有录音保存，但云 API 会收到音频/文本；如果采用 Google ML Kit 也明确披露所依赖组件和下载行为。不伪造审计、性能基准、用户数、星标或项目维护者信息。

---

## 11. 验证计划与最终验收 Gate

### Gate A — 代码与构建

- [ ] 当前目录存在完整 Android Studio/Gradle 项目，`gradlew` 可在 GitHub Actions runner 编译；不是 Flutter 或 WebView。
- [ ] GitHub `android-ci.yml` lint / unit / APK 构建成功，至少一组 UI 自动化测试运行成功，有可见 CI run URL。
- [ ] 不存在手写假模型名/错误下载地址或 `TODO: implement real model` 却在 UI 中显示已就绪；项目可查明当前真实支持情况。

### Gate B — 主流程

- [ ] 录音/记录/设置三个顶部 tabs 实际可切换；深浅主题、中文英文、本地时间背景、按规格的几种动画可以在屏幕上观察。
- [ ] 麦克风权限拒绝/授权分别正确处理；识别引擎未就绪时提示配置，准备成功后实际进行 PCM 流式识别。
- [ ] 至少一个从上游下载的 sherpa-onnx streaming 中英模型能在 Android 上初始化并处理音频帧；至少一个 ML Kit 语言包能下载并翻译。没有设备联调时必须把“CI 验证”和“物理设备待验”分开写。
- [ ] 豆包 WebSocket adapter 完整、事件协议 fixture/mock tests 通过；真实账户功能仅在有真实测试 Key 时称已联调。
- [ ] 文本段落流式更新、翻译回填、UI 双语对应；意外中断后历史不丢已经确认的文本；从未写入音频文件。

### Gate C — 背景与数据

- [ ] Service 属于 microphone FGS，锁屏后不依赖 Activity；Notification 暂停/恢复/停止动作实际工作，保存状态合理。
- [ ] Android 文件中无音频落盘路径；没有 PCM log/API Key log；断网、低存储、Key 错误、模型损坏有可恢复错误提示。
- [ ] 记录可搜索/删除/复制，MD 与 PDF 真实生成并用 SAF 和系统分享，中文 PDF 无缺字/溢出。

### Gate D — Release 与更新

- [ ] `v0.1.0` Release 存在且包含**持续稳定签名** APK、SHA256SUMS、update.json；真实能下载、文件大小非零、SHA 匹配、签名验证通过。
- [ ] App `检查新版本` 能识别已是最新/新版本/网络失败；`下载新版本` 有真实进度且只通过用户确认调用系统安装器。
- [ ] GitHub repo public、README 真实精美、源码许可/隐私/贡献/安全文档和 CI badge 链接有效。

### 测试清单（至少）

1. `SessionRepository` 新增段落/排序/删除/中断恢复/迁移。
2. `SegmentAssembler` partial 替换、final 去重、二遍修正、时间戳、乱序翻译返回。
3. `TranslationQueue` 重试/限流/错语言/退出 drain。
4. `DoubaoProtocol` header/parser/binary frames、EOS、error status。
5. `ModelDownloader` checksum/pass/fail、resume、取消、ZIP 路径穿越、断网/空间不足。
6. `UpdateChecker` semver+versionCode、无 Release、下载 SHA 错误、签名不同、请求失败。
7. `Markdown/PdfExporter` 中文、长段落、特殊符号、分页、系统分享。
8. `Screen transitions`/权限及设置的 Compose UI instrumentation tests。
9. 前台服务：UI 关闭、模拟锁屏、暂停、结束、通知动作，至少模拟器测试并明确真实设备限制。
10. 最小压力测试：大量段落/2h 时间轴模拟事件不出现无限队列、Room 丢段或 UI 卡顿（可用人工 fixture，不需要存真实音频）。

**务必把自动化测试不具备的真机能力写在文档最后，而不是编造通过结果。**

---

## 12. Agent 执行流程（必须立即落地）

**Phase 0 — Bootstrap & Research**：检查当前目录、Git/GitHub 认证；查官方 API、Android 背景权限、sherpa SDK & 模型发布文件、ML Kit、最新兼容 Gradle/AGP/Kotlin。确定能真实编译的版本；记录模型合法来源和 SHA。

**Phase 1 — Design & App Shell**：生成原生 Kotlin Compose 项目、设计令牌、Ambient Sky composable、原创 logo、顶部 tab、中文/英文主题、空态和全部路由；任何设置可持久化。UI 预览/截图可在云端跑。

**Phase 2 — Data & Service**：Room + DataStore + API key security + AudioRecord microphone FGS + notification + 状态机 + 锁屏后仍继续服务；先实现带测试替身的端到端状态机，接着替换生产引擎。

**Phase 3 — Real Engines**：sherpa Android 真流式本地 ASR + 应用内下载 + ML Kit 翻译；豆包实时双向 WebSocket + 文本云翻译；按时间充裕和依赖可用性补齐阿里云/OpenAI 适配，严格记录实际完成状态。

**Phase 4 — Library & Update**：完整 MD/PDF、分享、搜索、设置页更新两按钮、GitHub Release 检查/签名下载/安装流程。

**Phase 5 — Repository Polish**：README 高品质原创 SVG、CI emulator 截图、文档治理、Issue 模板、隐私与许可声明。

**Phase 6 — Delivery Loop**：创建公开 GitHub repo → 签名 Secret 一次性准备 → push CI → `gh run watch` → 解析错误并修复、重复直至通过 → push tag v0.1.0 → 确认 Release 签名 APK/update.json/校验文件 → 验证更新检查逻辑 → 最终报告。

**最终交付答复模板**（只能填真实的链接、实测状态）：

```text
Repository: https://github.com/<owner>/open-listener
Release:    https://github.com/<owner>/open-listener/releases/tag/v0.1.0
APK:        <Release asset URL>
CI:         <successful GitHub Actions run URL>

Delivered: [实际已经运行和验证的功能]
Verified:  [自动化测试、云端构建、签名、是否实机]
Limits:    [需要自己的 API Key；具体厂商/OEM 息屏限制；未交付的可选适配器等]
Local Android SDK installed: NO
```

若存在权限/密钥/网络方面确实无法自行解决的外部阻塞，准确报告**完成到哪里、哪个命令失败、如何继续**，同时仍应提供已完成代码和可运行的 CI，不得只留下设计文档。

---

## 13. 正式外部资料（启动执行时重查，不要信过期版本名）

- Hark Pro 美学与设计： https://hark.com/articles/introducing-hark-pro
- Hark Pro 使用说明： https://hark.com/using-hark
- Hark Pro Android 官方： https://play.google.com/store/apps/details?id=com.hark.android
- Android microphone FGS： https://developer.android.com/develop/background-work/services/fgs/service-types
- Android FGS 后台限制： https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Compose： https://developer.android.com/develop/ui/compose
- sherpa-onnx Android： https://k2-fsa.github.io/sherpa/onnx/android/index.html
- sherpa-onnx streaming demo： https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnx
- ML Kit Translation： https://developers.google.com/ml-kit/language/translation/android
- 豆包双向流式识别： https://docs.volcengine.com/docs/doubao-voice/bidirectional-streaming-automatic-speech-recognition-websocket?lang=zh
- 阿里云实时 ASR： https://help.aliyun.com/zh/model-studio/real-time-speech-recognition-user-guide
- OpenAI Realtime Transcription： https://developers.openai.com/api/docs/guides/realtime-transcription
- GitHub Releases API： https://docs.github.com/en/rest/releases/releases#get-the-latest-release
- Android 签名与升级： https://developer.android.com/guide/app-bundle/app-signing

---

## 最后的指令（给 Agent）

**现在开始执行。目标不是回答“可以做”，而是把当前目录变成真实的 Open Listener Android GitHub 开源仓库，并产出 CI 构建的可安装签名 APK。先确保真实闭环、可测试和签名稳定，再追求视觉与附加 Provider 数量。即使用户之后只发送“按照文档执行”，也不要再次询问已经写在这里的产品决策。**
