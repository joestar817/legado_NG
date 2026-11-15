package io.legado.features.video

/** Monotonic-time watchdog and retry budget, independent of the player and Android lifecycle. */
class PlaybackRecovery {
    private var attempts = 0
    private var waitingSince: Long? = null
    private var progressAt = 0L
    private var stableSince: Long? = null
    private var lastPosition = 0L
    private var lastBuffered = 0L

    fun reset() {
        attempts = 0
        restarted()
    }

    /** Recreating a decoder or seeking must not replenish the automatic retry budget. */
    fun restarted() {
        waitingSince = null
        stableSince = null
        lastPosition = 0L
        lastBuffered = 0L
    }

    fun nextAttempt(): Int? = if (attempts < MAX_ATTEMPTS) ++attempts else null

    fun stalled(now: Long, position: Long, buffered: Long, waiting: Boolean,
                playing: Boolean, playWhenReady: Boolean): Boolean {
        if (!playWhenReady) {
            restarted()
            return false
        }
        if (waiting) {
            stableSince = null
            if (waitingSince == null) {
                waitingSince = now
                progressAt = now
            } else if (position > lastPosition || buffered > lastBuffered) {
                progressAt = now
            }
        } else {
            waitingSince = null
            if (playing && position > lastPosition) {
                val start = stableSince ?: now.also { stableSince = it }
                if (now - start >= STABLE_PLAYBACK_MS) attempts = 0
            } else {
                stableSince = null
            }
        }
        lastPosition = position
        lastBuffered = buffered
        val start = waitingSince ?: return false
        return now - progressAt >= NO_PROGRESS_MS || now - start >= MAX_WAIT_MS
    }

    companion object {
        private const val MAX_ATTEMPTS = 2
        private const val NO_PROGRESS_MS = 25_000L
        private const val MAX_WAIT_MS = 90_000L
        private const val STABLE_PLAYBACK_MS = 30_000L
    }
}
