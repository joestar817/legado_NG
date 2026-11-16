package io.legado.app.ui.config

import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoiceDrawerPositionTest {
    @Test
    fun selectedVoiceIsLocatedWithinItsEngineAfterFilteringAndReordering() {
        val first = group("first", card("first", "one"), card("first", "two"))
        val second = group(
            "second",
            card("second", "one"),
            card("second", "two"),
            card("second", "selected", selected = true),
        )
        val full = listOf(first, second)
        val target = full.selectedVoiceTarget()!!

        assertEquals(TtsVoiceDrawerTarget("second", "second/selected"), target)
        assertEquals(TtsVoiceDrawerPosition(page = 1, row = 2), full.voicePosition(target))

        val filtered = listOf(second.copy(cards = listOf(second.cards.last())))
        assertEquals(target, filtered.selectedVoiceTarget())
        assertEquals(TtsVoiceDrawerPosition(page = 0, row = 0), filtered.voicePosition(target))

        val reordered = listOf(second, first)
        assertEquals(TtsVoiceDrawerPosition(page = 0, row = 2), reordered.voicePosition(target))
        assertNull(listOf(first).voicePosition(target))
        assertNull(listOf(second.copy(cards = second.cards.dropLast(1))).voicePosition(target))
        assertNull(listOf(first).selectedVoiceTarget())
    }

    @Test
    fun searchAndFiltersKeepEveryEngineTabAndOnlyMatchVoiceNames() {
        val groups = listOf(
            group(
                "first",
                card("first", "xiao", name = "晓晓", languages = listOf("中"), gender = "女"),
                card("first", "yun", name = "云希", languages = listOf("中"), gender = "男"),
            ),
            group(
                "second",
                card("second", "zhai", name = "晓臻", languages = listOf("粤"), gender = "女", selected = true),
            ),
        )

        val filtered = groups.filterVoices("晓", setOf("中"), setOf("女"))
        assertEquals(listOf("first", "second"), filtered.map { it.engineId })
        assertEquals(listOf("晓晓"), filtered[0].cards.map { it.option.voice.name })
        assertEquals(emptyList<TtsVoiceDrawerCard>(), filtered[1].cards)
        assertNull(filtered.selectedVoiceTarget())
        assertEquals(TtsVoiceDrawerPosition(1, 0), groups.voicePosition(groups.selectedVoiceTarget()!!))

        val engineSearch = groups.filterVoices("first", emptySet(), emptySet())
        assertEquals(2, engineSearch.size)
        assertEquals(listOf(0, 0), engineSearch.map { it.cards.size })
        assertEquals(groups, groups.filterVoices("", emptySet(), emptySet()))
    }

    private fun group(id: String, vararg cards: TtsVoiceDrawerCard) =
        TtsVoiceDrawerGroup(engineId = id, engineName = id, cards = cards.toList())

    private fun card(
        engineId: String,
        voiceId: String,
        selected: Boolean = false,
        name: String = voiceId,
        languages: List<String> = emptyList(),
        gender: String? = null,
    ) =
        TtsVoiceDrawerCard(
            option = TtsVoiceOption(
                engine = TtsEngineSetting(id = engineId, name = engineId, type = TtsEngineType.SCRIPT),
                voice = TtsVoice(id = voiceId, name = name),
                systemDefault = false,
            ),
            key = "$engineId/$voiceId",
            selected = selected,
            languageLabels = languages,
            genderLabel = gender,
            style = null,
            tags = emptyList(),
            canEditParams = false,
            hasVoiceParams = false,
        )
}
