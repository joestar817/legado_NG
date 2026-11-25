package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.ui.book.read.page.provider.ReadCharStyle

internal enum class ReadCharacterMarkMode(val key: String) {
    UNDERLINE("underline"),
    BOLD("bold"),
    OFF("off"),
}

internal data class ReadCharacterMarkSettings(
    val mode: ReadCharacterMarkMode = ReadCharacterMarkMode.UNDERLINE,
    val appearance: ReadHighlightRule = defaultAppearance,
    val tapToOpenPortrait: Boolean = true,
) {
    val opensPortrait get() = mode != ReadCharacterMarkMode.OFF && tapToOpenPortrait

    val textStyle: ReadCharStyle? = if (mode == ReadCharacterMarkMode.BOLD) {
        val day = appearance.colors(isNight = false)
        val night = appearance.colors(isNight = true)
        ReadCharStyle(
            textColor = day.textColor,
            bgColor = day.bgColor,
            fontWeight = appearance.fontWeight,
            isItalic = appearance.isItalic,
            nightColors = night.takeUnless { it.textColor == day.textColor && it.bgColor == day.bgColor }
                ?.copy(underlineColor = null),
            explicitFontWeight = true,
        )
    } else null

    val underlineStyle: ReadCharStyle? = if (mode == ReadCharacterMarkMode.UNDERLINE) {
        val day = appearance.colorValue(ReadHighlightRule.COLOR_UNDERLINE, isNight = false)
        val night = appearance.colorValue(ReadHighlightRule.COLOR_UNDERLINE, isNight = true)
        ReadCharStyle(
            underlineMode = appearance.underlineMode,
            underlineColor = day,
            underlineWidth = appearance.underlineWidth,
            underlineOffset = appearance.underlineOffset,
            nightColors = if (night != day) ReadHighlightColors(null, null, night) else null,
        )
    } else null

    fun save(context: Context) {
        preferences(context).edit {
            putString("mode", mode.key)
            putInt("underlineMode", appearance.underlineMode)
            putFloat("underlineWidth", appearance.underlineWidth)
            putFloat("underlineOffset", appearance.underlineOffset)
            putInt("fontWeight", appearance.fontWeight)
            putBoolean("isItalic", appearance.isItalic)
            putBoolean("nightFollowDay", appearance.followsDayAtNight())
            setOptionalInt("textColor", appearance.textColor)
            setOptionalInt("bgColor", appearance.bgColor)
            setOptionalInt("underlineColor", appearance.underlineColor)
            setOptionalInt("textColorNight", appearance.textColorNight)
            setOptionalInt("bgColorNight", appearance.bgColorNight)
            setOptionalInt("underlineColorNight", appearance.underlineColorNight)
            setOptionalInt("textColorNightMode", appearance.textColorNightMode)
            setOptionalInt("bgColorNightMode", appearance.bgColorNightMode)
            setOptionalInt("underlineColorNightMode", appearance.underlineColorNightMode)
            putBoolean("tapToOpenPortrait", tapToOpenPortrait)
        }
    }

    companion object {
        private val defaultAppearance = ReadHighlightRule(
            underlineMode = 1,
            underlineWidth = 2f,
            underlineOffset = 0f,
            fontWeight = 700,
            nightFollowDay = true,
        )

        fun load(context: Context): ReadCharacterMarkSettings {
            val prefs = preferences(context)
            val defaults = defaultAppearance
            val mode = prefs.getString("mode", null)
            return ReadCharacterMarkSettings(
                mode = ReadCharacterMarkMode.entries.firstOrNull { it.key == mode }
                    ?: ReadCharacterMarkMode.UNDERLINE,
                appearance = defaults.copy(
                    underlineMode = prefs.getInt("underlineMode", defaults.underlineMode).coerceIn(1, 4),
                    underlineWidth = prefs.getFloat("underlineWidth", defaults.underlineWidth)
                        .takeIf(Float::isFinite) ?: defaults.underlineWidth,
                    underlineOffset = prefs.getFloat("underlineOffset", defaults.underlineOffset)
                        .takeIf(Float::isFinite) ?: defaults.underlineOffset,
                    fontWeight = prefs.getInt("fontWeight", defaults.fontWeight),
                    isItalic = prefs.getBoolean("isItalic", defaults.isItalic),
                    nightFollowDay = prefs.getBoolean("nightFollowDay", true),
                    textColor = prefs.optionalInt("textColor"),
                    bgColor = prefs.optionalInt("bgColor"),
                    underlineColor = prefs.optionalInt("underlineColor"),
                    textColorNight = prefs.optionalInt("textColorNight"),
                    bgColorNight = prefs.optionalInt("bgColorNight"),
                    underlineColorNight = prefs.optionalInt("underlineColorNight"),
                    textColorNightMode = prefs.optionalInt("textColorNightMode"),
                    bgColorNightMode = prefs.optionalInt("bgColorNightMode"),
                    underlineColorNightMode = prefs.optionalInt("underlineColorNightMode"),
                ).normalized(),
                tapToOpenPortrait = prefs.getBoolean("tapToOpenPortrait", true),
            )
        }

        private fun SharedPreferences.optionalInt(key: String) =
            if (contains(key)) getInt(key, 0) else null

        private fun SharedPreferences.Editor.setOptionalInt(key: String, value: Int?) {
            if (value == null) remove(key) else putInt(key, value)
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences("read-character-marks", Context.MODE_PRIVATE)
    }
}
