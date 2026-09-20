package io.legado.app.help.storage

import com.google.gson.JsonParser
import io.legado.app.constant.PreferKey
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupResourcesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun builtInReadingBackgroundsKeepTheirTypesWithoutResourceFiles() {
        val root = temp.newFolder()
        val config = """{
            "bgType":1,"bgStr":"秋山书意-日间.webp",
            "bgTypeNight":1,"bgStrNight":"秋山书意-夜间.webp",
            "bgTypeEInk":0,"bgStrEInk":"#FFFFFF"
        }"""
        File(root, "readConfig.json").writeText("[$config]")
        File(root, "shareReadConfig.json").writeText(config)
        File(root, "highlightRule.json").writeText("[]")

        BackupResources.prepare(root, setOf(BackupModule.READER.id), emptyMap<String, Any>())

        assertEquals(JsonParser.parseString("[$config]"), JsonParser.parseString(File(root, "readConfig.json").readText()))
        assertEquals(JsonParser.parseString(config), JsonParser.parseString(File(root, "shareReadConfig.json").readText()))
        assertFalse(File(root, BackupResources.ASSETS).exists())
        File(root, "config.xml").writeText("<map />")
        BackupResources.finishManifest(root, listOf("config.xml", "readConfig.json", "shareReadConfig.json", "highlightRule.json"))
        assertEquals(setOf(BackupModule.READER.id), BackupResources.validate(root))
    }

    @Test fun bundledAssetReferencesStayInConfigurationWithoutBeingPackaged() {
        val root = temp.newFolder()
        val themes = """[{"backgroundImgPath":"asset://bg/竹影之韵.webp"}]"""
        File(root, "themeConfig.json").writeText(themes)
        val prefs = mapOf(
            PreferKey.bgImage to "asset://defaultData/theme/reading_ng_summer_childhood.webp",
            PreferKey.bgImageN to "asset://defaultData/theme/reading_ng_summer_childhood_dark.webp",
            PreferKey.defaultCover to "assets://bg/起点读书.jpg",
            PreferKey.defaultCoverDark to "file:///android_asset/bg/起点读书.jpg",
            "ngManagedThemes.v1" to """[{"lightBackground":{"path":"asset://bg/暖色渐变.webp"}}]""",
        )

        val result = BackupResources.prepare(root, setOf(BackupModule.APPEARANCE.id, BackupModule.COVERS.id), prefs)

        assertEquals(prefs, result)
        assertEquals(JsonParser.parseString(themes), JsonParser.parseString(File(root, "themeConfig.json").readText()))
        assertFalse(File(root, BackupResources.ASSETS).exists())
        val manifest = JsonParser.parseString(File(root, BackupResources.MANIFEST).readText()).asJsonObject
        assertEquals(0, manifest.getAsJsonObject("files").size())
    }

    @Test fun rejectsTraversalAndSiblingPrefix() {
        val root = temp.newFolder("backup")
        listOf("../backup-sibling/file", "/absolute", "a/../../file", "a\\file", "file:x").forEach {
            assertThrows(IllegalArgumentException::class.java) { BackupResources.safeFile(root, it) }
        }
    }

    @Test fun resourceRewritingNeverChangesRuleTextOrNames() {
        val json = com.google.gson.JsonParser.parseString("""{
            "pattern":"backup-resource://resources/test", "name":"/a/name",
            "bgImage":"backup-resource://resources/test", "fontPath":"font",
            "lightImages":["image"], "sampleText":"font"
        }""")
        val result = BackupResources.transform(json) { "resolved:$it" }.asJsonObject
        assertEquals("backup-resource://resources/test", result.get("pattern").asString)
        assertEquals("/a/name", result.get("name").asString)
        assertEquals("font", result.get("sampleText").asString)
        assertEquals("resolved:font", result.get("fontPath").asString)
        assertEquals("resolved:image", result.getAsJsonArray("lightImages")[0].asString)
    }

    @Test fun extractionPreservesNestedResourceBytes() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("resources/theme/font.ttf"))
            it.write("font-data".toByteArray())
            it.closeEntry()
        }
        val root = temp.newFolder()
        BackupResources.extract(output.toByteArray().inputStream(), root)
        assertEquals("font-data", File(root, "resources/theme/font.ttf").readText())
    }

    @Test fun verifiesConfigurationDigestBeforeRestore() {
        val root = temp.newFolder()
        File(root, "config.xml").writeText("<map />")
        File(root, BackupResources.MANIFEST).writeText("""{"version":1,"modules":["other"],"files":{}}""")
        BackupResources.finishManifest(root, listOf("config.xml"))
        assertEquals(setOf("other"), BackupResources.validate(root))
        File(root, "config.xml").appendText("damaged")
        assertThrows(IllegalArgumentException::class.java) { BackupResources.validate(root) }
    }

    @Test fun rejectsFileFromUnselectedModule() {
        val root = temp.newFolder()
        File(root, "config.xml").writeText("<map />")
        File(root, "bookshelf.json").writeText("[]")
        File(root, BackupResources.MANIFEST).writeText("""{"version":1,"modules":["rss"],"files":{}}""")
        BackupResources.finishManifest(root, listOf("config.xml", "bookshelf.json"))
        assertThrows(IllegalArgumentException::class.java) { BackupResources.validate(root) }
    }
}
