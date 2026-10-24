package io.legado.app.help.bgm

/** Foreground audition timing. Position is elapsed media time, never a guessed original-song offset. */
data class BgmPreviewProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val fullDurationMs: Long = 0,
    val isPreview: Boolean = false,
    val canSeek: Boolean = false,
) {
    fun seekPosition(requestedMs: Long): Long? =
        if (canSeek && durationMs > 0) requestedMs.coerceIn(0, durationMs) else null
}

internal fun auditionProgress(
    positionMs: Long,
    mediaDurationMs: Long,
    fullDurationMs: Long,
    isPreview: Boolean,
    seekable: Boolean,
): BgmPreviewProgress {
    val duration = mediaDurationMs.coerceAtLeast(0)
    return BgmPreviewProgress(
        positionMs = if (duration > 0) positionMs.coerceIn(0, duration) else 0,
        durationMs = duration,
        fullDurationMs = if (isPreview) maxOf(duration, fullDurationMs.coerceAtLeast(0)) else duration,
        isPreview = isPreview,
        canSeek = seekable && duration > 0,
    )
}
