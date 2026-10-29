package io.github.vexpaer.openlistener.updates

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.vexpaer.openlistener.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resumeWithException

@Serializable
data class ReleaseAsset(val name: String, val browser_download_url: String, val size: Long)

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>
)

@Serializable
data class UpdateMetadata(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val apkName: String,
    val sha256: String,
    val minSdk: Int,
    val applicationId: String
)

data class UpdateInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val apk: ReleaseAsset,
    val sha256: String,
    val minSdk: Int,
    val applicationId: String,
    val notes: String?
)

class UpdateException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { NO_RELEASE, RATE_LIMITED, HTTP, NETWORK, INVALID_METADATA, INCOMPATIBLE, CHECKSUM, PACKAGE, INSTALL_PERMISSION }
}

/** Public releases only; no token is needed or sent. Keep this instance until installation. */
class GitHubReleaseUpdater(
    context: Context,
    client: OkHttpClient = OkHttpClient()
) {
    private val context = context.applicationContext
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val owner = BuildConfig.GITHUB_OWNER
    private val repository = BuildConfig.GITHUB_REPOSITORY
    private val endpoint = "https://api.github.com/repos/$owner/$repository/releases/latest"
    private val verifiedFiles = ConcurrentHashMap<String, UpdateInfo>()

    /** null means a valid latest release is not newer. Failures include NO_RELEASE, not null. */
    suspend fun check(): Result<UpdateInfo?> = try {
        Result.success(withContext(Dispatchers.IO) {
            val release = request(endpoint, api = true) { response ->
                json.decodeFromString<GitHubRelease>(readText(response, 2 * 1024 * 1024))
            }
            metadataRequire(!release.draft && !release.prerelease, "Latest release must be published and stable")
            val metadataAsset = release.assets.singleOrNull { it.name == "update.json" }
                ?: invalidMetadata("Latest release must contain exactly one update.json asset")
            UpdatePolicy.validateAsset(metadataAsset, release.tag_name, owner, repository, 64 * 1024L)
            val metadata = request(metadataAsset.browser_download_url) { response ->
                json.decodeFromString<UpdateMetadata>(readText(response, 64 * 1024))
            }
            val installed = installedIdentity()
            UpdatePolicy.select(release, metadata, owner, repository, installed, Build.VERSION.SDK_INT)
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(asUpdateException(failure))
    }

    /** Throws on failure/cancellation, removes partial files; progress always runs on Main. */
    suspend fun download(update: UpdateInfo, onProgress: (Long, Long?) -> Unit): File = withContext(Dispatchers.IO) {
        UpdatePolicy.validateUpdate(update, owner, repository, installedIdentity(), Build.VERSION.SDK_INT)
        val directory = cacheDirectory()
        val temporary = File.createTempFile("download-", ".part", directory)
        val target = File(directory, "${temporary.nameWithoutExtension}.apk")
        try {
            request(update.apk.browser_download_url) { response ->
                val body = response.body ?: throw IOException("GitHub returned an empty APK response")
                val contentLength = body.contentLength()
                metadataRequire(contentLength < 0 || contentLength == update.apk.size, "APK Content-Length differs from release asset size")
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var reported = 0L
                var lastReport = System.nanoTime()
                withContext(Dispatchers.Main.immediate) { onProgress(0, update.apk.size) }
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            received += count
                            metadataRequire(received <= update.apk.size, "APK exceeds the declared asset size")
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val now = System.nanoTime()
                            if (received - reported >= 256 * 1024 || now - lastReport >= 100_000_000L) {
                                withContext(Dispatchers.Main.immediate) { onProgress(received, update.apk.size) }
                                reported = received
                                lastReport = now
                            }
                        }
                        output.fd.sync()
                    }
                }
                metadataRequire(received == update.apk.size, "APK download is truncated")
                UpdatePolicy.verifyChecksum(digest.digest().toHex(), update.sha256)
                verifyArchive(temporary, update)
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(target)) throw IOException("Cannot commit verified APK to private cache")
                verifiedFiles[target.canonicalPath] = update
                withContext(Dispatchers.Main.immediate) { onProgress(received, update.apk.size) }
            }
            target
        } catch (failure: Throwable) {
            verifiedFiles.remove(target.canonicalPath)
            temporary.delete()
            target.delete()
            if (failure is CancellationException) throw failure
            currentCoroutineContext().ensureActive()
            if (failure is Exception) throw asUpdateException(failure)
            throw failure
        }
    }

    /** Revalidates on IO before handing a content URI to the system installer. Never starts an Activity. */
    suspend fun installIntentFile(file: File): Intent = withContext(Dispatchers.IO) {
        val canonical = file.canonicalFile
        val update = verifiedFiles[canonical.path]
            ?: throw UpdateException(UpdateException.Reason.PACKAGE, "Only an APK verified by this updater instance can be installed; download again")
        if (canonical.parentFile != cacheDirectory().canonicalFile || !canonical.isFile || canonical.length() != update.apk.size) {
            throw UpdateException(UpdateException.Reason.PACKAGE, "Verified APK is missing or outside private update cache")
        }
        UpdatePolicy.validateUpdate(update, owner, repository, installedIdentity(), Build.VERSION.SDK_INT)
        UpdatePolicy.verifyChecksum(checksum(canonical), update.sha256)
        verifyArchive(canonical, update)
        currentCoroutineContext().ensureActive()
        if (!canRequestPackageInstalls()) {
            throw UpdateException(UpdateException.Reason.INSTALL_PERMISSION, "Allow Open Listener to install unknown apps in system settings, then retry")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", canonical)
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Verified Open Listener update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun installIntent(file: File): Intent = installIntentFile(file)

    fun canRequestPackageInstalls(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun latestReleaseIntent(): Intent = Intent(
        Intent.ACTION_VIEW, Uri.parse("https://github.com/$owner/$repository/releases/latest")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun cacheDirectory(): File = File(context.cacheDir, "updates").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Cannot create private updates cache")
        if (canonicalFile.parentFile != context.cacheDir.canonicalFile) throw IOException("Invalid private updates cache path")
    }

    private fun installedIdentity(): PackageIdentity = packageIdentity(packageInfo(null))

    private fun verifyArchive(file: File, update: UpdateInfo) {
        val archive = packageIdentity(packageInfo(file))
        UpdatePolicy.verifyArchive(archive, installedIdentity(), update, Build.VERSION.SDK_INT)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(archive: File?): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (archive == null) context.packageManager.getPackageInfo(context.packageName, flags)
        else context.packageManager.getPackageArchiveInfo(archive.absolutePath, flags)
            ?: throw UpdateException(UpdateException.Reason.PACKAGE, "Downloaded file is not a readable signed APK")
    }

    @Suppress("DEPRECATION")
    private fun packageIdentity(info: PackageInfo): PackageIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return PackageIdentity(
            info.packageName,
            info.versionName ?: "",
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.applicationInfo?.minSdkVersion ?: throw UpdateException(UpdateException.Reason.PACKAGE, "APK has no application metadata"),
            signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
        )
    }

    private suspend fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private suspend fun readText(response: Response, limit: Int): String {
        val body = response.body ?: throw IOException("GitHub returned an empty metadata response")
        metadataRequire(body.contentLength() <= limit, "GitHub metadata is too large")
        return body.byteStream().use { input ->
            java.io.ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    metadataRequire(output.size() + count <= limit, "GitHub metadata is too large")
                    output.write(buffer, 0, count)
                }
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                decoder.decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
            }
        }
    }

    /** GitHub browser asset URLs redirect to its signed CDN; never follow arbitrary hosts or HTTP. */
    private suspend fun <T> request(url: String, api: Boolean = false, read: suspend (Response) -> T): T {
        var next = url.toHttpUrl()
        repeat(6) { redirects ->
            metadataRequire(if (api) next.toString() == endpoint else UpdatePolicy.isTrustedTransport(next), "Untrusted GitHub URL")
            val result = requestOnce(next, api) { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    metadataRequire(!api && redirects < 5, "Unexpected GitHub redirect")
                    val redirected = response.header("Location")?.let { next.resolve(it) }
                        ?: invalidMetadata("Invalid GitHub redirect")
                    metadataRequire(UpdatePolicy.isTrustedTransport(redirected), "Untrusted GitHub redirect")
                    RedirectOrValue<T>(redirected, null)
                } else {
                    requireSuccess(response, api)
                    val encoding = response.header("Content-Encoding")
                    metadataRequire(encoding == null || encoding.equals("identity", true), "Unexpected compressed GitHub response")
                    RedirectOrValue(null, read(response))
                }
            }
            result.redirect?.let { next = it } ?: return result.value as T
        }
        throw UpdateException(UpdateException.Reason.HTTP, "Too many GitHub asset redirects")
    }

    private data class RedirectOrValue<T>(val redirect: HttpUrl?, val value: T?)

    private suspend fun <T> requestOnce(url: HttpUrl, api: Boolean, read: suspend (Response) -> T): T = coroutineScope {
        val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
            .header("Accept", if (api) "application/vnd.github+json" else "application/octet-stream")
            .header("X-GitHub-Api-Version", "2022-11-28").build()
        val call = client.newCall(request)
        // Cancellation must close a blocked socket read, not only wait until the next buffer.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            call.awaitResponse().use { response ->
                metadataRequire(response.request.url == url, "HTTP client changed the trusted request URL")
                read(response)
            }
        } finally {
            cancellation.cancel()
        }
    }

    private fun requireSuccess(response: Response, api: Boolean) {
        if (response.isSuccessful) return
        val reason: UpdateException.Reason
        val message: String
        when {
            api && response.code == 404 -> {
                reason = UpdateException.Reason.NO_RELEASE
                message = "No public stable release was found (or the configured public repository does not exist)"
            }
            response.code == 429 || response.code == 403 && (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null) -> {
                reason = UpdateException.Reason.RATE_LIMITED
                message = "GitHub API rate limit reached; retry later (reset: ${response.header("X-RateLimit-Reset") ?: "unknown"})"
            }
            else -> {
                reason = UpdateException.Reason.HTTP
                message = "GitHub ${if (api) "public latest-release API" else "release asset"} request failed: HTTP ${response.code}"
            }
        }
        throw UpdateException(reason, message)
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

internal data class PackageIdentity(
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val signers: Set<String>
)

/** Android-free policy, shared by the network path, installation path and JVM tests. */
internal object UpdatePolicy {
    private val digestPattern = Regex("[a-fA-F0-9]{64}")
    private val apkPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")
    private const val MAX_APK_SIZE = 512L * 1024 * 1024

    fun select(release: GitHubRelease, metadata: UpdateMetadata, owner: String, repository: String, installed: PackageIdentity, sdk: Int): UpdateInfo? {
        metadataRequire(!release.draft && !release.prerelease, "Release is draft or prerelease")
        metadataRequire(metadata.tag == release.tag_name, "update.json tag differs from release tag")
        val apk = release.assets.singleOrNull { it.name == metadata.apkName }
            ?: invalidMetadata("Release must contain exactly one APK named by update.json")
        val update = UpdateInfo(metadata.tag, metadata.versionName, metadata.versionCode, apk, metadata.sha256.lowercase(), metadata.minSdk, metadata.applicationId, release.body)
        validateMetadata(update, owner, repository, installed.applicationId, sdk)
        val latest = SemanticVersion.parse(update.versionName)
        val current = SemanticVersion.parse(installed.versionName)
        // No patch-as-code derivation and no semantic downgrade disguised as a larger code.
        if (update.versionCode <= installed.versionCode) {
            metadataRequire(latest <= current, "Newer semantic version has non-increasing versionCode")
            return null
        }
        metadataRequire(latest > current, "Increasing versionCode must also increase the semantic version")
        return update
    }

    fun validateUpdate(update: UpdateInfo, owner: String, repository: String, installed: PackageIdentity, sdk: Int) {
        validateMetadata(update, owner, repository, installed.applicationId, sdk)
        metadataRequire(update.versionCode > installed.versionCode && SemanticVersion.parse(update.versionName) > SemanticVersion.parse(installed.versionName), "Update is not newer than the installed app")
    }

    private fun validateMetadata(update: UpdateInfo, owner: String, repository: String, applicationId: String, sdk: Int) {
        val version = SemanticVersion.parse(update.versionName)
        metadataRequire(version.prerelease.isEmpty(), "Stable release cannot contain a prerelease version")
        metadataRequire(update.tag == update.versionName || update.tag == "v${update.versionName}", "Tag and versionName disagree")
        metadataRequire(update.versionCode in 1..2_100_000_000L, "Invalid Android versionCode")
        metadataRequire(update.applicationId == applicationId, "Metadata applicationId differs from this app")
        metadataRequire(digestPattern.matches(update.sha256), "Missing or invalid SHA-256")
        metadataRequire(update.minSdk in 1..10_000, "Invalid minimum Android API")
        if (update.minSdk > sdk) throw UpdateException(UpdateException.Reason.INCOMPATIBLE, "Update requires Android API ${update.minSdk}; this device is API $sdk")
        metadataRequire(apkPattern.matches(update.apk.name) && !update.apk.name.contains(".."), "Unsafe or non-APK asset name")
        validateAsset(update.apk, update.tag, owner, repository, MAX_APK_SIZE)
    }

    fun validateAsset(asset: ReleaseAsset, tag: String, owner: String, repository: String, limit: Long) {
        metadataRequire(asset.size in 1..limit, "Invalid release asset size")
        val url = runCatching { asset.browser_download_url.toHttpUrl() }.getOrNull()
            ?: invalidMetadata("Invalid release asset URL")
        metadataRequire(url.isHttps && url.host == "github.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null, "Asset URL must be HTTPS on github.com without credentials, query or fragment")
        metadataRequire(url.pathSegments == listOf(owner, repository, "releases", "download", tag, asset.name), "Asset URL does not belong to this repository, release and asset")
    }

    fun isTrustedTransport(url: HttpUrl): Boolean = url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
        (url.host == "github.com" || url.host == "release-assets.githubusercontent.com" || url.host == "objects.githubusercontent.com" || url.host == "github-releases.githubusercontent.com")

    fun verifyChecksum(actual: String, expected: String) {
        if (!digestPattern.matches(expected) || !MessageDigest.isEqual(actual.lowercase().toByteArray(Charsets.US_ASCII), expected.lowercase().toByteArray(Charsets.US_ASCII))) {
            throw UpdateException(UpdateException.Reason.CHECKSUM, "APK SHA-256 checksum mismatch; the download was discarded")
        }
    }

    fun verifyArchive(archive: PackageIdentity, installed: PackageIdentity, update: UpdateInfo, sdk: Int) {
        val valid = archive.applicationId == installed.applicationId && archive.applicationId == update.applicationId &&
            archive.versionCode == update.versionCode && archive.versionCode > installed.versionCode &&
            archive.versionName == update.versionName && SemanticVersion.parse(archive.versionName) > SemanticVersion.parse(installed.versionName) &&
            archive.minSdk == update.minSdk && archive.minSdk <= sdk &&
            installed.signers.isNotEmpty() && archive.signers.isNotEmpty() && archive.signers == installed.signers
        if (!valid) throw UpdateException(UpdateException.Reason.PACKAGE, "APK package, version, minimum API or current signing certificates do not match; installation refused")
    }
}

internal data class SemanticVersion(val numbers: List<BigInteger>, val prerelease: List<String>) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        numbers.zip(other.numbers).forEach { (left, right) -> left.compareTo(right).takeIf { it != 0 }?.let { return it } }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) return when {
            prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
            prerelease.isEmpty() -> 1
            else -> -1
        }
        prerelease.zip(other.prerelease).forEach { (left, right) ->
            val leftNumber = left.toBigIntegerOrNull()
            val rightNumber = right.toBigIntegerOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (comparison != 0) return comparison
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$")
        fun parse(value: String): SemanticVersion {
            metadataRequire(value.length <= 128, "Semantic version is too long")
            val match = pattern.matchEntire(value) ?: invalidMetadata("Invalid semantic version: $value")
            val prerelease = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            metadataRequire(prerelease.none { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }, "Invalid semantic prerelease number")
            return SemanticVersion((1..3).map { match.groupValues[it].toBigInteger() }, prerelease)
        }
    }
}

private fun metadataRequire(valid: Boolean, message: String) { if (!valid) invalidMetadata(message) }
private fun invalidMetadata(message: String): Nothing = throw UpdateException(UpdateException.Reason.INVALID_METADATA, message)
private fun asUpdateException(failure: Exception): UpdateException = if (failure is UpdateException) failure else UpdateException(
    if (failure is IOException) UpdateException.Reason.NETWORK else UpdateException.Reason.INVALID_METADATA,
    if (failure is IOException) "GitHub update network request failed; check the connection and retry" else "GitHub release metadata is invalid", failure
)
private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
