package io.legado.app.help.config

import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON

/**
 * Phase 2b：本书稀疏覆盖数据模型 + LegacyBookStyle 兼容层。
 *
 * - 新模型：[BookReadStyleOverrides]（JSON 存于 Book.ReadConfig.independentOverrides），只存被改动的字段。
 * - 旧模型：Book.ReadConfig.independentReadStyle 整份 Config，先作为不可变 pinned 快照基准参与解析（不 diff）。
 * - 本文件只提供模型与解析上下文构造；热路径接线随 Phase 2d。
 */

data class BookReadStyleOverrides(
    @SerializedName("basePreset")
    val basePreset: BookBasePreset? = null,
    @SerializedName("font")
    val font: SparseFontOverrides? = null,
) {
    fun isEmpty(): Boolean = basePreset == null && (font?.isEmpty() != false)
}

data class BookBasePreset(
    @SerializedName("mode")
    val mode: String,
    @SerializedName("snapshot")
    val snapshot: ReadPresetSnapshot? = null,
) {
    companion object {
        const val MODE_PINNED = "pinned"
        const val MODE_FOLLOW_GLOBAL = "follow_global"
    }

    fun toReadBasePreset(): ReadBasePreset? = when (mode) {
        MODE_PINNED -> ReadBasePreset(
            mode = ReadBasePresetMode.PINNED,
            snapshot = snapshot ?: ReadPresetSnapshot(),
        )

        MODE_FOLLOW_GLOBAL -> ReadBasePreset(mode = ReadBasePresetMode.FOLLOW_GLOBAL)
        else -> null
    }
}

data class SparseFontOverrides(
    @SerializedName("default")
    val default: String? = null,
    @SerializedName("latin")
    val latin: String? = null,
    @SerializedName("cjk")
    val cjk: String? = null,
    @SerializedName("other")
    val other: String? = null,
) {
    fun isEmpty(): Boolean = default == null && latin == null && cjk == null && other == null

    fun forScope(scope: ReadValueScope): String? = when (scope) {
        ReadValueScope.DEFAULT -> default
        ReadValueScope.LATIN -> latin
        ReadValueScope.CJK -> cjk
        ReadValueScope.OTHER -> other
    }

    fun withScope(scope: ReadValueScope, value: String?): SparseFontOverrides = when (scope) {
        ReadValueScope.DEFAULT -> copy(default = value)
        ReadValueScope.LATIN -> copy(latin = value)
        ReadValueScope.CJK -> copy(cjk = value)
        ReadValueScope.OTHER -> copy(other = value)
    }

    /** 转成 ReadPresetSnapshot.scriptFonts 用的稀疏 map（只含非空维度）。 */
    fun toScriptFontMap(): Map<ReadValueScope, String> = buildMap {
        default?.let { put(ReadValueScope.DEFAULT, it) }
        latin?.let { put(ReadValueScope.LATIN, it) }
        cjk?.let { put(ReadValueScope.CJK, it) }
        other?.let { put(ReadValueScope.OTHER, it) }
    }
}

object BookReadStyleCompatibility {

    fun overridesFromJson(json: String?): BookReadStyleOverrides? = json?.takeIf { it.isNotBlank() }?.let {
        runCatching { GSON.fromJson(it, BookReadStyleOverrides::class.java) }.getOrNull()
    }

    fun toJson(overrides: BookReadStyleOverrides): String = GSON.toJson(overrides)

    /**
     * 构造解析上下文。
     *
     * 叠加顺序（§3.7-1）：稀疏覆盖 → 显式 basePreset → [legacyConfig] 隐式 pinned 基准 → 全局。
     * 旧整份拷贝不 diff，语义等价于 pinned 快照基准 + 空 overrides；
     * 仅当新模型写入了显式 basePreset 时才被取代（首次本书编辑应物化/选择基准）。
     * 注意：[legacyConfig] 的 textFont 为空串时保留为空串值（= 本书显式用系统字体），
     * 不可当作缺失回落全局——否则旧副本的行为会发生静默漂移。
     *
     * @param overrides 新稀疏覆盖（优先）；为 null 时回落到 [legacyConfig]（LegacyBookStyle 兼容层）。
     * @param legacyConfig 旧整份拷贝；作为隐式 pinned 快照基准参与解析。
     */
    fun contextFor(
        scope: ReadValueScope,
        overrides: BookReadStyleOverrides?,
        legacyConfig: ReadBookConfig.Config?,
        globalScriptFont: String?,
        globalDefaultFont: String?,
        platformFont: String = "platform",
        epub: ReadEpubContext? = null,
        presetScriptFont: String? = null,
    ): ReadValueContext {
        if (overrides != null || legacyConfig != null) {
            return ReadValueContext(
                scope = scope,
                bookScriptFont = overrides?.font?.forScope(scope),
                bookDefaultFont = overrides?.font?.default,
                basePreset = overrides?.basePreset?.toReadBasePreset()
                    ?: legacyConfig?.let { legacy ->
                        ReadBasePreset(
                            mode = ReadBasePresetMode.PINNED,
                            snapshot = ReadPresetSnapshot(
                                scriptFonts = legacy.scriptFonts?.toScriptFontMap() ?: emptyMap(),
                                defaultFont = legacy.textFont,
                            ),
                        )
                    },
                presetScriptFont = presetScriptFont,
                globalScriptFont = globalScriptFont,
                globalDefaultFont = globalDefaultFont,
                platformFont = platformFont,
                epub = epub,
            )
        }
        return ReadValueContext(
            scope = scope,
            presetScriptFont = presetScriptFont,
            globalScriptFont = globalScriptFont,
            globalDefaultFont = globalDefaultFont,
            platformFont = platformFont,
            epub = epub,
        )
    }
}
