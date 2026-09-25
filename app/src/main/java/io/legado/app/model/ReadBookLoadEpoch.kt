package io.legado.app.model

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource

/** A cancellation fence for blocking IO that can finish after reader teardown. */
internal class ReadBookLoadEpoch {
    @Volatile
    var current: Long = 0
        private set

    fun invalidate() {
        current++
    }

    fun isCurrent(generation: Long): Boolean = generation == current
}

/** Immutable values captured before the shared save executor can be delayed. */
internal data class ReadBookProgressSnapshot(
    val book: Book,
    val source: BookSource?,
    val chapterIndex: Int,
    val chapterPosition: Int,
    val time: Long,
) {
    fun apply(): Boolean {
        val chapterChanged = book.durChapterIndex != chapterIndex
        book.lastCheckCount = 0
        book.durChapterTime = time
        book.durChapterIndex = chapterIndex
        book.durChapterPos = chapterPosition
        return chapterChanged
    }
}
