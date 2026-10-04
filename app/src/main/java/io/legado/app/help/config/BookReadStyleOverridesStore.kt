package io.legado.app.help.config

import io.legado.app.data.entities.Book

/**
 * Phase 2d：本书稀疏覆盖写入层。
 *
 * Gate B（物化基准，修订版）：
 * - [materializePinnedBaseIfNeeded]：首次成功提交本书编辑时，把 legacy 整份拷贝转成显式 pinned
 *   basePreset；**legacy 保留为隐式全量基准**——在稀疏模型覆盖全部属性之前 legacy 不能退役，
 *   否则重启后 onlyThisBook=false、非字体属性（颜色/背景/间距）全部丢失。
 * - [materializeFollowGlobal]：用户选择跟随全局时不生成 pinned base，legacy 同样保留。
 * - 显式退役推迟到 Phase 5 归一化。
 *
 * Gate A（重置语义）：
 * - [resetAll] 同时清 independentOverrides 与 independentReadStyle，绝不等价于只清 sparse
 *   overrides（否则 legacy 会以隐式基准身份复活旧样式）。
 *
 * 写入粒度：
 * - 所有稀疏写入直接更新 independentOverrides，不经过 ReadBookConfig.config getter /
 *   durConfig setter，从结构上避免稀疏写入扩散为整份 shareConfig 覆写。
 */
internal class BookReadStyleOverridesStore(
    /** 事务性持久化：一次写入 overrides 与 legacy 两个字段。 */
    private val persist: (bookUrl: String, overrides: String?, legacy: String?) -> Unit,
) {
    fun current(owner: Book): BookReadStyleOverrides? =
        BookReadStyleCompatibility.overridesFromJson(owner.config.independentOverrides)

    fun legacy(owner: Book): ReadBookConfig.Config? =
        BookReadStyleSession.decode(owner.config.independentReadStyle)

    fun materializePinnedBaseIfNeeded(owner: Book): BookReadStyleOverrides? {
        current(owner)?.let { return it }
        val legacyConfig = legacy(owner) ?: return null
        val overrides = BookReadStyleOverrides(
            basePreset = BookBasePreset(
                mode = BookBasePreset.MODE_PINNED,
                snapshot = ReadPresetSnapshot(
                    scriptFonts = emptyMap(),
                    defaultFont = legacyConfig.textFont,
                ),
            ),
        )
        val json = BookReadStyleCompatibility.toJson(overrides)
        // Gate B（修订）：物化显式 pinned 基准，legacy 保留为隐式全量基准。
        // 在稀疏模型覆盖全部属性之前，legacy 不能退役——否则重启后 onlyThisBook=false、
        // 非字体属性（颜色/背景/间距）全部丢失。
        persist(owner.bookUrl, json, owner.config.independentReadStyle)
        owner.config.independentOverrides = json
        return overrides
    }

    fun materializeFollowGlobal(owner: Book): BookReadStyleOverrides {
        val overrides = BookReadStyleOverrides(
            basePreset = BookBasePreset(mode = BookBasePreset.MODE_FOLLOW_GLOBAL),
        )
        val json = BookReadStyleCompatibility.toJson(overrides)
        // follow_global 只表达「基准不 pin」，非字体属性仍需 legacy 承载，同样保留。
        persist(owner.bookUrl, json, owner.config.independentReadStyle)
        owner.config.independentOverrides = json
        return overrides
    }

    fun writeDefaultFont(owner: Book, value: String?): BookReadStyleOverrides? =
        writeScope(owner, ReadValueScope.DEFAULT, value)

    /** Phase 3：写单脚本维度的本书字体 override（DEFAULT/LATIN/CJK/OTHER）。 */
    fun writeScope(owner: Book, scope: ReadValueScope, value: String?): BookReadStyleOverrides? {
        materializePinnedBaseIfNeeded(owner)
        val existing = current(owner) ?: BookReadStyleOverrides()
        val newFont = (existing.font ?: SparseFontOverrides()).withScope(scope, value)
        val newOverrides = existing.copy(font = if (newFont.isEmpty()) null else newFont)
        val json = if (newOverrides.isEmpty()) null else BookReadStyleCompatibility.toJson(newOverrides)
        persist(owner.bookUrl, json, owner.config.independentReadStyle)
        owner.config.independentOverrides = json
        return newOverrides.takeUnless { json == null }
    }

    fun resetAll(owner: Book) {
        persist(owner.bookUrl, null, null)
        owner.config.independentOverrides = null
        owner.config.independentReadStyle = null
    }

    fun effectiveDefaultFont(
        owner: Book,
        globalFont: String,
        platformFont: String = "platform",
    ): ResolvedReadValue {
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.DEFAULT,
            overrides = current(owner),
            legacyConfig = legacy(owner),
            globalScriptFont = null,
            globalDefaultFont = globalFont,
            platformFont = platformFont,
        )
        return EffectiveReadValueResolverContract.resolve(context)
    }
}
