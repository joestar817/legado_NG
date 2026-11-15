package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDrawerThemeProvider
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgThemeSnapshot

/** Match the card surface to the reader's own day/night palette. */
internal fun readingContentCardSnapshot(
    snapshot: NgThemeSnapshot,
    primaryStrengthPercent: Int,
): NgThemeSnapshot {
    val cardColor = NgDrawerPalette.resolveAdaptiveContentCardColor(
        snapshot = snapshot,
        primaryStrengthPercent = primaryStrengthPercent,
    )
    return snapshot.copy(colors = snapshot.colors.copy(cardContainer = cardColor))
}

@Composable
internal fun ReadDrawerContentTheme(
    snapshot: NgThemeSnapshot,
    content: @Composable () -> Unit,
) {
    val strength = ReadFloatingAppearanceState.primaryStrengthPercent
    val cardSnapshot = remember(snapshot, strength) {
        readingContentCardSnapshot(snapshot, strength)
    }
    NgDrawerThemeProvider(
        snapshot = cardSnapshot,
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        hasThemeProfile = false,
        content = content,
    )
}
