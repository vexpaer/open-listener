package io.github.vexpaer.openlistener.models

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class ModelPhase { NOT_INSTALLED, DOWNLOADING, VERIFYING, EXTRACTING, VALIDATING, INSTALLED, ERROR }

data class ModelStatus(
    val id: String,
    val phase: ModelPhase,
    val downloadedBytes: Long = 0,
    val totalBytes: Long,
    val error: String? = null,
    val inUse: Boolean = false
)

/** A process-owned reference; the model may not be replaced/deleted until close(). */
class ModelLease internal constructor(val directory: File, private val release: () -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

/**
 * App-private, checksum-pinned model installation. Keep one instance in Application.
 * download/delete are suspending operations; the caller owns their cancellation.
 * No microphone PCM, packaged test WAVs, or user audio is written to disk here.
 */
class ModelStore(context: Context) {
    val catalog: List<ModelManifest> = ModelCatalog.models
    private val root = File(context.applicationContext.noBackupFilesDir, "models")
    private val downloader = ModelDownloader()
    private val _state = MutableStateFlow(catalog.associate { model ->
        model.id to ModelStatus(model.id, if (installedDirectory(model) != null) ModelPhase.INSTALLED
            else ModelPhase.NOT_INSTALLED, totalBytes = model.bytes)
    })
    val state: StateFlow<Map<String, ModelStatus>> = _state.asStateFlow()

    fun status(id: String = DEFAULT_ID): ModelStatus = _state.value.getValue(id)
    fun isInstalled(id: String = DEFAULT_ID): Boolean = installedDirectory(manifest(id)) != null
    fun modelDirectory(id: String = DEFAULT_ID): File? = installedDirectory(manifest(id))

    suspend fun download(id: String = DEFAULT_ID) = withContext(Dispatchers.IO) {
        val model = manifest(id)
        val key = key(model)
        operationMutexes.getOrPut(key) { Mutex() }.withLock {
            if (installedDirectory(model) != null) {
                publish(model, ModelPhase.INSTALLED, model.bytes)
                return@withLock
            }
            reserveMutation(key)
            val staging = File(root, ".staging-${model.id}-${UUID.randomUUID()}")
            try {
                check(root.isDirectory || root.mkdirs()) { "Unable to create private model storage" }
                cleanupInterruptedInstall(model)
                publish(model, ModelPhase.DOWNLOADING)
                val archive = downloader.download(
                    model.url, File(root, "${model.id}.tar.bz2"), model.sha256,
                    model.bytes, model.installedBytes
                ) { received, _ -> publish(model, ModelPhase.DOWNLOADING, received) }
                publish(model, ModelPhase.VERIFYING, model.bytes)
                currentCoroutineContext().ensureActive()
                check(downloader.sha256(archive) == model.sha256) { "Model archive checksum mismatch" }
                publish(model, ModelPhase.EXTRACTING, model.bytes)
                downloader.safeExtractTarBz2(archive, staging, model)
                publish(model, ModelPhase.VALIDATING, model.bytes)
                currentCoroutineContext().ensureActive()
                SherpaModelRuntime.validate(staging, model)
                currentCoroutineContext().ensureActive()
                File(staging, MARKER).outputStream().use { stream ->
                    stream.write(model.sha256.toByteArray(Charsets.US_ASCII))
                    stream.fd.sync()
                }
                val active = directory(model)
                // A corrupt/incomplete previous install is never used. A new directory
                // is published by one same-filesystem rename only after native validation.
                if (active.exists()) check(active.deleteRecursively()) { "Unable to remove corrupt installation" }
                check(staging.renameTo(active)) { "Unable to atomically activate the verified model" }
                publish(model, ModelPhase.INSTALLED, model.bytes)
                archive.delete()
            } catch (cancelled: CancellationException) {
                publish(model, if (isInstalled(id)) ModelPhase.INSTALLED else ModelPhase.NOT_INSTALLED,
                    error = "Download cancelled; verified partial download can be resumed")
                throw cancelled
            } catch (error: Exception) {
                publish(model, ModelPhase.ERROR, error = error.message ?: "Model installation failed")
                throw error
            } catch (error: LinkageError) {
                publish(model, ModelPhase.ERROR, error = "Sherpa JNI runtime is unavailable or incompatible")
                throw error
            } finally {
                staging.deleteRecursively()
                releaseMutation(key)
            }
        }
    }

    suspend fun delete(id: String = DEFAULT_ID) = withContext(Dispatchers.IO) {
        val model = manifest(id)
        val key = key(model)
        // Fail promptly instead of queueing behind a potentially long download.
        check(operationMutexes.getOrPut(key) { Mutex() }.tryLock()) { "Model download or deletion is in progress" }
        try {
            reserveMutation(key)
            try {
                check(!directory(model).exists() || directory(model).deleteRecursively()) { "Unable to delete model" }
                File(root, "${model.id}.tar.bz2").delete()
                File(root, "${model.id}.tar.bz2.part").delete()
                cleanupInterruptedInstall(model)
                publish(model, ModelPhase.NOT_INSTALLED)
            } finally {
                releaseMutation(key)
            }
        } finally {
            operationMutexes.getValue(key).unlock()
        }
    }

    /** Full per-file checksums are rechecked off the UI thread before a recognizer uses a model. */
    suspend fun acquire(id: String = DEFAULT_ID): ModelLease = withContext(Dispatchers.IO) {
        val model = manifest(id)
        val key = key(model)
        val path = synchronized(useLock) {
            check(key !in mutating) { "Model installation or deletion is in progress" }
            val installed = installedDirectory(model) ?: throw IOException("Download the local speech model first")
            users[key] = (users[key] ?: 0) + 1
            installed
        }
        val lease = ModelLease(path) {
            synchronized(useLock) {
                val remaining = (users[key] ?: 1) - 1
                if (remaining <= 0) users.remove(key) else users[key] = remaining
            }
            _state.update { old -> old + (id to old.getValue(id).copy(inUse = isInUse(key))) }
        }
        try {
            model.files.forEach { expected ->
                currentCoroutineContext().ensureActive()
                check(downloader.sha256(File(path, expected.path)) == expected.sha256) {
                    "Installed model is damaged; delete and download it again"
                }
            }
            publish(model, ModelPhase.INSTALLED, model.bytes)
            lease
        } catch (error: Throwable) {
            lease.close()
            if (error !is CancellationException) publish(model, ModelPhase.ERROR, error = error.message)
            throw error
        }
    }

    private fun publish(model: ModelManifest, phase: ModelPhase, received: Long = 0, error: String? = null) {
        val status = ModelStatus(model.id, phase, received, model.bytes, error, isInUse(key(model)))
        _state.update { it + (model.id to status) }
    }

    private fun installedDirectory(model: ModelManifest): File? {
        val path = directory(model)
        val marker = File(path, MARKER)
        if (!marker.isFile || marker.length() != 64L || marker.readText(Charsets.US_ASCII) != model.sha256) return null
        return path.takeIf { model.files.all { required ->
            val file = File(path, required.path)
            file.isFile && file.length() == required.bytes
        } }
    }

    private fun manifest(id: String) = catalog.singleOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown model: $id")
    private fun directory(model: ModelManifest) = File(root, "${model.id}-${model.sha256.take(16)}")
    private fun key(model: ModelManifest) = directory(model).absolutePath
    private fun isInUse(key: String) = synchronized(useLock) { (users[key] ?: 0) > 0 }
    private fun reserveMutation(key: String) = synchronized(useLock) {
        check((users[key] ?: 0) == 0) { "This model is in use; stop recognition before deleting or replacing it" }
        check(mutating.add(key)) { "Model storage is busy" }
    }
    private fun releaseMutation(key: String) { synchronized(useLock) { mutating.remove(key) } }
    private fun cleanupInterruptedInstall(model: ModelManifest) {
        root.listFiles()?.filter { it.name.startsWith(".staging-${model.id}-") }?.forEach {
            check(it.deleteRecursively()) { "Unable to remove interrupted model staging directory" }
        }
    }

    companion object {
        const val DEFAULT_ID = ModelCatalog.DEFAULT_ID
        private const val MARKER = ".verified-sha256"
        // Shared by all stores in this process, including service/UI and tests.
        private val useLock = Any()
        private val users = mutableMapOf<String, Int>()
        private val mutating = mutableSetOf<String>()
        private val operationMutexes = ConcurrentHashMap<String, Mutex>()
    }
}
