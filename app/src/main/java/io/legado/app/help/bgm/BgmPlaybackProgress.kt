package io.legado.app.help.bgm

/** Live formal-player state. It is never serialized into the saved queue. */
data class BgmPlaybackProgress(
    val trackKey: String? = null,
    val selectionRevision: Long = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val canTogglePause: Boolean = false,
    val pausedByUser: Boolean = false,
) {
    fun seekPosition(requestedMs: Long, expectedTrackKey: String, expectedRevision: Long): Long? =
        if (canSeek && durationMs > 0 && trackKey == expectedTrackKey &&
            selectionRevision == expectedRevision
        ) requestedMs.coerceIn(0, durationMs) else null
}

internal fun formalBgmProgress(
    trackKey: String?,
    selectionRevision: Long,
    positionMs: Long,
    mediaDurationMs: Long,
    seekable: Boolean,
    canTogglePause: Boolean,
    pausedByUser: Boolean,
): BgmPlaybackProgress {
    val duration = mediaDurationMs.coerceAtLeast(0)
    return BgmPlaybackProgress(
        trackKey = trackKey,
        selectionRevision = selectionRevision,
        positionMs = if (duration > 0) positionMs.coerceIn(0, duration) else 0,
        durationMs = duration,
        canSeek = trackKey != null && seekable && duration > 0,
        canTogglePause = trackKey != null && canTogglePause,
        pausedByUser = pausedByUser,
    )
}
