package io.legado.app.help.bgm

/** A seek can report ENDED while paused; only an actively authorized stream may auto-advance. */
internal fun canAdvanceBgmAtEnd(
    playWhenReady: Boolean,
    allowedToPlay: Boolean,
    waitingForAdvance: Boolean,
): Boolean = playWhenReady && allowedToPlay && !waitingForAdvance

/** Speech gaps are expected between TTS segments and chapters. */
internal class BgmSpeechGate(private val graceMs: Long = 8_000L) {
    var manualPlayback = false
        private set
    var hasSpeech = false
        private set
    var paused = true
        private set
    private var waitingSince: Long? = null

    fun started() {
        manualPlayback = false
        hasSpeech = true
        paused = false
        waitingSince = null
    }

    fun waiting(now: Long) {
        if (hasSpeech && !paused && waitingSince == null) waitingSince = now
    }

    fun pause() {
        manualPlayback = false
        paused = true
        waitingSince = null
    }

    fun playManually() { manualPlayback = true }

    fun primaryStarting() { manualPlayback = false }

    fun canPlay(now: Long): Boolean = manualPlayback || (hasSpeech && !paused &&
        (waitingSince?.let { now - it < graceMs } ?: true))

    fun remainingWait(now: Long): Long? = waitingSince?.let {
        (graceMs - (now - it)).coerceAtLeast(0L)
    }
}

internal class BgmFailureBudget {
    private var failures = 0

    /** Never cycle indefinitely through an unavailable queue, including single-repeat mode. */
    fun failed(queueSize: Int): Boolean {
        failures++
        return failures >= queueSize.coerceIn(1, 3)
    }

    fun reset() {
        failures = 0
    }
}
