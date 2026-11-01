## Open Listener 0.1.16

- 修复已保存模型和 API 配置却偶尔仍显示“请配置模型”的状态竞争：设置检查改为最新请求覆盖旧请求，取消重复检查，保存凭据或选择模型后立即复核，并在应用回到前台时刷新。
- 未完成首次模型检查或只是发生临时 SDK／网络错误时，不再误显示“请配置模型”。
- 新增 SenseVoice Small INT8（中、英、日、韩、粤），通过 sherpa-onnx 离线识别；连续音频采用有界分句识别。
- 新增 X-ASR 480 ms INT8（中／英，含标点），使用 sherpa-onnx 在线流式 Zipformer Transducer。
- 新增 Moonshine v2 English Tiny 和 English Base，采用匹配的 sherpa-onnx 离线推理接口，并在静音或有界片段结束时输出转写。
- 四个模型均使用上游正式发布的压缩包及 SHA-256 校验值；安装后记录本地文件指纹，使用时校验文件完整性。离线模型仅在本机处理录音，不上传音频。
- 保留原有 Zipformer 模型、历史记录及云端 API 配置；更新模型下载与版本说明。

安装新模型后请在设置中选择它。SenseVoice 和 Moonshine 属于分段离线识别，字幕通常在检测到停顿或达到分段上限后出现。
