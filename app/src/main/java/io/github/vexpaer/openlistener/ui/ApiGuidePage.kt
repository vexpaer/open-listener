package io.github.vexpaer.openlistener.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun ApiHelpButton(tag: String, onClick: () -> Unit) {
    val description = tr("API 配置教程", "API setup guide")
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag).semantics { contentDescription = description }) {
        Text("📖", fontSize = 22.sp)
    }
}

/** A separate dialog keeps the credential editor composed, preserving unsaved fields. */
@Composable
internal fun ApiGuidePage(provider: String, savedBaseUrl: String, onOpenUrl: (String) -> Unit, onBack: () -> Unit) {
    val guide = apiGuide(provider, savedBaseUrl)
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("api-guide-page"), color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("api-guide-back")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr("返回配置", "Back to setup"))
                    }
                    Text(tr("API 配置教程", "API setup guide"), style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                                Icon(Icons.Outlined.Cloud, null, Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(guide.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(tr("三步完成连接", "Connect in three steps"), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    guide.steps.forEachIndexed { index, step ->
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                        Text("0${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(tr("官方入口", "Official links"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    guide.links.forEachIndexed { index, (title, url) ->
                        Surface(onClick = { onOpenUrl(url) }, shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("api-guide-link-$index")) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(title, style = MaterialTheme.typography.titleSmall)
                                    Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text(tr("云服务按服务商规则计费。", "Cloud usage is billed by the provider."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class ApiGuide(val title: String, val steps: List<String>, val links: List<Pair<String, String>>)

@Composable
private fun apiGuide(provider: String, savedBaseUrl: String): ApiGuide {
    val getKey = tr("获取 API Key", "Get an API key")
    val docs = tr("官方教程", "Official guide")
    return when (provider) {
        "doubao" -> ApiGuide(tr("豆包 · 语音识别", "Doubao · Speech recognition"), listOf(
            tr("打开豆包语音控制台，在同一项目中开通“流式语音识别 2.0”。", "Open the Doubao Speech console and enable Streaming ASR 2.0 in your project."),
            tr("在“API 管理”中创建并复制 API Key。", "Create and copy a key under API management."),
            tr("返回填写 X-Api-Key 和对应的 X-Api-Resource-Id。2.0 小时版资源 ID 为 volc.seedasr.sauc.duration，然后保存。", "Enter X-Api-Key and the matching X-Api-Resource-Id. The 2.0 hourly resource ID is volc.seedasr.sauc.duration, then save.")
        ), listOf(getKey to "https://console.volcengine.com/speech/new/setting/apikeys",
            docs to "https://docs.volcengine.com/docs/doubao-voice/console-guidance?lang=zh"))
        "asr_openai", "asr_custom" -> ApiGuide(tr("语音识别 · OpenAI 兼容", "Speech · OpenAI compatible"), listOf(
            tr("在服务商控制台创建 API Key 并开通语音转写模型。", "Create an API key and enable a speech transcription model."),
            tr("填写 HTTPS API 根路径和模型 ID，例如 gpt-transcribe。自定义服务必须兼容 /audio/transcriptions 的 WAV 文件上传。", "Enter the HTTPS API root and model ID, for example gpt-transcribe. Custom services must accept WAV uploads at /audio/transcriptions."),
            tr("每段音频最多 12 秒。可填写服务商支持的语音模型 ID。", "Audio is sent in chunks up to 12 seconds. Enter a speech model ID supported by your provider.")
        ), listOf(getKey to "https://platform.openai.com/api-keys", docs to "https://developers.openai.com/api/docs/guides/speech-to-text"))
        "asr_qwen" -> ApiGuide(tr("阿里云百炼 · 语音识别", "Alibaba Cloud · Speech recognition"), listOf(
            tr("在百炼创建 API Key，开通 Qwen-ASR。", "Create a Model Studio API key and enable Qwen-ASR."),
            tr("模型填写 qwen3-asr-flash 或兼容该接口的版本，Base URL 与 Key 地域一致。", "Use qwen3-asr-flash or a compatible version, with a Base URL in the same region as your key."),
            tr("通过 Qwen-ASR 的 Chat Completions 接口上传短音频。", "Short audio chunks are uploaded through the Qwen-ASR Chat Completions API.")
        ), listOf(getKey to "https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key", docs to "https://help.aliyun.com/zh/model-studio/qwen-asr-api-reference"))
        "translation_doubao" -> ApiGuide(tr("豆包方舟 · 翻译", "Doubao Ark · Translation"), listOf(
            tr("打开火山方舟控制台，选择项目并创建 API Key。", "Open Volcengine Ark, select your project and create an API key."),
            tr("开通可用的文本模型，复制模型 ID 或推理接入点 ep ID。", "Enable a text model and copy its model ID or inference endpoint ep ID."),
            tr("返回填入 Key 和模型 ID。Base URL 使用 https://ark.cn-beijing.volces.com/api/v3，保存即可。", "Enter the key and model ID. Use https://ark.cn-beijing.volces.com/api/v3 as the Base URL and save.")
        ), listOf(getKey to "https://ark.volcengine.com/region:cn-beijing/apiKey",
            docs to "https://docs.volcengine.com/docs/ark/quick-start?lang=zh"))
        "translation_qwen" -> ApiGuide(tr("阿里云百炼 · 翻译", "Alibaba Cloud Model Studio · Translation"), listOf(
            tr("登录阿里云百炼，选择地域并创建 API Key。", "Sign in to Model Studio, choose a region and create an API key."),
            tr("选择可用的文本模型，例如 qwen-plus。", "Choose an available text model, such as qwen-plus."),
            tr("返回填写 Key、模型名和同地域的 Base URL。北京地域可使用 https://dashscope.aliyuncs.com/compatible-mode/v1，然后保存。", "Enter the key, model name and a Base URL in the same region. Beijing can use https://dashscope.aliyuncs.com/compatible-mode/v1, then save.")
        ), listOf(getKey to "https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key",
            docs to "https://help.aliyun.com/zh/model-studio/get-api-key"))
        "translation_openai" -> ApiGuide("OpenAI · ${tr("翻译", "Translation")}", listOf(
            tr("登录 OpenAI API 平台，在项目中创建 API Key。", "Sign in to the OpenAI API platform and create an API key in your project."),
            tr("确认 API 账户有可用额度，选择支持 Chat Completions 的文本模型。", "Confirm that your API account has available credit and choose a text model supporting Chat Completions."),
            tr("返回填写 Key 和模型 ID。Base URL 使用 https://api.openai.com/v1，然后保存。", "Enter the key and model ID, use https://api.openai.com/v1 as the Base URL and save.")
        ), listOf(getKey to "https://platform.openai.com/api-keys",
            docs to "https://developers.openai.com/api/docs/quickstart"))
        else -> {
            val uri = Uri.parse(savedBaseUrl)
            val website = if (uri.scheme == "https" && !uri.host.isNullOrBlank())
                Uri.Builder().scheme("https").authority(uri.host).build().toString() else null
            ApiGuide(tr("自定义服务 · 翻译", "Custom provider · Translation"), listOf(
                tr("在你所选服务商的官网创建 API Key。", "Create an API key on your chosen provider's website."),
                tr("从服务商文档复制支持 OpenAI Chat Completions 格式的 HTTPS Base URL 和模型 ID。", "Copy an HTTPS Base URL and model ID supporting the OpenAI Chat Completions format from its documentation."),
                tr("返回填入 Key、Base URL 和模型 ID。Base URL 不含 /chat/completions，保存即可。", "Enter the key, Base URL and model ID. Leave /chat/completions off the Base URL and save.")
            ), listOfNotNull(website?.let { tr("所选服务商网站", "Selected provider website") to it },
                tr("兼容接口说明", "Compatible API format") to "https://developers.openai.com/api/reference/chat/create"))
        }
    }
}
