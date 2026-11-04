package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSelectionActionOrderTest {

    @Test
    fun newUsersGetCharacterCardAtTheFront() {
        val actions = TextSelectionActionOrder.mergeLatest(emptyList())

        assertEquals(TextSelectionBuiltInAction.CHARACTER_CARD, actions.first())
        assertEquals(TextSelectionBuiltInAction.entries.toSet(), actions.toSet())
    }

    @Test
    fun usersWithSavedOrderGetCharacterCardAtTheFrontOnce() {
        val saved = listOf(
            TextSelectionBuiltInAction.REPLACE,
            TextSelectionBuiltInAction.COPY,
            TextSelectionBuiltInAction.SEARCH,
        )

        val actions = TextSelectionActionOrder.mergeLatest(saved)

        assertEquals(TextSelectionBuiltInAction.CHARACTER_CARD, actions.first())
        assertEquals(
            saved,
            actions.drop(1).take(saved.size),
        )
    }

    @Test
    fun usersWhoAlreadyReorderedCharacterCardKeepTheirOrder() {
        val saved = listOf(
            TextSelectionBuiltInAction.COPY,
            TextSelectionBuiltInAction.CHARACTER_CARD,
            TextSelectionBuiltInAction.REPLACE,
        )

        val actions = TextSelectionActionOrder.mergeLatest(saved)

        assertEquals(saved, actions.take(saved.size))
        assertEquals(TextSelectionBuiltInAction.entries.toSet(), actions.toSet())
    }
}
