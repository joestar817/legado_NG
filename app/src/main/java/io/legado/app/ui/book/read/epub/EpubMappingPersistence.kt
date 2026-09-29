package io.legado.app.ui.book.read.epub

/** Orders cache publication across reader instances; serialization never holds this lock. */
internal object EpubMappingPersistence {
    private var revision = 0L
    @Synchronized fun next(): Long = ++revision
    @Synchronized fun publish(token: Long, commit: () -> Unit) { if (token == revision) commit() }
}
