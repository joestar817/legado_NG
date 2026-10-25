package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ReadHighlightRulePackageManagerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun export(rules: List<ReadHighlightRule>, resources: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ReadHighlightRulePackageManager.export(rules, output, temporaryFolder.root) {
                resources[it]?.inputStream()
            }
        }.toByteArray()

    @Test fun packagedResourcesAndAllStylesSurviveRoundTrip() {
        val image = byteArrayOf(1, 2, 3, 4)
        val font = byteArrayOf(5, 6, 7, 8)
        val rule = ReadHighlightRule(
            id = "all", name = "全部样式", pattern = "对话", sampleText = "对话示例",
            targetScope = 2, enabled = false, textColor = 123, bgColor = 456,
            underlineMode = 5, underlineColor = 789, underlineWidth = 2.5f,
            underlineOffset = 3.5f, underlineSvgPath = "M0,0 L10,2",
            bgImage = "assets://background/paper.png", bgImageFit = 3, bgImageScale = 1.4f,
            fontPath = "assets://font/reader.ttf", fontWeight = 700, isItalic = true,
            npLeft = 0.12f, npRight = 0.23f, npTop = 0.34f, npBottom = 0.45f,
            textColorNight = 0, bgColorNight = 0x33000000, underlineColorNight = 321,
            nightFollowDay = true, textColorNightMode = ReadHighlightRule.NIGHT_CUSTOM,
            bgColorNightMode = ReadHighlightRule.NIGHT_DEFAULT,
            underlineColorNightMode = ReadHighlightRule.NIGHT_FOLLOW_DAY,
        )
        val bytes = export(listOf(rule), mapOf(rule.bgImage!! to image, rule.fontPath!! to font))
        val restored = ReadHighlightRulePackageManager.importZip(bytes, temporaryFolder.newFolder()).rules.single()
        assertEquals(rule.copy(bgImage = restored.bgImage, fontPath = restored.fontPath), restored)
        assertArrayEquals(image, File(restored.bgImage!!).readBytes())
        assertArrayEquals(font, File(restored.fontPath!!).readBytes())
    }

    @Test fun copiesWithSharedContentUseOneResourceEntry() {
        val data = byteArrayOf(1, 2, 3)
        val rules = (0 until 128).map { index ->
            ReadHighlightRule(id = "$index", pattern = "a", bgImage = "image$index", fontPath = "shared-font")
        }
        val resources = rules.associate { it.bgImage!! to data } + ("shared-font" to data)
        val bytes = export(rules, resources)
        val entries = mutableListOf<String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                entries += (zip.nextEntry ?: break).name
                zip.closeEntry()
            }
        }
        assertEquals(2, entries.size) // 相同字节只写一个资源，另一个是配置。
        assertEquals(128, ReadHighlightRulePackageManager.importZip(bytes, temporaryFolder.newFolder()).rules.size)
    }

    @Test fun configurationBetweenTwoAndFiveMiBCanRoundTrip() {
        val rule = ReadHighlightRule(id = "large", pattern = "a", sampleText = "x".repeat(3 * 1024 * 1024))
        val bytes = export(listOf(rule), emptyMap())
        assertEquals(rule, ReadHighlightRulePackageManager.importZip(bytes, temporaryFolder.newFolder()).rules.single())
    }

    @Test fun tooManyDistinctResourcesAreRejectedBeforeWriting() {
        val rules = (0 until 256).map { ReadHighlightRule(id = "$it", pattern = "a", bgImage = "image$it") }
        val output = ByteArrayOutputStream()
        try {
            ReadHighlightRulePackageManager.export(rules, output, temporaryFolder.root) { it.byteInputStream() }
            fail("256个资源加配置超过导入允许的256个条目")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, output.size())
        }
    }

    @Test fun missingResourceAndOversizedConfigurationDoNotWriteOutput() {
        listOf(
            ReadHighlightRule(pattern = "a", bgImage = "missing.png"),
            ReadHighlightRule(pattern = "a", sampleText = "x".repeat(5 * 1024 * 1024)),
        ).forEach { rule ->
            val output = ByteArrayOutputStream()
            try {
                ReadHighlightRulePackageManager.export(listOf(rule), output, temporaryFolder.root) { null }
                fail("应拒绝不完整或超过限制的规则包")
            } catch (_: IllegalStateException) {
                assertEquals(0, output.size())
            } catch (_: IllegalArgumentException) {
                assertEquals(0, output.size())
            }
        }
        assertTrue(temporaryFolder.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun readsStandaloneHighlightRuleDocument() {
        val rules = decodePackagedHighlightRules(
            """[{"id":"standalone","name":"独立","pattern":"standalone"}]""",
        )

        assertEquals(listOf("standalone"), rules.map(ReadHighlightRule::id))
    }
}
