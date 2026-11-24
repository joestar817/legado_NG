package io.legado.app.help.config

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/** One background source shared by day and night, with optional custom values for each. */
@Keep
data class NgThemeDrawerProfile(
    @SerializedName("source") val source: String = DEFAULT_SOURCE,
    @SerializedName("light") val light: NgThemeDrawerStyle = NgThemeDrawerStyle(),
    @SerializedName("dark") val dark: NgThemeDrawerStyle = NgThemeDrawerStyle(),
) {
    fun forNight(isNight: Boolean): NgThemeDrawerStyle = if (isNight) dark else light

    fun updated(isNight: Boolean, style: NgThemeDrawerStyle): NgThemeDrawerProfile =
        if (isNight) copy(dark = style) else copy(light = style)

    fun normalized(): NgThemeDrawerProfile = copy(
        source = source.takeIf { it in SOURCES } ?: DEFAULT_SOURCE,
        light = (light as NgThemeDrawerStyle?)?.normalized() ?: NgThemeDrawerStyle(),
        dark = (dark as NgThemeDrawerStyle?)?.normalized() ?: NgThemeDrawerStyle(),
    )

    companion object {
        const val THEME_COLOR = "theme_color"
        const val LIQUID_GLASS = "liquid_glass"
        const val DEFAULT_SOURCE = LIQUID_GLASS
        private val SOURCES = setOf(THEME_COLOR, "custom_color", "theme_image", "custom_image", LIQUID_GLASS)
    }
}

@Keep
data class NgThemeDrawerStyle(
    @SerializedName("backgroundColor") val backgroundColor: Int? = null,
    @SerializedName("imagePath") val imagePath: String? = null,
) {
    fun normalized(): NgThemeDrawerStyle = copy(
        backgroundColor = backgroundColor?.let { it or (0xFF shl 24) },
        imagePath = imagePath?.trim()?.takeIf(String::isNotEmpty),
    )
}
