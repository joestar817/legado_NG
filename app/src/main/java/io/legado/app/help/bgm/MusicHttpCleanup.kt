package io.legado.app.help.bgm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Invalidates a disposable authentication client immediately, then releases its sockets on IO.
 * TLS close can write to the network. Cleanup must also outlive a cancelled login coroutine.
 */
internal class MusicHttpCleanup(
    private val clearCookies: () -> Unit,
    private val cancelCalls: () -> Unit,
    private val evictConnections: () -> Unit,
    private val shutdownExecutor: () -> Unit,
    private val executor: Executor = Dispatchers.IO.asExecutor(),
) {
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        clearCookies()
        executor.execute {
            // A failed teardown step must not skip the remaining resources.
            attempt(cancelCalls)
            attempt(evictConnections)
            attempt(shutdownExecutor)
        }
    }

    private fun attempt(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            // The context is already invalid; teardown is best effort and has no UI result.
        }
    }
}
