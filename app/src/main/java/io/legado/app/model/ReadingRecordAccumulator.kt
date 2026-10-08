package io.legado.app.model

import java.time.Instant
import java.time.ZoneId
import java.util.IdentityHashMap

internal enum class ReadingRecordKind { TEXT, MANGA, AUDIO }

internal enum class ReadingRecordSource(val kind: ReadingRecordKind) {
    PAGE(ReadingRecordKind.TEXT),
    MANGA_PAGE(ReadingRecordKind.MANGA),
    AUDIO(ReadingRecordKind.AUDIO),
    READ_ALOUD(ReadingRecordKind.TEXT);

    val isPage: Boolean get() = this == PAGE || this == MANGA_PAGE
}

internal data class ReadingRecordTime(
    val elapsedMillis: Long,
    val wallMillis: Long,
    val zone: ZoneId,
)

internal data class ReadingRecordTotal(val readTime: Long, val lastRead: Long)

internal data class ReadingRecordBatch(
    val books: Map<String, ReadingRecordTotal>,
    val days: Map<Long, Long>,
) {
    fun isEmpty(): Boolean = books.isEmpty() && days.isEmpty()
}

/**
 * The old reading-record checkpoints and lifecycle boundaries share one elapsed interval.
 * Pages and read-aloud for the same text book count their union. Different reader models
 * retain their own records, and the calendar receives exactly the same total increments.
 * Callers serialize access; this class never schedules checkpoints or performs I/O.
 */
internal class ReadingRecordAccumulator(private var enabled: Boolean) {
    private data class BookBinding(val key: String, val name: String)
    private data class PageState(var bookKey: String?, var active: Boolean = true)
    private data class ServiceState(
        val owner: Any,
        var active: Boolean = false,
        var bookKey: String? = null,
    )

    private class KindState {
        var book: BookBinding? = null
        var previous: ReadingRecordTime? = null
        val pages = IdentityHashMap<Any, PageState>()
        val services = mutableMapOf<ReadingRecordSource, ServiceState>()

        fun isActive(bookKey: String): Boolean =
            pages.values.any { it.active && it.bookKey == bookKey } ||
                    services.values.any { it.active && it.bookKey == bookKey }
    }

    private val kinds = ReadingRecordKind.entries.associateWith { KindState() }
    private val pendingBooks = linkedMapOf<String, ReadingRecordTotal>()
    private val pendingDays = linkedMapOf<Long, Long>()

    val hasPending: Boolean get() = pendingBooks.isNotEmpty() || pendingDays.isNotEmpty()

    fun bindBook(kind: ReadingRecordKind, key: String, name: String, now: ReadingRecordTime) {
        val state = kinds.getValue(kind)
        checkpoint(kind, now)
        val oldKey = state.book?.key
        state.book = BookBinding(key, name)
        state.pages.values.forEach { page ->
            // A resumed page may precede asynchronous book initialization. Explicit
            // targets for another book must not accrue against the old global book.
            if (page.bookKey == null || page.bookKey == oldKey) page.bookKey = key
        }
        if (oldKey != key) {
            state.services.values.forEach { service ->
                service.active = false
                service.bookKey = null
            }
        }
    }

    fun pageResumed(
        source: ReadingRecordSource,
        owner: Any,
        bookKey: String?,
        now: ReadingRecordTime,
    ) {
        if (!source.isPage) return
        val state = kinds.getValue(source.kind)
        checkpoint(source.kind, now)
        val page = state.pages[owner]
        if (page == null) {
            state.pages[owner] = PageState(bookKey)
        } else {
            // A source change may have replaced the Activity's original intent key.
            // Keep the latest binding until this owner is destroyed.
            page.active = true
        }
    }

    fun register(source: ReadingRecordSource, owner: Any, now: ReadingRecordTime) {
        if (source.isPage) return
        val state = kinds.getValue(source.kind)
        checkpoint(source.kind, now)
        if (state.services[source]?.owner !== owner) {
            state.services[source] = ServiceState(owner)
        }
    }

