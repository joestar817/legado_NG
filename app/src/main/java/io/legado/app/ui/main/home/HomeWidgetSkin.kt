package io.legado.app.ui.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal val HomeWidgetSkinCornerRadius = 12.dp
internal val HomeWidgetSkinHeaderHeight = 48.dp

internal enum class HomeWidgetBackdropVariant { STANDARD, CALENDAR, BOOK_NEWS }

@Immutable
private data class HomeWidgetSkin(
    val palette: HomeListeningPalette,
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val headerTint: Color,
    val edgeTop: Color,
    val edgeMiddle: Color,
    val edgeBottom: Color,
)

private val storyWidgetSkin = HomeWidgetSkin(
    palette = HomeListeningPalette(
        foreground = Color(0xFF102D21), secondary = Color(0xFF58634F),
        primary = Color(0xFF215C49), primaryContent = Color.White,
        secondaryContainer = Color(0xFFFFFDEE), secondaryContent = Color(0xFF102D21),
    ),
    top = Color(0xFFFFFCEE), middle = Color(0xFFFDFBEA), bottom = Color(0xFFF8F7E7),
    headerTint = Color(0xFFE8EECF).copy(alpha = 0.20f),
    edgeTop = Color.White.copy(alpha = 0.92f),
    edgeMiddle = Color(0xFFFFFFF2).copy(alpha = 0.65f),
    edgeBottom = Color.White.copy(alpha = 0.85f),
)

private val nightWidgetSkin = HomeWidgetSkin(
    palette = HomeListeningPalette(
        foreground = Color(0xFFFFF1D2), secondary = Color(0xFFADC3E1),
        primary = Color(0xFFF3B953), primaryContent = Color(0xFF12233C),
        secondaryContainer = Color(0xFF253953), secondaryContent = Color(0xFFFFF1D2),
        error = Color(0xFFFFAFA0),
    ),
    top = Color(0xFF12243E), middle = Color(0xFF0B192F), bottom = Color(0xFF0C1C32),
    headerTint = Color(0xFF497193).copy(alpha = 0.07f),
    edgeTop = Color(0xFFF6CC85).copy(alpha = 0.66f),
    edgeMiddle = Color(0xFFB5C1DB).copy(alpha = 0.30f),
    edgeBottom = Color(0xFFF3BC61).copy(alpha = 0.62f),
)

private fun homeWidgetSkin(styleId: String): HomeWidgetSkin? = when (styleId) {
    "storybook" -> storyWidgetSkin
    "night" -> nightWidgetSkin
    else -> null
}

/** One palette per family; basic glass keeps its existing theme-owned palette. */
internal fun homeWidgetSkinPalette(styleId: String): HomeListeningPalette? = homeWidgetSkin(styleId)?.palette

