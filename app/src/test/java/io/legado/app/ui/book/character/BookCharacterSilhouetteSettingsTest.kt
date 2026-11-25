package io.legado.app.ui.book.character

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.legado.app.data.entities.BookCharacter.RoleTag
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.ANCIENT
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.AUTO
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.FANTASY
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.GENERIC
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle.MODERN
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class BookCharacterSilhouetteSettingsTest {

    @Test
    fun sharedSelectionOverridesBothBooksAndAutomaticRestoresTheirOwnStyles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val observed = Channel<BookCharacterSilhouetteStyle>(Channel.UNLIMITED)
        val observer = launch {
            BookCharacterSilhouetteSettings.changes(context).collect { observed.send(it) }
        }
        try {
            assertEquals(AUTO, withTimeout(2_000) { observed.receive() })
            assertEquals(FANTASY, BookCharacterSilhouetteSettings.get(context).resolve(FANTASY))
            assertEquals(MODERN, BookCharacterSilhouetteSettings.get(context).resolve(MODERN))

            BookCharacterSilhouetteSettings.set(context, ANCIENT)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(ANCIENT, withTimeout(2_000) { observed.receive() })
            assertEquals(ANCIENT, BookCharacterSilhouetteSettings.get(context).resolve(FANTASY))
            assertEquals(ANCIENT, BookCharacterSilhouetteSettings.get(context).resolve(MODERN))

            BookCharacterSilhouetteSettings.set(context, AUTO)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(AUTO, withTimeout(2_000) { observed.receive() })
            assertEquals(FANTASY, BookCharacterSilhouetteSettings.get(context).resolve(FANTASY))
            assertEquals(MODERN, BookCharacterSilhouetteSettings.get(context).resolve(MODERN))
        } finally {
            observer.cancelAndJoin()
        }
    }

    @Test
    fun styleChangesOnlyAlterUnassignedPortraitArtwork() {
        assertNotEquals(ANCIENT.forRole(RoleTag.MALE_LEAD), MODERN.forRole(RoleTag.MALE_LEAD))
        assertNotEquals(ANCIENT.forRole(RoleTag.FEMALE_SUPPORT), MODERN.forRole(RoleTag.FEMALE_SUPPORT))

        listOf(RoleTag.MALE_LEAD, RoleTag.FEMALE_SUPPORT).forEach { roleTag ->
            val importedFallback = GENERIC.forRole(roleTag, hasPortrait = true)
            BookCharacterSilhouetteStyle.entries.forEach { style ->
                assertEquals(importedFallback, style.forRole(roleTag, hasPortrait = true))
            }
        }
    }
}
