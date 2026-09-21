package io.legado.app.data.entities

import io.legado.app.help.storage.Md3BackupCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookReadConfigLanguageTest {

    private val gson = Md3BackupCompatibility.bookGson

    @Test
    fun languageFieldsSurviveJson() {
        val restored = gson.fromJson(
            """{"languageHint":"zh-CN","scriptClass":"cjk","readStyleName":"秋山书意"}""",
            Book.ReadConfig::class.java,
        )
        assertEquals("zh-CN", restored.languageHint)
        assertEquals("cjk", restored.scriptClass)
        assertEquals("秋山书意", restored.readStyleName)
    }

    @Test
    fun missingLanguageFieldsStayNull() {
        val restored = gson.fromJson("{\"reverseToc\":true}", Book.ReadConfig::class.java)
        assertNull(restored.languageHint)
        assertNull(restored.scriptClass)
        assertNull(restored.readStyleName)
    }
}
