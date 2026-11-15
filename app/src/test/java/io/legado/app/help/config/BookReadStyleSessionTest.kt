package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test

class BookReadStyleSessionTest {
    private val stored = linkedMapOf<String, String?>()
    private val saved = linkedMapOf<String, String?>()
    private val session = BookReadStyleSession { url, snapshot ->
        stored[url] = snapshot.active
        saved[url] = snapshot.saved
    }
    private fun book(url: String, style: String? = null) = Book(bookUrl = url).apply {
        config.independentReadStyle = style
    }

    @Test fun bookWithoutOverrideFollowsGlobalWithoutWriting() {
        assertFalse(session.bind(book("a")))
        assertNull(session.config)
        session.save()
        assertTrue(stored.isEmpty())
    }

    @Test fun snapshotCapturesSharedLayoutAndPresetAppearanceWithoutSharingObjects() {
        val preset = ReadBookConfig.Config(name = "模板", bgType = 0, bgStr = "#112233", textSize = 18,
            textColor = "#123456", readFloatingPrimaryStrength = 72,
            readFloatingSeed = 0xFFCC8844.toInt(), readFloatingSeedNight = 0xFF41566B.toInt(),
            readFloatingColorStyle = ReadFloatingColorStyle.FRUIT_SALAD)
        val shared = ReadBookConfig.Config(textSize = 26, textFont = "font.ttf", lineSpacingExtra = 19,
            paddingLeft = 33, titleColor = 123, bgAlpha = 81)
        val copy = preset.copyForBook(shared)
        assertEquals("#112233", copy.bgStr)
        assertEquals("#123456", GSON.toJsonTree(copy).asJsonObject["textColor"].asString)
        assertEquals(72, copy.readFloatingPrimaryStrength)
        assertEquals(0xFFCC8844.toInt(), copy.readFloatingSeed)
        assertEquals(0xFF41566B.toInt(), copy.readFloatingSeedNight)
        assertEquals(ReadFloatingColorStyle.FRUIT_SALAD, copy.readFloatingColorStyle)
        assertEquals(26, copy.textSize)
        assertEquals("font.ttf", copy.textFont)
        assertEquals(19, copy.lineSpacingExtra)
        assertEquals(33, copy.paddingLeft)
        assertEquals(123, copy.titleColor)
        assertEquals(81, copy.bgAlpha)
        copy.textSize = 40
        copy.bgStr = "#FFFFFF"
        assertEquals(18, preset.textSize)
        assertEquals(26, shared.textSize)
        assertEquals("#112233", preset.bgStr)
    }

    @Test fun selectingAndEditingOnlyChangesBookCopy() {
        val global = ReadBookConfig.Config(name = "模板", textSize = 18)
        val owner = book("a")
        session.bind(owner)
        session.use(global)
        session.config!!.textSize = 30
        session.config!!.name = "本书命名"
        session.save()
        assertEquals(18, global.textSize)
        assertEquals("模板", global.name)
        assertEquals(30, BookReadStyleSession.decode(stored["a"])!!.textSize)
        assertEquals(stored["a"], owner.config.independentReadStyle)
    }

    @Test fun switchingBooksFlushesOnlyPreviousBookAndRestoresOwnCopy() {
        session.bind(book("a"))
        session.use(ReadBookConfig.Config(textSize = 25))
        session.config!!.textSize = 28
        assertTrue(session.bind(book("b")))
        assertNull(session.config)
        assertFalse(stored.containsKey("b"))
        assertTrue(session.bind(book("a", stored["a"])))
        assertEquals(28, session.config!!.textSize)
    }

    @Test fun reopeningSameBookKeepsUnsavedEditsOnFreshBookObject() {
        session.bind(book("a"))
        session.use(ReadBookConfig.Config(textSize = 25))
        session.config!!.textSize = 29
        val fresh = book("a")
        assertFalse(session.bind(fresh))
        assertEquals(29, session.config!!.textSize)
        assertEquals(29, BookReadStyleSession.decode(fresh.config.independentReadStyle)!!.textSize)
    }

    @Test fun readingBoundBookForExportUsesUnsavedMemoryAndRespectsFollowGlobal() {
        val owner = book("a")
        session.bind(owner)
        session.use(ReadBookConfig.Config(textSize = 25))
        val stale = book("a", owner.config.independentReadStyle)
        val before = stored.toMap()
        session.config!!.textSize = 33

        assertSame(session.config, session.configFor(stale))
        assertEquals(33, session.configFor(stale)!!.textSize)
        assertEquals(before, stored)
        assertEquals(25, BookReadStyleSession.decode(stale.config.independentReadStyle)!!.textSize)

        session.followGlobal()
        val afterFollowGlobal = stored.toMap()
        assertNull(session.configFor(stale))
        assertEquals(afterFollowGlobal, stored)
        assertEquals(25, BookReadStyleSession.decode(stale.config.independentReadStyle)!!.textSize)
    }

    @Test fun readingOtherBookForExportDoesNotBindPersistOrCreateConfig() {
        session.bind(book("a"))
        session.use(ReadBookConfig.Config(textSize = 25))
        val active = session.config
        val other = book("b", GSON.toJson(ReadBookConfig.Config(textSize = 31)))
        val before = stored.toMap()
        val exported = session.configFor(other)!!
        assertEquals(31, exported.textSize)
        exported.textSize = 40
        assertSame(active, session.config)
        assertEquals(25, session.config!!.textSize)
        assertEquals(before, stored)
        assertEquals(31, BookReadStyleSession.decode(other.config.independentReadStyle)!!.textSize)

        val noConfig = Book(bookUrl = "c")
        assertNull(noConfig.readConfig)
        assertNull(session.configFor(noConfig))
        assertNull(noConfig.readConfig)
        assertSame(active, session.config)
    }

