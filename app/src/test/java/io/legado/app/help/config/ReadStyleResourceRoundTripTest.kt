package io.legado.app.help.config

import android.app.Application
import android.graphics.Bitmap
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.storage.BackupModule
import io.legado.app.help.storage.BackupResources
import io.legado.app.utils.GSON
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = ReadStyleResourceTestApplication::class, manifest = Config.NONE, sdk = [28])
class ReadStyleResourceRoundTripTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun presetKeepsTitleAndBothDecorationsAfterOriginalFilesAreRemoved() {
        val sources = temporaryFolder.newFolder("source")
        val header = png(sources, "header.png", 4, 2)
        val footer = png(sources, "footer.png", 3, 1)
        val titleFont = File(sources, "title.ttf").apply { writeText("title-font-fixture") }
        val headerBytes = header.readBytes()
        val footerBytes = footer.readBytes()
        val config = ReadBookConfig.Config(
            bgType = 0,
            headerImage = header.path, headerImageEnabled = true,
            headerImageWidth = 4, headerImageHeight = 2,
            footerImage = footer.path, footerImageEnabled = false,
            footerImageWidth = 3, footerImageHeight = 1,
            titleFont = titleFont.path, titleColor = -125, titleColorNight = -250,
            titleMode = 1, titleSize = 6, titleTopSpacing = 12, titleBottomSpacing = 16,
            textBold = 400, titleBold = 700, titleSegType = 1, titleSegScaling = 1.4f,
            titleSegDistance = 8, titleSegFlag = "章", titleLineSpacingExtra = 4,
            tipHeaderColor = -300, tipHeaderColorNight = -400,
            tipFooterColor = -500, tipFooterColorNight = -600,
        )
        val output = ByteArrayOutputStream()
        ReadStylePackageManager.export(config, output, temporaryFolder.newFolder("export"),
            { File(it).takeIf(File::isFile)?.inputStream() })
        assertEquals(header.path, config.headerImage)
        assertEquals(footer.path, config.footerImage)
        assertTrue(header.delete())
        assertTrue(footer.delete())
        assertTrue(titleFont.delete())

        val imported = ReadStylePackageManager.import(output.toByteArray().inputStream(),
            "decorations", temporaryFolder.newFolder("import"))
        assertTrue(imported.warnings.isEmpty())
        assertArrayEquals(headerBytes, File(imported.config.headerImage).readBytes())
        assertArrayEquals(footerBytes, File(imported.config.footerImage).readBytes())
        assertEquals("title-font-fixture", File(imported.config.titleFont).readText())
        assertEquals(config.copy(headerImage = imported.config.headerImage,
            footerImage = imported.config.footerImage, titleFont = imported.config.titleFont,
            ngReadStyleSource = imported.sourceFormat,
            ngUnknownFields = imported.config.ngUnknownFields), imported.config)
        assertFalse(imported.config.ngUnknownFields.keys.any { it.startsWith("footerImage") })
    }

    @Test fun strictPresetRejectsInvalidFooterAndRollsBackInstalledFiles() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("readConfig.json"))
            zip.write("""{"bgType":0,"footerImage":"footer.png","footerImageEnabled":true,
                "footerImageWidth":900,"footerImageHeight":200,"ngResourcePackageVersion":1}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("footer.png"))
            zip.write("not an image".toByteArray())
            zip.closeEntry()
        }
        val parent = temporaryFolder.newFolder("invalid-import")
        val failure = runCatching {
            ReadStylePackageManager.import(archive.toByteArray().inputStream(), "invalid-footer", parent)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("页脚图片"))
        assertFalse(File(parent, "invalid-footer").exists())
        assertTrue(parent.list().orEmpty().isEmpty())
    }

    @Test fun nativeBackupRestoresGlobalSharedAndBookResourcesWithoutOriginalPaths() {
        val sources = temporaryFolder.newFolder("backup-source")
        val header = png(sources, "header.png", 4, 2)
        val footer = png(sources, "footer.png", 3, 1)
        val headerBytes = header.readBytes()
        val footerBytes = footer.readBytes()
        val titleFont = File(sources, "title.ttf").apply { writeText("title-font-fixture") }
        val config = ReadBookConfig.Config(
            bgType = 0,
            headerImage = header.path, headerImageEnabled = true,
            headerImageWidth = 4, headerImageHeight = 2,
            footerImage = footer.path, footerImageEnabled = false,
            footerImageWidth = 3, footerImageHeight = 1,
            titleFont = titleFont.path, titleColor = -125, titleColorNight = -250,
            titleSegType = 1, titleSegScaling = 1.4f,
        )
        val style = GSON.toJsonTree(config).asJsonObject.apply {
            add("future", JsonObject().apply { addProperty("path", "backup-resource://keep-unknown") })
        }
        val root = temporaryFolder.newFolder("backup")
        File(root, "bookshelf.json").writeText(JsonArray().apply {
            add(JsonObject().apply {
                addProperty("bookUrl", "fixture.txt")
                add("readConfig", JsonObject().apply { addProperty("independentReadStyle", style.toString()) })
            })
        }.toString())
        File(root, ReadBookConfig.configFileName).writeText("[${GSON.toJson(config)}]")
        File(root, ReadBookConfig.shareConfigFileName).writeText(GSON.toJson(config))
        File(root, ReadHighlightRuleStore.fileName).writeText("[]")
        val modules = setOf(BackupModule.BOOKS.id, BackupModule.READER.id)
        BackupResources.prepare(root, modules, emptyMap<String, Any>())
        File(root, "config.xml").writeText("<map />")
        BackupResources.finishManifest(root, listOf("config.xml", "bookshelf.json",
            ReadBookConfig.configFileName, ReadBookConfig.shareConfigFileName, ReadHighlightRuleStore.fileName))
        assertEquals(modules, BackupResources.validate(root))
        listOf(header, footer, titleFont).forEach { assertTrue(it.delete()) }

        BackupResources.restoreResources(root, emptyMap<String, Any>())

        val global = JsonParser.parseString(File(root, ReadBookConfig.configFileName).readText()).asJsonArray[0]
        val shared = JsonParser.parseString(File(root, ReadBookConfig.shareConfigFileName).readText())
        val book = JsonParser.parseString(File(root, "bookshelf.json").readText()).asJsonArray[0].asJsonObject
        val restoredStyle = JsonParser.parseString(book.getAsJsonObject("readConfig")
            .get("independentReadStyle").asString).asJsonObject
        listOf(global, shared, restoredStyle).forEach { json ->
            val actual = GSON.fromJson(json, ReadBookConfig.Config::class.java)
            assertArrayEquals(headerBytes, File(actual.headerImage).readBytes())
            assertArrayEquals(footerBytes, File(actual.footerImage).readBytes())
            assertEquals("title-font-fixture", File(actual.titleFont).readText())
            assertEquals(config, actual.copy(headerImage = config.headerImage,
                footerImage = config.footerImage, titleFont = config.titleFont))
        }
        assertEquals(style.get("future"), restoredStyle.get("future"))
    }

    private fun png(root: File, name: String, width: Int, height: Int): File = File(root, name).apply {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}

/** Isolated context for archive I/O, without production services or database startup. */
class ReadStyleResourceTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}
