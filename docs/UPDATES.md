# 安全更新与文本导出

## Release 协议

客户端匿名读取 `https://api.github.com/repos/<BuildConfig.GITHUB_OWNER>/<BuildConfig.GITHUB_REPOSITORY>/releases/latest`，不需要 GitHub Token，只支持公开仓库。仅接受明确标记为 `draft=false`、`prerelease=false` 的 release。API `404` 返回 `NO_RELEASE` 失败（公开仓库不存在和没有稳定版无法由匿名 API 区分），不是“已是最新版”；`403` 限流和 `429` 返回 `RATE_LIMITED`，其余 HTTP、网络、元数据错误分别报告。

Release 必须包含唯一 `update.json` 以及其指名的唯一 APK。`update.json` 所有字段必填，不从 tag 的 patch 推算 versionCode，也不随意选择第一个 APK。

```json
{
  "tag": "v0.2.0",
  "versionName": "0.2.0",
  "versionCode": 2,
  "apkName": "OpenListener-v0.2.0.apk",
  "sha256": "<实际签名 APK 的 64 位十六进制 SHA-256，发布时替换>",
  "minSdk": 26,
  "applicationId": "io.github.vexpaer.openlistener"
}
```

上例为格式说明，不是可发布的 checksum。发布工作流应从最终已签名 APK/Gradle 单一版本来源提取 metadata，同时发布 `SHA256SUMS`；客户端以必填 `update.json.sha256` 核验实际 APK，不依赖可选或缺失的 checksum 文件。先 `apksigner verify` 并验证 metadata，再发布。

校验规则：

- `tag` 必须等于 release tag，并等于 `versionName` 或 `v` + `versionName`。`versionName` 使用完整 SemVer `major.minor.patch`（可含 build metadata，但稳定 release 不接受 prerelease）。比较数值而非字典序，忽略 build metadata 的优先级，支持已安装 prerelease 升级到正式版。
- Android versionCode 为显式整数，范围 `1..2100000000`。新版本必须同时满足 versionCode 和 SemVer 增大；相互矛盾时返回错误，不误报“已最新”。有效的相同/旧版本返回成功 `null`。
- `applicationId` 必须为当前安装包；minSdk 必须有效且不高于设备 API。APK 的 packageName/versionName/versionCode/minSdk 必须与 metadata 一致。
- asset URL 必须是 HTTPS、443、无凭据/query/fragment，且 decoded path 必须精确对应 `github.com/<owner>/<repo>/releases/download/<tag>/<assetName>`。禁止其他仓库、路径穿越、source archive/AAB、不安全文件名和任意 host。APK 大小上限 512 MiB，metadata 64 KiB，release JSON 2 MiB。
- 手动处理最多 5 次 HTTPS 重定向，仅允许 GitHub 和 `release-assets.githubusercontent.com` / `objects.githubusercontent.com` / `github-releases.githubusercontent.com` 的精确主机名；不允许 HTTP 降级、伪造后缀、任意 CDN。禁用 OkHttp 自动重定向。传入的 OkHttpClient 及其拦截器属于受信应用代码，不能安装跳过 TLS 验证的 TrustManager/HostnameVerifier。
- 下载使用私有 `cacheDir/updates` 随机 `.part` 文件；验证收到的字节数、SHA-256、PackageManager archive 身份和当前**非空、完全相同的 signer 集合**。API 26/27 使用 `GET_SIGNATURES`，28+ 使用 `GET_SIGNING_CERTIFICATES` 的当前 APK signers。此实现故意拒绝证书轮换/签名变更（包括多 signer 集合增减），不仅比较历史证书的交集。
- 只有全部通过才原子重命名 `.apk` 并返回；失败/取消删除本次文件。没有断点续传：重试从零重新下载和验证。进度为实际累计 bytes，total 为 release 声明并在下载结束严格核对的大小；回调在 `Dispatchers.Main.immediate` 上，初始/最终事件保证发送，中间按 256 KiB/100ms 节流。100% 仅表示字节已到齐，成功状态以 `download` 正常返回为准。
- 安装 intent 前重新校验文件路径、大小、checksum、package/version/signers，拒绝任意调用者提供的文件；仅允许本 updater 实例成功下载并登记的文件，进程重建后需要重新下载。系统安装器仍负责最终签名及设备策略检查。

