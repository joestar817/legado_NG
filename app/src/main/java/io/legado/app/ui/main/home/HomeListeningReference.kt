package io.legado.app.ui.main.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect

/** Native layout coordinates measured from the selected 164/340dp concepts. */
@Immutable
internal data class HomeListeningReference(
    val styleId: String,
    val width: Float,
    val height: Float,
    val headerHeight: Float,
    val sceneStartX: Float? = null,
    val headerIconX: Float,
    val headerIconY: Float,
    val headerIconSize: Float,
    val headerTextX: Float,
    val headerTextY: Float,
    val headerTextSize: Float,
    val cover: Rect,
    val textX: Float,
    val textY: Float,
    val textWidth: Float,
    val textHeight: Float,
    val centeredText: Boolean = false,
    val bookSize: Float,
    val bookLine: Float,
    val chapterSize: Float,
    val chapterLine: Float,
    val statusSize: Float,
    val statusLine: Float,
    val chapterGap: Float,
    val statusGap: Float,
    val statusIcon: Float,
    val actionsY: Float,
    val primaryCenterX: Float,
    val secondaryCenterX: Float,
    val primarySize: Float,
    val secondarySize: Float,
    val captionSize: Float,
    val captionLine: Float,
    val captionGap: Float,
)

internal fun homeListeningReference(variant: HomeWidgetVariant): HomeListeningReference {
    val large = variant.size == HomeWidgetSize.LARGE
    return when (variant.styleId) {
        "storybook" -> if (large) storyLarge else storySmall
        "paper" -> if (large) paperLarge else paperSmall
        "night" -> if (large) nightLarge else nightSmall
        else -> error("No reference for ${variant.styleId}")
    }
}

/** Available title area follows the decoration boundary, not the old lettering's erase rectangle. */
internal val HomeListeningReference.headerSafeRight: Float
    get() = when {
        width > 200f && styleId == "storybook" -> 195f
        width > 200f -> 230f
        styleId == "storybook" || styleId == "night" -> 98f
        else -> 110f
    }

private val storyLarge = HomeListeningReference(
    "storybook", 340f, 169.7f, 30.3f,
    sceneStartX = 190f,
    headerIconX = 15f, headerIconY = 6f, headerIconSize = 26f,
    headerTextX = 45f, headerTextY = 9f, headerTextSize = 14f,
    cover = Rect(14f, 37.9f, 74.4f, 118.43f),
    textX = 85.4f, textY = 37.8f, textWidth = 106f, textHeight = 56f,
    bookSize = 15.5f, bookLine = 22f, chapterSize = 12f, chapterLine = 16f,
    statusSize = 10.5f, statusLine = 16f, chapterGap = 0f, statusGap = 0f, statusIcon = 12f,
    actionsY = 104f, primaryCenterX = 105.1f, secondaryCenterX = 166.8f,
    primarySize = 41.6f, secondarySize = 41.9f, captionSize = 9.5f, captionLine = 13f, captionGap = 0f,
)

private val paperLarge = HomeListeningReference(
    "paper", 340f, 150.4f, 38f,
    headerIconX = 18f, headerIconY = 10f, headerIconSize = 20f,
    headerTextX = 47f, headerTextY = 12f, headerTextSize = 16f,
    cover = Rect(17.8f, 49f, 74.5f, 124.6f),
    textX = 89.4f, textY = 57f, textWidth = 90f, textHeight = 64f,
    bookSize = 17f, bookLine = 23f, chapterSize = 13f, chapterLine = 18f,
    statusSize = 11f, statusLine = 16f, chapterGap = 3f, statusGap = 3f, statusIcon = 11f,
    actionsY = 59.5f, primaryCenterX = 224.7f, secondaryCenterX = 290f,
    primarySize = 48f, secondarySize = 45.5f, captionSize = 10f, captionLine = 13f, captionGap = 5f,
)

private val nightLarge = HomeListeningReference(
    "night", 340f, 177.2f, 42f,
    headerIconX = 20f, headerIconY = 14f, headerIconSize = 26f,
    headerTextX = 49f, headerTextY = 16f, headerTextSize = 14.5f,
    cover = Rect(19.8f, 46.6f, 82.7f, 130.47f),
    textX = 95f, textY = 51.1f, textWidth = 91f, textHeight = 64f,
    bookSize = 17f, bookLine = 24f, chapterSize = 13f, chapterLine = 18f,
    statusSize = 11f, statusLine = 16f, chapterGap = 3f, statusGap = 3f, statusIcon = 12f,
    actionsY = 54.3f, primaryCenterX = 228.9f, secondaryCenterX = 291.3f,
    primarySize = 45f, secondarySize = 44.4f, captionSize = 10f, captionLine = 13f, captionGap = 3f,
)

private val storySmall = illustratedSmallReference("storybook")

private val paperSmall = HomeListeningReference(
    "paper", 164f, 208.4f, 36.6f,
    headerIconX = 14f, headerIconY = 11.5f, headerIconSize = 17f,
    headerTextX = 39f, headerTextY = 12f, headerTextSize = 13f,
    cover = Rect(13.4f, 46.1f, 65.4f, 115.43f),
    textX = 75.7f, textY = 53f, textWidth = 77f, textHeight = 56f,
    bookSize = 13f, bookLine = 18f, chapterSize = 10f, chapterLine = 13f,
    statusSize = 9.5f, statusLine = 13f, chapterGap = 5f, statusGap = 4f, statusIcon = 9.5f,
    actionsY = 128.8f, primaryCenterX = 52.1f, secondaryCenterX = 113.6f,
    primarySize = 39.3f, secondarySize = 38.3f, captionSize = 9f, captionLine = 11f, captionGap = 0f,
)

private val nightSmall = illustratedSmallReference("night")

/** Illustrated small cards share geometry; only their materials and footer artwork differ. */
private fun illustratedSmallReference(styleId: String) = HomeListeningReference(
    styleId, 164f, 284f, 36f,
    headerIconX = 12f, headerIconY = 9.5f, headerIconSize = 22f,
    headerTextX = 36.5f, headerTextY = 13.5f, headerTextSize = 12f,
    cover = Rect(12f, 44f, 56f, 102.67f),
    textX = 65f, textY = 48f, textWidth = 86f, textHeight = 56f,
    bookSize = 13f, bookLine = 18f, chapterSize = 10f, chapterLine = 13f,
    statusSize = 9.5f, statusLine = 13f, chapterGap = 5f, statusGap = 4f, statusIcon = 9.5f,
    actionsY = 123.5f, primaryCenterX = 46f, secondaryCenterX = 118f,
    primarySize = 42f, secondarySize = 42f, captionSize = 9.5f, captionLine = 12f, captionGap = 3f,
)
