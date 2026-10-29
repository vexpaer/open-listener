# Open Listener

**Listen. Understand. Keep the words.**

原生 Android · 实时双语文字 · 从不保存音频

[English](README.md) · [构建](#构建) · [隐私](PRIVACY.md)

> **当前为未构建验证的源码实现，不是已发布版本。** 创建公开 GitHub 仓库被执行权限边界拦截，尚未运行 Android/JVM 测试，没有可下载 APK、成功 CI 或 Release。待验证项目见 [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)。

Open Listener 面向课程、讲座和研讨会。打开应用后在可见页面点击一次，实时阅读原文与译文，并将已经确认的文字保存为可搜索记录。麦克风 PCM 不会写入音频文件。

## 功能

- **录音：** 顶部三 Tab、双语实时文字、partial/final 区分、通知栏暂停/继续/结束。
- **记录：** Room 持久化已确认段落，支持搜索、复制、改标题、删除、Markdown/PDF 导出和 Android 分享。
- **数据路径透明：** 可选本地 sherpa-onnx 流式识别 + ML Kit 设备端翻译，或自行配置云端服务商。
- **安全更新：** 检查公开稳定 Release 的元数据、SHA-256、包名、版本和签名证书后才交给系统安装器。

UI 测试中的示例文字会明确标注为测试构建示例，不代表真实课堂内容。

## 隐私重点

- 音频只经过有界内存 PCM 管线，不保存 URI、文件或回放队列。
- 本地识别和 ML Kit 在模型安装后可保持设备端路径；ML Kit 是第三方闭源 SDK。
- 豆包识别会向火山引擎发送实时 PCM；云端翻译会发送已确认文字。凭据保存在 Android 加密偏好中，不会导出。
- Android 前台服务和厂商省电策略仍可能影响长时间会话，不能承诺所有设备永不中断。

详细说明见 [`PRIVACY.md`](PRIVACY.md) 和 [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)。

## 构建

项目使用 Kotlin、Jetpack Compose、Material 3，`minSdk 26`、`targetSdk 36`、Java 17。仓库不要求用户本机安装 Android SDK；GitHub Actions 会下载固定工具链，并校验 sherpa-onnx 运行时。

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

该命令适用于已有 Android 工具链的环境；项目支持的可复现构建路径是 GitHub Actions。贡献方式见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

## 许可证

应用源代码采用 Apache-2.0。上游 sherpa-onnx runtime/source 与模型权重保留各自许可证，版本、来源和校验值见 [`docs/MODEL_CATALOG.md`](docs/MODEL_CATALOG.md)。
