package io.legado.app.ui.book.changesource

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ChangeSourceFilterTest {
    private val manga = ChangeSourceFilter(BookSourceType.image, true, "")

    @Test
    fun bookMediaFlagsMapToDeclaredSourceTypes() {
        listOf(
            BookType.text to BookSourceType.default,
            BookType.image to BookSourceType.image,
            BookType.audio to BookSourceType.audio,
            BookType.video to BookSourceType.video,
            (BookType.text or BookType.webFile) to BookSourceType.file,
        ).forEach { (bookType, sourceType) ->
            assertEquals(sourceType, ChangeSourceFilter.sourceTypeForBook(bookType))
        }
    }

    @Test
    fun nonMediaStatusBitsDoNotChangeScope() {
        val status = BookType.local or BookType.updateError or BookType.archive or BookType.notShelf
        assertEquals(BookSourceType.image, ChangeSourceFilter.sourceTypeForBook(BookType.image or status))
        assertEquals(BookSourceType.default, ChangeSourceFilter.sourceTypeForBook(BookType.text or status))
    }

    @Test
    fun explicitBookTypeWinsOverSourceAndDownloadFlags() {
        val sourceType = BookType.text or BookType.webFile
        assertEquals(BookSourceType.audio, ChangeSourceFilter.sourceTypeForBook(sourceType, BookType.audio))
        assertEquals(BookSourceType.default, ChangeSourceFilter.sourceTypeForBook(sourceType, BookType.text))
        assertEquals(BookSourceType.image, ChangeSourceFilter.sourceTypeForBook(BookType.video, BookType.image))
    }

    @Test
    fun invalidManualTypeDoesNotReplaceRealMediaType() {
        assertEquals(BookSourceType.video, ChangeSourceFilter.sourceTypeForBook(BookType.video, BookType.local))
        assertEquals(BookSourceType.image, ChangeSourceFilter.sourceTypeForBook(BookType.image, BookType.audio or BookType.text))
    }

    @Test
    fun combinedMediaFlagsFollowCommonActivityRouting() {
        assertEquals(BookSourceType.video, ChangeSourceFilter.sourceTypeForBook(BookType.video or BookType.audio or BookType.image))
        assertEquals(BookSourceType.audio, ChangeSourceFilter.sourceTypeForBook(BookType.audio or BookType.image))
        assertEquals(BookSourceType.image, ChangeSourceFilter.sourceTypeForBook(BookType.image or BookType.webFile))
    }

    @Test
    fun novelScopeDoesNotIncludeDownloadSources() {
        val novel = ChangeSourceFilter(BookSourceType.default, true, "")
        assertTrue(novel.acceptsSource(BookSourceType.default, true, null))
        assertFalse(novel.acceptsSource(BookSourceType.file, true, null))
    }

    @Test
    fun allGroupsStillLimitsDeclaredSourceType() {
        assertTrue(manga.acceptsSource(BookSourceType.image, true, "NG"))
        assertFalse(manga.acceptsSource(BookSourceType.default, true, "NG"))
        assertFalse(manga.acceptsSource(BookSourceType.audio, true, "NG"))
        assertFalse(manga.acceptsSource(BookSourceType.video, true, "NG"))
    }

    @Test
    fun ungroupedSourceParticipatesInAllGroups() {
        assertTrue(manga.acceptsSource(BookSourceType.image, true, null))
        assertTrue(manga.acceptsSource(BookSourceType.image, true, ""))
        assertTrue(manga.acceptsSource(BookSourceType.image, true, " ; ， "))
    }

    @Test
    fun typeAndSelectedGroupFormIntersection() {
        val ng = manga.copy(group = "NG")
        assertTrue(ng.acceptsSource(BookSourceType.image, true, "漫画,NG"))
        assertFalse(ng.acceptsSource(BookSourceType.default, true, "NG"))
        assertFalse(ng.acceptsSource(BookSourceType.image, true, "漫画"))
        assertFalse(ng.acceptsSource(BookSourceType.image, true, null))
    }

    @Test
    fun groupMatchIsExactAndSharesAllSupportedDelimiters() {
        val ng = manga.copy(group = "NG")
        assertTrue(ng.acceptsSource(BookSourceType.image, true, "漫画； NG，测试; A, B"))
        assertFalse(ng.acceptsSource(BookSourceType.image, true, "NG扩展"))
        assertFalse(ng.acceptsSource(BookSourceType.image, true, "前NG"))
        assertEquals(listOf("漫画", "NG", "测试", "A", "B"),
            ChangeSourceFilter.splitGroups("漫画； NG，测试; A, B"))
    }

    @Test
    fun disabledSourcesStayExcludedWhenTypeRestrictionIsOff() {
        assertFalse(manga.copy(sameTypeOnly = false).acceptsSource(BookSourceType.default, false, "NG"))
        assertFalse(manga.acceptsSource(BookSourceType.image, false, "NG"))
    }

    @Test
    fun disablingTypeRestrictionKeepsSelectedGroup() {
        val broad = manga.copy(sameTypeOnly = false, group = "NG")
        assertTrue(broad.acceptsSource(BookSourceType.default, true, "NG"))
        assertTrue(broad.acceptsSource(BookSourceType.file, true, "NG"))
        assertFalse(broad.acceptsSource(BookSourceType.image, true, "其它"))
        assertEquals("NG", broad.group)
    }

    @Test
    fun emptyMatchingGroupDoesNotBecomeAllGroups() {
        val selected = manga.copy(group = "仅小说")
        val sources = listOf(BookSourceType.default to "仅小说", BookSourceType.image to "漫画")
        assertTrue(sources.none { (type, group) -> selected.acceptsSource(type, true, group) })
        assertEquals("仅小说", selected.group)
    }

    @Test
    fun stoppingInvalidatesCachedAndNetworkCallbacksFromSameScope() {
        val guard = ChangeSourceRequestGuard()
        val initialCache = guard.snapshot(manga)
        val search = guard.begin(manga)
        assertFalse(guard.isCurrent(initialCache, manga))
        assertTrue(guard.isCurrent(search, manga))
        guard.invalidate()
        assertFalse(guard.isCurrent(search, manga))
    }

    @Test
    fun newSearchDoesNotReactivateOldScopeAfterSwitchingBack() {
        val guard = ChangeSourceRequestGuard()
        val first = guard.begin(manga)
        guard.begin(manga.copy(group = "NG"))
        val latest = guard.begin(manga)
        assertFalse(guard.isCurrent(first, manga))
        assertTrue(guard.isCurrent(latest, manga))
    }

    @Test
    fun preferenceChangesInvalidateResultsBeforeCancellationArrives() {
        val guard = ChangeSourceRequestGuard()
        val request = guard.begin(manga)
        assertFalse(guard.isCurrent(request, manga.copy(sameTypeOnly = false)))
        assertFalse(guard.isCurrent(request, manga.copy(group = "NG")))
        assertFalse(guard.isCurrent(request, manga.copy(sourceType = BookSourceType.audio)))
    }

    @Test
    fun lateScopePreparationCannotInvalidateLatestSearch() {
        val guard = ChangeSourceRequestGuard()
        val previous = guard.begin(manga)
        val latestFilter = manga.copy(group = "NG")
        val latest = guard.begin(latestFilter)
        if (guard.isCurrent(previous, latestFilter)) guard.invalidate()
        assertTrue(guard.isCurrent(latest, latestFilter))
    }

    @Test
    fun stoppedRequestCannotPublishAfterPendingCacheWriteCompletes() {
        val guard = ChangeSourceRequestGuard()
        val request = guard.begin(manga)
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val published = AtomicBoolean(false)
        val worker = Thread {
            writeStarted.countDown()
            finishWrite.await()
            if (guard.isCurrent(request, manga)) published.set(true)
        }
        worker.start()
        try {
            assertTrue(writeStarted.await(2, TimeUnit.SECONDS))
            guard.invalidate()
            assertFalse(guard.isCurrent(request, manga))
        } finally {
            finishWrite.countDown()
            worker.join(2000)
        }
        assertFalse(worker.isAlive)
        assertFalse(published.get())
    }
}
