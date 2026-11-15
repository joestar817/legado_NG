package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.read.config.preferredCustomTitleSegmentType
import io.legado.app.ui.book.read.config.selectTitleSegmentType
import io.legado.app.ui.book.read.page.provider.ReadTitleSegmentType
import io.legado.app.ui.book.read.page.provider.ReadTitleStyleParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ReadStylePackageManagerTest {
    @Test
    fun `resource ownership is local and never imported exported or copied`() {
        val json = """{"bgType":0,"ngResourcePresetTarget":"foreign","ngResourcePresetSha256":"${"a".repeat(64)}"}"""
        val imported = ReadStylePackageManager.import(
            zipOf("readConfig.json" to json.toByteArray()).inputStream(), "ownership", temporaryFolder.newFolder(),
        ).config
        assertNull(imported.ngResourcePresetTarget)
        assertNull(imported.ngResourcePresetSha256)
        imported.ngResourcePresetTarget = "local"
        imported.ngResourcePresetSha256 = "b".repeat(64)
        val copy = imported.copyForBook()
        assertNull(copy.ngResourcePresetTarget)
        assertNull(copy.ngResourcePresetSha256)
        val output = ByteArrayOutputStream()
        ReadStylePackageManager.export(imported, output, temporaryFolder.newFolder(), { null })
        val exported = JsonParser.parseString(unzip(output.toByteArray()).getValue("readConfig.json").toString(Charsets.UTF_8)).asJsonObject
        assertFalse(exported.has("ngResourcePresetTarget"))
        assertFalse(exported.has("ngResourcePresetSha256"))
    }

    @Test
    fun `MD3 title rules survive import and can be restored after chapter number mode`() {
        val title = "第112章 新章启程"
        val rules = listOf(
            Triple(1, "", listOf("第112", "章 新章启程")),
            Triple(2, "章", listOf("第112章", "新章", "启程")),
            Triple(3, "^第[0-9]{1,6}章", listOf("第112章", "新章启程")),
        )
        for ((type, rule, expected) in rules) {
            val json = """{"bgType":0,"underline":false,"titleSegType":$type,"titleSegDistance":4,
                "titleSegFlag":"$rule","titleSegScaling":0.9,"titleLineSpacingSub":12}"""
            val result = ReadStylePackageManager.import(
                zipOf("readConfig.json" to json.toByteArray()).inputStream(),
                "md3-title-$type", temporaryFolder.newFolder(),
            )
            val config = result.config
            assertEquals("md3-read-style", result.sourceFormat)
            assertEquals(type, config.titleSegType)
            assertEquals(0, config.titleSegCustomType)
            fun segments(style: ReadBookConfig.Config) = ReadTitleStyleParser.parse(
                title, style.titleSegType, style.titleSegDistance, style.titleSegFlag, style.titleSegScaling,
            ).map { it.text }
            assertEquals(expected, segments(config))

            config.selectTitleSegmentType(ReadTitleSegmentType.CHAPTER_NUMBER)
            assertEquals(type, config.titleSegCustomType)
            assertEquals(listOf("第112章", "新章启程"), segments(config))
            val exported = ByteArrayOutputStream()
            ReadStylePackageManager.export(config, exported, temporaryFolder.newFolder(), { null })
            val restored = ReadStylePackageManager.import(
                exported.toByteArray().inputStream(), "ng-title-$type", temporaryFolder.newFolder(),
            ).config
            assertEquals(ReadTitleSegmentType.CHAPTER_NUMBER, restored.titleSegType)
            assertEquals(type, restored.titleSegCustomType)
            assertEquals(rule, restored.titleSegFlag)
            assertEquals(12, restored.titleLineSpacingSub)
            assertEquals(0.9f, restored.titleSegScaling, 0f)
            restored.selectTitleSegmentType(restored.preferredCustomTitleSegmentType())
            assertEquals(expected, segments(restored))
        }
    }

    @Test
    fun `export bundles header image and keeps its layout metadata without changing source`() {
        val image = byteArrayOf(1, 3, 5, 7)
        val imagePath = "assets://decorations/header.png"
        val config = ReadBookConfig.Config(
            bgType = 0,
            headerImage = imagePath,
            headerImageEnabled = true,
            headerImageWidth = 1920,
            headerImageHeight = 540,
            titleFont = "",
            titleSegType = 1,
            titleSegScaling = 1.4f,
        )
        val opened = mutableListOf<String>()
        val output = ByteArrayOutputStream()

        val result = ReadStylePackageManager.export(
            config, output, File(temporaryFolder.root, "header-export"),
            { reference ->
                opened += reference
                if (reference == imagePath) image.inputStream() else null
            },
        )
        val entries = unzip(output.toByteArray())
        val json = JsonParser.parseString(entries.getValue("readConfig.json").decodeToString()).asJsonObject
        val bundledImage = json.get("headerImage").asString

        assertTrue(result.warnings.isEmpty())
        assertEquals(listOf(imagePath), opened)
        assertEquals(setOf("readConfig.json", bundledImage), entries.keys)
        assertTrue(bundledImage.startsWith("read_header_image_"))
        assertArrayEquals(image, entries.getValue(bundledImage))
        assertTrue(json.get("headerImageEnabled").asBoolean)
        assertEquals(1920, json.get("headerImageWidth").asInt)
        assertEquals(540, json.get("headerImageHeight").asInt)
        assertEquals(1.4f, json.get("titleSegScaling").asFloat, 0f)
        assertEquals(imagePath, config.headerImage)
        assertTrue(config.headerImageEnabled)
    }

    @Test
    fun `legacy package without header image keeps zero reserved dimensions`() {
        val result = ReadStylePackageManager.import(
            zipOf("readConfig.json" to """{"bgType":0,"titleSize":5}""".toByteArray()).inputStream(),
            "without-header", temporaryFolder.newFolder(),
        )

        assertFalse(result.config.headerImageEnabled)
        assertEquals("", result.config.headerImage)
        assertEquals(0, result.config.headerImageWidth)
        assertEquals(0, result.config.headerImageHeight)
        assertEquals(5, result.config.titleSize)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `missing optional header image cannot leave an enabled empty area`() {
        val result = ReadStylePackageManager.import(
            zipOf("readConfig.json" to """{
                "bgType":0,"headerImage":"missing.png","headerImageEnabled":true,
                "headerImageWidth":1920,"headerImageHeight":540
            }""".trimIndent().toByteArray()).inputStream(),
            "missing-header", temporaryFolder.newFolder(),
        )

        assertFalse(result.config.headerImageEnabled)
        assertEquals("", result.config.headerImage)
        assertEquals(0, result.config.headerImageWidth)
        assertEquals(0, result.config.headerImageHeight)
        assertTrue(result.warnings.any { it.contains("页眉图片") })
    }

    @Test
    fun `missing optional footer image cannot leave an enabled empty area`() {
        val result = ReadStylePackageManager.import(
            zipOf("readConfig.json" to """{
                "bgType":0,"footerImage":"missing.png","footerImageEnabled":true,
                "footerImageWidth":1080,"footerImageHeight":120
            }""".trimIndent().toByteArray()).inputStream(),
            "missing-footer", temporaryFolder.newFolder(),
        )

        assertFalse(result.config.footerImageEnabled)
        assertEquals("", result.config.footerImage)
        assertEquals(0, result.config.footerImageWidth)
        assertEquals(0, result.config.footerImageHeight)
        assertTrue(result.warnings.any { it.contains("页脚图片") })
    }

    @Test
    fun `bundles builtin background all four fonts and reader preferences`() {
        val image = byteArrayOf(1, 2, 3)
        val resources = mapOf(
            "assets://bg/test.png" to image,
            "assets://body.ttf" to byteArrayOf(4, 5),
            "assets://title.ttf" to byteArrayOf(6, 7),
            "assets://header.ttf" to byteArrayOf(8, 9),
            "assets://footer.ttf" to byteArrayOf(10, 11),
        )
        val config = ReadBookConfig.Config(
            bgType = 1, bgStr = "test.png", bgTypeNight = 1, bgStrNight = "test.png",
            textFont = "assets://body.ttf", titleFont = "assets://title.ttf",
            headerFont = "assets://header.ttf", footerFont = "assets://footer.ttf",
            highlightRules = arrayListOf(ReadHighlightRule(id = "test", pattern = "a",
                bgImage = "assets://bg/test.png", fontPath = "assets://body.ttf", npLeft = 0.2f)),
        )
        val settings = JsonObject().apply {
            addProperty(PreferKey.autoChangeSource, false)
            addProperty(PreferKey.textSelectionShowText, false)
            add("toolbarOrder", JsonArray().apply { add("copy") })
            add("toolbarDisabled", JsonArray().apply { add("share") })
        }
        val output = ByteArrayOutputStream()
        ReadStylePackageManager.export(config, output, File(temporaryFolder.root, "stage"),
            { resources[it]?.inputStream() }, settings)
        val entries = unzip(output.toByteArray())
        assertEquals(6, entries.size)
        val imported = ReadStylePackageManager.import(output.toByteArray().inputStream(), "all-fonts", temporaryFolder.newFolder())
        assertEquals(settings, imported.readerSettings)
        assertEquals(2, imported.config.bgType)
        assertEquals(imported.config.bgStr, imported.config.bgStrNight)
        assertEquals(imported.config.bgStr, imported.config.highlightRules.single().bgImage)
        assertEquals(imported.config.textFont, imported.config.highlightRules.single().fontPath)
        assertArrayEquals(resources.getValue("assets://header.ttf"), File(imported.config.headerFont).readBytes())
        assertArrayEquals(resources.getValue("assets://footer.ttf"), File(imported.config.footerFont).readBytes())
        assertEquals(0.2f, imported.config.highlightRules.single().npLeft, 0f)
    }

    @Test
    fun `removed settings are ignored while current reader preferences are preserved`() {
        val json = """{"ngReaderSettings":{"disableReturnKey":true,"mouseWheelPage":false,"volumeKeyPage":true,"volumeKeyPageOnPlay":false,"noAnimScrollPage":true}}"""
        val imported = ReadStylePackageManager.import(
            zipOf("readConfig.json" to json.toByteArray()).inputStream(),
            "legacy-return-key", temporaryFolder.newFolder(),
        )
        val settings = requireNotNull(imported.readerSettings)
        assertFalse(settings.get(PreferKey.mouseWheelPage).asBoolean)
        assertFalse(PreferKey.mouseWheelPage in ReadPresetPreferences.preferenceKeys)
        assertTrue(settings.get(PreferKey.volumeKeyPage).asBoolean)
        assertTrue(PreferKey.volumeKeyPage in ReadPresetPreferences.preferenceKeys)
        assertFalse("disableReturnKey" in ReadPresetPreferences.preferenceKeys)
        assertFalse("volumeKeyPageOnPlay" in ReadPresetPreferences.preferenceKeys)
        assertFalse("noAnimScrollPage" in ReadPresetPreferences.preferenceKeys)
    }

    @Test
    fun `rejects malformed reader preference before installation`() {
        val json = """{"ngReaderSettings":{"screenOrientation":"invalid"}}"""
        val parent = temporaryFolder.newFolder()
        val result = runCatching {
            ReadStylePackageManager.import(zipOf("readConfig.json" to json.toByteArray()).inputStream(), "invalid", parent)
        }
        assertTrue(result.isFailure)
        assertFalse(File(parent, "invalid").exists())
    }

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `imports md3 fields and installs bundled resources`() {
        val parent = temporaryFolder.newFolder("packages")
        val zip = zipOf(
            "readConfig.json" to """
                {
                  "name":"fixture",
                  "bgType":2,
                  "bgStr":"background.jpg",
                  "textBold":500,
                  "underline":true,
                  "dottedLine":true,
                  "futureMd3Field":{"value":1},
                  "highlightRules":[{
                    "id":"quote",
                    "name":"dialogue",
                    "pattern":"[“”]",
                    "enabled":true,
                    "position":1,
                    "textColor":-123,
                    "bgImage":"highlight.png"
                  }]
                }
            """.trimIndent().toByteArray(),
            "background.jpg" to byteArrayOf(1, 2, 3),
            "highlight.png" to byteArrayOf(4, 5, 6),
        )

        val result = ReadStylePackageManager.import(
            ByteArrayInputStream(zip),
            "fixture-hash",
            parent,
        )

        assertEquals("md3-read-style", result.sourceFormat)
        assertEquals(500, result.config.textBold)
        assertTrue(result.config.underline)
        assertTrue(File(result.config.bgStr).isFile)
        assertTrue(File(result.config.highlightRules.single().bgImage!!).isFile)
        assertTrue("futureMd3Field" in result.config.ngUnknownFields)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `imports real yuanxiu md3 package and keeps rules when optional assets are absent`() {
        val fixture = System.getenv("READ_STYLE_MD3_FIXTURE")?.let(::File)
        assumeTrue("READ_STYLE_MD3_FIXTURE 未指向远岫 MD3 排版包", fixture?.isFile == true)
        val parent = temporaryFolder.newFolder("real-yuanxiu-packages")

        val result = ReadStylePackageManager.import(
            fixture!!.inputStream(),
            "real-yuanxiu",
            parent,
        )

        assertEquals("md3-read-style", result.sourceFormat)
        assertEquals("远岫", result.config.name)
        assertEquals(2, result.config.bgType)
        assertTrue(File(result.config.bgStr).isFile)
        assertEquals(6, result.config.highlightRules.size)
        assertEquals(1, result.config.highlightRules.count(ReadHighlightRule::enabled))
        assertEquals(
            listOf("青诗", "水墨", "春抚", "桃夭", "夏与冰格", "远岫"),
            result.config.highlightRules.map(ReadHighlightRule::name),
        )
        assertTrue(result.config.highlightRules.all { it.pattern.isNotBlank() })
        assertTrue(result.config.highlightRules.all { it.bgImage == null })
        assertTrue(result.warnings.any { it.contains("高亮规则的背景图未随包携带") })
    }

    @Test
    fun `exports and reimports a self contained full md3 rule package`() {
        val resources = temporaryFolder.newFolder("full-md3-resources")
        val dayBackground = File(resources, "day.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val nightBackground = File(resources, "night.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val einkBackground = File(resources, "eink.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val textFont = File(resources, "text.ttf").apply { writeBytes(byteArrayOf(10, 11)) }
        val titleFont = File(resources, "title.ttf").apply { writeBytes(byteArrayOf(12, 13)) }
        val ruleBackground = File(resources, "rule.9.png").apply { writeBytes(byteArrayOf(14, 15)) }
        val ruleFont = File(resources, "rule.ttf").apply { writeBytes(byteArrayOf(16, 17)) }
        val rule = ReadHighlightRule(
            id = "full-rule",
            name = "全量规则",
            pattern = "[“”]",
            sampleText = "“测试”",
            targetScope = ReadHighlightRule.TARGET_BODY,
            enabled = true,
            position = 7,
            textColor = -123456,
            bgColor = -654321,
            underlineMode = 5,
            underlineColor = -777777,
            underlineWidth = 2.5f,
            underlineOffset = 3.5f,
            underlineSvgPath = "M0,0 L8,2",
            bgImage = ruleBackground.absolutePath,
            bgImageFit = 3,
            bgImageScale = 1.25f,
            fontPath = ruleFont.absolutePath,
            fontWeight = 650,
            isItalic = true,
            npLeft = 0.12f,
            npRight = 0.22f,
            npTop = 0.32f,
            npBottom = 0.42f,
            textColorNight = 0, bgColorNight = 0x33000000, underlineColorNight = 123,
            nightFollowDay = false, textColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            bgColorNightMode = ReadHighlightRule.NIGHT_CUSTOM,
            underlineColorNightMode = ReadHighlightRule.NIGHT_FOLLOW_DAY,
        )
        val config = ReadBookConfig.Config(
            name = "full-md3",
            bgType = 2,
            bgStr = dayBackground.absolutePath,
            bgTypeNight = 2,
            bgStrNight = nightBackground.absolutePath,
            bgTypeEInk = 2,
            bgStrEInk = einkBackground.absolutePath,
            textFont = textFont.absolutePath,
            titleFont = titleFont.absolutePath,
            underline = true,
            dottedLine = true,
            highlightRules = arrayListOf(rule),
            ngReadStyleSource = "md3-read-style",
            ngUnknownFields = mapOf("future" to "true"),
        )
        val output = ByteArrayOutputStream()

        val exportResult = ReadStylePackageManager.export(
            config = config,
            output = output,
            stagingRoot = temporaryFolder.newFolder("full-md3-export"),
            openResource = { reference -> File(reference).takeIf(File::isFile)?.inputStream() },
        )
        val entries = unzip(output.toByteArray())
        val exportedJson = entries.getValue("readConfig.json").decodeToString()

        assertTrue(exportResult.warnings.isEmpty())
        assertFalse(exportedJson.contains(resources.absolutePath.replace('\\', '/')))
        assertFalse(exportedJson.contains("ngReadStyleSource"))
        assertFalse(exportedJson.contains("ngUnknownFields"))
        assertTrue(entries.keys.any { it.startsWith("read_background_0_") })
        assertTrue(entries.keys.any { it.startsWith("read_font_text_") })
        assertTrue(entries.keys.any { it.startsWith("highlight_rule_bg_0_") })
        assertTrue(entries.keys.any { it.startsWith("highlight_rule_font_0_") })

        val imported = ReadStylePackageManager.import(
            ByteArrayInputStream(output.toByteArray()),
            "full-md3-roundtrip",
            temporaryFolder.newFolder("full-md3-import"),
        ).config
        val importedRule = imported.highlightRules.single()

        assertEquals("full-md3", imported.name)
        assertTrue(File(imported.bgStr).isFile)
        assertTrue(File(imported.bgStrNight).isFile)
        assertTrue(File(imported.bgStrEInk).isFile)
        assertTrue(File(imported.textFont).isFile)
        assertTrue(File(imported.titleFont).isFile)
        assertEquals(rule.copy(bgImage = importedRule.bgImage, fontPath = importedRule.fontPath), importedRule)
        assertTrue(File(importedRule.bgImage!!).isFile)
        assertTrue(File(importedRule.fontPath!!).isFile)
    }

    @Test
    fun `imports arc package and maps compatible fields`() {
        val parent = temporaryFolder.newFolder("arc-packages")
        val zip = zipOf(
            "readConfig.json" to """
                {
                  "name":"arc",
                  "paperEffect":true,
                  "readScrollFollowBackground":true,
                  "titleMode":3,
                  "underlineMode":2,
                  "underlineStrokeWidth":0.5,
                  "underlineDashLength":3.0
                }
            """.trimIndent().toByteArray(),
        )

        val result = ReadStylePackageManager.import(
            ByteArrayInputStream(zip),
            "arc-hash",
            parent,
        )

        assertEquals("arc-read-style", result.sourceFormat)
        assertEquals(1, result.config.titleMode)
        assertTrue(result.config.underline)
        assertTrue(result.config.dottedLine)
        assertEquals(1, result.config.underlineHeight)
        assertEquals(3f, result.config.dottedBase, 0f)
        assertEquals(3f, result.config.dottedRatio, 0f)
        assertTrue(result.warnings.any { it.contains("纸张质感") })
        assertTrue(result.warnings.any { it.contains("滚动背景跟随") })
    }

    @Test
    fun `rejects path traversal without leaving installed files`() {
        val parent = temporaryFolder.newFolder("packages")
        val zip = zipOf(
            "readConfig.json" to "{}".toByteArray(),
            "../outside.txt" to "bad".toByteArray(),
        )

        val error = runCatching {
            ReadStylePackageManager.import(ByteArrayInputStream(zip), "bad-hash", parent)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertFalse(File(parent, "bad-hash").exists())
        assertFalse(File(parent.parentFile, "outside.txt").exists())
    }

    @Test
    fun `reimport preserves installed resources and reads the original configuration`() {
        val parent = temporaryFolder.newFolder("reimport-packages")
        val image = ByteArray(256 * 1024 + 17) { (it * 31).toByte() }
        val zip = zipOf(
            "readConfig.json" to """{"name":"fixture","bgType":2,"bgStr":"background.png"}""".toByteArray(),
            "background.png" to image,
        )
        val first = ReadStylePackageManager.import(zip.inputStream(), "same-package", parent)
        first.config.name = "changed after import"

        val second = ReadStylePackageManager.import(zip.inputStream(), "same-package", parent)

        assertEquals("fixture", second.config.name)
        assertEquals(first.installedRoot, second.installedRoot)
        assertArrayEquals(image, File(second.config.bgStr).readBytes())
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
    }

    @Test
    fun `reimport still rejects corrupt resource data and preserves installed files`() {
        val parent = temporaryFolder.newFolder("corrupt-reimport")
        val config = """{"bgType":2,"bgStr":"background.png"}""".toByteArray()
        val image = "unique-stored-resource-for-crc-check".toByteArray()
        val valid = zipOf("readConfig.json" to config, "background.png" to image)
        val installed = ReadStylePackageManager.import(valid.inputStream(), "same-package", parent)
        val bad = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("readConfig.json"))
                zip.write(config)
                zip.closeEntry()
                val checksum = CRC32().apply { update(image) }
                zip.putNextEntry(ZipEntry("background.png").apply {
                    method = ZipEntry.STORED
                    size = image.size.toLong()
                    compressedSize = size
                    crc = checksum.value
                })
                zip.write(image)
                zip.closeEntry()
            }
            output.toByteArray()
        }
        val position = bad.indices.first { start ->
            start + image.size <= bad.size && image.indices.all { bad[start + it] == image[it] }
        }
        bad[position] = (bad[position].toInt() xor 1).toByte()

        val result = runCatching {
            ReadStylePackageManager.import(bad.inputStream(), "same-package", parent)
        }

        assertTrue(result.isFailure)
        assertArrayEquals(image, File(installed.config.bgStr).readBytes())
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
    }

    @Test
    fun `reimport rejects traversal even when the package is installed`() {
        val parent = temporaryFolder.newFolder("traversal-reimport")
        val valid = zipOf("readConfig.json" to "{}".toByteArray())
        val installed = ReadStylePackageManager.import(valid.inputStream(), "same-package", parent)
        val invalid = zipOf(
            "readConfig.json" to "{}".toByteArray(),
            "../outside-reimport.txt" to "bad".toByteArray(),
        )

        val result = runCatching {
            ReadStylePackageManager.import(invalid.inputStream(), "same-package", parent)
        }

        assertTrue(result.isFailure)
        assertTrue(installed.installedRoot.isDirectory)
        assertFalse(File(parent.parentFile, "outside-reimport.txt").exists())
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
    }

    @Test
    fun `reimport still enforces the uncompressed entry size limit`() {
        val parent = temporaryFolder.newFolder("oversize-reimport")
        val valid = zipOf("readConfig.json" to "{}".toByteArray())
        val installed = ReadStylePackageManager.import(valid.inputStream(), "same-package", parent)
        val oversized = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("readConfig.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("oversized.bin"))
                val block = ByteArray(64 * 1024)
                repeat(1025) { zip.write(block) }
                zip.closeEntry()
            }
            output.toByteArray()
        }

        val result = runCatching {
            ReadStylePackageManager.import(oversized.inputStream(), "same-package", parent)
        }

        assertTrue(result.isFailure)
        assertTrue(installed.installedRoot.isDirectory)
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
    }

    @Test
    fun `reimport does not install a partial package if cached resources disappear`() {
        val parent = temporaryFolder.newFolder("missing-reimport")
        val zip = zipOf("readConfig.json" to "{}".toByteArray())
        val installed = ReadStylePackageManager.import(zip.inputStream(), "same-package", parent)
        val input = object : FilterInputStream(zip.inputStream()) {
            private var removed = false

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (!removed) {
                    removed = true
                    assertTrue(installed.installedRoot.deleteRecursively())
                }
                return super.read(bytes, offset, length)
            }
        }

        val result = runCatching {
            ReadStylePackageManager.import(input, "same-package", parent)
        }

        assertTrue(result.isFailure)
        assertFalse(installed.installedRoot.exists())
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith(".staging-") })
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return entries
    }
}
