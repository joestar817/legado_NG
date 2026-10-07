package io.legado.app.ui.main.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class HomeListeningPalette(
    val foreground: Color,
    val secondary: Color,
    val primary: Color,
    val primaryContent: Color,
    val secondaryContainer: Color,
    val secondaryContent: Color,
    val error: Color = Color(0xFFB42318),
)

@Immutable
internal data class HomeListeningAppearance(
    val styleId: String,
    val palette: HomeListeningPalette,
    val container: Color,
    val border: Color,
    @param:DrawableRes val texture: Int,
)

internal fun homeListeningAppearance(variant: HomeWidgetVariant): HomeListeningAppearance? =
    homeListeningSkinPalette(variant.styleId)?.let { palette ->
        val night = variant.styleId == "night"
        HomeListeningAppearance(variant.styleId, palette,
            if (night) Color(0xFF0B192F) else Color(0xFFFDFBEA),
            if (night) Color(0xFFCEAA68) else Color(0xFFD7DEC9),
            if (night) R.drawable.ng_home_listening_night_texture else R.drawable.ng_home_listening_paper_texture)
    }

/** Restore listening's established colors without restyling the reading component. */
internal fun homeListeningSkinPalette(styleId: String): HomeListeningPalette? = when (styleId) {
    "storybook" -> HomeListeningPalette(Color(0xFF102D21), Color(0xFF727069), Color(0xFF28664F),
        Color.White, Color(0xFFFCFAEA), Color(0xFF52574E))
    "night" -> HomeListeningPalette(Color(0xFFFFF1D2), Color(0xFF9CB7DE), Color(0xFFF3B953),
        Color(0xFF12233C), Color(0xFF2B3750), Color(0xFFFFF0D9), Color(0xFFFFAFA0))
    else -> null
}

/** Every component consumes the same outer geometry and its family's native material. */
@Composable
internal fun HomeWidgetSurface(
    variant: HomeWidgetVariant,
    modifier: Modifier,
    minimumHeight: Dp,
    headerHeight: Dp,
    headerOffset: Dp,
    listening: Boolean = false,
    backdropVariant: HomeWidgetBackdropVariant = HomeWidgetBackdropVariant.STANDARD,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = if (listening) homeListeningSkinPalette(variant.styleId) else homeWidgetSkinPalette(variant.styleId)
    val surfaceModifier = modifier.heightIn(min = minimumHeight)
    if (palette == null) {
        NgGlassSurface(
            modifier = surfaceModifier,
            shape = RoundedCornerShape(HomeWidgetSkinCornerRadius),
            liquidCornerRadius = HomeWidgetSkinCornerRadius,
            style = NgGlassDefaults.neutralStyle(containerAlpha = NgTheme.effects.dialogAlpha),
            role = NgMaterialRole.CONTENT,
            contentPadding = PaddingValues(0.dp),
            content = content,
        )
    } else {
        val style = NgGlassDefaults.flatNeutralStyle(containerAlpha = 1f).copy(
            containerTop = Color.Transparent, containerBottom = Color.Transparent,
            accentGlow = Color.Transparent, borderColor = Color.Transparent,
            edgeHighlight = Color.Transparent, surfaceGloss = Color.Transparent,
            depthEdge = Color.Transparent, contentColor = palette.foreground,
            blurRadius = 0.dp, shadowElevation = 4.dp,
            borderWidth = 0.dp, highlightWidth = 0.dp,
        )
        NgVisualSurface(
            modifier = surfaceModifier, role = NgMaterialRole.CONTENT,
            cornerRadius = HomeWidgetSkinCornerRadius, style = style,
            visualSystemOverride = NgVisualSystem.TRANSPARENT_GLASS,
            contentPadding = PaddingValues(0.dp),
            transparentBackdrop = {
                if (listening) HomeListeningMaterialBackdrop(
                    variant.styleId, Modifier.matchParentSize(), headerHeight, headerOffset,
                    bottomInset = if (variant.size == HomeWidgetSize.SMALL) 8.dp else 16.dp,
                    small = variant.size == HomeWidgetSize.SMALL,
                ) else HomeWidgetSkinBackdrop(variant.styleId, headerHeight, headerOffset, Modifier.matchParentSize(),
                    bottomInset = if (variant.size == HomeWidgetSize.SMALL) 8.dp else 16.dp,
                    variant = backdropVariant,
                    small = variant.size == HomeWidgetSize.SMALL,
                    bookNewsCornerArtwork = backdropVariant == HomeWidgetBackdropVariant.BOOK_NEWS &&
                        variant.styleId == "storybook" && variant.size == HomeWidgetSize.LARGE)
            },
            content = content,
        )
    }
}
