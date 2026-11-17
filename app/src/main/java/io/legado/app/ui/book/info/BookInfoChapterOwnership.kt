package io.legado.app.ui.book.info

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.applyCustomBookType
import io.legado.app.utils.GSON
import kotlinx.coroutines.sync.Mutex

internal data class BookInfoSnapshot(
    val book: Book,
    val chapters: List<BookChapter>? = null,
    val initialBook: Book = book.copy(readConfig = book.readConfig?.copy()),
)

/** Source rules mutate their inputs, including fields outside Book's primary constructor. */
internal fun Book.copyForBookInfo(): Book = copy(readConfig = readConfig?.copy()).also {
    it.infoHtml = infoHtml
    it.tocHtml = tocHtml
    it.downloadUrls = downloadUrls?.toList()
}

/** Reapply edits made to the same book while the source was loading or a save was waiting. */
internal fun Book.keepBookInfoEdits(before: Book, edited: Book) {
    if (before.bookUrl != edited.bookUrl || before.origin != edited.origin) return
    if (before.customCoverUrl != edited.customCoverUrl) customCoverUrl = edited.customCoverUrl
    if (before.customIntro != edited.customIntro) customIntro = edited.customIntro
    if (before.customTag != edited.customTag) customTag = edited.customTag
    if (before.canUpdate != edited.canUpdate) canUpdate = edited.canUpdate
    if (before.group != edited.group) group = edited.group
    if (before.order != edited.order) order = edited.order
    if (before.durChapterTime != edited.durChapterTime) durChapterTime = edited.durChapterTime
    if (before.durChapterIndex != edited.durChapterIndex) {
        durChapterIndex = edited.durChapterIndex
        durChapterTitle = edited.durChapterTitle
    }
    if (before.durChapterPos != edited.durChapterPos) durChapterPos = edited.durChapterPos
    if (before.durVolumeIndex != edited.durVolumeIndex) durVolumeIndex = edited.durVolumeIndex
    if (before.chapterInVolumeIndex != edited.chapterInVolumeIndex) {
        chapterInVolumeIndex = edited.chapterInVolumeIndex
    }
    if (before.readConfig != edited.readConfig) {
        readConfig = edited.readConfig?.copy()
        applyCustomBookType()
    }
    if (before.variable != edited.variable) {
        val custom = edited.variableMap["custom"]
        if (before.variableMap["custom"] != custom) {
            if (custom == null) variableMap.remove("custom") else variableMap["custom"] = custom
            variable = GSON.toJson(variableMap)
        }
    }
}

internal fun chaptersForBook(bookUrl: String?, chapters: List<BookChapter>?): List<BookChapter>? {
    if (bookUrl == null || chapters == null) return null
    return chapters.takeIf { list -> list.all { it.bookUrl == bookUrl } }
}

internal class BookInfoLoadedChapters {
    private data class Snapshot(val bookUrl: String, val chapters: List<BookChapter>)
    private val publicationMutex = Mutex()

    @Volatile
    private var latest: Snapshot? = null

    fun record(bookUrl: String, chapters: List<BookChapter>) {
        latest = Snapshot(bookUrl, chapters)
    }

    fun clear() {
        latest = null
    }

    fun forBook(bookUrl: String, displayed: List<BookChapter>?): List<BookChapter>? {
        val loaded = latest?.takeIf { it.bookUrl == bookUrl }?.chapters
        return chaptersForBook(bookUrl, loaded) ?: chaptersForBook(bookUrl, displayed)
    }

    suspend fun <T> withPublication(block: suspend () -> T): T {
        publicationMutex.lock()
        try {
            return block()
        } finally {
            publicationMutex.unlock()
        }
    }
}
