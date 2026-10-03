package io.legado.app.help.config

import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * Phase 3：`global.typography.scripts` 运行时存储（先字体，其余排版属性 Phase 4）。
 *
 * - 形状复用 [SparseFontOverrides]（default/latin/cjk/other），与 book 级 override 同构；
 * - 持久化到 pref `readScriptTypography`（JSON），空模型写 null（与备份策略兼容）；
 * - 渲染接线随 TXT/EPUB 生产化挂接（Phase 3 后续步骤），本文件只负责模型读写。
 */
object ReadScriptTypographyStore {
    const val PREF_KEY = "readScriptTypography"

    fun load(): SparseFontOverrides = runCatching {
        appCtx.getPrefString(PREF_KEY)?.let { json ->
            GSON.fromJson(json, SparseFontOverrides::class.java)
        }
    }.getOrNull() ?: SparseFontOverrides()

    fun save(value: SparseFontOverrides) {
        appCtx.putPrefString(PREF_KEY, if (value.isEmpty()) null else GSON.toJson(value))
    }

    fun font(scope: ReadValueScope): String? = load().forScope(scope)

    fun setFont(scope: ReadValueScope, value: String?) {
        save(load().withScope(scope, value))
    }

    fun clear() = appCtx.putPrefString(PREF_KEY, null)
}
