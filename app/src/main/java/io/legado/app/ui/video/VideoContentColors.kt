package io.legado.app.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class VideoContentColors(
    val surface: Color,
    val cell: Color,
    val content: Color,
    val muted: Color,
    val accent: Color,
    val selectedContainer: Color,
    val onAccent: Color,
)

/** Neutral video surfaces follow the app's mode without inheriting a tinted theme background. */
@Composable
internal fun videoContentColors(): VideoContentColors {
    val theme = NgTheme.snapshot
    return remember(theme) {
        VideoContentColors(
            surface = if (theme.isDark) Color(0xFF111315) else Color.White,
            cell = if (theme.isDark) Color(0xFF1C1F23) else Color(0xFFF6F6F6),
            content = if (theme.isDark) Color(0xFFF4F4F5) else Color(0xFF1D1D1F),
            muted = if (theme.isDark) Color(0xFFADB1BC) else Color(0xFF6E6E73),
            accent = Color(theme.colors.primary),
            selectedContainer = Color(videoSelectedContainer(theme.colors.primary)),
            onAccent = Color.White,
        )
    }
}

/** Only filled selections are darkened, so white labels and checks remain readable. */
private fun videoSelectedContainer(primary: Int): Int {
    val background = NgColorMath.opaque(primary)
    val white = android.graphics.Color.WHITE
    if (NgColorMath.contrastRatio(background, white) >= 4.5) return background
    var low = 0f
    var high = 1f
    repeat(12) {
        val amount = (low + high) / 2
        val candidate = NgColorMath.blend(background, android.graphics.Color.BLACK, amount)
        if (NgColorMath.contrastRatio(candidate, white) >= 4.5) high = amount else low = amount
    }
    return NgColorMath.blend(background, android.graphics.Color.BLACK, high)
}
