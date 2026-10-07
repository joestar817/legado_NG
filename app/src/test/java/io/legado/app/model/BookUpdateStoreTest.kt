package io.legado.app.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookUpdate
import io.legado.app.data.entities.bookUpdateChapterCount
import io.legado.app.data.entities.mergeBookUpdateIfNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BookUpdateStoreTest {
    private val baseline = BookUpdateSnapshot("https://site/book/1", "https://site", 100)

    @Test fun `only an existing same source shelf baseline can be captured`() {
        assertEquals(baseline, bookUpdateBaseline(baseline, baseline, baseline.origin))
        assertNull(bookUpdateBaseline(baseline, null, baseline.origin))
        assertNull(bookUpdateBaseline(baseline.copy(totalChapterNum = 0), baseline, baseline.origin))
        assertNull(bookUpdateBaseline(baseline, baseline.copy(totalChapterNum = 0), baseline.origin))
        assertNull(bookUpdateBaseline(baseline.copy(totalChapterNum = 99), baseline, baseline.origin))
        assertNull(bookUpdateBaseline(baseline, baseline, "https://another-source"))
        assertNull(bookUpdateBaseline(baseline, baseline.copy(origin = "https://another-source"), baseline.origin))
        assertNull(bookUpdateBaseline(baseline, baseline.copy(bookUrl = "https://site/book/2"), baseline.origin))
    }

    @Test fun `local and temporary books do not create discovery baselines`() {
        listOf(baseline.copy(isLocal = true), baseline.copy(isNotShelf = true),
            baseline.copy(bookUrl = ""), baseline.copy(origin = "")).forEach { excluded ->
            assertNull(bookUpdateBaseline(excluded, excluded, excluded.origin))
            assertNull(bookUpdateBaseline(baseline, excluded, baseline.origin))
        }
    }

    @Test fun `only directory growth remains a discovery after the current shelf recheck`() {
        assertTrue(isBookUpdateDiscovery(baseline, baseline, baseline.copy(totalChapterNum = 102)))
        assertFalse(isBookUpdateDiscovery(baseline, baseline, baseline))
        assertFalse(isBookUpdateDiscovery(baseline, baseline, baseline.copy(totalChapterNum = 99)))
        assertFalse(isBookUpdateDiscovery(baseline, null, baseline.copy(totalChapterNum = 102)))
        assertFalse(isBookUpdateDiscovery(baseline, baseline.copy(totalChapterNum = 99),
            baseline.copy(totalChapterNum = 102)))
    }

    @Test fun `source switches URL replacements and removal during a request reject the result`() {
        val growth = baseline.copy(totalChapterNum = 102)
        listOf(baseline.copy(origin = "https://changed"),
            baseline.copy(bookUrl = "https://site/book/2"), baseline.copy(isLocal = true),
            baseline.copy(isNotShelf = true)).forEach { changed ->
            assertFalse(isBookUpdateDiscovery(baseline, changed, growth))
            assertFalse(isBookUpdateDiscovery(baseline, baseline, changed.copy(totalChapterNum = 102)))
        }
    }

    @Test fun `a delayed response cannot rediscover or overwrite an already advanced directory`() {
        val current = baseline.copy(totalChapterNum = 108)
        assertFalse(isBookUpdateDiscovery(baseline, current, baseline.copy(totalChapterNum = 105)))
        assertFalse(isBookUpdateDiscovery(baseline, current, current))
        assertTrue(isBookUpdateDiscovery(baseline, current, baseline.copy(totalChapterNum = 110)))
    }

    @Test fun `discovery day uses the local date at its captured instant`() {
        val foundAt = Instant.parse("2026-10-05T16:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(),
            bookUpdateEpochDay(foundAt, ZoneId.of("Asia/Shanghai")))
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(),
            bookUpdateEpochDay(foundAt, ZoneId.of("UTC")))
    }

    @Test fun `same day growth preserves the first baseline instead of adding response deltas`() {
        val first = BookUpdate(100, baseline.bookUrl, baseline.origin, "第103章", 1000, 103, 100)
        assertEquals(first, mergeBookUpdateIfNewer(null, first))
        assertEquals(3, bookUpdateChapterCount(first))
        val incoming = first.copy(latestChapterTitle = "第108章", foundAt = 2000,
            totalChapterNum = 108, baselineChapterNum = 103)
        val merged = mergeBookUpdateIfNewer(first, incoming)!!
        assertEquals(incoming.copy(baselineChapterNum = 100), merged)
        assertEquals(8, bookUpdateChapterCount(merged))
        assertEquals(10, bookUpdateChapterCount(mergeBookUpdateIfNewer(merged,
            incoming.copy(totalChapterNum = 110, baselineChapterNum = 108))!!))
    }

    @Test fun `duplicate and delayed responses cannot double count or replace the latest metadata`() {
        val latest = BookUpdate(100, baseline.bookUrl, baseline.origin, "第108章", 1000, 108, 100)
        assertNull(mergeBookUpdateIfNewer(latest,
            latest.copy(latestChapterTitle = "慢响应", foundAt = 2000, totalChapterNum = 103)))
        assertNull(mergeBookUpdateIfNewer(latest,
            latest.copy(latestChapterTitle = "重复响应", foundAt = 3000, baselineChapterNum = 108)))
        assertEquals(8, bookUpdateChapterCount(latest))
        listOf(latest.copy(epochDay = 101), latest.copy(bookUrl = "another-book"),
            latest.copy(origin = "another-source")).forEach {
            assertNull(mergeBookUpdateIfNewer(latest, it.copy(totalChapterNum = 110)))
        }
    }

    @Test fun `a migrated unknown day stays unknown after growth and the next day can start fresh`() {
        val unknown = BookUpdate(100, baseline.bookUrl, baseline.origin, "第105章", 1000, 105)
        val later = unknown.copy(latestChapterTitle = "第108章", foundAt = 2000,
            totalChapterNum = 108, baselineChapterNum = 105)
        val merged = mergeBookUpdateIfNewer(unknown, later)!!
        assertNull(merged.baselineChapterNum)
        assertNull(bookUpdateChapterCount(merged))
        val nextDay = later.copy(epochDay = 101, totalChapterNum = 110, baselineChapterNum = 108)
        assertEquals(2, bookUpdateChapterCount(mergeBookUpdateIfNewer(null, nextDay)!!))
    }

    @Test fun `previous discoveries prevent counting yesterday again before Book catches up`() {
        val floor = bookUpdateDiscoveryFloor(currentTotal = 100, recordedTotal = 105)
        assertFalse(105 > floor)
        val today = BookUpdate(101, baseline.bookUrl, baseline.origin, "第108章", 2000, 108, floor)
        assertEquals(3, bookUpdateChapterCount(today))
        // A known rollback cannot lower the floor, and a newer persisted directory wins.
        assertEquals(105, bookUpdateDiscoveryFloor(currentTotal = 95, recordedTotal = 105))
        assertEquals(108, bookUpdateDiscoveryFloor(currentTotal = 108, recordedTotal = 105))
        assertEquals(100, bookUpdateDiscoveryFloor(currentTotal = 100, recordedTotal = null))
    }

    @Test fun `only a valid positive count is exposed and known baselines are not lost`() {
        val record = BookUpdate(100, baseline.bookUrl, baseline.origin, "第105章", 1000, 105, 100)
        listOf<Int?>(null, 0, -1, 105, 106, Int.MIN_VALUE).forEach {
            assertNull(bookUpdateChapterCount(record.copy(baselineChapterNum = it)))
        }
        assertEquals(Int.MAX_VALUE - 1,
            bookUpdateChapterCount(record.copy(totalChapterNum = Int.MAX_VALUE, baselineChapterNum = 1)))
        val merged = mergeBookUpdateIfNewer(record,
            record.copy(totalChapterNum = 108, baselineChapterNum = null))!!
        assertEquals(100, merged.baselineChapterNum)
        assertEquals(8, bookUpdateChapterCount(merged))
    }

    @Test fun `stored discovery fields have explicit stable JSON names`() {
        val record = BookUpdate(10, baseline.bookUrl, baseline.origin, "最新一章", 123L, 102, 100)
        val gson = Gson()
        val json = gson.toJson(record)
        assertEquals(setOf("epochDay", "bookUrl", "origin", "latestChapterTitle", "foundAt", "totalChapterNum", "baselineChapterNum"),
            JsonParser.parseString(json).asJsonObject.keySet())
        assertEquals(record, gson.fromJson(json, BookUpdate::class.java))
    }

    @Test fun `older serialized discoveries without the new field remain unknown`() {
        val record = BookUpdate(10, baseline.bookUrl, baseline.origin, "最新一章", 123L, 102, 100)
        val gson = Gson()
        val oldJson = gson.toJsonTree(record).asJsonObject.apply { remove("baselineChapterNum") }
        val restored = gson.fromJson(oldJson, BookUpdate::class.java)
        assertEquals(record.copy(baselineChapterNum = null), restored)
        assertNull(bookUpdateChapterCount(restored))
    }
}
