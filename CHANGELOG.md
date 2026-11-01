# Changelog

## 0.1.16 · 2026-11-02

- Correct stale "configure models" banners: latest-wins preflight, explicit checked status, retry on preference/credential changes and foreground resume.
- Add checksum-pinned SenseVoice Small INT8, X-ASR 480 ms INT8 with punctuation, and Moonshine v2 English Tiny/Base.
- Add offline segment recognition for SenseVoice and Moonshine and keep X-ASR as a real streaming recognizer.
- Validate official archives, store exact installed file fingerprints and use the matching native sherpa-onnx offline APIs.


## 0.1.15 · 2026-11-02

- Model selections now use square theme-colored checkboxes for local speech and saved cloud profiles.
- Cloud account editing supports confirmed encrypted credential removal and safe selection fallback; fixes pink selected backgrounds and duplicate plus signs.
- Local model downloads move into the overflow menu; installed checkmarks follow the installed label.
- Dark-mode introduction uses white text, and the carrot button is slightly lighter.
- Saved transcript editing now happens directly on the detail page with contextual Save/Discard buttons.
- Playback seek knob and rail now share one Canvas centerline with a tested seek-coordinate transform.


## 0.1.14 · 2026-11-02

- 播放录音时字幕自动跟随并高亮；详情页新增正文编辑模式，标题点击即进入两行自动选中重命名。
- 首页缺少模型统一提示与直达设置内页；简化语言选项与记录操作菜单。
- 暖色主题全局衬线字体、白描边胡萝卜、渐变背景桌面组件和图标按钮。
- 桌面组件可从明确用户点击直接尝试启动麦克风前台服务，并兼容权限后备流程。
- 修正音频细进度条与滑块对齐，设置页增加作者署名。


## 0.1.13 · 2026-11-01

- 20 秒背景运动周期，暖色调动态衬线排版，胡萝卜录音按钮与紧凑字幕切换。
- 桌面小组件：实时字幕、开始／暂停／继续／停止控制，遵守 Android 麦克风前台启动要求。
- 云端模型改为可新增、可选择、可编辑的加密配置列表，迁移原有云端凭据。
- 版本进度与安装操作置于说明上方，简化通知并移除音量条。
- 更细的录音时间轨道、无尾部时长；新记录不再使用 Lecture 前缀。


## 0.1.12 · 2026-11-01

- Foreground notification displays the latest original or translated subtitles (user-selectable), an expanded audio meter and pause/resume/stop actions; duplicate app title removed.
- Streamlined English UI labels and enforced single-line controls on compact screens.
- Optional solid theme background, off by default.
- Seekable audio playback in saved records with one play/pause control above the transcript modes.
- Theme-aware selection chips; translation-only reading uses normal foreground text color.
- Notification presentation unit tests.


## 0.1.11 · 2026-11-01

- 豆包 AST 2.0 自动恢复临时 WebSocket 故障；有界内存音频队列在重连期间缓冲输入，异常时显示恢复状态。
- 按中英句末标点合并同传分句，实时字幕、历史记录及 Markdown/PDF/ZIP 导出统一分段。
- 保留原始分句用于异步译文修订；增加断线重连、标点合句与译文修订测试。
- 兼容豆包新旧控制台鉴权方式，改进 HTTP 401/403/429 及服务端追踪 ID 提示。


## 0.1.10 · 2026-10-30

- 反转本地 / 云端模型的选中与未选中视觉效果。
- 默认主题恢复原来的蓝色；保留用户已保存的自定义颜色。
- Logo 居中缩小到原来的 80%，四周填充纯白色。
- 新增 English GigaSpeech 流式 Zipformer INT8 模型，下载和权重均经过 SHA-256 校验。
- 设置面板不透明度提高至 30%。
- 记录页右下角新增全部导出 ZIP，包含每条记录的 Markdown 和已保留的 WAV 录音。
- 移除识别与翻译准备进度条，开始 / 暂停图形使用连续变形动画。
- 背景运动再提速两倍：完整周期由 80 秒缩短至 40 秒。

## 0.1.9 · 2026-10-30

