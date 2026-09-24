package io.legado.app.ui.book.read.epub

import io.legado.app.data.entities.BookChapter
import io.legado.app.model.epub.EpubPaths
import io.legado.app.model.epub.EpubResourceLink

/** Match a publication-local link to an existing TOC chapter, without guessing between fragments. */
internal object EpubChapterLinks {
    fun find(chapters: List<BookChapter>, link: EpubResourceLink): BookChapter? {
        val matches = chapters.filter { chapter ->
            runCatching { EpubPaths.resolve("mimetype", chapter.url).path == link.path }.getOrDefault(false)
        }
        if (matches.isEmpty()) return null
        val fragment = link.fragment?.takeIf { it.isNotEmpty() }
        if (fragment == null) return matches.firstOrNull { it.startFragmentId.isNullOrEmpty() } ?: matches.first()
        return matches.firstOrNull { it.startFragmentId == fragment ||
            runCatching { EpubPaths.resolve("mimetype", it.url).fragment == fragment }.getOrDefault(false) }
            ?: matches.singleOrNull()
    }
}
