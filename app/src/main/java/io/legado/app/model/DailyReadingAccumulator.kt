package io.legado.app.model

import java.time.Instant
import java.time.ZoneId
import java.util.IdentityHashMap

internal enum class DailyReadingSource { PAGE, AUDIO, READ_ALOUD }

internal data class DailyReadingTime(
    val elapsedMillis: Long,
    val wallMillis: Long,
    val zone: ZoneId,
)

/** Counts the union of live reading sources. Callers serialize access. */
internal class DailyReadingAccumulator(private var enabled: Boolean) {
    private data class SourceState(val owner: Any, var active: Boolean)

    private val sources = mutableMapOf<DailyReadingSource, SourceState>()
    private val pages = IdentityHashMap<Any, Boolean>()
    private val pending = linkedMapOf<Long, Long>()
    private var previous: DailyReadingTime? = null

    val hasActiveSources: Boolean get() = pages.values.any { it } || sources.values.any { it.active }
    val hasPending: Boolean get() = pending.isNotEmpty()

    fun register(source: DailyReadingSource, owner: Any, now: DailyReadingTime) {
        checkpoint(now)
        if (source == DailyReadingSource.PAGE) {
            if (!pages.containsKey(owner)) pages[owner] = false
        } else if (sources[source]?.owner !== owner) {
            sources[source] = SourceState(owner, false)
        }
    }

    fun setActive(source: DailyReadingSource, owner: Any, active: Boolean, now: DailyReadingTime) {
        checkpoint(now)
        if (source == DailyReadingSource.PAGE) {
            if (pages.containsKey(owner)) pages[owner] = active
        } else {
            sources[source]?.takeIf { it.owner === owner }?.active = active
        }
    }

    fun remove(source: DailyReadingSource, owner: Any, now: DailyReadingTime) {
        checkpoint(now)
        if (source == DailyReadingSource.PAGE) {
            pages.remove(owner)
        } else if (sources[source]?.owner === owner) {
            sources.remove(source)
        }
    }

    fun setEnabled(value: Boolean, now: DailyReadingTime) {
        checkpoint(now)
        enabled = value
    }

    fun checkpoint(now: DailyReadingTime) {
        val start = previous
        previous = now
        if (start == null || !enabled || !hasActiveSources) return
        val duration = now.elapsedMillis - start.elapsedMillis
        if (duration <= 0L) return
        // Wall-clock changes never change the elapsed duration. Attribute this bounded
        // sample using its original wall time/zone, then re-anchor to the new clock.
        var cursor = Instant.ofEpochMilli(start.wallMillis)
        var remaining = duration
        while (remaining > 0L) {
            val date = cursor.atZone(start.zone).toLocalDate()
            val nextDay = date.plusDays(1).atStartOfDay(start.zone).toInstant()
            val untilMidnight = nextDay.toEpochMilli() - cursor.toEpochMilli()
            val part = minOf(remaining, untilMidnight)
            if (part <= 0L) break
            val day = date.toEpochDay()
            pending[day] = (pending[day] ?: 0L) + part
            cursor = cursor.plusMillis(part)
            remaining -= part
        }
    }

    fun pendingSnapshot(): Map<Long, Long> = pending.toMap()

    /** Acknowledge only after the entire database transaction succeeds. */
    fun acknowledge(written: Map<Long, Long>) {
        written.forEach { (day, duration) ->
            val remaining = (pending[day] ?: 0L) - duration
            if (remaining > 0L) pending[day] = remaining else pending.remove(day)
        }
    }
}
