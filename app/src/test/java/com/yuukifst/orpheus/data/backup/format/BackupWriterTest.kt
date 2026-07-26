package com.yuukifst.orpheus.data.backup.format

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.yuukifst.orpheus.data.backup.model.BackupManifest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupWriterTest {

    private val gson = GsonBuilder().create()
    private val output = ByteArrayOutputStream()
    private val context: Context = mockk {
        every { contentResolver.openOutputStream(any<Uri>()) } returns output
    }
    private val writer = BackupWriter(context, gson)

    @Test
    fun `uses handler entry count for v2 favorites envelope instead of object heuristic`() = runTest {
        output.reset()
        val favoritesPayload = """
            {
              "version": 2,
              "local": [
                {"songId": 1, "isFavorite": true, "timestamp": 1},
                {"songId": 2, "isFavorite": true, "timestamp": 2}
              ],
              "youtube": [
                {"videoId": "abc", "title": "Song", "channelName": "Artist"}
              ]
            }
        """.trimIndent()
        val expectedEntryCount = 3

        writer.write(
            uri = mockk(relaxed = true),
            manifest = BackupManifest(),
            modulePayloads = mapOf("favorites" to favoritesPayload),
            moduleEntryCounts = mapOf("favorites" to expectedEntryCount)
        ).getOrThrow()

        val manifest = readManifestFromBackup(output.toByteArray())
        assertEquals(expectedEntryCount, manifest.modules["favorites"]?.entryCount)
    }

    @Test
    fun `falls back to array heuristic when handler entry count is not provided`() = runTest {
        output.reset()
        val arrayPayload = """[{"songId": 1}, {"songId": 2}]"""

        writer.write(
            uri = mockk(relaxed = true),
            manifest = BackupManifest(),
            modulePayloads = mapOf("favorites" to arrayPayload)
        ).getOrThrow()

        val manifest = readManifestFromBackup(output.toByteArray())
        assertEquals(2, manifest.modules["favorites"]?.entryCount)
    }

    private fun readManifestFromBackup(bytes: ByteArray): BackupManifest {
        ZipInputStream(ByteArrayInputStream(bytes, BackupFormatDetector.PXPL_MAGIC.size, bytes.size)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == BackupManifest.MANIFEST_FILENAME) {
                    val json = zip.readBytes().toString(Charsets.UTF_8)
                    return gson.fromJson(json, BackupManifest::class.java)
                }
            }
        }
        throw IllegalStateException("manifest.json not found in backup")
    }
}
