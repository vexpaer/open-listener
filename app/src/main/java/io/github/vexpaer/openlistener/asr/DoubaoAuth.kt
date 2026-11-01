package io.github.vexpaer.openlistener.asr

import okhttp3.Request

/** Authentication and HTTP-handshake diagnostics shared by Doubao WebSocket services. */
internal object DoubaoAuth {
    fun hasCredentials(apiKey: String?, appId: String?, accessKey: String?): Boolean =
        !apiKey.isNullOrBlank() || (!appId.isNullOrBlank() && !accessKey.isNullOrBlank())

    fun addHeaders(
        builder: Request.Builder,
        apiKey: String?,
        appId: String?,
        accessKey: String?,
        resourceId: String,
        legacyAppHeader: String
    ): Request.Builder {
        val normalizedApiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedApiKey != null) {
            builder.header("X-Api-Key", normalizedApiKey)
        } else {
            val normalizedAppId = appId?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Doubao API credentials are incomplete")
            val normalizedAccessKey = accessKey?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("Doubao API credentials are incomplete")
            builder.header(legacyAppHeader, normalizedAppId)
                .header("X-Api-Access-Key", normalizedAccessKey)
        }
        return builder.header("X-Api-Resource-Id", resourceId)
    }

    fun handshakeError(service: String, status: Int, logId: String?): String {
        val detail = when (status) {
            401 -> "$service HTTP 401：API Key 或旧版鉴权凭据无效、已禁用或已过期。"
            403 -> "$service HTTP 403：当前凭据没有此模型的调用权限。请确认服务已开通，且 API Key 与模型服务属于同一项目。"
            404 -> "$service HTTP 404：服务接口或资源不存在，请检查接口版本和 Resource ID。"
            429 -> "$service HTTP 429：请求超过并发或用量额度。"
            in 500..599 -> "$service 服务端暂时不可用（HTTP $status）。"
            else -> "$service WebSocket 握手失败（HTTP $status）。"
        }
        val safeLogId = logId?.trim()?.takeIf { it.length <= 128 && it.matches(Regex("[A-Za-z0-9_-]+")) }
        return if (safeLogId == null) detail else "$detail 服务端追踪 ID：$safeLogId"
    }
}