    fun setActive(
        source: ReadingRecordSource,
        owner: Any,
        active: Boolean,
        now: ReadingRecordTime,
    ) {
        val state = kinds.getValue(source.kind)
        if (source.isPage) {
            val page = state.pages[owner] ?: return
            checkpoint(source.kind, now)
            page.active = active
        } else {
            val service = state.services[source]?.takeIf { it.owner === owner } ?: return
            checkpoint(source.kind, now)
            service.active = active
            service.bookKey = if (active) state.book?.key else null
        }
    }

    /** A surviving service must confirm the book actually playing after a model switch. */
    fun setPlaybackActive(
        source: ReadingRecordSource,
        owner: Any,
        active: Boolean,
        bookKey: String?,
        now: ReadingRecordTime,
    ) {
        if (source.isPage) return
        if (active && (bookKey == null || bookKey != kinds.getValue(source.kind).book?.key)) {
            return
        }
        setActive(source, owner, active, now)
    }

    fun remove(source: ReadingRecordSource, owner: Any, now: ReadingRecordTime) {
        val state = kinds.getValue(source.kind)
        if (source.isPage) {
            if (!state.pages.containsKey(owner)) return
            checkpoint(source.kind, now)
            state.pages.remove(owner)
        } else {
            if (state.services[source]?.owner !== owner) return
            checkpoint(source.kind, now)
            state.services.remove(source)
        }
    }

    fun setEnabled(value: Boolean, now: ReadingRecordTime) {
        ReadingRecordKind.entries.forEach { checkpoint(it, now) }
        enabled = value
    }

    fun checkpoint(kind: ReadingRecordKind, now: ReadingRecordTime) {
        val state = kinds.getValue(kind)
        val start = state.previous
        state.previous = now
        val book = state.book ?: return
        if (start == null || !enabled || !state.isActive(book.key)) return
        val duration = now.elapsedMillis - start.elapsedMillis
        if (duration <= 0L) return
        val previousTotal = pendingBooks[book.name]
        pendingBooks[book.name] = ReadingRecordTotal(
            readTime = (previousTotal?.readTime ?: 0L) + duration,
            lastRead = maxOf(previousTotal?.lastRead ?: now.wallMillis, now.wallMillis),
        )
        addDays(start, duration)
    }

    private fun addDays(start: ReadingRecordTime, duration: Long) {
        // Elapsed time is authoritative. A wall-clock/zone change only re-anchors
        // the next interval, and local midnights include actual DST day lengths.
        var cursor = Instant.ofEpochMilli(start.wallMillis)
        var remaining = duration
        while (remaining > 0L) {
            val date = cursor.atZone(start.zone).toLocalDate()
            val nextDay = date.plusDays(1).atStartOfDay(start.zone).toInstant()
            val untilMidnight = nextDay.toEpochMilli() - cursor.toEpochMilli()
            val part = minOf(remaining, untilMidnight)
            if (part <= 0L) break
            val day = date.toEpochDay()
            pendingDays[day] = (pendingDays[day] ?: 0L) + part
            cursor = cursor.plusMillis(part)
            remaining -= part
        }
    }

    fun pendingSnapshot() = ReadingRecordBatch(pendingBooks.toMap(), pendingDays.toMap())

    /** Acknowledge both maps only after their shared database transaction succeeds. */
    fun acknowledge(written: ReadingRecordBatch) {
        written.books.forEach { (name, total) ->
            val pending = pendingBooks[name] ?: return@forEach
            val remaining = pending.readTime - total.readTime
            if (remaining > 0L) {
                pendingBooks[name] = pending.copy(readTime = remaining)
            } else {
                pendingBooks.remove(name)
            }
        }
        written.days.forEach { (day, duration) ->
            val remaining = (pendingDays[day] ?: 0L) - duration
            if (remaining > 0L) pendingDays[day] = remaining else pendingDays.remove(day)
        }
    }
}
