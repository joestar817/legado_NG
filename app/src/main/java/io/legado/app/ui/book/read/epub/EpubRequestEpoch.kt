package io.legado.app.ui.book.read.epub

import java.util.concurrent.atomic.AtomicLong

/** Invalidates work already queued on another thread as well as its eventual delivery. */
internal class EpubRequestEpoch {
    private val value = AtomicLong()
    fun next(): Long = value.incrementAndGet()
    fun isCurrent(token: Long): Boolean = value.get() == token
}
