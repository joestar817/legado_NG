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
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("position") val position: Int = 0,
    @SerializedName("textColor") val textColor: Int? = null,
    @SerializedName("textColorNight") val textColorNight: Int? = null,
    @SerializedName("bgColor") val bgColor: Int? = null,
    @SerializedName("bgColorNight") val bgColorNight: Int? = null,
    @SerializedName("underlineMode") val underlineMode: Int = 0,
    @SerializedName("underlineColor") val underlineColor: Int? = null,
    @SerializedName("underlineColorNight") val underlineColorNight: Int? = null,
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
    )

    fun appliesToTitle(isTitle: Boolean): Boolean = when (targetScope) {
        TARGET_TITLE -> isTitle
        TARGET_BODY -> !isTitle
        else -> true
    }

    fun resolveTextColor(isNight: Boolean): Int? =
        if (isNight) textColorNight ?: textColor else textColor

    fun resolveBackgroundColor(isNight: Boolean): Int? =
        if (isNight) bgColorNight ?: bgColor else bgColor

    fun resolveUnderlineColor(isNight: Boolean): Int? =
        if (isNight) underlineColorNight ?: underlineColor else underlineColor

    companion object {
        const val TARGET_ALL = 0
        const val TARGET_TITLE = 1
        const val TARGET_BODY = 2
    }
}
