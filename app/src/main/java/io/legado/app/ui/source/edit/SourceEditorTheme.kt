package io.legado.app.ui.source.edit

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgCodePalette
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgThemeSnapshot

internal enum class SourceEditorTheme(
    val key: String,
    @StringRes val label: Int,
    val snapshot: NgThemeSnapshot,
    val syntax: NgCodePalette?,
) {
    WHITE("white", R.string.text_default,
        editorColors(background = 0xFFF5F5F5, panel = 0xFFFFFFFF, accent = 0xFF008577,
            text = 0xFF1D1B20, muted = 0xFF585960, outline = 0xFF919196, dark = false), null),
    ONE_DARK("one_dark", R.string.source_editor_one_dark,
        editorColors(background = 0xFF282C34, panel = 0xFF303641, accent = 0xFF61AFEF,
            text = 0xFFCED4DF, muted = 0xFFABB2BF, outline = 0xFF59616E, dark = true),
        NgCodePalette(legado = Color(0xFF56B6C2), comment = Color(0xFF929BAA), string = Color(0xFF98C379),
            number = Color(0xFFD19A66), keyword = Color(0xFFC678DD), key = Color(0xFFE06C75), tag = Color(0xFF61AFEF))),
    VS_CODE_DARK_PLUS("vscode_dark_plus", R.string.source_editor_vscode_dark_plus,
        editorColors(background = 0xFF1E1E1E, panel = 0xFF252526, accent = 0xFF75BEFF,
            text = 0xFFD4D4D4, muted = 0xFFB0B0B0, outline = 0xFF585858, dark = true),
        NgCodePalette(legado = Color(0xFF4EC9B0), comment = Color(0xFF6A9955), string = Color(0xFFCE9178),
            number = Color(0xFFB5CEA8), keyword = Color(0xFF569CD6), key = Color(0xFF9CDCFE), tag = Color(0xFFDCDCAA))),
    SOLARIZED_LIGHT("solarized_light", R.string.source_editor_solarized_light,
        editorColors(background = 0xFFFDF6E3, panel = 0xFFEEE8D5, accent = 0xFF176C9E,
            text = 0xFF586E75, muted = 0xFF657B83, outline = 0xFF93A1A1, dark = false),
        NgCodePalette(legado = Color(0xFF6C71C4), comment = Color(0xFF657B83), string = Color(0xFF2A827A),
            number = Color(0xFFB24F12), keyword = Color(0xFF687D00), key = Color(0xFF176C9E), tag = Color(0xFFCB4B16))),
}

private fun editorColors(
    background: Long, panel: Long, accent: Long, text: Long, muted: Long, outline: Long, dark: Boolean,
): NgThemeSnapshot {
    val base = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = background.toInt(), accentColor = accent.toInt(), backgroundColor = background.toInt(),
        bottomBackground = panel.toInt(), errorColor = if (dark) 0xFFE06C75.toInt() else 0xFFBA1A1A.toInt(),
        isDark = dark, isEInk = false,
    ))
    return base.copy(colors = base.colors.copy(
        onSurface = text.toInt(), onBackground = text.toInt(), onTopBar = text.toInt(),
        onSurfaceVariant = muted.toInt(), outline = outline.toInt(), cardContainer = panel.toInt(),
        drawerContainer = background.toInt(), outlineVariant = outline.toInt(),
    ))
}