## 更新调用接口

保留构造方式 `GitHubReleaseUpdater(context, client = OkHttpClient())`；内部仅保存 applicationContext。

```kotlin
val updater = GitHubReleaseUpdater(context)

// 在 ViewModel/viewModelScope 中调用；suspend 返回值恢复到调用者原来的线程。
val available: Result<UpdateInfo?> = updater.check()
// success(null): 已是最新版；failure(UpdateException): 明确失败原因。
val update = available.getOrThrow() ?: return@launch

// 确认当前无冲突会话后再让用户发起下载；UI 启用策略属于调用方。
val apk = updater.download(update) { received: Long, total: Long? ->
    // 始终在 Main；total 当前始终为合法 release asset.size。
    // 更新 StateFlow，不要在回调里做耗时操作。
}

if (!updater.canRequestPackageInstalls()) {
    context.startActivity(updater.unknownSourcesSettingsIntent())
    // 返回 App 后重新检查权限，再调用下面的 API；不要自动循环打开设置。
} else {
    context.startActivity(updater.installIntentFile(apk))
}
```

`installIntent(file)` 也是 suspend，与 `installIntentFile(file)` 等价。所有网络/文件/PackageManager 校验在 IO，调用者在 Main 收到结果后自行启动 intent。安装采用 `ACTION_VIEW` + `content://` FileProvider URI、`ClipData` 和 `FLAG_GRANT_READ_URI_PERMISSION`，绝不静默安装。`latestReleaseIntent()` 可由 UI 明确提供为浏览器回退。

必须由应用集成方配置（本任务限定不改 Manifest/UI）：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

FileProvider authority 必须为 `${applicationId}.fileprovider`，`exported=false`、`grantUriPermissions=true`；其 paths 需要 `<cache-path name="updates" path="updates/" />`（当前项目已有）。未授予安装权限时 `installIntentFile` 抛 `INSTALL_PERMISSION`；先引导 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 的本包设置，再让用户决定安装。设备管理策略或系统安装器仍可能拒绝。

取消 UI 所属 coroutine Job 会取消 OkHttp Call（包括阻塞读取），传播 `CancellationException`，不伪装普通 Result 失败；调用者不要用不区分取消的 `runCatching` 吞掉它。一般错误的 `UpdateException.reason` 可供 UI 映射本地化文本；不要把任意远端 response body 直接显示给用户。正式发布持续使用同一个安全保管的 keystore；SHA-256 metadata 不是独立签名信任根，当前已安装签名匹配才是额外身份约束。

## Markdown / PDF 导出 API

`TranscriptExporter` 只接受文本数据和 `OutputStream`，不申请存储权限、不创建原始音频文件。通过 SAF `ACTION_CREATE_DOCUMENT` 得到 URI 后外部调用 `contentResolver.openOutputStream(uri)`。以下 stream API **取得输出流所有权**，成功、失败或取消都会关闭；调用方不要继续使用该流。

```kotlin
val output = context.contentResolver.openOutputStream(uri) ?: error("Cannot open SAF output")
TranscriptExporter.writeMarkdown(session, segments, output) // UTF-8，suspend/IO
// 或：
val pdfOutput = context.contentResolver.openOutputStream(pdfUri) ?: error("Cannot open SAF output")
TranscriptExporter.writePdf(
    session, segments, pdfOutput,
    TranscriptExporter.PdfOptions(fontScale = 1.25f)
)
```

保留同步接口 `markdown(session, segments): String`、`markdown(session, segments, output)`、`pdf(session, segments, output, options = PdfOptions())`，同步导出请在工作线程调用。优先使用可取消 `writeMarkdown` / `writePdf`；取消不会被包装成成功，可能留有不完整 SAF 文档，调用方需提示用户并可删除该 URI。

