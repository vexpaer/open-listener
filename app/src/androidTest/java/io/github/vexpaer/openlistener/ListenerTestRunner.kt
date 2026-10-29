package io.github.vexpaer.openlistener

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import io.github.vexpaer.openlistener.asr.*
import io.github.vexpaer.openlistener.audio.PcmCapture
import io.github.vexpaer.openlistener.data.AppPreferences
import io.github.vexpaer.openlistener.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Test-only DI. Production APKs cannot select these synthetic engines. */
class ListenerTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestListenerApplication::class.java.name, context)
}

class TestListenerApplication : OpenListenerApp() {
    @Volatile var syntheticSession = false
    override fun newAsr(prefs: AppPreferences): AsrEngine = if (syntheticSession) SyntheticAsr() else super.newAsr(prefs)
    override fun newTranslator(prefs: AppPreferences): TranslationEngine = if (syntheticSession) SyntheticTranslator() else super.newTranslator(prefs)
    override fun newCapture(): PcmCapture = if (syntheticSession) SyntheticCapture() else super.newCapture()
}

private class SyntheticCapture : PcmCapture {
    override val audioFrames = Channel<ByteArray>(8)
    private var job: Job? = null
    override suspend fun start(scope: CoroutineScope, onLevel: (Float) -> Unit, onError: (Throwable) -> Unit) {
        job = scope.launch {
            while (isActive) {
                audioFrames.send(ByteArray(3_200))
                onLevel(0f)
                delay(100)
            }
        }
    }
    override suspend fun stop() { job?.cancelAndJoin(); job = null }
}

private class SyntheticAsr : AsrEngine {
    override val capabilities = AsrCapabilities("test-only", "Test only", true, setOf("en", "zh-CN"), false)
    override val availability = EngineAvailability.Ready
    private val output = Channel<TranscriptEvent>(32)
    override val events = output.receiveAsFlow()
    private var session = ""
    private var frames = 0
    private var sequence = 0L
    override suspend fun prepare() = availability
    override suspend fun start(sessionId: String) { session = sessionId }
    override suspend fun acceptPcm(pcm16: ByteArray) {
        if (++frames % 5 == 0) output.send(TranscriptEvent(session, sequence++, false,
            "Illustrative foreground-service test text", frames * 100L, frames * 100L + 100))
    }
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun flush() = Unit
    override suspend fun stop() = Unit
    override suspend fun close() { output.close() }
}

private class SyntheticTranslator : TranslationEngine {
    override val capabilities = TranslationCapabilities("test-only", "Test only", false, setOf("en", "zh-CN"))
    override val availability = EngineAvailability.Ready
    override suspend fun prepare() = availability
    override suspend fun translate(segmentId: Long, text: String, source: String, target: String) =
        TranslationResult(segmentId, "仅测试构建中的后台示例译文", "COMPLETED")
    override suspend fun close() = Unit
}
