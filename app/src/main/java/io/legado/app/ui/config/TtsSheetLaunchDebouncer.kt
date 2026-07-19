package io.legado.app.ui.config

internal class TtsSheetLaunchDebouncer(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS
) {
    private var lastAcceptedAt = Long.MIN_VALUE

    fun tryAcquire(nowMillis: Long): Boolean {
        if (lastAcceptedAt != Long.MIN_VALUE &&
            nowMillis - lastAcceptedAt < intervalMillis
        ) {
            return false
        }
        lastAcceptedAt = nowMillis
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 700L
    }
}
