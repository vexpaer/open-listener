package io.github.vexpaer.openlistener.updates

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val installed = PackageIdentity("io.github.vexpaer.openlistener", "1.9.9", 123, 26, setOf("signer-a"))
    private val asset = ReleaseAsset("OpenListener.apk", "https://github.com/vexpaer/open-listener/releases/download/v1.10.0/OpenListener.apk", 100)
    private val metadata = UpdateMetadata("v1.10.0", "1.10.0", 124, asset.name, "ab".repeat(32), 26, installed.applicationId)
    private val release = GitHubRelease(metadata.tag, draft = false, prerelease = false, assets = listOf(asset))

    private fun select(meta: UpdateMetadata = metadata, rel: GitHubRelease = release, sdk: Int = 36, abis: List<String> = emptyList()): UpdateInfo? =
        UpdatePolicy.select(rel, meta, "vexpaer", "open-listener", installed, sdk, abis)

    @Test fun comparesSemanticNumbersNotLexicallyOrPatchAsVersionCode() {
        val result = select()!!
        assertEquals(124L, result.versionCode)
        assertEquals("1.10.0", result.versionName)
        assertTrue(SemanticVersion.parse("1.10.0") > SemanticVersion.parse("1.9.99"))
        assertTrue(SemanticVersion.parse("1.0.0") > SemanticVersion.parse("1.0.0-rc.10"))
        assertTrue(SemanticVersion.parse("1.0.0-rc.10") > SemanticVersion.parse("1.0.0-rc.9"))
        assertEquals(0, SemanticVersion.parse("1.0.0+first").compareTo(SemanticVersion.parse("1.0.0+second")))
    }

    @Test fun latestSameVersionIsNullButInconsistentVersionAxesAreErrors() {
        val sameMeta = metadata.copy(tag = "v1.9.9", versionName = "1.9.9", versionCode = 123)
        val sameAsset = asset.copy(browser_download_url = asset.browser_download_url.replace("v1.10.0", "v1.9.9"))
        assertNull(select(sameMeta, release.copy(tag_name = sameMeta.tag, assets = listOf(sameAsset))))
        rejects { select(metadata.copy(versionCode = 123)) }
        rejects { select(sameMeta.copy(versionCode = 124), release.copy(tag_name = sameMeta.tag, assets = listOf(sameAsset))) }
    }

    @Test fun rejectsMissingRequiredMetadataFieldsAndMalformedSemver() {
        listOf("{}", "{\"tag\":\"v1.10.0\",\"versionName\":\"1.10.0\",\"versionCode\":124}").forEach { text ->
            try { Json.decodeFromString<UpdateMetadata>(text); throw AssertionError("Accepted incomplete metadata") }
            catch (_: kotlinx.serialization.SerializationException) { }
        }
        listOf("v1.2.3", "1.02.3", "1.2", "1.2.3.4", "1.2.3-01", "", "1.2.3 ").forEach { value ->
            rejects { SemanticVersion.parse(value) }
        }
    }

    @Test fun rejectsDraftPrereleaseTagDigestPackageAndVersionErrors() {
        rejects { select(rel = release.copy(draft = true)) }
        rejects { select(rel = release.copy(prerelease = true)) }
        rejects { select(metadata.copy(tag = "v1.10.1")) }
        rejects { select(metadata.copy(versionName = "1.10.0-rc.1")) }
        rejects { select(metadata.copy(sha256 = "")) }
        rejects { select(metadata.copy(sha256 = "z".repeat(64))) }
        rejects { select(metadata.copy(applicationId = "attacker.app")) }
        rejects { select(metadata.copy(versionCode = 0)) }
        rejects { select(metadata.copy(versionCode = 2_100_000_001L)) }
        rejects { select(metadata.copy(minSdk = 0)) }
        rejects(UpdateException.Reason.INCOMPATIBLE) { select(metadata.copy(minSdk = 37)) }
    }

    @Test fun rejectsAmbiguousMissingNonApkOversizedOrUntrustedAssets() {
        rejects { select(rel = release.copy(assets = emptyList())) }
        rejects { select(rel = release.copy(assets = listOf(asset, asset))) }
        listOf(
            "http://github.com/vexpaer/open-listener/releases/download/v1.10.0/OpenListener.apk",
            "https://github.com.evil.example/vexpaer/open-listener/releases/download/v1.10.0/OpenListener.apk",
            asset.browser_download_url.replace("vexpaer/", "another/"),
            asset.browser_download_url.replace("v1.10.0", "v0.1.0"),
            "https://user:password@github.com/vexpaer/open-listener/releases/download/v1.10.0/OpenListener.apk",
            "${asset.browser_download_url}?token=leak",
            "${asset.browser_download_url}#fragment"
        ).forEach { url -> rejects { select(rel = release.copy(assets = listOf(asset.copy(browser_download_url = url)))) } }
        rejects { select(rel = release.copy(assets = listOf(asset.copy(size = 0)))) }
        rejects { select(rel = release.copy(assets = listOf(asset.copy(size = 513L * 1024 * 1024)))) }
        rejects { select(metadata.copy(apkName = "source.zip"), release.copy(assets = listOf(asset.copy(name = "source.zip")))) }
        assertTrue(!UpdatePolicy.isTrustedTransport("https://evil.example/update.apk".toHttpUrl()))
        assertTrue(!UpdatePolicy.isTrustedTransport("http://release-assets.githubusercontent.com/file".toHttpUrl()))
        assertTrue(UpdatePolicy.isTrustedTransport("https://release-assets.githubusercontent.com/file?signature=public".toHttpUrl()))
    }

    @Test fun checksumIsMandatoryAndCaseInsensitive() {
        UpdatePolicy.verifyChecksum("AB".repeat(32), "ab".repeat(32))
        rejects(UpdateException.Reason.CHECKSUM) { UpdatePolicy.verifyChecksum("cd".repeat(32), metadata.sha256) }
        rejects(UpdateException.Reason.CHECKSUM) { UpdatePolicy.verifyChecksum(metadata.sha256, "") }
    }

    @Test fun archiveMustMatchEveryMetadataFieldAndExactNonemptyCurrentSigners() {
        val update = select()!!
        val archive = PackageIdentity(installed.applicationId, update.versionName, update.versionCode, update.minSdk, installed.signers)
        UpdatePolicy.verifyArchive(archive, installed, update, 36)
        listOf(
            archive.copy(applicationId = "attacker.app"),
            archive.copy(versionName = "1.10.1"),
            archive.copy(versionCode = 125),
            archive.copy(minSdk = 27),
            archive.copy(signers = emptySet()),
            archive.copy(signers = setOf("signer-b")),
            archive.copy(signers = setOf("signer-a", "signer-b"))
        ).forEach { invalid -> rejects(UpdateException.Reason.PACKAGE) { UpdatePolicy.verifyArchive(invalid, installed, update, 36) } }
        rejects(UpdateException.Reason.PACKAGE) { UpdatePolicy.verifyArchive(archive, installed.copy(signers = emptySet()), update, 36) }
        rejects(UpdateException.Reason.PACKAGE) { UpdatePolicy.verifyArchive(archive, installed, update, 25) }
    }

    @Test fun selectsArm64OrUniversalByDeviceAbiAndPreservesLegacyMetadata() {
        val arm = asset.copy(name = "OpenListener-arm64-v8a.apk", browser_download_url = asset.browser_download_url.replace("OpenListener.apk", "OpenListener-arm64-v8a.apk"), size = 50)
        val universal = UpdateArtifactMetadata("universal", asset.name, metadata.sha256, asset.size, listOf("arm64-v8a", "x86_64"))
        val armMetadata = UpdateArtifactMetadata("arm64-v8a", arm.name, "cd".repeat(32), arm.size, listOf("arm64-v8a"))
        val meta = metadata.copy(artifacts = listOf(universal, armMetadata))
        val rel = release.copy(assets = listOf(arm, asset))
        assertEquals(arm.name, select(meta, rel, abis = listOf("arm64-v8a", "armeabi-v7a"))!!.apk.name)
        assertEquals("arm64-v8a", select(meta, rel, abis = listOf("arm64-v8a"))!!.abi)
        assertEquals(asset.name, select(meta, rel, abis = listOf("x86_64", "x86"))!!.apk.name)
        assertEquals(asset.name, select(abis = listOf("arm64-v8a"))!!.apk.name)
        val olderJson = Json { ignoreUnknownKeys = true }.encodeToString(UpdateMetadata.serializer(), meta)
        assertEquals(asset.name, Json { ignoreUnknownKeys = true }.decodeFromString<UpdateMetadata>(olderJson).apkName)
        rejects(UpdateException.Reason.INCOMPATIBLE) { select(meta, rel, abis = listOf("x86")) }
        rejects(UpdateException.Reason.INCOMPATIBLE) { select(meta, rel, abis = emptyList()) }
        val selected = select(meta, rel, abis = listOf("arm64-v8a"))!!
        rejects(UpdateException.Reason.INCOMPATIBLE) { UpdatePolicy.validateUpdate(selected, "vexpaer", "open-listener", installed, 36, listOf("x86_64")) }
    }

    @Test fun rejectsInvalidAbiMetadataWithoutWeakeningAssetValidation() {
        val universal = UpdateArtifactMetadata("universal", asset.name, metadata.sha256, asset.size, listOf("arm64-v8a", "x86_64"))
        fun check(entries: List<UpdateArtifactMetadata>) = select(metadata.copy(artifacts = entries), abis = listOf("arm64-v8a"))
        rejects { check(listOf(universal, universal)) }
        rejects { check(listOf(universal.copy(sha256 = "cd".repeat(32)))) }
        rejects { check(listOf(universal.copy(size = 99))) }
        rejects { check(listOf(universal.copy(supportedAbis = emptyList()))) }
        rejects { check(listOf(universal.copy(supportedAbis = listOf("mips")))) }
        rejects { check(listOf(universal.copy(abi = "mips"))) }
        rejects { check(listOf(universal.copy(abi = "arm64-v8a", supportedAbis = listOf("arm64-v8a")))) }
    }

    private fun rejects(reason: UpdateException.Reason = UpdateException.Reason.INVALID_METADATA, action: () -> Unit) {
        try { action(); throw AssertionError("Unsafe input was accepted") }
        catch (failure: UpdateException) { assertEquals(reason, failure.reason) }
    }
}
