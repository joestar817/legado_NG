package io.legado.app.ui.main.home

import androidx.compose.runtime.Immutable

@Immutable
internal data class HomeReadingAppearance(
    val styleId: String,
    val palette: HomeListeningPalette,
)

/** Skin families share one palette; content and geometry are owned by the widget. */
internal fun homeReadingAppearance(variant: HomeWidgetVariant): HomeReadingAppearance? =
    homeWidgetSkinPalette(variant.styleId)?.let { HomeReadingAppearance(variant.styleId, it) }
