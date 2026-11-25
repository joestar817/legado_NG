package io.legado.app.ui.book.character

import io.legado.app.data.entities.BookCharacterTtsBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class BookCharacterVoiceTest {
    @Test
    fun replacingVoiceKeepsRoleIdentityAndExistingSettings() {
        val current = BookCharacterTtsBinding.character("book", 12L).copy(
            engineId = "engine",
            voiceId = "old",
            bindingMode = BookCharacterTtsBinding.BindingMode.AUTO,
            emotionStyleMapJson = """{"calm":"gentle"}""",
            createdAt = 10L,
            updatedAt = 20L,
        )

        val updated = current.withSelectedVoice(
            "engine", "new", BookCharacterTtsBinding.BindingMode.MANUAL, now = 30L,
        )

        assertEquals(
            current.copy(voiceId = "new", bindingMode = BookCharacterTtsBinding.BindingMode.MANUAL, updatedAt = 30L),
            updated,
        )
        assertNotSame(current, updated)
        assertEquals("old", current.voiceId)
    }

    @Test
    fun choosingDialogueFallbackClearsTheVoiceAndKeepsTheRole() {
        val current = BookCharacterTtsBinding.character("book", 12L).copy(
            engineId = "engine",
            voiceId = "old",
            createdAt = 0L,
        )

        val updated = current.withSelectedVoice(
            "engine", null, BookCharacterTtsBinding.BindingMode.INHERIT, now = 30L,
        )

        assertEquals(current.targetId, updated.targetId)
        assertEquals(BookCharacterTtsBinding.BindingMode.INHERIT, updated.bindingMode)
        assertEquals(30L, updated.createdAt)
        assertEquals(30L, updated.updatedAt)
        assertNull(updated.voiceId)
    }
}
