package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadHighlightColors

/** MD3 排版高亮规则命中后，附着在文字列上的运行时样式。 */
data class ReadCharStyle(
    val textColor: Int? = null,
    val bgColor: Int? = null,
    val underlineMode: Int = 0,
    val underlineColor: Int? = null,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
    val fontPath: String = "",
    val fontWeight: Int = 400,
    val isItalic: Boolean = false,
    val npLeft: Float = 0.1f,
    val npRight: Float = 0.1f,
    val npTop: Float = 0.1f,
    val npBottom: Float = 0.1f,
    val nightColors: ReadHighlightColors? = null,
) {
    // Resolve paint identities once per matched rule, never allocate per glyph/frame.
    // Dormant night colors must not split an otherwise identical daytime underline.
    private val dayStyle: ReadCharStyle? = if (nightColors == null) null else copy(nightColors = null)
    private val nightStyle: ReadCharStyle? = nightColors?.let {
        copy(textColor = it.textColor, bgColor = it.bgColor, underlineColor = it.underlineColor, nightColors = null)
    }

    fun forTheme(isNight: Boolean): ReadCharStyle =
        if (isNight) nightStyle ?: this else dayStyle ?: this

    fun resolveTextColor(isNight: Boolean): Int? =
        if (isNight && nightColors != null) nightColors.textColor else textColor

    fun resolveBackgroundColor(isNight: Boolean): Int? =
        if (isNight && nightColors != null) nightColors.bgColor else bgColor

    fun resolveUnderlineColor(isNight: Boolean): Int? =
        if (isNight && nightColors != null) nightColors.underlineColor else underlineColor
}
