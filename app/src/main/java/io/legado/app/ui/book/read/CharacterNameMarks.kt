package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookCharacter
import io.legado.app.ui.book.character.aliases
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.NativeHighlightPositionMap
import java.util.WeakHashMap

/** Positions are in the same UTF-16 display text used by the reader's range painters. */
internal class CharacterNameMarks private constructor(
    private val bookUrl: String,
    private val namesByPrefix: Map<Char, List<Name>>,
) {
    private data class Name(val spelling: String, val characterId: Long?)
    data class Mark(val start: Int, val endExclusive: Int, val characterId: Long)

    private val chapterCache = WeakHashMap<NativeHighlightPositionMap, List<Mark>>()

    val isEmpty get() = namesByPrefix.isEmpty()

    fun forBook(bookUrl: String): CharacterNameMarks = if (this.bookUrl == bookUrl) this else empty

    fun sameEntriesAs(other: CharacterNameMarks): Boolean {
        return this === other || bookUrl == other.bookUrl && namesByPrefix == other.namesByPrefix
    }

    fun ranges(chapter: TextChapter): List<Mark> {
        if (namesByPrefix.isEmpty() || chapter.chapter.bookUrl != bookUrl) return emptyList()
        val map = chapter.highlightPositionMap
        return synchronized(chapterCache) {
            chapterCache.getOrPut(map) { find(map.displayText) }
        }
    }

    fun characterAt(chapter: TextChapter, position: Int): Long? {
        val marks = ranges(chapter)
        return marks.getOrNull(firstEndingAfter(marks, position))
            ?.takeIf { it.start <= position }?.characterId
    }

    internal fun find(text: String): List<Mark> = buildList {
        if (namesByPrefix.isEmpty()) return@buildList
        var start = 0
        while (start < text.length) {
            val hit = namesByPrefix[text[start].lowercaseChar()]?.firstOrNull {
                matches(text, start, it.spelling)
            }
            if (hit == null) {
                start++
                continue
            }
            val end = start + hit.spelling.length
            // An ambiguous longer name also consumes its span. The prefix may be someone else.
            hit.characterId?.let { add(Mark(start, end, it)) }
            start = end
        }
    }

    private fun matches(text: String, start: Int, name: String): Boolean {
        val end = start + name.length
        if (end > text.length || !text.regionMatches(start, name, 0, name.length, ignoreCase = true)) {
            return false
        }
        return !(name.first().isLatinWordPart() && start > 0 && text[start - 1].isLatinWordPart()) &&
            !(name.last().isLatinWordPart() && end < text.length && text[end].isLatinWordPart())
    }

    private fun Char.isLatinWordPart(): Boolean {
        return this == '_' || isDigit() || this in 'a'..'z' || this in 'A'..'Z' ||
            code in 0x00c0..0x024f && isLetter()
    }

    companion object {
        val empty = CharacterNameMarks("", emptyMap())

        fun from(bookUrl: String, characters: List<BookCharacter>): CharacterNameMarks {
            val names = mutableMapOf<String, Long?>()
            val aliases = mutableMapOf<String, Long?>()
            fun add(map: MutableMap<String, Long?>, value: String, characterId: Long) {
                val trimmed = CharacterCardSelection.unwrappedName(value)
                if (trimmed.isEmpty() || trimmed.any { it == '\n' || it == '\r' }) return
                val spelling = buildString(trimmed.length) {
                    trimmed.forEach { append(it.lowercaseChar()) }
                }
                if (!map.containsKey(spelling)) {
                    map[spelling] = characterId
                } else if (map[spelling] != characterId) {
                    map[spelling] = null
                }
            }
            characters.forEach { character ->
                if (character.enabled && character.id > 0L) {
                    add(names, character.name, character.id)
                    character.aliases().forEach { add(aliases, it, character.id) }
                }
            }
            // A primary name wins over an identical alias regardless of the sort order.
            aliases.putAll(names)
            val byPrefix = aliases.map { (spelling, id) -> Name(spelling, id) }
                .sortedByDescending { it.spelling.length }
                .groupBy { it.spelling.first() }
            return if (byPrefix.isEmpty()) empty else CharacterNameMarks(bookUrl, byPrefix)
        }

        fun firstEndingAfter(marks: List<Mark>, position: Int): Int {
            val index = marks.binarySearchBy(position) { it.endExclusive }
            return if (index >= 0) index + 1 else -index - 1
        }
    }
}
