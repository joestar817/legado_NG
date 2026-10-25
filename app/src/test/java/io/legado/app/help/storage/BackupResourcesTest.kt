package io.legado.app.help.storage

import com.google.gson.JsonParser
import com.google.gson.JsonObject
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

    @Test fun independentBookHeaderImageIsCollectedAndRestoredInsideStoredStyle() {
        val originalStyle = JsonParser.parseString("""{
            "headerImage":"/pictures/header.png","headerImageEnabled":true,
            "headerImageWidth":1920,"headerImageHeight":540,
            "name":"backup-resource://keep-name",
            "highlightRules":[{
                "pattern":"backup-resource://resources/header.png",
                "sampleText":"/pictures/header.png"
            }]
        }""").asJsonObject
        val book = JsonObject().apply {
            addProperty("name", "/pictures/header.png")
            add("readConfig", JsonObject().apply {
                addProperty("independentReadStyle", originalStyle.toString())
                addProperty("pageOffset", 12)
            })
        }
        val copied = mutableListOf<String>()
        BackupResources.collectBookReadStyleResources(book) { reference ->
            copied += reference
            "backup-resource://resources/header.png"
        }
        assertEquals(listOf("/pictures/header.png"), copied)
        val packaged = JsonParser.parseString(
            book.getAsJsonObject("readConfig").get("independentReadStyle").asString,
        ).asJsonObject
        val expected = originalStyle.deepCopy().apply {
            addProperty("headerImage", "backup-resource://resources/header.png")
        }
        assertEquals(expected, packaged)

        val restoredBook = BackupResources.transform(book) { reference ->
            reference.replace("backup-resource://", "/restored/")
        }.asJsonObject
        val restored = JsonParser.parseString(
            restoredBook.getAsJsonObject("readConfig").get("independentReadStyle").asString,
        ).asJsonObject
        expected.addProperty("headerImage", "/restored/resources/header.png")
        assertEquals(expected, restored)
        assertEquals("/pictures/header.png", restoredBook.get("name").asString)
        assertEquals(12, restoredBook.getAsJsonObject("readConfig").get("pageOffset").asInt)
    }

    @Test fun legacyBookStylesWithoutImageStayOpaqueAndUnchanged() {
        listOf(
            "legacy-style-name",
            "{invalid-json",
            "[]",
            "{ \"textSize\": 24, \"pattern\": \"backup-resource://keep\" }",
        ).forEach { storedStyle ->
            val book = JsonObject().apply {
                add("readConfig", JsonObject().apply { addProperty("independentReadStyle", storedStyle) })
            }
            BackupResources.collectBookReadStyleResources(book) { error("No resource should be collected") }
            val restored = BackupResources.transform(book) { error("No resource should be rewritten") }.asJsonObject
            assertEquals(storedStyle, restored.getAsJsonObject("readConfig").get("independentReadStyle").asString)
        }
    }

    @Test fun globalHeaderImageReferenceRewritesWithoutChangingDimensionsOrText() {
        val config = JsonParser.parseString("""{
            "headerImage":"backup-resource://resources/header.png","headerImageEnabled":false,
            "headerImageWidth":1920,"headerImageHeight":540,
            "pattern":"backup-resource://resources/header.png",
            "sampleText":"backup-resource://resources/header.png"
        }""").asJsonObject
        val expected = config.deepCopy().apply {
            addProperty("headerImage", "/restored/header.png")
        }

        assertEquals(expected, BackupResources.transform(config) { "/restored/header.png" })
    }

    @Test fun independentFontOnlyStyleDoesNotDependOnAnImageKey() {
        val style = JsonParser.parseString("""{
            "textFont":"/fonts/body.ttf","titleFont":"/fonts/title.ttf",
            "titleColor":-125,"titleColorNight":-250,
            "future":{"path":"backup-resource://keep-unknown"}
        }""").asJsonObject
        val book = JsonObject().apply {
            add("readConfig", JsonObject().apply {
                addProperty("independentReadStyle", style.toString())
            })
        }
        val copied = mutableListOf<String>()
        BackupResources.collectBookReadStyleResources(book) {
            copied += it
            "backup-resource://resources/${it.substringAfterLast('/')}"
        }
        assertEquals(listOf("/fonts/body.ttf", "/fonts/title.ttf"), copied)

        val restored = BackupResources.transform(book) {
            it.replace("backup-resource://resources/", "/restored/")
        }.asJsonObject
        val actual = JsonParser.parseString(
            restored.getAsJsonObject("readConfig").get("independentReadStyle").asString,
        ).asJsonObject
        val expected = style.deepCopy().apply {
            addProperty("textFont", "/restored/body.ttf")
            addProperty("titleFont", "/restored/title.ttf")
        }
        assertEquals(expected, actual)
    }

    @Test fun independentStyleCollectsEveryReaderResourceWithoutChangingOtherFields() {
        val style = JsonParser.parseString("""{
            "bgType":2,"bgStr":"/images/day.png","bgTypeNight":2,"bgStrNight":"/images/night.png",
            "bgTypeEInk":1,"bgStrEInk":"bundled.webp",
            "textFont":"/fonts/body.ttf","titleFont":"/fonts/title.ttf",
            "headerFont":"/fonts/header.ttf","footerFont":"/fonts/footer.ttf",
            "headerImage":"/images/header.png","footerImage":"/images/footer.png",
            "footerImageEnabled":false,"footerImageWidth":480,"footerImageHeight":80,
            "highlightRules":[{"pattern":"/images/rule.png","bgImage":"/images/rule.png",
                "fontPath":"/fonts/rule.ttf"}],"future":{"path":"do-not-change"}
        }""").asJsonObject
        val book = JsonObject().apply {
            add("readConfig", JsonObject().apply { addProperty("independentReadStyle", style.toString()) })
        }
        val references = mutableListOf<String>()
        BackupResources.collectBookReadStyleResources(book) {
            references += it
            "backup-resource://resources/${it.substringAfterLast('/')}"
        }
        assertEquals(listOf("/images/day.png", "/images/night.png", "/fonts/body.ttf", "/fonts/title.ttf",
            "/fonts/header.ttf", "/fonts/footer.ttf", "/images/header.png", "/images/footer.png",
            "/images/rule.png", "/fonts/rule.ttf"), references)
        val restored = BackupResources.transform(book) {
            it.replace("backup-resource://resources/", "/restored/")
        }.asJsonObject
        val actual = JsonParser.parseString(
            restored.getAsJsonObject("readConfig").get("independentReadStyle").asString,
        ).asJsonObject
        val expected = style.deepCopy()
        listOf("bgStr", "bgStrNight", "textFont", "titleFont", "headerFont", "footerFont",
            "headerImage", "footerImage").forEach { key ->
            expected.addProperty(key, "/restored/${style.get(key).asString.substringAfterLast('/')}")
        }
        expected.getAsJsonArray("highlightRules")[0].asJsonObject.apply {
            addProperty("bgImage", "/restored/rule.png")
            addProperty("fontPath", "/restored/rule.ttf")
        }
        assertEquals(expected, actual)
    }

    @Test fun drawerImagesAreCollectedAndRestoredWithoutChangingThemeValues() {
        val theme = JsonParser.parseString("""{
            "name":"backup-resource://keep-name",
            "drawerProfile":{
                "source":"custom_image",
                "light":{"imagePath":"/pictures/day.webp"},
                "dark":{"imagePath":"/pictures/night.webp","backgroundColor":-16049873}
            }
        }""").asJsonObject
        val copied = mutableListOf<String>()
        BackupResources.collectThemeDrawerImages(theme) { path ->
            copied += path
            "backup-resource://resources/${path.substringAfterLast('/')}"
        }
        assertEquals(listOf("/pictures/day.webp", "/pictures/night.webp"), copied)
        val restored = BackupResources.transform(theme) { path ->
            path.replace("backup-resource://", "/restored/")
        }.asJsonObject
        val profile = restored.getAsJsonObject("drawerProfile")
        assertEquals("/restored/resources/day.webp", profile.getAsJsonObject("light").get("imagePath").asString)
        assertEquals("/restored/resources/night.webp", profile.getAsJsonObject("dark").get("imagePath").asString)
        assertEquals("custom_image", profile.get("source").asString)
        assertEquals(-16049873, profile.getAsJsonObject("dark").get("backgroundColor").asInt)
        assertEquals("backup-resource://keep-name", restored.get("name").asString)
    }

    @Test fun absentOrEmptyDrawerImagesDoNotAddBackupResources() {
        listOf("{}", """{"drawerProfile":null}""",
            """{"drawerProfile":{"light":{"imagePath":null},"dark":{"imagePath":""}}}""").forEach { raw ->
            val theme = JsonParser.parseString(raw).asJsonObject
            BackupResources.collectThemeDrawerImages(theme) { error("No image should be copied") }
            assertEquals(JsonParser.parseString(raw), theme)
        }
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
