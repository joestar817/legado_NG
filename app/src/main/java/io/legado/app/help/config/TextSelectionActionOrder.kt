package io.legado.app.help.config

import io.legado.app.R
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/** 稳定功能标识用于偏好存储，资源 ID 仅用于当前版本菜单匹配。 */
enum class TextSelectionBuiltInAction(val key: String, val menuId: Int, val titleRes: Int) {
    REPLACE("replace", R.id.menu_replace, R.string.replace),
    AI_PURIFY("ai_purify", R.id.menu_ai_purify, R.string.ai_purify),
    COPY("copy", R.id.menu_copy, android.R.string.copy),
    HIGHLIGHT("highlight", R.id.menu_bookmark, R.string.text_highlight),
    SEARCH("search", R.id.menu_search_content, R.string.search),
    READ_ALOUD("read_aloud", R.id.menu_aloud, R.string.read_aloud),
    DICTIONARY("dictionary", R.id.menu_dict, R.string.dict),
    BROWSER("browser", R.id.menu_browser, R.string.browser),
    SHARE("share", R.id.menu_share_str, R.string.share),
    EDIT("edit", R.id.menu_edit_content, R.string.edit),
}

object TextSelectionActionOrder {
    private const val PREF_KEY = "textSelectionActionOrder"
    private const val DISABLED_KEY = "textSelectionDisabledActions"

    fun disabledKeys(): Set<String> = appCtx.getPrefString(DISABLED_KEY).orEmpty()
        .split(',').filter { it.isNotEmpty() }.toSet()

    fun load(): List<TextSelectionBuiltInAction> {
        val keys = appCtx.getPrefString(PREF_KEY).orEmpty().split(',')
        val saved = keys.mapNotNull { key -> TextSelectionBuiltInAction.entries.find { it.key == key } }
        return (saved + TextSelectionBuiltInAction.entries).distinct()
    }

    fun save(actions: List<TextSelectionBuiltInAction>, disabledKeys: Set<String>) {
        appCtx.putPrefString(PREF_KEY, actions.joinToString(",") { it.key })
        appCtx.putPrefString(DISABLED_KEY, disabledKeys.joinToString(","))
    }
}
