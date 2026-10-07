package io.legado.app.ui.main.home

import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.data.appDb
import io.legado.app.help.book.isAudio
import io.legado.app.model.AudioPlay
import io.legado.app.model.ListeningHistoryEntry
import io.legado.app.model.ListeningHistorySource
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.aloud.ListeningPlayback
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer

/** A transient UI snapshot; playback ownership stays in the existing models/services. */
internal data class HomeListeningState(
    val playback: ListeningPlayback? = null,
    val book: Book? = null,
    val bookUrl: String = "",
    val bookName: String = "",
    val author: String = "",
    val cover: String? = null,
    val chapterTitle: String = "",
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val history: ListeningHistoryEntry? = null,
)

internal enum class HomeListeningAction { OPEN, TOGGLE, PREVIOUS, NEXT }

/** Called off the UI thread; this does not initialize either playback model. */
internal fun readLastHomeListeningState(entry: ListeningHistoryEntry?): HomeListeningState {
    entry ?: return HomeListeningState()
    val book = appDb.bookDao.getBook(entry.bookUrl) ?: return HomeListeningState()
    if ((entry.source == ListeningHistorySource.AUDIO) != book.isAudio) return HomeListeningState()
    val chapter = appDb.bookChapterDao.getChapter(entry.bookUrl, entry.chapterIndex)
        ?: return HomeListeningState()
    return HomeListeningState(
        playback = when (entry.source) {
            ListeningHistorySource.READ_ALOUD -> ListeningPlayback.READ_ALOUD
            ListeningHistorySource.AUDIO -> ListeningPlayback.AUDIO
        },
        book = book,
        bookUrl = book.bookUrl,
        bookName = book.name,
        author = book.getRealAuthor(),
        cover = book.getDisplayCover(),
        chapterTitle = chapter.title,
        history = entry,
    )
}

internal fun matchesHomeListeningTarget(expected: HomeListeningState, current: HomeListeningState): Boolean =
    current.book != null && current.bookUrl == expected.bookUrl &&
        current.playback == expected.playback && current.history == expected.history

internal fun readHomeListeningState(): HomeListeningState {
    val playback = ReadAloudMiniPlayer.currentMainPlayback() ?: return HomeListeningState()
    val book = when (playback) {
        ListeningPlayback.READ_ALOUD -> ReadBook.book?.takeIf {
            it.bookUrl == BaseReadAloudService.activeBookUrl
        }
        ListeningPlayback.AUDIO -> AudioPlay.book
    } ?: return HomeListeningState()
    val chapterIndex = when (playback) {
        ListeningPlayback.READ_ALOUD -> ReadBook.durChapterIndex
        ListeningPlayback.AUDIO -> AudioPlay.durChapterIndex
    }
    val chapterCount = when (playback) {
        ListeningPlayback.READ_ALOUD -> ReadBook.simulatedChapterSize
        ListeningPlayback.AUDIO -> AudioPlay.simulatedChapterSize
    }
    val chapterTitle = when (playback) {
        ListeningPlayback.READ_ALOUD -> ReadBook.curTextChapter
            ?.takeIf { it.chapter.index == chapterIndex }?.title
        ListeningPlayback.AUDIO -> AudioPlay.durChapter
            ?.takeIf { it.index == chapterIndex }?.title
    }.orEmpty()
    return HomeListeningState(
        playback = playback,
        book = book,
        bookUrl = book.bookUrl,
        bookName = book.name,
        author = book.getRealAuthor(),
        cover = book.getDisplayCover(),
        chapterTitle = chapterTitle,
        playing = when (playback) {
            ListeningPlayback.READ_ALOUD -> BaseReadAloudService.isPlay()
            ListeningPlayback.AUDIO -> AudioPlay.status == Status.PLAY
        },
        preparing = when (playback) {
            ListeningPlayback.READ_ALOUD -> BaseReadAloudService.isPreparing()
            ListeningPlayback.AUDIO -> AudioPlay.loading.value
        },
        canPrevious = chapterIndex > 0,
        canNext = chapterIndex >= 0 && chapterIndex < chapterCount - 1,
    )
}
