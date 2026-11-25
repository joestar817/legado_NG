package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterNameMarksTest {
    @Test
    fun marksMainNamesAndUniqueAliasesWithTheSameId() {
        val names = index(BookCharacter(id = 1, name = "比比东", aliasesJson = "[null,\"圣女\"]"))
        assertEquals(
            listOf(CharacterNameMarks.Mark(1, 4, 1), CharacterNameMarks.Mark(6, 8, 1)),
            names.find("「比比东」是圣女。"),
        )
    }

    @Test
    fun prefersLongestMatchAndMainNameBeatsAlias() {
        val names = index(
            BookCharacter(id = 1, name = "韩立", aliasesJson = "[\"厉飞雨\"]"),
            BookCharacter(id = 2, name = "厉飞雨"),
            BookCharacter(id = 3, name = "韩立言"),
        )
        assertEquals(listOf(3L, 2L, 1L), names.find("韩立言、厉飞雨、韩立").map { it.characterId })
    }

    @Test
    fun ambiguousAliasDoesNotFallBackToItsShorterPrefix() {
        val names = index(
            BookCharacter(id = 1, name = "韩"),
            BookCharacter(id = 2, name = "厉飞雨", aliasesJson = "[\"韩公子\"]"),
            BookCharacter(id = 3, name = "韩立", aliasesJson = "[\"韩公子\"]"),
        )
        assertTrue(names.find("韩公子").isEmpty())
    }

    @Test
    fun disabledCharactersAndEmbeddedLatinNamesAreNotMarked() {
        val names = index(
            BookCharacter(id = 1, name = "安娜", enabled = false),
            BookCharacter(id = 2, name = "Ann"),
        )
        assertEquals(
            listOf(CharacterNameMarks.Mark(11, 14, 2)),
            names.find("安娜遇到ANNIE和 ANN。"),
        )
    }

    @Test
    fun intersectionUsesHalfOpenPositions() {
        val marks = index(BookCharacter(id = 1, name = "韩立")).find("韩立与韩立")
        assertEquals(0, CharacterNameMarks.firstEndingAfter(marks, 1))
        assertEquals(1, CharacterNameMarks.firstEndingAfter(marks, 2))
        assertEquals(2, CharacterNameMarks.firstEndingAfter(marks, 5))
    }

    private fun index(vararg characters: BookCharacter) =
        CharacterNameMarks.from("fixture", characters.toList())
}
