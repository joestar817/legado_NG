package io.legado.app.ui.book.audio

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.ListeningMotionConfig
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.model.AudioPlay
import io.legado.app.model.CacheBook
import io.legado.app.ui.book.read.CatalogDrawerDialog
import io.legado.app.ui.book.read.aloud.ReadAloudPlayerTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** 有声书播放器的选集宿主；播放状态和跳转继续由 AudioPlay 独立管理。 */
internal class AudioCatalogDialog : CatalogDrawerDialog() {

    override fun catalogBook(): Book? = AudioPlay.book

    override fun currentChapterIndex(): Int = AudioPlay.durChapterIndex

    override fun showCacheState(): Boolean = true

    override fun showCacheAction(): Boolean = true

    override fun isCacheRunning(book: Book): Boolean {
        return CacheBook.cacheBookMap[book.bookUrl]?.isStop() == false
    }

    override fun onCacheAction(book: Book, currentlyRunning: Boolean): Boolean {
        if (currentlyRunning) {
            CacheBook.remove(requireContext(), book.bookUrl)
            return false
        }
        if (book.lastChapterIndex < 0) return false
        CacheBook.start(requireContext(), book, 0, book.lastChapterIndex)
        return true
    }

    override fun cachedChapterFileNames(book: Book): Set<String> {
        val bookSource = AudioPlay.bookSource ?: return emptySet()
        return AudioDownloadCache.getCachedChapterFileNames(bookSource, book)
    }

    override fun initialCatalogTheme(book: Book): NgThemeSnapshot? =
        ReadAloudPlayerTheme.initialDrawerSnapshot(
            context = requireContext(),
            book = book,
            sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
            settings = ListeningMotionConfig.current(),
        )

    override suspend fun resolveCatalogTheme(book: Book): NgThemeSnapshot =
        ReadAloudPlayerTheme.resolveDrawerSnapshot(
            context = requireContext(),
            book = book,
            sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
            settings = ListeningMotionConfig.current(),
        )

    override fun onChapterSelected(chapter: BookChapter) {
        AudioPlay.skipTo(chapter.index)
        dismissAllowingStateLoss()
    }
}
