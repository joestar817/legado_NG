package io.legado.app.ui.book.read.epub

import io.legado.app.data.entities.Book
import io.legado.app.help.book.ContentPositionMap
import io.legado.app.model.epub.EpubMappedContent
import io.legado.app.ui.book.read.page.entities.TextChapter
import java.lang.ref.WeakReference

/** One bounded, pure mapping; never retains a publication, renderer, Activity or native chapter. */
internal object EpubOpeningMappingCache {
    private data class Key(
        val bookUrl: String, val revision: String, val chapterUrl: String, val index: Int,
        val nextUrl: String, val start: String?, val end: String?, val removeHeadings: Boolean, val removeRuby: Boolean,
        val source: String, val cachedText: String, val cachedSteps: List<ContentPositionMap.Step>,
        val preparedText: String, val preparedStepCount: Int, val removedTitle: ContentPositionMap.Range?,
    )
    private data class Entry(
        val key: Key, val chapter: WeakReference<TextChapter>, val prepared: WeakReference<ContentPositionMap>,
        val mapped: EpubMappedContent,
    )
    private var entry: Entry? = null

    /** Speculative hidden load only; Surface must still compare the final document's exact HTML. */
    @Synchronized
    fun openingDocument(book: Book, chapter: TextChapter, revision: String?) = entry?.takeIf {
        revision != null && it.key.revision == revision && it.key.bookUrl == book.bookUrl &&
            it.chapter.get() === chapter && it.prepared.get() === chapter.contentPositionMap && chapter.isCompleted
    }?.mapped?.documents?.singleOrNull()?.source

    fun find(book: Book, chapter: TextChapter, revision: String?,
             restore: (String) -> ContentPositionMap?): EpubMappedContent? {
        val value = synchronized(this) { entry } ?: return null
        if (revision == null || value.key.revision != revision || value.key.bookUrl != book.bookUrl ||
            value.chapter.get() !== chapter || value.prepared.get() !== chapter.contentPositionMap || !chapter.isCompleted) return null
        // Revalidate the current editor provenance without reopening unchanged XHTML. The complete
        // archive digest has already been checked by the caller's new snapshot.
        val cached = restore(value.key.source) ?: return null
        return get(book, chapter, revision, cached)
    }

    private fun key(book: Book, chapter: TextChapter, revision: String, cached: ContentPositionMap): Key {
        val prepared = checkNotNull(chapter.contentPositionMap)
        return Key(book.bookUrl, revision, chapter.chapter.url, chapter.chapter.index,
            chapter.chapter.getVariable("nextUrl"), chapter.chapter.startFragmentId, chapter.chapter.endFragmentId,
            book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag), cached.source, cached.text, cached.steps.toList(),
            prepared.text, prepared.steps.size, prepared.removedTitle)
    }

    @Synchronized
    fun get(book: Book, chapter: TextChapter, revision: String?, cached: ContentPositionMap): EpubMappedContent? {
        val value = entry ?: return null
        if (revision == null || !chapter.isCompleted || value.chapter.get() !== chapter ||
            value.prepared.get() !== chapter.contentPositionMap || value.key != key(book, chapter, revision, cached)) return null
        return value.mapped
    }

    @Synchronized
    fun put(book: Book, chapter: TextChapter, revision: String?, cached: ContentPositionMap, mapped: EpubMappedContent) {
        if (revision == null) return
        entry = null
        // Edited cache provenance can contain nested mutable recorders. Recompute that uncommon
        // path rather than retaining an unbounded transformation graph across openings.
        if (!chapter.isCompleted || cached.steps.isNotEmpty() || mapped.documents.size > 8) return
        var bytes = 2L * (mapped.text.length + cached.source.length + cached.text.length)
        for (document in mapped.documents) {
            if (document.nodes.size > 10_000 || document.media.size > 1_024) return
            bytes += document.source.html.length * 2L
            bytes += document.source.nodes.sumOf { 128L + it.element.length * 2L + it.text.length * 2L }
            bytes += document.source.excludedElements.sumOf { 40L + it.length * 2L }
            bytes += document.source.media.sumOf { 80L + (it.element.length + it.source.length) * 2L }
            bytes += document.nodes.sumOf { 160L + (it.original.length + it.text.length + it.element.length) * 2L +
                (it.starts.size + it.ends.size) * 4L }
            bytes += document.media.sumOf { 80L + it.element.length * 2L }
            if (bytes > 2L * 1024 * 1024) return
        }
        if (bytes <= 2L * 1024 * 1024) entry = Entry(key(book, chapter, revision, cached),
            WeakReference(chapter), WeakReference(checkNotNull(chapter.contentPositionMap)), mapped)
    }
}
