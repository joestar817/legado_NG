package io.legado.app.ui.book.read.aloud

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.CatalogDrawerDialog
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** TTS 播放器的目录宿主；列表、搜索、排序和滚动全部复用共享目录内容。 */
internal class ReadAloudCatalogDialog : CatalogDrawerDialog() {

    override fun catalogBook(): Book? = ReadBook.book

    override fun currentChapterIndex(): Int = ReadBook.durChapterIndex

    override fun showCacheState(): Boolean = true

    override fun isLocalBook(): Boolean = ReadBook.isLocalBook

    override fun initialCatalogTheme(book: Book): NgThemeSnapshot? =
        ListeningCoverTheme.cached(book, ReadBook.bookSource?.bookSourceUrl)
            ?: ListeningCoverTheme.fallback(requireContext())

    override suspend fun resolveCatalogTheme(book: Book): NgThemeSnapshot =
        ListeningCoverTheme.resolve(
            context = requireContext(),
            book = book,
            sourceOrigin = ReadBook.bookSource?.bookSourceUrl,
        )

    override fun onChapterSelected(chapter: BookChapter) {
        (activity as? ReadAloudPlayerActivity)?.openChapterFromCatalog(chapter.index)
        dismissAllowingStateLoss()
    }
}
