package io.legado.app.help.config

import io.legado.app.help.book.BookScriptClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadStyleLanguagePolicyTest {

    private val names = listOf("秋山书意", "经典纯白", "暖纸书香")
    private val bindings = LanguageStyleBindings(cjk = "秋山书意", latin = "经典纯白")

    @Test
    fun firstOpenAppliesMappedPresetOnce() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = "zh-CN",
            sampleTexts = listOf("凡人修仙传"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertEquals("秋山书意", decision.styleNameToSelect)
        assertEquals(BookScriptClass.Cjk, decision.scriptClass)
        assertTrue(decision.commitRememberedStyle)
    }

    @Test
    fun laterOpenRestoresRememberedName() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = "暖纸书香",
            languageHint = "zh-CN",
            sampleTexts = listOf("凡人修仙传"),
            existingStyleNames = names,
            currentStyleName = "秋山书意",
            bindings = bindings,
        )
        assertEquals("暖纸书香", decision.styleNameToSelect)
        assertEquals(BookScriptClass.Cjk, decision.scriptClass)
        assertFalse(decision.commitRememberedStyle)
    }

    @Test
    fun unassignedOtherKeepsCurrentAndCommits() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = "ar",
            sampleTexts = listOf("هذا كتاب عربي فيه نص كاف للتصنيف اللغوي في هذه الرواية"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertEquals("暖纸书香", decision.styleNameToSelect)
        assertEquals(BookScriptClass.Other, decision.scriptClass)
        assertTrue(decision.commitRememberedStyle)
    }

    @Test
    fun unknownSampleDoesNotCommit() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = null,
            sampleTexts = listOf("Hi"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertEquals("暖纸书香", decision.styleNameToSelect)
        assertNull(decision.scriptClass)
        assertFalse(decision.commitRememberedStyle)
    }

    @Test
    fun unknownFirstOpenThenContentDetectionAppliesMapping() {
        val unknown = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = null,
            sampleTexts = listOf("Hi"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertFalse(unknown.commitRememberedStyle)

        val corrected = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = null,
            sampleTexts = listOf("这是一部中文小说，讲述修仙者在乱世中求存的故事。"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertEquals("秋山书意", corrected.styleNameToSelect)
        assertEquals(BookScriptClass.Cjk, corrected.scriptClass)
        assertTrue(corrected.commitRememberedStyle)
    }

    @Test
    fun missingRememberedPresetFallsBackToCurrentWithoutRewriting() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = "deleted",
            languageHint = null,
            sampleTexts = emptyList(),
            existingStyleNames = names,
            currentStyleName = "经典纯白",
            bindings = bindings,
        )
        assertEquals("经典纯白", decision.styleNameToSelect)
        assertFalse(decision.commitRememberedStyle)
    }

    @Test
    fun latinMappingAppliesOnFirstOpen() {
        val decision = ReadStyleLanguagePolicy.decide(
            rememberedStyleName = null,
            languageHint = "en-US",
            sampleTexts = listOf("Call me Ishmael. Some years ago never mind how long precisely"),
            existingStyleNames = names,
            currentStyleName = "暖纸书香",
            bindings = bindings,
        )
        assertEquals("经典纯白", decision.styleNameToSelect)
        assertEquals(BookScriptClass.Latin, decision.scriptClass)
        assertTrue(decision.commitRememberedStyle)
    }
}
