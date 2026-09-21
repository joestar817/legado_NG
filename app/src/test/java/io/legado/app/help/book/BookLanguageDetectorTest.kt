package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookLanguageDetectorTest {

    @Test
    fun hanSampleIsCjk() {
        assertEquals(
            BookScriptClass.Cjk,
            BookLanguageDetector.classifyScript("凡人修仙传讲述了一个普通少年的修仙之路"),
        )
    }

    @Test
    fun kanaSampleIsCjk() {
        assertEquals(
            BookScriptClass.Cjk,
            BookLanguageDetector.classifyScript("これは日本語の小説です。物語は東京から始まる。"),
        )
    }

    @Test
    fun hangulSampleIsCjk() {
        assertEquals(
            BookScriptClass.Cjk,
            BookLanguageDetector.classifyScript("이것은 한국어 소설입니다. 이야기는 서울에서 시작됩니다."),
        )
    }

    @Test
    fun englishAndFrenchSamplesAreLatin() {
        assertEquals(
            BookScriptClass.Latin,
            BookLanguageDetector.classifyScript(
                "Call me Ishmael. Some years ago never mind how long precisely",
            ),
        )
        assertEquals(
            BookScriptClass.Latin,
            BookLanguageDetector.classifyScript(
                "Bonjour le monde, voici un roman français avec beaucoup de texte.",
            ),
        )
    }

    @Test
    fun arabicAndCyrillicSamplesAreOther() {
        assertEquals(
            BookScriptClass.Other,
            BookLanguageDetector.classifyScript("هذا كتاب عربي فيه نص كاف للتصنيف اللغوي في هذه الرواية"),
        )
        assertEquals(
            BookScriptClass.Other,
            BookLanguageDetector.classifyScript("Это русская книга с достаточным количеством текста для классификации"),
        )
    }

    @Test
    fun mixedChineseAndEnglishIsCjk() {
        assertEquals(
            BookScriptClass.Cjk,
            BookLanguageDetector.classifyScript("修仙界的规则很简单。The golden core is only the beginning of the path."),
        )
    }

    @Test
    fun shortTextStaysUnknownUnlessCjk() {
        assertNull(BookLanguageDetector.classifyScript("It"))
        assertEquals(BookScriptClass.Cjk, BookLanguageDetector.classifyScript("凡人修仙传"))
    }

    @Test
    fun languageTagsMapToScriptClasses() {
        assertEquals(BookScriptClass.Cjk, BookLanguageDetector.fromLanguageTag("zh-CN"))
        assertEquals(BookScriptClass.Cjk, BookLanguageDetector.fromLanguageTag("ja-JP"))
        assertEquals(BookScriptClass.Cjk, BookLanguageDetector.fromLanguageTag("ko"))
        assertEquals(BookScriptClass.Latin, BookLanguageDetector.fromLanguageTag("en-US"))
        assertEquals(BookScriptClass.Latin, BookLanguageDetector.fromLanguageTag("fr"))
        assertEquals(BookScriptClass.Other, BookLanguageDetector.fromLanguageTag("ar"))
        assertEquals(BookScriptClass.Other, BookLanguageDetector.fromLanguageTag("ru-RU"))
        assertEquals(BookScriptClass.Latin, BookLanguageDetector.fromLanguageTag("sr-Latn"))
        assertNull(BookLanguageDetector.fromLanguageTag(""))
        assertNull(BookLanguageDetector.fromLanguageTag(null))
    }

    @Test
    fun sampleOverridesDefaultEnglishMetadata() {
        assertEquals(
            BookScriptClass.Cjk,
            BookLanguageDetector.detect(
                metadataLanguage = "en",
                samples = listOf("这是一部中文小说，讲述修仙者在乱世中求存的故事。"),
            ),
        )
        assertEquals(
            BookScriptClass.Latin,
            BookLanguageDetector.detect(
                metadataLanguage = "zh",
                samples = listOf("Call me Ishmael. Some years ago never mind how long precisely having little or no money"),
            ),
        )
        assertEquals(
            BookScriptClass.Latin,
            BookLanguageDetector.detect(metadataLanguage = "en-US", samples = listOf("Hi")),
        )
    }
}
