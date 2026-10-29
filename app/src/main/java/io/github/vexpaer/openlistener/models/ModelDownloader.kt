package io.github.vexpaer.openlistener.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

class ModelDownloader {
    /** Compatibility entry point; production manifests should supply expectedBytes. */
    suspend fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        onProgress: suspend (received: Long, total: Long?) -> Unit
    ): File = downloadChecked(url, destination, expectedSha256, null, 0, onProgress)

    suspend fun download(
        url: String,
        destination: File,
        expectedSha256: String,
        expectedBytes: Long,
        reserveBytes: Long,
        onProgress: suspend (received: Long, total: Long?) -> Unit
    ): File = downloadChecked(url, destination, expectedSha256, expectedBytes, reserveBytes, onProgress)

    private suspend fun downloadChecked(
        url: String,
        destination: File,
        expectedSha256: String,
        expectedBytes: Long?,
        reserveBytes: Long,
        onProgress: suspend (Long, Long?) -> Unit
    ): File = withContext(Dispatchers.IO) {
        requireHttps(url)
        require(expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "A real SHA-256 is required" }
        require(expectedBytes == null || expectedBytes > 0)
        val parent = requireNotNull(destination.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create model download directory" }
        if (destination.isFile && (expectedBytes == null || destination.length() == expectedBytes) &&
            sha256(destination).equals(expectedSha256, true)) {
            onProgress(destination.length(), expectedBytes ?: destination.length())
            return@withContext destination
        }
        check(!destination.exists() || destination.delete()) { "Unable to remove invalid model archive" }
        val partial = File(destination.path + ".part")
        val limit = expectedBytes ?: MAX_ARCHIVE_BYTES
        var failure: IOException? = null
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            if (partial.length() > limit) check(partial.delete()) { "Unable to remove oversized partial model" }
            checkSpace(parent, (limit - partial.length()).coerceAtLeast(0) + reserveBytes)
            try {
                if (!partial.isFile || partial.length() != expectedBytes) {
                    transfer(url, partial, expectedBytes, reserveBytes, onProgress)
                }
                currentCoroutineContext().ensureActive()
                if (expectedBytes != null && partial.length() != expectedBytes) {
                    throw IOException("Incomplete model download: ${partial.length()} / $expectedBytes bytes")
                }
                if (!sha256(partial).equals(expectedSha256, true)) {
                    partial.delete()
                    throw IOException("Model checksum does not match the catalog; corrupt partial removed")
                }
                check(partial.renameTo(destination)) { "Unable to commit verified archive" }
                return@withContext destination
            } catch (cancelled: CancellationException) {
                throw cancelled // Retain .part for an explicit later retry.
            } catch (error: IOException) {
                failure = error
                if (attempt < 2) delay(1_000L shl attempt)
            }
        }
        throw requireNotNull(failure)
    }

    private suspend fun transfer(
        url: String,
        partial: File,
        expectedBytes: Long?,
        reserveBytes: Long,
        onProgress: suspend (Long, Long?) -> Unit
    ) {
        val existing = partial.length()
        val connection = openHttps(url, existing)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("Model server returned HTTP $code")
            }
            check(connection.getHeaderField("Content-Encoding").let { it == null || it == "identity" }) {
                "Encoded HTTP payloads cannot be safely resumed"
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            val start = if (append) existing else 0L
            val range = connection.getHeaderField("Content-Range")
            val rangeMatch = range?.let { CONTENT_RANGE.matchEntire(it) }
            if (append && (rangeMatch == null || rangeMatch.groupValues[1].toLong() != existing ||
                    (expectedBytes != null && rangeMatch.groupValues[3].toLong() != expectedBytes))) {
                partial.delete()
                throw IOException("Invalid Content-Range; partial removed")
            }
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1)
            val total = if (append) rangeMatch!!.groupValues[3].toLong()
                else contentLength.takeIf { it >= 0 }
            if (expectedBytes != null && total != null && total != expectedBytes) {
                throw IOException("Server model size differs from the catalog")
            }
            val max = expectedBytes ?: MAX_ARCHIVE_BYTES
            checkSpace(requireNotNull(partial.parentFile), (max - start).coerceAtLeast(0) + reserveBytes)
            connection.inputStream.use { input ->
                RandomAccessFile(partial, "rw").use { output ->
                    if (!append) output.setLength(0)
                    output.seek(start)
                    var received = start
                    var lastProgress = 0L
                    onProgress(received, expectedBytes ?: total)
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (received + count > max) {
                            output.setLength(0)
                            throw IOException("Model download exceeds catalog size limit")
                        }
                        output.write(buffer, 0, count)
                        received += count
                        if (received - lastProgress >= 256 * 1024) {
                            onProgress(received, expectedBytes ?: total)
                            lastProgress = received
                        }
                    }
                    output.fd.sync()
                    onProgress(received, expectedBytes ?: total)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Follow redirects explicitly so a release CDN may never downgrade HTTPS. */
    private fun openHttps(url: String, start: Long): HttpURLConnection {
        var current = URI(url)
        repeat(6) {
            requireHttps(current.toString())
            val connection = (current.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "OpenListener-model-store")
                if (start > 0) setRequestProperty("Range", "bytes=$start-")
            }
            try {
                if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                    val next = connection.getHeaderField("Location") ?: throw IOException("Missing HTTPS redirect")
                    current = current.resolve(next)
                    connection.disconnect()
                } else return connection
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        throw IOException("Too many model download redirects")
    }

    /** Installs only explicitly catalogued weights; no bundled test WAV is written. */
    suspend fun safeExtractTarBz2(archive: File, destination: File, manifest: ModelManifest) =
        withContext(Dispatchers.IO) {
            check(!destination.exists()) { "Extraction requires a new empty directory" }
            check(destination.mkdirs()) { "Unable to create model staging directory" }
            val wanted = manifest.files.associateBy { it.path }
            val seen = mutableSetOf<String>()
            var entries = 0
            var expanded = 0L
            archive.inputStream().buffered().use { compressed ->
                BZip2CompressorInputStream(compressed, false).use { bzip ->
                    TarArchiveInputStream(bzip).use { tar ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val entry = tar.nextTarEntry ?: break
                            check(++entries <= 128) { "Too many model archive entries" }
                            val normalized = entry.name.removePrefix("./").trimEnd('/')
                            check(normalized == manifest.archiveRoot || normalized.startsWith(manifest.archiveRoot + "/")) {
                                "Unexpected model archive root"
                            }
                            safeTarget(destination, normalized)
                            check(entry.isDirectory || (entry.isFile && !entry.isSymbolicLink && !entry.isLink)) {
                                "Links and special files are not permitted in model archives"
                            }
                            check(entry.size >= 0 && entry.size <= MAX_EXPANDED_BYTES - expanded) {
                                "Model archive exceeds decompression limit"
                            }
                            expanded += entry.size
                            val relative = normalized.removePrefix(manifest.archiveRoot + "/")
                            val expected = wanted[relative] ?: continue
                            check(entry.isFile && entry.size == expected.bytes && seen.add(relative)) {
                                "Duplicate or invalid model entry"
                            }
                            val target = safeTarget(destination, relative)
                            check(target.parentFile?.isDirectory == true)
                            val digest = MessageDigest.getInstance("SHA-256")
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var written = 0L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = tar.read(buffer)
                                    if (count < 0) break
                                    written += count
                                    check(written <= expected.bytes) { "Oversized model entry" }
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                }
                                check(written == expected.bytes) { "Truncated model entry" }
                            }
                            check(digest.digest().hex().equals(expected.sha256, true)) { "Extracted weight checksum mismatch" }
                        }
                    }
                }
            }
            check(seen == wanted.keys) { "Model archive is missing required files" }
        }

    /** Legacy ZIP helper, bounded and rejects traversal; production uses tar.bz2. */
    fun safeExtract(zip: File, destination: File) {
        check(!destination.exists() || destination.listFiles()?.isEmpty() == true) { "Extraction requires an empty directory" }
        check(destination.isDirectory || destination.mkdirs())
        var expanded = 0L
        val seen = mutableSetOf<String>()
        ZipFile(zip).use { archive ->
            val entries = archive.entries().asSequence().toList()
            check(entries.size <= 128) { "Too many ZIP entries" }
            entries.forEach { entry ->
                val target = safeTarget(destination, entry.name)
                check(seen.add(target.canonicalPath)) { "Duplicate ZIP entry" }
                if (entry.isDirectory) check(target.isDirectory || target.mkdirs()) else {
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    archive.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                expanded += count
                                check(expanded <= MAX_EXPANDED_BYTES) { "ZIP exceeds decompression limit" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun safeTarget(destination: File, name: String): File {
        check(name.isNotBlank() && !name.contains('\\') && !name.contains(':') && !name.startsWith('/') &&
            name.split('/').none { it == ".." || it == "." }) { "Unsafe model archive path" }
        val root = destination.canonicalFile
        val target = File(root, name).canonicalFile
        check(target.path.startsWith(root.path + File.separator)) { "Unsafe model archive path" }
        return target
    }

    private fun requireHttps(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "Model downloads must use HTTPS"
        }
    }

    private fun checkSpace(directory: File, required: Long) {
        if (directory.usableSpace < required + SPACE_MARGIN_BYTES) {
            throw IOException("Not enough private storage for model download and installation")
        }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 1_073_741_824L
        private const val MAX_EXPANDED_BYTES = 419_430_400L
        private const val SPACE_MARGIN_BYTES = 32L * 1024 * 1024
        private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}
