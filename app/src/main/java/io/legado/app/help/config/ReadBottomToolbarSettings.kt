package io.legado.app.help.config

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

internal enum class ReadBottomTool(
    val key: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    CATALOG("catalog", R.string.chapter_list, R.drawable.ic_manga_menu_catalog),
    READ_ALOUD("read_aloud", R.string.read_aloud, R.drawable.ic_read_menu_aloud),
    INTERFACE("interface", R.string.interface_setting, R.drawable.ic_read_menu_interface),
    MORE("more", R.string.more, R.drawable.ic_manga_menu_settings),
    PAGE_TURNING("page_turning", R.string.read_settings_tab_page, R.drawable.ic_read_menu_page_turning),
    SCROLL("scroll", R.string.read_menu_scroll, R.drawable.ic_auto_page),
    CONTENT("content", R.string.content, R.drawable.ic_read_menu_content),
    FLOATING("floating", R.string.read_menu_floating, R.drawable.ic_read_menu_floating),
}

internal object ReadBottomToolbarSettings {
    val configurableTools = ReadBottomTool.entries.filterNot { it == ReadBottomTool.MORE }

    fun hiddenTools(context: Context): Set<ReadBottomTool> {
        val keys = context.getPrefString(PreferKey.readBottomToolbarHidden).orEmpty().split(',')
        return configurableTools.filter { it.key in keys }.toSet()
    }

    fun saveHiddenTools(context: Context, tools: Set<ReadBottomTool>) {
        val keys = configurableTools.filter { it in tools }.joinToString(",") { it.key }
        context.putPrefString(PreferKey.readBottomToolbarHidden, keys)
    }

    fun order(context: Context): List<ReadBottomTool> {
        val saved = context.getPrefString(PreferKey.readBottomToolbarOrder).orEmpty().split(',')
            .mapNotNull { key -> ReadBottomTool.entries.find { it.key == key } }
        return (saved + ReadBottomTool.entries).distinct()
    }

    fun saveOrder(context: Context, tools: List<ReadBottomTool>) {
        context.putPrefString(PreferKey.readBottomToolbarOrder, tools.joinToString(",") { it.key })
    }

    fun visibleRows(
        hidden: Set<ReadBottomTool>,
        order: List<ReadBottomTool> = ReadBottomTool.entries,
    ): List<List<ReadBottomTool>> = order
        .filter { it == ReadBottomTool.MORE || it !in hidden }
        .chunked(4)

    fun shortcuts(hidden: Set<ReadBottomTool>): List<ReadBottomTool> =
        configurableTools.filter { it in hidden && it != ReadBottomTool.SCROLL }
}
