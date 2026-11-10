package io.legado.app.help.font

import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontUsageInspectorTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun plainPathAndEncodedFileUriReferToTheSameFont() {
        val font = temporaryFolder.newFile("风云 体.ttf")
        val result = FontUsageInspector.matchReferences(
            mapOf("font" to listOf(font.absolutePath)),
            listOf(FontUsageReference(font.toURI().toString(), "界面字体")),
        )
        assertEquals(listOf("界面字体"), result.getValue("font"))
    }

    @Test fun canonicalAliasesAreMatchedButEqualNamesInAnotherDirectoryAreNot() {
        val own = temporaryFolder.newFolder("managed")
        val external = temporaryFolder.newFolder("external")
        val font = File(own, "same.ttf").apply { writeText("font") }
        val other = File(external, "same.ttf").apply { writeText("font") }
        val result = FontUsageInspector.matchReferences(
            mapOf("font" to listOf(font.absolutePath)),
            listOf(
                FontUsageReference(File(own, "../managed/same.ttf").path, "正文"),
                FontUsageReference(other.path, "外部目录"),
            ),
        )
        assertEquals(listOf("正文"), result.getValue("font"))
    }

    @Test fun oneAssetWithMultipleOwnedCopiesChecksEveryPathAndDeduplicatesLocations() {
        val first = temporaryFolder.newFile("first.ttf")
        val second = temporaryFolder.newFile("private.font")
        val result = FontUsageInspector.matchReferences(
            mapOf("same-content" to listOf(first.path, second.path), "unused" to emptyList()),
            listOf(
                FontUsageReference(first.path, "界面字体"),
                FontUsageReference(second.path, "界面字体"),
                FontUsageReference(first.path, "书籍《示例》·标题"),
            ),
        )
        assertEquals(listOf("界面字体", "书籍《示例》·标题"), result.getValue("same-content"))
        assertTrue(result.getValue("unused").isEmpty())
    }

    @Test fun contentUrisRemainDistinctFromSimilarFilePaths() {
        val uri = "content://fonts/document/a%2Fb.ttf"
        val result = FontUsageInspector.matchReferences(
            mapOf("uri" to listOf(uri), "file" to listOf("/document/a/b.ttf")),
            listOf(FontUsageReference(uri, "外部来源")),
        )
        assertEquals(listOf("外部来源"), result.getValue("uri"))
        assertTrue(result.getValue("file").isEmpty())
    }

    @Test fun eachReadingFontSlotAndDisabledEmbeddedHighlightRuleAreProtected() {
        val font = temporaryFolder.newFile("font.ttf")
        val config = ReadBookConfig.Config(
            textFont = font.path, titleFont = font.path,
            headerFont = font.path, footerFont = font.path,
            highlightRules = arrayListOf(ReadHighlightRule(name = "停用规则", enabled = false, fontPath = font.path)),
        )
        val references = FontUsageInspector.referencesForConfig("书籍《测试》", config)
        val result = FontUsageInspector.matchReferences(mapOf("font" to listOf(font.path)), references)
        assertEquals(5, result.getValue("font").size)
        assertTrue(result.getValue("font").any { it.endsWith("·页脚") })
        assertTrue(result.getValue("font").any { it.contains("停用规则") })
        assertFalse(config.highlightRules.single().enabled)
        assertEquals(font.path, config.textFont)
    }

    @Test fun confirmationUsesReadableLocationsAndKeepsTheOriginalDeletionKeys() {
        val font = temporaryFolder.newFile("font.ttf")
        val preset = ReadBookConfig.Config(
            titleFont = font.path,
            highlightRules = arrayListOf(ReadHighlightRule(name = "重要·引用", fontPath = font.path)),
        )
        val presetReferences = FontUsageInspector.referencesForConfig(
            "阅读预设「秋山·书意」", preset, "「秋山·书意」预设",
        )
        val duplicate = presetReferences.first { it.path == font.path }.copy(path = font.toURI().toString())
        val result = FontUsageInspector.matchUsageReferences(
            mapOf("font" to listOf(font.path)),
            presetReferences + duplicate + FontUsageReference(font.path, "界面字体", "应用界面"),
        ).getValue("font")

        assertEquals(
            listOf("阅读预设「秋山·书意」·标题", "阅读预设「秋山·书意」·高亮规则「重要·引用」", "界面字体"),
            result.map(FontUsageReference::location),
        )
        assertEquals(
            listOf("「秋山·书意」预设的章节标题", "「秋山·书意」预设的高亮规则「重要·引用」", "应用界面"),
            result.map(FontUsageReference::displayLocation),
        )
    }

    @Test fun deletionSwitchesOnlyTheMatchingSlotsAndRulesToSystemDefault() {
        val selected = temporaryFolder.newFile("font.ttf")
        val otherFolder = temporaryFolder.newFolder("other")
        val other = File(otherFolder, "font.ttf")
        val affected = ReadHighlightRule(name = "匹配", enabled = false, fontPath = selected.path)
        val unaffected = ReadHighlightRule(name = "其他", fontPath = other.path)
        val config = ReadBookConfig.Config(
            textFont = other.path,
            titleFont = selected.toURI().toString(),
            headerFont = selected.path,
            footerFont = "",
            highlightRules = arrayListOf(affected, unaffected),
        )
        val identity = FontReferenceIdentity.normalize(selected.path)
        val matches: (String?) -> Boolean = { FontReferenceIdentity.normalize(it) == identity }

        assertTrue(config.resetDeletedFont(matches))
        assertEquals(other.path, config.textFont)
        assertEquals(SYSTEM_DEFAULT_FONT, config.titleFont)
        assertEquals(SYSTEM_DEFAULT_FONT, config.headerFont)
        assertEquals("", config.footerFont)
        assertEquals(listOf(affected.copy(fontPath = SYSTEM_DEFAULT_FONT), unaffected), config.highlightRules)
        assertFalse(config.resetDeletedFont(matches))
    }

    @Test fun systemSelectionsAndEmptyPathsDoNotBlockDeletion() {
        val result = FontUsageInspector.matchReferences(
            mapOf("font" to listOf(temporaryFolder.newFile("font.ttf").path)),
            listOf("system", "serif", "monospace", "", null).map { FontUsageReference(it, "系统字体") },
        )
        assertTrue(result.getValue("font").isEmpty())
    }

    @Test fun corruptPresetDocumentsFailInsteadOfBecomingEmptyUsage() {
        assertThrows(Exception::class.java) { FontUsageInspector.decodePresets("null") }
        assertThrows(Exception::class.java) { FontUsageInspector.decodePresets("[null]") }
        assertThrows(Exception::class.java) { FontUsageInspector.decodePresets("{broken") }
        assertThrows(Exception::class.java) { FontUsageInspector.decodePreset("null") }
        assertThrows(Exception::class.java) { FontUsageInspector.decodePreset("[") }
    }

    @Test fun rawCandidateIsProtectedWhenAnExternalVolumeReferenceCannotBeResolved() {
        val candidate = temporaryFolder.newFile("font.ttf")
        val unknown = "content://com.android.externalstorage.documents/document/1234-5678%3AFonts%2Ffont.ttf"
        assertThrows(IllegalStateException::class.java) {
            FontUsageInspector.matchReferences(mapOf("font" to listOf(candidate.path)),
                listOf(FontUsageReference(unknown, "书籍·正文")),
                normalize = { FontReferenceIdentity.normalize(it) })
        }
    }

    @Test fun aVerifiedSecondaryVolumeReferenceProtectsTheRawCandidate() {
        val volume = temporaryFolder.newFolder("1234-5678")
        val candidate = java.io.File(volume, "Fonts/font.ttf")
        val reference = "content://com.android.externalstorage.documents/tree/1234-5678%3AFonts/document/1234-5678%3AFonts%2Ffont.ttf"
        val usages = FontUsageInspector.matchReferences(mapOf("font" to listOf(candidate.path)),
            listOf(FontUsageReference(reference, "书籍·正文")),
            normalize = { FontReferenceIdentity.normalize(it, volumeRoots = mapOf("1234-5678" to volume.path)) })
        assertEquals(listOf("书籍·正文"), usages.getValue("font"))
    }

    @Test fun persistedPresetFontFieldsAreReadWithoutChangingTheirPaths() {
        val configs = FontUsageInspector.decodePresets("""[{"name":"夜间","titleFont":"/fonts/title.ttf","footerFont":"/fonts/footer.ttf"}]""")
        assertEquals("/fonts/title.ttf", configs.single().titleFont)
        assertEquals("/fonts/footer.ttf", configs.single().footerFont)
        val single = FontUsageInspector.decodePreset("""{"textFont":"/fonts/body.ttf"}""")
        assertEquals("/fonts/body.ttf", single.textFont)
    }
}
