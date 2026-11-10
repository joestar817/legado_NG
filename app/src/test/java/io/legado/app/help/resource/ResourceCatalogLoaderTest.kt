package io.legado.app.help.resource

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ResourceCatalogLoaderTest {
    private val fonts = category("fonts", "font")
    private val backgrounds = category("backgrounds", "background")
    private val themes = category("themes", "theme")
    private val commit = "a".repeat(40)
    private val contentCommit = "b".repeat(40)

    private fun category(id: String, type: String) =
        ResourceCategory(id, id, type, "resources/$id/index.json", 1)

    private fun item(category: ResourceCategory) = ResourceItem(
        id = "${category.type}.example", name = "示例", author = "作者", versionCode = 1,
        payload = ResourcePayload("resources/${category.id}/example/file.${format(category)}",
            format(category), 32, "c".repeat(64)),
    )

    private fun format(category: ResourceCategory) = when (category.type) {
        "font" -> "ttf"
        "background" -> "png"
        else -> "ngtheme"
    }

    private inner class Source(var categories: List<ResourceCategory>) : ResourceCatalogSource {
        var head = commit
        var sourceCommit = contentCommit
        var headError: Exception? = null
        var headGate: CompletableDeferred<Unit>? = null
        var catalogCalls = 0
        var indexCalls = 0
        val requests = Channel<Pending>(Channel.UNLIMITED)

        override suspend fun latestCommit(): String {
            headGate?.await()
            return headError?.let { throw it } ?: head
        }
        override suspend fun catalog(commit: String): ResourceCatalog {
            catalogCalls++
            return ResourceCatalog(1, sourceCommit, categories)
        }
        override suspend fun index(commit: String, catalog: ResourceCatalog, category: ResourceCategory): ResourceIndex {
            indexCalls++
            val pending = Pending(commit, catalog.sourceCommit, category)
            requests.send(pending)
            return pending.result.await()
        }
    }

    private inner class Pending(val commit: String, val sourceCommit: String, val category: ResourceCategory) {
        val result = CompletableDeferred<ResourceIndex>()
        fun finish() { result.complete(ResourceIndex(1, sourceCommit, category.type, listOf(item(category)))) }
        fun fail(error: Exception) { result.completeExceptionally(error) }
    }

    private suspend fun Source.next() = withTimeout(5_000) { requests.receive() }
    private suspend fun ResourceCatalogLoader.until(predicate: (ResourceCatalogLoadState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }

    private class Cache(var value: CachedResourceCatalog?) : ResourceCatalogCache {
        override fun load() = value
        override fun remember(snapshot: ResourceSnapshot, complete: Boolean) = Unit
        override fun invalidate() { value = null }
    }

    private fun cache(categories: List<ResourceCategory>, loaded: List<ResourceCategory>) = Cache(CachedResourceCatalog(
        snapshot = loaded.fold(ResourceSnapshot(catalogCommit = commit,
            catalog = ResourceCatalog(1, contentCommit, categories))) { snapshot, category ->
            snapshot.withResources(category, listOf(item(category)))
        },
        loaded = loaded.map { it.id }.toSet(),
    ))

    @Test fun cacheIsVisibleBeforeHeadCheckAndSameHeadFetchesOnlyMissingCategory() = runBlocking {
        val source = Source(listOf(fonts, themes)).apply { headGate = CompletableDeferred() }
        val loader = ResourceCatalogLoader(this, source, cache = cache(source.categories, listOf(fonts)))
        try {
            loader.refresh()
            val offline = loader.until { it.cached != null }
            assertEquals(listOf(item(fonts)), offline.cached!!.snapshot.entries.map { it.item })
            assertNull(offline.snapshot)
            assertTrue(source.requests.tryReceive().isFailure)
            source.headGate!!.complete(Unit)
            val missing = source.next()
            assertEquals(themes, missing.category)
            assertEquals(setOf(fonts.id), loader.state.value.loaded)
            missing.finish()
            loader.until { it.loaded.size == 2 }
            assertEquals(1, source.indexCalls)
            assertEquals(0, source.catalogCalls)
        } finally { loader.close() }
    }

    @Test fun newHeadRebindsEqualHashesAndFetchesChangedOrMissingCategories() = runBlocking {
        val unchanged = fonts.copy(indexSha256 = "a".repeat(64))
        val oldTheme = themes.copy(indexSha256 = "b".repeat(64))
        val changed = themes.copy(indexSha256 = "c".repeat(64))
        val missing = backgrounds.copy(indexSha256 = "d".repeat(64))
        val source = Source(listOf(unchanged, changed, missing)).apply {
            head = "d".repeat(40)
            sourceCommit = "e".repeat(40)
        }
        val loader = ResourceCatalogLoader(this, source, cache = cache(listOf(unchanged, oldTheme), listOf(unchanged, oldTheme)))
        try {
            loader.prioritize(setOf("theme"))
            loader.refresh()
            val updating = source.next()
            assertEquals(changed, updating.category)
            val partial = loader.state.value
            val reused = partial.snapshot!!.entries.single()
            assertEquals(source.head, reused.catalogCommit)
            assertEquals(source.sourceCommit, reused.sourceCommit)
            assertEquals(item(unchanged), reused.item)
            val browse = ResourceCenterState(snapshot = partial.snapshot, cachedSnapshot = partial.cached?.snapshot,
                loadedCategories = partial.loaded)
            val stale = browse.visibleCatalogEntries().single { it.category.id == changed.id }
            assertNull(browse.confirmedEntry(stale))
            assertNotNull(browse.confirmedEntry(reused))
            updating.finish()
            val newGroup = source.next()
            assertEquals(missing, newGroup.category)
            newGroup.finish()
            loader.until { it.loaded.size == 3 }
            assertEquals(2, source.indexCalls)
        } finally { loader.close() }
    }

    @Test fun timeoutRetainsCacheButAuthoritativeDenialInvalidatesIt() = runBlocking {
        val source = Source(listOf(fonts)).apply { headError = IOException("timeout") }
        val cache = cache(source.categories, source.categories)
        val loader = ResourceCatalogLoader(this, source, cache = cache)
        try {
            loader.refresh()
            val timeout = loader.until { it.error != null }
            assertNotNull(timeout.cached)
            assertNull(timeout.snapshot)
            assertFalse(timeout.accessDenied)
            source.headError = ResourceHttpException(403, authoritative = true)
            loader.until { !it.checking }
            loader.refresh()
            val denied = loader.until { it.accessDenied }
            assertNull(denied.cached)
            assertNull(cache.value)
            assertEquals(0, source.indexCalls)
        } finally { loader.close() }
    }

    @Test fun prioritizesCurrentGroupAndPublishesBeforeBackgroundCompletes() = runBlocking {
        val source = Source(listOf(fonts, backgrounds, themes))
        val loader = ResourceCatalogLoader(this, source)
        try {
            loader.prioritize(setOf("theme"))
            loader.refresh()
            val theme = source.next()
            assertEquals(themes, theme.category)
            assertTrue(source.requests.tryReceive().isFailure)
            theme.finish()
            val partial = loader.until { themes.id in it.loaded }
            assertEquals(listOf(themes.id), partial.snapshot!!.entries.map { it.category.id })
            assertEquals(setOf(fonts.id, backgrounds.id), partial.pending)
            val first = source.next()
            val second = source.next()
            assertEquals(listOf(fonts, backgrounds), listOf(first.category, second.category))
            second.finish()
            first.finish()
            val complete = loader.until { it.pending.isEmpty() }
            assertEquals(listOf(fonts.id, backgrounds.id, themes.id), complete.snapshot!!.entries.map { it.category.id })
        } finally { loader.close() }
    }

    @Test fun promotesQueuedSelectionAndCoalescesRunningRequests() = runBlocking {
        val source = Source(listOf(fonts, backgrounds, themes))
        val loader = ResourceCatalogLoader(this, source, parallelism = 1)
        try {
            loader.refresh()
            val font = source.next()
            repeat(3) { loader.prioritize(setOf("theme")); loader.refresh(force = false) }
            assertTrue(source.requests.tryReceive().isFailure)
            font.finish()
            val theme = source.next()
            assertEquals(themes, theme.category)
            theme.finish()
            val background = source.next()
            assertEquals(backgrounds, background.category)
            background.finish()
            loader.until { it.pending.isEmpty() }
            assertEquals(3, source.indexCalls)
            assertEquals(1, source.catalogCalls)
        } finally { loader.close() }
    }

    @Test fun sameCommitReusesSuccessfulIndicesAndNewCommitReplacesWholeGeneration() = runBlocking {
        val source = Source(listOf(fonts))
        val loader = ResourceCatalogLoader(this, source)
        try {
            loader.refresh()
            source.next().finish()
            val first = loader.until { it.loaded == setOf(fonts.id) && !it.checking }.snapshot
            loader.refresh(force = false)
            loader.until { !it.checking }
            assertSame(first, loader.state.value.snapshot)
            assertEquals(1, source.indexCalls)
            assertEquals(1, source.catalogCalls)
            loader.refresh()
            val refreshed = loader.until { !it.checking }.snapshot!!
            assertTrue(refreshed.checkedAt > first!!.checkedAt)
            assertSame(first.entries, refreshed.entries)
            assertEquals(first.catalogCommit, refreshed.catalogCommit)
            assertEquals(1, source.indexCalls)
            assertEquals(1, source.catalogCalls)
            source.head = "d".repeat(40)
            loader.refresh()
            val fresh = source.next()
            assertEquals(source.head, fresh.commit)
            assertTrue(loader.state.value.snapshot!!.entries.isEmpty())
            fresh.finish()
            val complete = loader.until { it.loaded == setOf(fonts.id) }
            assertEquals(source.head, complete.snapshot!!.catalogCommit)
            assertTrue(complete.snapshot!!.entries.all { it.catalogCommit == source.head })
        } finally { loader.close() }
    }

    @Test fun oneFailedGroupDoesNotHideSuccessfulGroupAndOnlyFailedGroupRetries() = runBlocking {
        val source = Source(listOf(fonts, themes))
        val loader = ResourceCatalogLoader(this, source)
        try {
            loader.refresh()
            source.next().finish()
            source.next().fail(IOException("断网"))
            val partial = loader.until { it.pending.isEmpty() }
            assertEquals(setOf(fonts.id), partial.loaded)
            assertEquals(setOf(themes.id), partial.errors.keys)
            loader.retry(setOf(fonts.id, themes.id))
            val retry = source.next()
            assertEquals(themes, retry.category)
            retry.finish()
            loader.until { it.loaded.size == 2 }
            assertEquals(3, source.indexCalls)
        } finally { loader.close() }
    }

    @Test fun authoritativeDenialClearsSessionAndAutomaticRevalidationCanRecover() = runBlocking {
        val source = Source(listOf(fonts))
        val loader = ResourceCatalogLoader(this, source)
        try {
            loader.refresh()
            source.next().finish()
            loader.until { it.loaded.isNotEmpty() }
            source.headError = ResourceHttpException(401, authoritative = true)
            loader.refresh(force = false)
            val denied = loader.until { it.accessDenied && !it.checking }
            assertNull(denied.snapshot)
            assertTrue(denied.loaded.isEmpty())
            source.headError = null
            loader.refresh(force = false)
            source.next().finish()
            loader.until { !it.accessDenied && it.loaded.isNotEmpty() }
            assertEquals(2, source.catalogCalls)
        } finally { loader.close() }
    }

    @Test fun denialCancelsOtherIndicesAndLateResponsesCannotRestoreSnapshot() = runBlocking {
        val source = Source(listOf(fonts, themes))
        val loader = ResourceCatalogLoader(this, source)
        try {
            loader.refresh()
            val font = source.next()
            val theme = source.next()
            font.fail(ResourceHttpException(403, authoritative = true))
            loader.until { it.accessDenied }
            theme.finish()
            loader.prioritize(setOf("theme"))
            assertNull(loader.state.value.snapshot)
            assertTrue(loader.state.value.pending.isEmpty())
        } finally { loader.close() }
    }

    @Test fun failedInitialLoadHasNoSnapshotAndNewSessionDoesNotReusePreviousMemory() = runBlocking {
        val source = Source(listOf(fonts))
        val first = ResourceCatalogLoader(this, source)
        first.refresh()
        source.next().finish()
        first.until { it.loaded.isNotEmpty() }
        first.close()
        assertNull(first.state.value.snapshot)
        source.headError = IOException("离线")
        val next = ResourceCatalogLoader(this, source)
        try {
            next.refresh()
            val failed = next.until { it.error != null && !it.checking }
            assertNull(failed.snapshot)
            assertEquals(1, source.indexCalls)
        } finally { next.close() }
    }

    @Test fun proxyDenialDoesNotCarrySourceAuthority() {
        assertFalse(ResourceHttpException(401, authoritative = false).accessDenied)
        assertTrue(ResourceHttpException(403, authoritative = true).accessDenied)
        assertFalse(ResourceHttpException(404, authoritative = true).accessDenied)
    }
}
