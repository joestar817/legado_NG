package io.legado.app.help.bgm

/** A single queue edit. Cancellation and the final disk/state commit share one lock. */
class BgmQueueOperation {
    private enum class State { ACTIVE, CANCELLED, COMMITTED }
    private var state = State.ACTIVE

    val isCancelled: Boolean
        @Synchronized get() = state == State.CANCELLED

    /** Once the atomic commit starts, it completes before cancellation can return. */
    @Synchronized
    fun cancel() {
        if (state == State.ACTIVE) state = State.CANCELLED
    }

    @Synchronized
    internal fun commitIfActive(commit: () -> Boolean): Boolean {
        if (state != State.ACTIVE) return false
        return commit().also { if (it) state = State.COMMITTED }
    }
}
