package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterCardSelectionTest {

    private val hanLi = BookCharacter(
        id = 1L,
        name = "韩立",
        aliasesJson = "[\"韩跑跑\",\"厉飞雨\"]",
    )
    private val nangong = BookCharacter(
        id = 2L,
        name = "南宫婉",
        aliasesJson = "[]",
    )
    private val characters = listOf(hanLi, nangong)

    @Test
    fun matchesCharacterName() {
        assertEquals(hanLi, CharacterCardSelection.match("韩立", characters))
    }

    @Test
    fun matchesAlias() {
        assertEquals(hanLi, CharacterCardSelection.match("韩跑跑", characters))
    }

    @Test
    fun exactNameWinsOverEarlierAlias() {
        val aliasOwner = BookCharacter(id = 1L, name = "韩立", aliasesJson = "[\"厉飞雨\"]")
        val exactNameOwner = BookCharacter(id = 2L, name = "厉飞雨", aliasesJson = "[]")

        assertEquals(
            exactNameOwner,
            CharacterCardSelection.match("厉飞雨", listOf(aliasOwner, exactNameOwner)),
        )
    }

    @Test
    fun listOrderDoesNotChangeExactNameMatch() {
        val aliasOwner = BookCharacter(id = 1L, name = "韩立", aliasesJson = "[\"厉飞雨\"]")
        val exactNameOwner = BookCharacter(id = 2L, name = "厉飞雨", aliasesJson = "[]")

        assertEquals(
            exactNameOwner,
            CharacterCardSelection.match("厉飞雨", listOf(exactNameOwner, aliasOwner)),
        )
    }

    @Test
    fun aliasStillMatchesWhenNoExactNameExists() {
        assertEquals(hanLi, CharacterCardSelection.match("厉飞雨", characters))
    }

    @Test
    fun ignoresSurroundingQuotesAndBrackets() {
        assertEquals(hanLi, CharacterCardSelection.match("「韩立」", characters))
        assertEquals(hanLi, CharacterCardSelection.match("“韩立”", characters))
        assertEquals(hanLi, CharacterCardSelection.match("（韩立）", characters))
    }

    @Test
    fun ignoresCaseAndRepeatedWhitespace() {
        val character = BookCharacter(name = "Li  Fei", aliasesJson = "[\"Little Li\"]")
        assertEquals(
            character,
            CharacterCardSelection.match("li fei", listOf(character)),
        )
        assertEquals(
            character,
            CharacterCardSelection.match("“LITTLE   LI”", listOf(character)),
        )
    }

    @Test
    fun rejectsSentencesBlankTextAndLongText() {
        assertNull(CharacterCardSelection.match("韩立说道", characters))
        assertNull(CharacterCardSelection.match("韩立，快走", characters))
        assertNull(CharacterCardSelection.match("", characters))
        assertNull(CharacterCardSelection.match("  ", characters))
        assertNull(CharacterCardSelection.match("这是一个超过二十四个字符的句子不应该被当成角色名字", characters))
    }

    @Test
    fun malformedAliasesDoNotBreakMatching() {
        val character = BookCharacter(id = 3L, name = "沈言卿", aliasesJson = "not-json")
        assertEquals(
            character,
            CharacterCardSelection.match("沈言卿", listOf(character)),
        )
        assertNull(CharacterCardSelection.match("言卿", listOf(character)))
    }
}