- Markdown 标题/元数据/原文/译文 escape 格式符号、链接、HTML 和实体；转换换行，防缩进注入代码块；不把用户文本当 HTML/Markdown 执行。段落按 sequence 排序，保留时间戳、语言、日期、时长、状态和未完成翻译提示。
- PDF 为 A4（595 × 842 pt），有每页 Open Listener/日期页眉及页码、40 pt 左右边距。标题、元数据、原文、译文均走 `StaticLayout(TextPaint)` 测量，按完整 layout 行高分页，不固定 72 字符，不按 UTF-8 bytes 或 surrogate 切片。长标题、长段落、CJK、英文长词、emoji 和显式换行使用系统 shaping/换行。
- `sans-serif` 使用 Android 系统 CJK/emoji fallback，不捆绑仅 Latin 字体；API 28+ 启用 fallback 字体行距，API 26+ include font padding。`PdfOptions.fontScale` 支持 0.75–2.0，超范围报错。不能保证每个 OEM 包含所有 Unicode/emoji 字形，设备缺少的字形仍需实机检查。
- `PdfDocument` 在异常/取消时也 finish 当前 page 并 close；输出写失败不会遗漏清理。取消检查在段落、分页/行遍历及 PDF 写出期间；一次系统文字 shaping 或 PdfDocument 原生渲染调用仍不能被中途打断。
- 系统分享由调用方将导出产物写入私有 `cacheDir/shared/`，通过 FileProvider + `ACTION_SEND` + MIME (`text/markdown` 或 `application/pdf`) + URI 临时读权限发出，并在分享缓存 TTL 到期后清理；本 exporter 不擅自启动 Activity 或处理 SAF/分享生命周期。

## 测试及依赖

本次**没有执行本机 Android 构建、安装 SDK/模拟器、Git 操作或真实网络 APK 下载**。测试已编写，须由云端 CI 构建运行后才能宣称通过。

纯 JVM（`app/src/test/java/io/github/vexpaer/openlistener/`）：

- `updates/UpdatePolicyTest.kt`：SemVer 数值及 prerelease、显式 versionCode、错误/missing metadata、同版本/矛盾版本、draft/prerelease、错误 appId/tag/hash/minSdk/URL/大小、checksum、签名集合与 APK 身份不匹配。
- `export/TranscriptExporterTest.kt`：Markdown escape、中文/emoji、顺序/时间戳、UTF-8、流关闭/写入失败、长文本不硬切。

Android（`app/src/androidTest/java/io/github/vexpaer/openlistener/`）：

- `updates/GitHubReleaseUpdaterAndroidTest.kt`：内存 OkHttp fixture，无真实外网；匿名 latest API、404/429/500、metadata、主线程真实 bytes 回调、SHA 错误、非 APK、大小错误/恶意重定向、取消及拒绝任意安装文件、权限设置 intent。
- `export/TranscriptExporterAndroidTest.kt`：系统 CJK 字体、混排/emoji 行边界与宽度、长标题/双语正文 2 倍字体多页 PDF、PdfRenderer 实际渲染页眉/正文/页码/边界、失败流关闭和取消。

生产依赖：Android framework（minSdk 26）、`androidx.core:core-ktx` FileProvider、OkHttp、kotlinx serialization JSON、`kotlinx-coroutines-android`。测试使用现有 JUnit 4、AndroidX Test core/runner 与 coroutine 依赖，无 Robolectric、MockWebServer、coroutines-test 新依赖；AndroidJUnitRunner 下 JUnit 4 已由现有 Test runner/测试栈提供。未修改 Gradle。

在 GitHub Actions Android runner 执行（不是本机执行）：

```sh
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon connectedDebugAndroidTest
```

建议 emulator 至少 API 26/28 和较新 API 各一组。另需 CI/真机以**同 keystore 的递增版本 APK**验证成功下载→FileProvider→未知来源授权返回→系统用户确认安装，实际发布资产/CDN 重定向、字体 fallback、OEM 安装限制及 SAF provider 取消/磁盘写失败都不能由内存 fixture 完全替代。签名不一致应拒绝；本次测试不伪造可被系统接受的已签名 APK，也不自动点击安装器。

## 官方依据

- [GitHub latest release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [Android app signing](https://developer.android.com/guide/app-bundle/app-signing)
- [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager)
- [StaticLayout.Builder](https://developer.android.com/reference/android/text/StaticLayout.Builder)
- [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
