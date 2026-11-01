package io.github.vexpaer.openlistener.service

import kotlin.math.sqrt

/** Pure presentation rules shared by collapsed and expanded foreground notifications. */
internal object NotificationSubtitle {
    fun select(mode: String, partialOriginal: String?, partialTranslation: String?,
               finalOriginal: String, finalTranslation: String): String =
        if (mode == "translation") partialTranslation?.takeIf { it.isNotBlank() } ?: finalTranslation
        else partialOriginal?.takeIf { it.isNotBlank() } ?: finalOriginal

    fun volume(level: Float, recording: Boolean): Int =
        if (!recording) 0 else (sqrt(level.coerceIn(0f, 1f)) * 300f).toInt().coerceIn(0, 100)
}
