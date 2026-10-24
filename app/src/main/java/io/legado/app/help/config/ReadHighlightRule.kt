package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** 独立于阅读预设的全局文本高亮规则。 */
@Keep
data class ReadHighlightRule(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("pattern") val pattern: String = "",
    @SerializedName("sampleText") val sampleText: String = "",
    @SerializedName("targetScope") val targetScope: Int = TARGET_ALL,
    @SerializedName("matchAcrossParagraphs") val matchAcrossParagraphs: Boolean = false,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("position") val position: Int = 0,
    @SerializedName("textColor") val textColor: Int? = null,
    @SerializedName("bgColor") val bgColor: Int? = null,
    @SerializedName("underlineMode") val underlineMode: Int = 0,
    @SerializedName("underlineColor") val underlineColor: Int? = null,
    @SerializedName("underlineWidth") val underlineWidth: Float = 1f,
    @SerializedName("underlineOffset") val underlineOffset: Float = 2f,
    @SerializedName("underlineSvgPath") val underlineSvgPath: String? = null,
    @SerializedName("bgImage") val bgImage: String? = null,
    @SerializedName("bgImageFit") val bgImageFit: Int = 0,
    @SerializedName("bgImageScale") val bgImageScale: Float = 1f,
    @SerializedName("fontPath") val fontPath: String? = null,
    @SerializedName("fontWeight") val fontWeight: Int = 400,
    @SerializedName("isItalic") val isItalic: Boolean = false,
    @SerializedName("npLeft") val npLeft: Float = 0.1f,
    @SerializedName("npRight") val npRight: Float = 0.1f,
    @SerializedName("npTop") val npTop: Float = 0.1f,
    @SerializedName("npBottom") val npBottom: Float = 0.1f,
    @SerializedName("textColorNight") val textColorNight: Int? = null,
    @SerializedName("bgColorNight") val bgColorNight: Int? = null,
    @SerializedName("underlineColorNight") val underlineColorNight: Int? = null,
    // null preserves the historical per-color fallback; new single-palette rules follow day.
    @SerializedName("nightFollowDay") val nightFollowDay: Boolean? = null,
    @SerializedName("textColorNightMode") val textColorNightMode: Int? = null,
    @SerializedName("bgColorNightMode") val bgColorNightMode: Int? = null,
    @SerializedName("underlineColorNightMode") val underlineColorNightMode: Int? = null,
) {

    fun normalized(): ReadHighlightRule = copy(
        targetScope = targetScope.takeIf { it in TARGET_ALL..TARGET_BODY } ?: TARGET_ALL,
        position = position.coerceAtLeast(0),
        underlineMode = underlineMode.coerceIn(0, 5),
        underlineWidth = underlineWidth.coerceIn(0.1f, 10f),
        underlineOffset = underlineOffset.coerceIn(0f, 20f),
        bgImage = bgImage?.trim()?.takeIf(String::isNotEmpty),
        bgImageFit = bgImageFit.coerceIn(0, 3),
        bgImageScale = bgImageScale.coerceIn(0.1f, 5f),
        fontPath = fontPath?.trim()?.takeIf(String::isNotEmpty),
        fontWeight = fontWeight.coerceIn(100, 900),
        npLeft = npLeft.coerceIn(0f, 0.5f),
        npRight = npRight.coerceIn(0f, 0.5f),
        npTop = npTop.coerceIn(0f, 0.5f),
        npBottom = npBottom.coerceIn(0f, 0.5f),
        textColorNightMode = normalizedNightMode(textColorNightMode, textColorNight),
        bgColorNightMode = normalizedNightMode(bgColorNightMode, bgColorNight),
        underlineColorNightMode = normalizedNightMode(underlineColorNightMode, underlineColorNight),
    )

    fun followsDayAtNight(): Boolean = nightFollowDay ?: (COLOR_TEXT..COLOR_UNDERLINE).all {
        nightColorMode(it) == NIGHT_FOLLOW_DAY
    }

    fun hasInvalidNightColorModes(): Boolean =
        normalizedNightMode(textColorNightMode, textColorNight) != textColorNightMode ||
            normalizedNightMode(bgColorNightMode, bgColorNight) != bgColorNightMode ||
            normalizedNightMode(underlineColorNightMode, underlineColorNight) != underlineColorNightMode

    fun nightColorMode(color: Int): Int {
        val mode = when (color) {
            COLOR_BACKGROUND -> bgColorNightMode
            COLOR_UNDERLINE -> underlineColorNightMode
            else -> textColorNightMode
        }
        return normalizedNightMode(mode, nightColorValue(color))
            ?: if (nightColorValue(color) != null) NIGHT_CUSTOM else NIGHT_FOLLOW_DAY
    }

    fun nightColorValue(color: Int): Int? = when (color) {
        COLOR_BACKGROUND -> bgColorNight
        COLOR_UNDERLINE -> underlineColorNight
        else -> textColorNight
    }

    fun colorValue(color: Int, isNight: Boolean): Int? {
        val day = when (color) {
            COLOR_BACKGROUND -> bgColor
            COLOR_UNDERLINE -> underlineColor
            else -> textColor
        }
        if (!isNight || followsDayAtNight()) return day
        return when (nightColorMode(color)) {
            NIGHT_CUSTOM -> nightColorValue(color)
            NIGHT_DEFAULT -> null
            else -> day
        }
    }

    fun colors(isNight: Boolean) = ReadHighlightColors(
        textColor = colorValue(COLOR_TEXT, isNight),
        bgColor = colorValue(COLOR_BACKGROUND, isNight),
        underlineColor = colorValue(COLOR_UNDERLINE, isNight),
    )

    fun withNightColorMode(color: Int, mode: Int, customColor: Int? = null): ReadHighlightRule = when (color) {
        COLOR_BACKGROUND -> copy(bgColorNightMode = mode, bgColorNight = customColor ?: bgColorNight)
        COLOR_UNDERLINE -> copy(underlineColorNightMode = mode, underlineColorNight = customColor ?: underlineColorNight)
        else -> copy(textColorNightMode = mode, textColorNight = customColor ?: textColorNight)
    }

    fun withColor(color: Int, value: Int?, isNight: Boolean): ReadHighlightRule = if (isNight) {
        withNightColorMode(color, if (value == null) NIGHT_DEFAULT else NIGHT_CUSTOM, value)
            .copy(nightFollowDay = false)
    } else when (color) {
        COLOR_BACKGROUND -> copy(bgColor = value)
        COLOR_UNDERLINE -> copy(underlineColor = value)
        else -> copy(textColor = value)
    }

    internal fun withNightFollowDay(followDay: Boolean): ReadHighlightRule {
        if (followDay) return copy(nightFollowDay = true)
        var rule = copy(nightFollowDay = false)
        for (color in COLOR_TEXT..COLOR_UNDERLINE) {
            // An explicitly disabled color is already a configured night value.
            if (nightColorMode(color) == NIGHT_DEFAULT) continue
            rule = rule.withColor(color, nightColorValue(color) ?: colorValue(color, false), true)
        }
        return rule
    }

    fun appliesToTitle(isTitle: Boolean): Boolean = when (targetScope) {
        TARGET_TITLE -> isTitle
        TARGET_BODY -> !isTitle
        else -> true
    }

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2

        const val COLOR_TEXT = 0
        const val COLOR_BACKGROUND = 1
        const val COLOR_UNDERLINE = 2
        const val NIGHT_FOLLOW_DAY = 0
        const val NIGHT_CUSTOM = 1
        const val NIGHT_DEFAULT = 2

        private fun normalizedNightMode(mode: Int?, value: Int?): Int? = when {
            mode == null -> null
            mode !in NIGHT_FOLLOW_DAY..NIGHT_DEFAULT -> NIGHT_FOLLOW_DAY
            mode == NIGHT_CUSTOM && value == null -> NIGHT_FOLLOW_DAY
            else -> mode
        }
    }
}

/** Resolved runtime colors; inheritance is owned by ReadHighlightRule, not by the renderers. */
data class ReadHighlightColors(val textColor: Int?, val bgColor: Int?, val underlineColor: Int?)
