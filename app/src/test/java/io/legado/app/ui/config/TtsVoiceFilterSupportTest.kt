package io.legado.app.ui.config

import io.legado.app.help.tts.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsVoiceFilterSupportTest {

    @Test
    fun `normalizes and orders language labels`() {
        val voices = listOf(
            TtsVoice(id = "1", name = "English", language = "en-US"),
            TtsVoice(id = "2", name = "Chinese", language = "zh-CN/yue"),
            TtsVoice(id = "3", name = "Japanese", language = "ja-JP")
        )

        assertEquals(
            listOf("中", "英", "日", "粤"),
            TtsVoiceFilterSupport.availableLanguageLabels(voices)
        )
    }

    @Test
    fun `normalizes known genders`() {
        assertEquals("男", TtsVoiceFilterSupport.genderLabel("male"))
        assertEquals("女", TtsVoiceFilterSupport.genderLabel("Woman"))
        assertEquals(null, TtsVoiceFilterSupport.genderLabel("neutral"))
    }
}
