package io.legado.app.help.config

/** A configuration slot, independent of the reader's day/night/system-following policy. */
internal enum class ReadAppearanceMode {
    DAY, NIGHT, EINK;

    companion object {
        fun fromIndex(index: Int): ReadAppearanceMode = entries.getOrElse(index) { DAY }

        fun current(): ReadAppearanceMode = when {
            AppConfig.isEInkMode -> EINK
            ReadBookConfig.isNightTheme -> NIGHT
            else -> DAY
        }
    }
}
