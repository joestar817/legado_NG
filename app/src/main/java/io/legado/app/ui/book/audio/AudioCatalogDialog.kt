package io.legado.app.ui.book.audio

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.model.AudioPlay
import io.legado.app.ui.book.listen.ListeningCoverTheme
import io.legado.app.ui.book.read.CatalogDrawerDialog
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** 有声书播放器的选集宿主；播放状态和跳转继续由 AudioPlay 独立管理。 */
internal class AudioCatalogDialog : CatalogDrawerDialog() {

    override fun catalogBook(): Book? = AudioPlay.book

    override fun currentChapterIndex(): Int = AudioPlay.durChapterIndex

    override fun initialCatalogTheme(book: Book): NgThemeSnapshot? =
        ListeningCoverTheme.cached(book, AudioPlay.bookSource?.bookSourceUrl)
            ?: ListeningCoverTheme.fallback(requireContext())

    override suspend fun resolveCatalogTheme(book: Book): NgThemeSnapshot =
        ListeningCoverTheme.resolve(
            context = requireContext(),
            book = book,
            sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
        )

    override fun onChapterSelected(chapter: BookChapter) {
        AudioPlay.skipTo(chapter.index)
        dismissAllowingStateLoss()
    }
}
