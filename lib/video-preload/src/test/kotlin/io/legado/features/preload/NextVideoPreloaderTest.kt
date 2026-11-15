package io.legado.features.preload

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NextVideoPreloaderTest {
    private data class Resource(val id: String, val expiry: Long? = null)
    private class Player { var releases = 0; var usable = true }
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var now = 10_000L
        val failures = mutableListOf<Exception>()
        val loader = NextVideoPreloader<String, Resource, Player>(scope,
            policy = VideoPreloadPolicy(debounceMs = 0), now = { now },
            expiresAt = { it.expiry }, usable = { it.usable },
            release = { it.releases++ }, onFailure = { failures += it })
        fun close() { loader.clear(); scope.cancel() }
    }

    @Test fun stablePlaybackPreparesOnlyOnePlayerAndTakeTransfersOwnership() {
        val f = Fixture()
        try {
            val player = Player()
            var resolutions = 0
            repeat(3) { f.loader.update("next", true, { resolutions++; Resource("next") }, { player }) }
            val warmed = f.loader.take("next")!!
            assertEquals(1, resolutions)
            assertSame(player, warmed.player)
            assertEquals("next", warmed.resource.id)
            f.loader.clear()
            assertEquals(0, player.releases)
            assertNull(f.loader.take("next"))
        } finally { f.close() }
    }

    @Test fun differentEpisodeOrQualityDisposesPreviousPlayer() {
        val f = Fixture()
        try {
            val player = Player()
            f.loader.update("next:720", true, { Resource("next") }, { player })
            assertNull(f.loader.take("next:1080"))
            assertEquals(1, player.releases)
        } finally { f.close() }
    }

    @Test fun skippingWhileResolutionIgnoresCancellationNeverPublishesOldResult() = runBlocking {
        val f = Fixture()
        try {
            val resolving = CompletableDeferred<Unit>()
            var players = 0
            f.loader.update("old", true, {
                withContext(NonCancellable) { resolving.await() }
                Resource("old")
            }, { players++; Player() })
            f.loader.update("new", false, { Resource("new") }, { Player() })
            resolving.complete(Unit)
            yield()
            assertEquals(0, players)
            assertNull(f.loader.take("old"))
        } finally { f.close() }
    }

    @Test fun temporaryBufferingCancelsResolutionButRetainsCompletedPreparation() = runBlocking {
        val f = Fixture()
        try {
            val resolving = CompletableDeferred<Resource>()
            var cancelled = false
            f.loader.update("next", true, {
                try { resolving.await() } finally { cancelled = true }
            }, { Player() })
            f.loader.update("next", false, { Resource("next") }, { Player() })
            assertTrue(cancelled)
            val player = Player()
            f.loader.update("next", true, { Resource("next") }, { player })
            f.loader.update("next", false, { error("must not resolve") }, { Player() })
            assertSame(player, f.loader.take("next")!!.player)
            assertEquals(0, player.releases)
        } finally { f.close() }
    }

    @Test fun knownCredentialExpiryAndLifetimeRejectOldMedia() {
        val f = Fixture()
        try {
            val credential = Player()
            f.loader.update("signed", true, { Resource("signed", f.now + 10_000) }, { credential })
            f.now += 5_000
            assertNull(f.loader.take("signed"))
            assertEquals(1, credential.releases)
            val regular = Player()
            f.loader.update("regular", true, { Resource("regular") }, { regular })
            f.now += 120_000
            assertNull(f.loader.take("regular"))
            assertEquals(1, regular.releases)
        } finally { f.close() }
    }

    @Test fun credentialAboutToExpireIsRejectedBeforePlayerCreation() {
        val f = Fixture()
        try {
            var created = false
            f.loader.update("next", true, { Resource("next", f.now + 4_999) }, { created = true; Player() })
            assertFalse(created)
            assertEquals(1, f.failures.size)
            assertNull(f.loader.take("next"))
        } finally { f.close() }
    }

    @Test fun backgroundFailureBacksOffWithoutBreakingForegroundPlayback() {
        val f = Fixture()
        try {
            var calls = 0
            val resolve: suspend () -> Resource = { calls++; error("network") }
            f.loader.update("next", true, resolve, { Player() })
            f.loader.update("next", true, resolve, { Player() })
            assertEquals(1, calls)
            f.now += 30_000
            f.loader.update("next", true, resolve, { Player() })
            assertEquals(2, calls)
            assertEquals(2, f.failures.size)
        } finally { f.close() }
    }

    @Test fun failedStandbyIsReleasedAndNotHandedToForeground() {
        val f = Fixture()
        try {
            val player = Player()
            f.loader.update("next", true, { Resource("next") }, { player })
            player.usable = false
            assertNull(f.loader.take("next"))
            f.loader.clear()
            assertEquals(1, player.releases)
        } finally { f.close() }
    }

    @Test fun clearingBeforeDebouncePreventsAnyNetworkRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val loader = NextVideoPreloader<String, Resource, Player>(scope, release = { it.releases++ })
        loader.update("next", true, { calls++; Resource("next") }, { Player() })
        loader.clear()
        yield()
        assertEquals(0, calls)
        scope.cancel()
    }

    @Test fun liveAndInsufficientBufferDoNotCompeteWithCurrentPlayback() {
        val policy = VideoPreloadPolicy()
        assertTrue(policy.eligible(true, false, 60_000, 1_000, 4_000, false))
        assertFalse(policy.eligible(true, false, 60_000, 1_000, 3_999, false))
        assertFalse(policy.eligible(true, true, 60_000, 1_000, 4_000, false))
        assertFalse(policy.eligible(false, false, 60_000, 1_000, 4_000, false))
        assertFalse(policy.eligible(true, false, 60_000, 1_000, 4_000, true))
        assertFalse(policy.eligible(true, false, 0, 1_000, 4_000, false))
    }

    @Test fun resolutionTimeoutBacksOffAndNeverCreatesAPlayer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val failure = CompletableDeferred<Exception>()
        var created = false
        val loader = NextVideoPreloader<String, Resource, Player>(scope,
            policy = VideoPreloadPolicy(debounceMs = 0, resolveTimeoutMs = 20),
            release = { it.releases++ }, onFailure = { failure.complete(it) })
        try {
            loader.update("next", true, { awaitCancellation() }, { created = true; Player() })
            assertTrue(withTimeout(2_000) { failure.await() } is TimeoutCancellationException)
            assertFalse(created)
        } finally { loader.clear(); scope.cancel() }
    }

    @Test fun clearDuringPlayerCreationReleasesTheLatePlayerExactlyOnce() {
        val f = Fixture()
        try {
            val player = Player()
            f.loader.update("next", true, { Resource("next") }, {
                f.loader.clear()
                player
            })
            assertNull(f.loader.take("next"))
            assertEquals(1, player.releases)
        } finally { f.close() }
    }

    @Test fun standbyBudgetStopsAtEitherLimitAndExpandsOnPromotion() {
        val budget = StandbyBufferBudget()
        assertTrue(budget.canLoad(7_999_999, 4 * 1024 * 1024 - 1))
        assertFalse(budget.canLoad(8_000_000, 0))
        assertFalse(budget.canLoad(0, 4 * 1024 * 1024))
        budget.activate()
        assertTrue(budget.canLoad(60_000_000, 32 * 1024 * 1024))
    }

    @Test fun loadingIndicatorIgnoresShortBufferingAndRestartsAfterRecovery() {
        val gate = PlaybackLoadingGate()
        assertFalse(gate.update(true, 0))
        assertFalse(gate.update(true, 499))
        assertFalse(gate.update(false, 499))
        assertFalse(gate.update(true, 1_000))
        assertTrue(gate.update(true, 1_500))
        assertFalse(gate.update(false, 1_501))
        assertFalse(gate.update(true, 2_000))
    }
}