- Remove emulator jobs and the manual emulator workflow; retain build, Lint, JVM tests and release verification.
- Apply the selected theme tones to the full ambient gradient and light blooms in both appearances.
- Double ambient motion speed (80-second cycle).
- Replace the inline HEX field and pink preset with a rainbow color-wheel entry and brightness control.
- Join Local / Cloud tabs and their content with square meeting edges with a horizontally sliding selection.
- Replace the record share emoji with a theme-tinted upload outline icon.
- Refresh bilingual README and release notes.

## 0.1.7 · 2026-10-30

- Reduce the tab equalizer to 15% opacity.
- Follow the actual transcript tail, including growing partials/translations; pause on manual history browsing and resume at the bottom.
- Add Doubao AST 2.0 S2T with protobuf transport, paired source/translation subtitles, final acknowledgements and pause/resume session handling.
- Bypass the separate translator for integrated speech and persist bilingual updates atomically.

## 0.1.6 · 2026-10-30

- Compact settings rows and local model cards; side-by-side update actions.
- A single share/export menu and continuous transcript reading surface.
- Redesigned API guides with numbered steps and linked cards.
- 90 ms character fade-in, tactile gradient recording controls and real microphone-driven tab equalizer at 30% opacity.
- Screenshot artifacts and UI checks for compact layouts, export menu and recording visualization.

## 0.1.5 · 2026-10-30

- Swipe navigation for Listen / Library / Settings; clearer English home copy.
- Dedicated speech and translation settings, with Local / Cloud switches inside each page.
- Verified English streaming Zipformer model with selectable local model preference.
- Qwen-ASR, OpenAI transcription and custom compatible cloud speech APIs with editable model IDs.
- Optional private WAV recording retention (off by default), 🎧 Library markers, in-app playback and audio deletion.
- Room v1 → v2 migration preserving existing transcripts; updated privacy documentation.
- Remove Library empty-state subtitle and rename source link to Github.

## 0.1.4 — 2026-10-30

- Replace the ambient sky with icy blue, soft drifting light and fine grain, with a matching dark palette.
- Add emoji help beside API configuration and inside credential dialogs, opening concise provider-specific guides with clickable official links.
- Keep unsaved API fields intact when opening and closing help.
- Correct merged-semantics lookup in the Library preview instrumentation check.

## 0.1.3 — 2026-10-30

- Use matching icon-only pause/resume and transparent stop controls.
- Remove listening status, timestamps and redundant introductory copy; use a 20% opacity live transcript surface.
- Show titles with a single-line transcript preview in Library and simplify Search.
- Group speech and translation settings into Local models / Cloud models detail pages.
- Reduce settings copy and fix inherited black text on translucent dark-theme surfaces.

## 0.1.2 — 2026-10-30

- Remove the top wordmark/status row and move navigation upward.
- Use capsule navigation and a sliding capsule indicator without pressed shading.
- Move spoken/translation language choices to the first Target language section.
- Move theme, app language and subtitle size into Appearance; remove system-choice and reduced-motion controls.
- Remove the Privacy & storage card and redundant listening labels.

## 0.1.1 — 2026-10-30

- Replace the launcher and documentation logo with the supplied Open Listener artwork.
- Publish Universal (ARM64 + x86_64) and smaller ARM64 APKs using the same stable signing certificate.
- Select the compatible update APK automatically, with per-artifact SHA-256/size checks and a Universal fallback for v0.1.0 clients.
- Display the selected APK architecture and download size in Settings.

## 0.1.0 — 2026-10-29

- Native Android Compose shell with top Listen / Library / Settings tabs.
- Text-only Room history, search, copy, rename, delete, Markdown/PDF export and sharing paths.
- Microphone foreground-service state machine with pause/resume/stop notification actions.
- Verified sherpa-onnx bilingual streaming model catalog and CI-only runtime preparation.
- Doubao SAUC v3 binary protocol parser, bounded WebSocket sending and error classification.
- ML Kit on-device translation and OpenAI-compatible cloud text translation adapters.
- GitHub Release update metadata, checksum and signing identity validation.
- CI, emulator tests and honest physical-device/provider validation notes.
