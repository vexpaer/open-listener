package io.github.vexpaer.openlistener

import io.github.vexpaer.openlistener.models.ModelDownloader
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ModelDownloaderTest {
    @Test
    fun rejectsNonHttpsModelUrl() {
        val downloader = ModelDownloader()
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                downloader.download("http://example.invalid/model.zip", File("build/model.zip"), "a".repeat(64)) { _, _ -> }
            }
        }
    }

    @Test
    fun rejectsZipSlipEntry() {
        val downloader = ModelDownloader()
        val archive = File.createTempFile("model", ".zip")
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("../outside.txt"))
            zip.write(1)
            zip.closeEntry()
        }
        assertThrows(IllegalStateException::class.java) {
            downloader.safeExtract(archive, File(archive.parentFile, "model-out"))
        }
        archive.delete()
    }
}
