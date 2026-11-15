package io.legado.features.preload

/** Uses monotonic time; raw buffering must still be passed to playback recovery and subtitles. */
class PlaybackLoadingGate(private val delayMs: Long = 500) {
    private var waitingSince: Long? = null
    init { require(delayMs >= 0) }

    fun update(waiting: Boolean, nowMs: Long): Boolean {
        if (!waiting) {
            reset()
            return false
        }
        val start = waitingSince ?: nowMs.also { waitingSince = it }
        return nowMs - start >= delayMs
    }

    fun reset() { waitingSince = null }
}
