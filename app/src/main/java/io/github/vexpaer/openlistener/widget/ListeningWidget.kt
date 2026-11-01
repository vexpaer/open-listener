package io.github.vexpaer.openlistener.widget

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.vexpaer.openlistener.MainActivity
import io.github.vexpaer.openlistener.R
import io.github.vexpaer.openlistener.service.MicrophoneRecognitionService

/**
 * Home-screen subtitle widget. Updates are pushed by the already-running foreground
 * service. Widget insertion or launcher reboot never starts microphone capture.
 */
class ListeningWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_START) return
        // A deliberate launcher widget click is a documented microphone-FGS exemption.
        // The first permission request (or an OEM rejection) falls back to the visible app.
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            try {
                ContextCompat.startForegroundService(context,
                    Intent(context, MicrophoneRecognitionService::class.java)
                        .setAction(MicrophoneRecognitionService.ACTION_START))
                return
            } catch (_: RuntimeException) {
                // SecurityException and ForegroundServiceStartNotAllowedException: open UI.
            }
        }
        context.startActivity(Intent(context, MainActivity::class.java)
            .setAction(ACTION_WIDGET_START)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, manager, it, "", "Ready", false, false) }
    }

    companion object {
        const val ACTION_WIDGET_START = "io.github.vexpaer.openlistener.WIDGET_START"

        fun publish(context: Context, text: String, state: MicrophoneRecognitionService.State) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ListeningWidget::class.java))
            if (ids.isEmpty()) return
            val recording = state == MicrophoneRecognitionService.State.RECORDING
            val paused = state == MicrophoneRecognitionService.State.PAUSED
            val status = when (state) {
                MicrophoneRecognitionService.State.RECORDING -> "Listening"
                MicrophoneRecognitionService.State.PAUSED -> "Paused"
                MicrophoneRecognitionService.State.PREPARING -> "Preparing"
                MicrophoneRecognitionService.State.STOPPING -> "Finishing"
                else -> "Ready"
            }
            ids.forEach { updateOne(context, manager, it, text, status, recording, paused) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int,
                              subtitle: String, status: String, recording: Boolean, paused: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.widget_listening)
            views.setTextViewText(R.id.widget_subtitle, subtitle.takeIf { it.isNotBlank() } ?: "Live subtitles appear here")
            views.setTextViewText(R.id.widget_state, status)
            views.setImageViewResource(R.id.widget_primary,
                if (recording) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            views.setContentDescription(R.id.widget_primary, when {
                recording -> "Pause"
                paused -> "Resume"
                else -> "Start"
            })
            val primaryIntent = if (recording || paused) {
                PendingIntent.getService(context, 1000 + id, Intent(context, MicrophoneRecognitionService::class.java)
                    .setAction(if (recording) MicrophoneRecognitionService.ACTION_PAUSE else MicrophoneRecognitionService.ACTION_RESUME),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else PendingIntent.getBroadcast(context, 1000 + id, Intent(context, ListeningWidget::class.java)
                .setAction(ACTION_WIDGET_START),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_primary, primaryIntent)
            val stopIntent = PendingIntent.getService(context, 2000 + id,
                Intent(context, MicrophoneRecognitionService::class.java).setAction(MicrophoneRecognitionService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_stop, stopIntent)
            views.setViewVisibility(R.id.widget_stop, if (recording || paused) android.view.View.VISIBLE else android.view.View.GONE)
            manager.updateAppWidget(id, views)
        }
    }
}
