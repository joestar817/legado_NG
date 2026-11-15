package io.legado.features.preload

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

data class VideoPreloadPolicy(
    val bufferAheadMs: Long = 3_000,
    val debounceMs: Long = 600,
    val resolveTimeoutMs: Long = 30_000,
    val lifetimeMs: Long = 120_000,
    val retryDelayMs: Long = 30_000,
    val expiryMarginMs: Long = 5_000,
) {
    init {
        require(bufferAheadMs >= 0 && debounceMs >= 0 && resolveTimeoutMs > 0)
        require(lifetimeMs > 0 && retryDelayMs >= 0 && expiryMarginMs >= 0)
    }

    fun eligible(playing: Boolean, waiting: Boolean, durationMs: Long, positionMs: Long,
                 bufferedPositionMs: Long, live: Boolean): Boolean =
        playing && !waiting && !live && durationMs > 0 && bufferedPositionMs - positionMs >= bufferAheadMs
}

data class PreloadedVideo<R, P>(val resource: R, val player: P)

/** Confined to the supplied player dispatcher. Taking a slot transfers its ownership to the caller. */
class NextVideoPreloader<K : Any, R : Any, P : Any>(
    private val scope: CoroutineScope,
    val policy: VideoPreloadPolicy = VideoPreloadPolicy(),
    private val now: () -> Long = System::currentTimeMillis,
    private val expiresAt: (R) -> Long? = { null },
    private val usable: (P) -> Boolean = { true },
    private val release: (P) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
) {
    private var key: K? = null
    private var generation = 0L
    private var task: Job? = null
    private var expiry: Job? = null
    private var slot: PreloadedVideo<R, P>? = null
    private var validUntil = 0L
    private var retryAt = 0L

    fun update(candidate: K?, eligible: Boolean, resolve: suspend () -> R, prepare: (R) -> P) {
        if (candidate != key) {
            clear()
            key = candidate
        }
        if (candidate == null) return
        val current = slot
        if (current != null && (now() >= validUntil || !usable(current.player))) {
            discard()
            retryAt = now() + policy.retryDelayMs
        }
        if (!eligible) {
            cancelPending()
            return
        }
        if (slot != null || task?.isActive == true || now() < retryAt) return
        val ticket = generation
        task = scope.launch(start = CoroutineStart.LAZY) {
            var player: P? = null
            try {
                delay(policy.debounceMs.milliseconds)
                val resource = withTimeout(policy.resolveTimeoutMs.milliseconds) { resolve() }
                ensureActive()
                if (ticket != generation) return@launch
                val limit = minOf(now() + policy.lifetimeMs,
                    expiresAt(resource)?.minus(policy.expiryMarginMs) ?: Long.MAX_VALUE)
                if (limit <= now()) error("预加载播放凭证即将过期")
                player = prepare(resource)
                ensureActive()
                if (ticket != generation) return@launch
                slot = PreloadedVideo(resource, player)
                player = null
                validUntil = limit
                expiry = scope.launch {
                    delay((limit - now()).coerceAtLeast(1).milliseconds)
                    if (ticket == generation) discard()
                }
            } catch (error: TimeoutCancellationException) {
                if (ticket == generation) failed(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (ticket == generation) failed(error)
            } finally {
                player?.let(release)
            }
        }
        task?.start()
    }

    fun take(candidate: K): PreloadedVideo<R, P>? {
        val current = slot
        val result = current?.takeIf { candidate == key && now() < validUntil && usable(it.player) }
        if (result != null) slot = null
        clear()
        return result
    }

    fun clear() {
        generation++
        cancelPending()
        discard()
        key = null
        retryAt = 0
    }

    private fun cancelPending() {
        if (task?.isActive == true) {
            generation++
            task?.cancel()
        }
        task = null
    }

    private fun discard() {
        expiry?.cancel()
        expiry = null
        val old = slot
        slot = null
        old?.player?.let(release)
    }

    private fun failed(error: Exception) {
        retryAt = now() + policy.retryDelayMs
        onFailure(error)
    }
}
