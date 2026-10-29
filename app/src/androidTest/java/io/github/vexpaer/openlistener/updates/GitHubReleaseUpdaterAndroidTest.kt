package io.github.vexpaer.openlistener.updates

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.vexpaer.openlistener.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** In-memory HTTP fixtures only; never calls the live GitHub API or installs an APK. */
class GitHubReleaseUpdaterAndroidTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val base = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPOSITORY}/releases/download/v999.0.0"
    private val apk = ReleaseAsset("OpenListener.apk", "$base/OpenListener.apk", 3)
    private val metadata = UpdateMetadata("v999.0.0", "999.0.0", BuildConfig.VERSION_CODE.toLong() + 1, apk.name, "00".repeat(32), 26, BuildConfig.APPLICATION_ID)
    private val release = GitHubRelease(metadata.tag, draft = false, prerelease = false, assets = listOf(ReleaseAsset("update.json", "$base/update.json", 300), apk))

    @Test fun publicLatestApiReportsNoReleaseRateLimitAndHttpErrorsDistinctly() = runBlocking {
        for ((code, expected) in listOf(404 to UpdateException.Reason.NO_RELEASE, 429 to UpdateException.Reason.RATE_LIMITED, 500 to UpdateException.Reason.HTTP)) {
            val updater = updater { chain -> response(chain, code, "{}") }
            val failure = updater.check().exceptionOrNull() as UpdateException
            assertEquals(expected, failure.reason)
            assertTrue(failure.message!!.isNotBlank())
        }
    }

    @Test fun checkLoadsPublicMetadataAndDoesNotDeriveCodeFromTagPatch() = runBlocking {
        val updater = updater { chain ->
            assertTrue(chain.request().header("Authorization") == null)
            when {
                chain.request().url.encodedPath.endsWith("/latest") -> response(chain, 200, Json.encodeToString(release))
                else -> response(chain, 200, Json.encodeToString(metadata))
            }
        }
        val result = updater.check().getOrThrow()!!
        assertEquals(BuildConfig.VERSION_CODE.toLong() + 1, result.versionCode)
        assertEquals(metadata.sha256, result.sha256)
    }

    @Test fun checkSelectsArm64AndUniversalFromTheSameRelease() = runBlocking {
        val arm = apk.copy(name = "OpenListener-arm64-v8a.apk", browser_download_url = "$base/OpenListener-arm64-v8a.apk", size = 2)
        val meta = metadata.copy(artifacts = listOf(
            UpdateArtifactMetadata("universal", apk.name, metadata.sha256, apk.size, listOf("arm64-v8a", "x86_64")),
            UpdateArtifactMetadata("arm64-v8a", arm.name, "ab".repeat(32), arm.size, listOf("arm64-v8a"))
        ))
        val rel = release.copy(assets = release.assets + arm)
        for ((abis, expected) in listOf(listOf("arm64-v8a", "armeabi-v7a") to arm, listOf("x86_64", "x86") to apk)) {
            val updater = GitHubReleaseUpdater(context, OkHttpClient.Builder().addInterceptor { chain ->
                val body = if (chain.request().url.encodedPath.endsWith("/latest")) Json.encodeToString(rel) else Json.encodeToString(meta)
                response(chain, 200, body)
            }.build(), deviceAbis = abis)
            assertEquals(expected, updater.check().getOrThrow()!!.apk)
        }
    }

    @Test fun checksumFailureRemovesPartialFileAndReportsRealBytesOnMain() = runBlocking {
        val before = cacheFiles()
        val progress = mutableListOf<Pair<Long, Long?>>()
        val updater = updater { chain -> response(chain, 200, "abc") }
        try {
            updater.download(update()) { received, total ->
                assertEquals(Looper.getMainLooper(), Looper.myLooper())
                progress += received to total
            }
            throw AssertionError("Checksum mismatch must fail")
        } catch (failure: UpdateException) { assertEquals(UpdateException.Reason.CHECKSUM, failure.reason) }
        assertEquals(before, cacheFiles())
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.second == 3L && it.first in 0..3L })
    }

    @Test fun validChecksumCannotMakeAnInvalidApkInstallable() = runBlocking {
        val before = cacheFiles()
        val updater = updater { chain -> response(chain, 200, "abc") }
        val digest = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        try { updater.download(update().copy(sha256 = digest)) { _, _ -> }; throw AssertionError("Not an APK") }
        catch (failure: UpdateException) { assertEquals(UpdateException.Reason.PACKAGE, failure.reason) }
        assertEquals(before, cacheFiles())
    }

    @Test fun wrongSizeAndUntrustedRedirectAreRejectedWithoutPublishingFile() = runBlocking {
        val before = cacheFiles()
        val oversized = updater { chain -> response(chain, 200, "abcd") }
        try { oversized.download(update()) { _, _ -> }; throw AssertionError("Wrong size") }
        catch (failure: UpdateException) { assertEquals(UpdateException.Reason.INVALID_METADATA, failure.reason) }
        val redirect = updater { chain -> response(chain, 302, "").newBuilder().header("Location", "https://evil.example/update.apk").build() }
        try { redirect.download(update()) { _, _ -> }; throw AssertionError("Untrusted redirect") }
        catch (failure: UpdateException) { assertEquals(UpdateException.Reason.INVALID_METADATA, failure.reason) }
        assertEquals(before, cacheFiles())
    }

    @Test fun cancellationIsNotWrappedInResult() = runBlocking {
        val updater = updater { chain -> Thread.sleep(500); response(chain, 200, Json.encodeToString(release)) }
        var cancelled = false
        try { withTimeout(50) { updater.check() } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun installerRefusesArbitraryFilesAndSettingsIntentTargetsOnlyThisPackage() = runBlocking {
        val updater = updater { chain -> response(chain, 500, "") }
        val file = File.createTempFile("not-verified-", ".apk", context.cacheDir)
        try {
            try { updater.installIntentFile(file); throw AssertionError("Unverified file") }
            catch (failure: UpdateException) { assertEquals(UpdateException.Reason.PACKAGE, failure.reason) }
            assertFalse(file.readBytes().isNotEmpty())
            assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, updater.unknownSourcesSettingsIntent().action)
            assertEquals("package:${context.packageName}", updater.unknownSourcesSettingsIntent().dataString)
            assertEquals(Intent.ACTION_VIEW, updater.latestReleaseIntent().action)
        } finally { file.delete() }
    }

    private fun update() = UpdateInfo(metadata.tag, metadata.versionName, metadata.versionCode, apk, metadata.sha256, metadata.minSdk, metadata.applicationId, null)
    private fun updater(handler: (Interceptor.Chain) -> Response) = GitHubReleaseUpdater(context, OkHttpClient.Builder().addInterceptor { handler(it) }.build())
    private fun response(chain: Interceptor.Chain, code: Int, text: String) = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body(text.toResponseBody("application/octet-stream".toMediaType())).build()
    private fun cacheFiles(): Set<String> = File(context.cacheDir, "updates").listFiles().orEmpty().map { it.name }.toSet()
}
