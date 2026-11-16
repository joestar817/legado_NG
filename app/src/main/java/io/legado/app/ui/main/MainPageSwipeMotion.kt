package io.legado.app.ui.main

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun shouldRegrabBookshelfSwipe(
    newDirection: Int?,
    pairDirection: Int,
    settlingToTarget: Boolean,
): Boolean {
    val directionIntoPair = if (settlingToTarget) -pairDirection else pairDirection
    return newDirection == directionIntoPair
}

/** Keep short returns brief and give longer moves time to decelerate. */
internal fun pageSwipeSettleDuration(remainingFraction: Float, requestedMillis: Int): Int {
    if (remainingFraction == 0f) return 0
    val fraction = remainingFraction.coerceAtMost(1f)
    val minimum = (70f + 130f * fraction).roundToInt()
    val maximum = (90f + 230f * fraction).roundToInt()
    return requestedMillis.coerceIn(minimum, maximum)
}

/** Start with ViewPager's distance and velocity calculation before applying the shared limits. */
internal fun bookshelfGroupSettleDuration(
    pageWidth: Int,
    remainingDistance: Float,
    velocityX: Float,
): Int {
    if (pageWidth <= 0 || remainingDistance == 0f) return 0
    val remainingFraction = (abs(remainingDistance) / pageWidth).coerceAtMost(1f)
    val velocity = abs(velocityX).toInt()
    val requestedMillis = if (velocity == 0) {
        ((1f + remainingFraction) * 100f).toInt()
    } else {
        val halfWidth = pageWidth / 2
        val distanceInfluence = sin((remainingFraction - 0.5f) * 0.47123894f)
        val adjustedDistance = halfWidth * (1f + distanceInfluence)
        4 * (1000f * adjustedDistance / velocity).roundToInt().coerceAtMost(150)
    }
    return pageSwipeSettleDuration(remainingFraction, requestedMillis)
}