/** Shared native material, with explicitly scoped artwork below the component's content. */
@Composable
internal fun HomeWidgetSkinBackdrop(
    styleId: String,
    headerHeight: Dp = HomeWidgetSkinHeaderHeight,
    headerOffset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 16.dp,
    variant: HomeWidgetBackdropVariant = HomeWidgetBackdropVariant.STANDARD,
    bookNewsCornerArtwork: Boolean = false,
    small: Boolean = false,
) {
    val skin = homeWidgetSkin(styleId) ?: return
    val night = styleId == "night"
    val headerArtwork = variant == HomeWidgetBackdropVariant.CALENDAR ||
        variant == HomeWidgetBackdropVariant.BOOK_NEWS
    val density = LocalDensity.current.density
    // The two shared lazy tiles retain a fixed physical grain size at every card size.
    val grain = remember(styleId, density) { listeningStructuredMaterialPaint(styleId, density) }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val headerStart = headerOffset.toPx().coerceIn(0f, height)
            val headerEnd = (headerStart + headerHeight.toPx()).coerceIn(headerStart, height)
            drawRect(Brush.linearGradient(listOf(skin.top, skin.middle, skin.bottom),
                Offset.Zero, Offset(width, height)))
            if (headerEnd > headerStart) {
                drawRect(
                    brush = Brush.verticalGradient(listOf(skin.headerTint, Color.Transparent),
                        startY = headerStart, endY = headerEnd),
                    topLeft = Offset(0f, headerStart), size = Size(width, headerEnd - headerStart),
                )
            }
            if (night) {
                // Calendar and book-news artwork live in the header; other widgets keep their desk anchor.
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFFFCC78).copy(alpha = 0.13f), Color.Transparent),
                    center = if (headerArtwork) Offset(width - 36.dp.toPx(),
                        headerStart + (headerEnd - headerStart) / 2f)
                    else Offset(width - 24.dp.toPx(), height - bottomInset.toPx() - 20.dp.toPx()),
                    radius = 88.dp.toPx(),
                ))
            } else {
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(width * 0.28f, headerEnd + 24.dp.toPx()), radius = 130.dp.toPx(),
                ))
                drawRect(Brush.radialGradient(
                    listOf(Color(0xFFE8EDCA).copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(width, height), radius = 100.dp.toPx(),
                ))
            }
            drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, width, height, grain) }
        }
        if (night && !headerArtwork && bottomInset > 0.dp) {
            // Existing bottom safety space becomes the shared desk edge, never extra content height.
            Image(
                painter = homeWidgetArtworkPainter(R.drawable.ng_home_listening_large_wood),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                alpha = 0.48f,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomInset),
            )
        }
        // Clean, restrained leaf corners are skin ornaments; the owl/person/lamp live in slots.
        if (night) {
            val leaf = if (night) R.drawable.ng_home_listening_large_night_leaves
                else R.drawable.ng_home_listening_large_paper_bottom
            Image(
                painter = homeWidgetArtworkPainter(leaf), contentDescription = null,
                contentScale = ContentScale.Fit, alignment = Alignment.TopEnd,
                alpha = if (night) 0.22f else 0.26f,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = headerOffset)
                    .size(56.dp, 60.dp).then(if (night) Modifier else Modifier.rotate(180f)),
            )
            Image(
                painter = homeWidgetArtworkPainter(leaf), contentDescription = null,
                contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                alpha = if (night) 0.14f else 0.20f,
                modifier = Modifier.align(Alignment.BottomStart).size(48.dp, 36.dp),
            )
        } else {
            HomeStorybookLeafBackdrop(Modifier.matchParentSize(), headerOffset, small)
        }
        if (bookNewsCornerArtwork && styleId == "storybook") {
            val girl = homeWidgetArtworkPainter(R.drawable.ng_home_updates_story_girl)
            val cornerLeaves = homeWidgetArtworkPainter(R.drawable.ng_home_listening_large_paper_bottom)
            Canvas(Modifier.matchParentSize()) {
                val bodyHeight = (size.height - (headerOffset + headerHeight).toPx()).coerceAtLeast(0f)
                // Decorations consume only the existing body area, including short/empty cards.
                val scale = minOf(1f, bodyHeight / 104.dp.toPx(), size.width / 142.dp.toPx())
                if (scale > 0f) {
                    val leafSize = Size(136.dp.toPx() * scale, 108.375.dp.toPx() * scale)
                    translate(size.width - leafSize.width + 4.dp.toPx() * scale,
                        size.height - leafSize.height + 6.dp.toPx() * scale) {
                        scale(scaleX = -1f, scaleY = 1f,
                            pivot = Offset(leafSize.width / 2f, leafSize.height / 2f)) {
                            with(cornerLeaves) { draw(leafSize, alpha = 0.44f) }
                        }
                    }
                    val girlSize = 100.dp.toPx() * scale
                    translate(size.width - 14.dp.toPx() * scale - girlSize,
                        size.height - 2.dp.toPx() * scale - girlSize) {
                        with(girl) { draw(Size(girlSize, girlSize)) }
                    }
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val inset = 0.85.dp.toPx()
            val radius = HomeWidgetSkinCornerRadius.toPx()
            drawRoundRect(
                brush = Brush.linearGradient(listOf(skin.edgeTop, skin.edgeMiddle, skin.edgeBottom),
                    Offset.Zero, Offset(size.width, size.height)),
                topLeft = Offset(inset, inset),
                size = Size((size.width - inset * 2).coerceAtLeast(0f),
                    (size.height - inset * 2).coerceAtLeast(0f)),
                cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                style = Stroke(0.85.dp.toPx()),
            )
        }
    }
}