    @Test fun processRestartRestoresConfigFromBookJson() {
        val owner = book("a")
        session.bind(owner)
        session.use(ReadBookConfig.Config(textSize = 32, textFont = "book-font.ttf", bgType = 0, bgStr = "#112233"))
        val serializedBookConfig = GSON.toJson(owner.config)
        val restored = GSON.fromJson(serializedBookConfig, Book.ReadConfig::class.java)
        val restarted = BookReadStyleSession { _, _ -> fail("Reading must not overwrite stored config") }
        restarted.bind(book("a", restored.independentReadStyle))
        assertEquals(32, restarted.config!!.textSize)
        assertEquals("book-font.ttf", restarted.config!!.textFont)
        assertEquals("#112233", restarted.config!!.bgStr)
    }

    @Test fun followGlobalRemovesOnlyCurrentBookOverride() {
        session.bind(book("a"))
        session.use(ReadBookConfig.Config(textSize = 22))
        val savedA = stored["a"]
        val second = book("b")
        session.bind(second)
        session.use(ReadBookConfig.Config(textSize = 30))
        session.followGlobal()
        assertNull(session.config)
        assertNull(second.config.independentReadStyle)
        assertNull(stored["b"])
        assertEquals(30, BookReadStyleSession.decode(saved["b"])!!.textSize)
        assertEquals(savedA, stored["a"])
    }

    @Test fun disabledBookCopySurvivesRestartAndIsRestoredOnlyForItsOwnBook() {
        val owner = book("a")
        session.bind(owner)
        session.use(ReadBookConfig.Config(name = "本书", textSize = 32, textFont = "book.ttf"))
        session.config!!.textSize = 37
        session.followGlobal()
        assertNull(session.config)
        assertNull(owner.config.independentReadStyle)
        assertEquals(37, BookReadStyleSession.decode(owner.config.savedIndependentReadStyle)!!.textSize)

        val restored = GSON.fromJson(GSON.toJson(owner.config), Book.ReadConfig::class.java)
        val restartedBook = Book(bookUrl = "a").apply { readConfig = restored }
        val other = book("b")
        val restarted = BookReadStyleSession { _, _ -> }
        restarted.bind(other)
        assertFalse(restarted.restoreSaved())
        restarted.bind(restartedBook)
        assertNull(restarted.config)
        assertTrue(restarted.restoreSaved())
        assertEquals(37, restarted.config!!.textSize)
        assertEquals("book.ttf", restarted.config!!.textFont)
        assertNull(restartedBook.config.savedIndependentReadStyle)
        assertNotNull(restartedBook.config.independentReadStyle)
        assertNull(other.config.savedIndependentReadStyle)
    }

    @Test fun refreshingTheSameBookKeepsItsDisabledCopy() {
        session.bind(book("a"))
        session.use(ReadBookConfig.Config(textSize = 24))
        session.config!!.textSize = 35
        session.followGlobal()
        val fresh = book("a")
        assertFalse(session.bind(fresh))
        assertEquals(35, BookReadStyleSession.decode(fresh.config.savedIndependentReadStyle)!!.textSize)
        assertTrue(session.restoreSaved())
        assertEquals(35, session.config!!.textSize)
    }

    @Test fun templateRenameOrRemovalCannotChangeDetachedBookStyle() {
        val template = ReadBookConfig.Config(name = "原模板", textSize = 22)
        val templates = mutableListOf(template)
        session.bind(book("a"))
        session.use(template)
        template.name = "改名"
        template.textSize = 50
        templates.clear()
        assertEquals("原模板", session.config!!.name)
        assertEquals(22, session.config!!.textSize)
    }

    @Test fun choosingAnotherTemplateReplacesOnlyIndependentStyle() {
        val first = ReadBookConfig.Config(name = "A", textSize = 20)
        val second = ReadBookConfig.Config(name = "B", textSize = 30)
        session.bind(book("a"))
        session.use(first)
        session.use(second)
        session.config!!.textSize = 40
        assertEquals(20, first.textSize)
        assertEquals(30, second.textSize)
    }

    @Test fun globalHighlightRulesAreNotCopiedIntoBookBusinessState() {
        val source = ReadBookConfig.Config(ngUnknownFields = mutableMapOf("custom" to "value"))
        val copy = source.copyForBook()
        assertNotSame(source.highlightRules, copy.highlightRules)
        assertNotSame(source.ngUnknownFields, copy.ngUnknownFields)
        assertTrue(copy.highlightRules.isEmpty())
    }

    @Test fun oldBookWithoutFieldStaysGlobalAndLanguageExperimentIsNotMigrated() {
        val old = GSON.fromJson("""{"reverseToc":true,"scriptClass":"latin","readStyleName":"English"}""", Book.ReadConfig::class.java)
        assertNull(old.independentReadStyle)
        assertNull(old.savedIndependentReadStyle)
        assertTrue(old.reverseToc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun corruptStoredNullIsNotSilentlyConvertedToGlobal() {
        session.bind(book("a", "null"))
    }
}
