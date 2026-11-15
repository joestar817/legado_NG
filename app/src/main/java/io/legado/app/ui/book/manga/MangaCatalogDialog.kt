package io.legado.app.ui.book.manga

import androidx.lifecycle.ViewModelProvider
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.isLocal
import io.legado.app.model.ReadManga
import io.legado.app.ui.book.read.CatalogDrawerDialog
import io.legado.app.ui.book.read.CatalogDrawerVisualStyle
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.design.theme.resolveNgMangaPanelTheme

/** 只适配漫画宿主，目录分页与显示状态继续复用阅读目录。 */
internal open class MangaCatalogDialog : CatalogDrawerDialog() {
    private val mangaViewModel: ReadMangaViewModel
        get() = ViewModelProvider(requireActivity())[ReadMangaViewModel::class.java]

    override fun catalogBook(): Book? = ReadManga.book

    override fun currentChapterIndex(): Int = ReadManga.durChapterIndex

    override fun maxChapterIndexExclusive(): Int = ReadManga.simulatedChapterSize

    override fun showTextTools(): Boolean = false

    override fun useDirectChapterControls(): Boolean = true

    override fun showCacheState(): Boolean = true

    override fun isLocalBook(): Boolean = ReadManga.book?.isLocal == true

    override fun useMangaPanelTheme(): Boolean = true

    override fun visualStyle(): CatalogDrawerVisualStyle = CatalogDrawerVisualStyle.READING_ORIGINAL

    override fun catalogWindowTheme(): NgThemeSnapshot =
        resolveNgMangaPanelTheme(NgThemeResolver.resolve(requireContext()))

    override fun initialCatalogTheme(book: Book): NgThemeSnapshot = catalogWindowTheme()

    override fun refreshCatalog(book: Book, complete: (Boolean) -> Unit) {
        mangaViewModel.refreshChapterList(book, complete)
    }

    override fun onChapterSelected(chapter: BookChapter) {
        mangaViewModel.openChapter(chapter.index)
        dismissAllowingStateLoss()
    }
}

internal class MangaCompactCatalogDialog : MangaCatalogDialog() {
    override fun visualStyle(): CatalogDrawerVisualStyle = CatalogDrawerVisualStyle.READING_COMPACT_SIDE
}

internal class MangaDenseCatalogDialog : MangaCatalogDialog() {
    override fun visualStyle(): CatalogDrawerVisualStyle = CatalogDrawerVisualStyle.READING_DENSE_LIST
}
