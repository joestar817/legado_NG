package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ReadStylePackageManagerTest {

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
