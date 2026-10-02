package io.legado.app.help.config

/**
 * Phase 0 契约类型：统一有效值解析模型（仅冻结接口与数据类型，生产实现留待 Phase 2）。
 *
 * 契约文档：docs/reading-settings-redesign-plan.md §3.6 / §3.7
 *
 * 当前契约只冻结**字体属性**（§3.7-3：脚本维度仅正文 textFont）。
 * value 建模为 String 字体标识；Phase 3 之前不扩展其他属性。
 *
 * 解析顺序（非 EPUB，或 EPUB 规则为 override / respect 但原书未声明该属性）：
 *   book.overrides[scope]       -> ThisBook   （本书脚本级覆盖）
 *   book.overrides[default]     -> ThisBook   （本书默认级覆盖；压过全局脚本档案）
 *   book.basePreset(scope)      -> Preset     （pinned 快照，或 follow_global 当前预设）
 *   global.scripts[scope]       -> Global
 *   global.default              -> Global
 *   platform fallback           -> Platform   （非空，保证解析是全函数）
 *
 * EPUB 规则为 respect 且原书声明了该属性：publisher 值直接胜出（source = Publisher）。
 */

/** 值作用于哪个脚本维度；Appearance 不参与脚本维度。 */
enum class ReadValueScope { DEFAULT, LATIN, CJK, OTHER }

/** 值的来源。source 是契约的一部分，不只是 UI 元数据。 */
enum class ReadValueSource { THIS_BOOK, PRESET, GLOBAL, PUBLISHER, PLATFORM }

data class ResolvedReadValue(
    val value: String,
    val source: ReadValueSource,
    val scope: ReadValueScope,
)

enum class ReadBasePresetMode { PINNED, FOLLOW_GLOBAL }

/** pinned 基准的不可变预设快照；脚本档案用 map 表达三个维度，缺省即继承。 */
data class ReadPresetSnapshot(
    val scriptFonts: Map<ReadValueScope, String> = emptyMap(),
    val defaultFont: String? = null,
)

data class ReadBasePreset(
    val mode: ReadBasePresetMode,
    val snapshot: ReadPresetSnapshot? = null,
) {
    init {
        require(mode != ReadBasePresetMode.PINNED || snapshot != null) {
            "pinned 基准必须携带不可变快照，不能只引用可变的 presetId"
        }
    }
}

enum class ReadEpubRule { RESPECT, OVERRIDE }

data class ReadEpubContext(
    val rule: ReadEpubRule,
    /** 原书是否声明了该属性并给出值；null 表示原书未声明（级联 caveat）。 */
    val publisherFont: String? = null,
)

data class ReadValueContext(
    val scope: ReadValueScope,
    val bookScriptFont: String? = null,
    val bookDefaultFont: String? = null,
    val basePreset: ReadBasePreset? = null,
    val globalScriptFont: String? = null,
    val globalDefaultFont: String? = null,
    /** 平台兜底字体。非空，由类型保证解析是全函数。 */
    val platformFont: String = "platform",
    val epub: ReadEpubContext? = null,
)

fun interface EffectiveReadValueResolver {
    fun resolve(context: ReadValueContext): ResolvedReadValue
}

/**
 * 生产解析器（Phase 2 落地）：机械实现 §3.6 优先级矩阵。
 * 契约测试 EffectiveReadValueResolverTest 直接消费本对象。
 */
object EffectiveReadValueResolverContract : EffectiveReadValueResolver {
    override fun resolve(context: ReadValueContext): ResolvedReadValue {
        val scope = context.scope

        // EPUB：respect 且原书声明了该属性 → publisher 胜出（级联 caveat：未声明则走 App 路径）。
        context.epub?.let { epub ->
            if (epub.rule == ReadEpubRule.RESPECT && epub.publisherFont != null) {
                return ResolvedReadValue(epub.publisherFont, ReadValueSource.PUBLISHER, scope)
            }
        }

        // 1a. 本书脚本级稀疏 override
        context.bookScriptFont?.let { return ResolvedReadValue(it, ReadValueSource.THIS_BOOK, scope) }
        // 1b. 本书默认级稀疏 override（压过全局脚本档案）
        context.bookDefaultFont?.let { return ResolvedReadValue(it, ReadValueSource.THIS_BOOK, scope) }

        // 2. 本书基准预设（pinned 快照，或 follow_global 当前预设）
        context.basePreset?.let { preset ->
            when (preset.mode) {
                ReadBasePresetMode.PINNED -> {
                    val snapshot = requireNotNull(preset.snapshot)
                    snapshot.scriptFonts[scope]?.let { return ResolvedReadValue(it, ReadValueSource.PRESET, scope) }
                    snapshot.defaultFont?.let { return ResolvedReadValue(it, ReadValueSource.PRESET, scope) }
                }

                ReadBasePresetMode.FOLLOW_GLOBAL -> {
                    context.globalScriptFont?.let { return ResolvedReadValue(it, ReadValueSource.PRESET, scope) }
                    context.globalDefaultFont?.let { return ResolvedReadValue(it, ReadValueSource.PRESET, scope) }
                }
            }
        }

        // 3. 全局脚本档案 → 4. 全局 default → 5. platform（非空兜底，全函数）
        context.globalScriptFont?.let { return ResolvedReadValue(it, ReadValueSource.GLOBAL, scope) }
        context.globalDefaultFont?.let { return ResolvedReadValue(it, ReadValueSource.GLOBAL, scope) }
        return ResolvedReadValue(context.platformFont, ReadValueSource.PLATFORM, scope)
    }
}
