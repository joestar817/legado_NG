package io.legado.app.help.storage

import android.app.Application
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class BackupBookCoversTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun booksWithoutCoversKeepOriginalReferencesAndProduceAValidBackup() {
        val root = temp.newFolder()
        val original = JsonParser.parseString("""[
            {"name":"local","customCoverUrl":"/unavailable/cover.jpg"},
            {"name":"file","customCoverUrl":"file:///unavailable/cover.jpg"},
            {"name":"content","customCoverUrl":"content://unavailable/cover"},
            {"name":"remote","customCoverUrl":"https://example.invalid/cover.jpg"},
            {"name":"built-in","customCoverUrl":"file:///android_asset/cover.jpg"},
            {"name":"no-cover","customCoverUrl":null}
        ]""")
        val bookshelf = File(root, "bookshelf.json").apply { writeText(original.toString()) }

        BackupResources.prepare(root, setOf(BackupModule.BOOKS.id), emptyMap<String, Any>())

        val packaged = JsonParser.parseString(bookshelf.readText())
        assertEquals(original, packaged)
        assertFalse(File(root, BackupResources.ASSETS).exists())
        File(root, "config.xml").writeText("<map />")
        BackupResources.finishManifest(root, listOf("config.xml", "bookshelf.json"))
        assertEquals(setOf(BackupModule.BOOKS.id), BackupResources.validate(root))
        assertEquals(original, BackupResources.transform(packaged) { reference ->
            check(!reference.startsWith("backup-resource://"))
            reference
        })
    }

    @Test fun selectingCoversStillFailsWhenACoverCannotBeRead() {
        val root = temp.newFolder()
        File(root, "bookshelf.json").writeText("""[
            {"name":"missing","customCoverUrl":"file:///codex-missing-cover-for-backup-test.jpg"}
        ]""")

        assertThrows(FileNotFoundException::class.java) {
            BackupResources.prepare(
                root,
                setOf(BackupModule.BOOKS.id, BackupModule.COVERS.id),
                emptyMap<String, Any>(),
            )
        }
    }

    @Test fun selectingCoversPackagesAndDeduplicatesReadableImages() {
        val root = temp.newFolder()
        val cover = temp.newFile("cover.jpg").apply { writeText("synthetic cover") }
        // Windows temp paths are not Android absolute paths; the device harness covers this case.
        assumeTrue(cover.absolutePath.startsWith('/'))
        val books = JsonArray().apply {
            repeat(2) { add(JsonObject().apply { addProperty("customCoverUrl", cover.absolutePath) }) }
        }
        val bookshelf = File(root, "bookshelf.json").apply { writeText(books.toString()) }
        val modules = setOf(BackupModule.BOOKS.id, BackupModule.COVERS.id)

        BackupResources.prepare(root, modules, emptyMap<String, Any>())

        val packaged = JsonParser.parseString(bookshelf.readText()).asJsonArray
        val token = packaged[0].asJsonObject.get("customCoverUrl").asString
        assertTrue(token.startsWith("backup-resource://resources/files/"))
        assertEquals(token, packaged[1].asJsonObject.get("customCoverUrl").asString)
        assertEquals("synthetic cover", File(root, token.removePrefix("backup-resource://")).readText())
        File(root, "config.xml").writeText("<map />")
        BackupResources.finishManifest(root, listOf("config.xml", "bookshelf.json"))
        assertEquals(modules, BackupResources.validate(root))
    }
}
