package io.legado.app.ui.book.read.aloud

/** Display capability is independent of the current app window, which may be half a tablet. */
internal data class ListeningWindowEnvironment(
    val largeDisplay: Boolean = false,
    val multiWindow: Boolean = false,
)

internal fun isLargeListeningDisplay(widthPx: Int, heightPx: Int, densityDpi: Int): Boolean =
    minOf(widthPx, heightPx).toFloat() * 160f / densityDpi.coerceAtLeast(1) >= 600f

internal fun shouldLockListeningPortrait(environment: ListeningWindowEnvironment): Boolean =
    !environment.largeDisplay && !environment.multiWindow

internal fun shouldUseListeningTwoPane(
    environment: ListeningWindowEnvironment,
    widthDp: Float,
    heightDp: Float,
): Boolean = environment.largeDisplay && !environment.multiWindow &&
    widthDp >= 840f && heightDp >= 480f && widthDp > heightDp
