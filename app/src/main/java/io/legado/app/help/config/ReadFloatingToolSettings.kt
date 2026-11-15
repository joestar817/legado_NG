package io.legado.app.help.config

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/** Stable keys identify the tools independently of their position in the rail. */
internal enum class ReadFloatingTool(
    val key: String,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val railDescriptionRes: Int = titleRes,
    val enabledByDefault: Boolean = true,
    val iconSizeDp: Int = 22,
) {
    BRIGHTNESS("brightness", R.drawable.ic_daytime, R.string.brightness),
    SEARCH("search", R.drawable.ic_search, R.string.search_content),
    REPLACE("replace", R.drawable.ic_cfg_replace, R.string.replace_rule_title),
    THEME("theme", R.drawable.ic_brightness, R.string.read_floating_side_tool_theme, R.string.dark_theme),
    AI("ai", R.drawable.ic_ai, R.string.ai_actions, iconSizeDp = 18),
    READ_ALOUD("read_aloud", R.drawable.ic_tts_headphones, R.string.read_aloud, enabledByDefault = false, iconSizeDp = 20),
    AUTO_SCROLL(
        "auto_scroll", R.drawable.ic_auto_page, R.string.read_menu_scroll,
        railDescriptionRes = R.string.auto_next_page, enabledByDefault = false, iconSizeDp = 18,
    ),
    PAGE_ANIM("page_anim", R.drawable.ic_manga_menu_reading, R.string.page_anim, enabledByDefault = false, iconSizeDp = 18),
    CHINESE("chinese", R.drawable.ic_translate, R.string.chinese_converter, enabledByDefault = false),
}

internal object ReadFloatingToolSettings {
    fun order(): List<ReadFloatingTool> {
        val saved = appCtx.getPrefString(PreferKey.readFloatingToolOrder).orEmpty().split(',')
            .mapNotNull { key -> ReadFloatingTool.entries.find { it.key == key } }
        return (saved + ReadFloatingTool.entries).distinct()
    }

    fun disabledKeys(): Set<String> {
        val disabled = appCtx.getPrefString(PreferKey.readFloatingToolDisabled).orEmpty()
            .split(',').filter { it.isNotEmpty() }.toSet()
        // Default-off tools require an explicit opt-in, independently of the original disabled list.
        val optedIn = appCtx.getPrefString(PreferKey.readFloatingToolOptIn).orEmpty()
            .split(',').filter { it.isNotEmpty() }.toSet()
        return disabled + ReadFloatingTool.entries.filter { !it.enabledByDefault && it.key !in optedIn }.map { it.key }
    }

    fun visibleTools(): List<ReadFloatingTool> {
        val disabled = disabledKeys()
        return order().filterNot { it.key in disabled }
    }

    fun saveOrder(tools: List<ReadFloatingTool>) {
        appCtx.putPrefString(PreferKey.readFloatingToolOrder, tools.joinToString(",") { it.key })
    }

    fun setDisabledKeys(keys: Set<String>) {
        val optionalKeys = ReadFloatingTool.entries.filterNot { it.enabledByDefault }.map { it.key }.toSet()
        val optedIn = optionalKeys - keys
        appCtx.defaultSharedPreferences.edit()
            .putString(PreferKey.readFloatingToolDisabled, (keys - optionalKeys).joinToString(","))
            .putString(PreferKey.readFloatingToolOptIn, optedIn.joinToString(","))
            .apply()
    }
}
