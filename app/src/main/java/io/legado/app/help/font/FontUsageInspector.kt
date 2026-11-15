package io.legado.app.help.font

import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.help.config.BookReadStyleSession
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import splitties.init.appCtx
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** location 用于核对删除范围，displayLocation 仅用于展示。 */
internal data class FontUsageReference(
    val path: String?,
    val location: String,
    val displayLocation: String = location,
)

/** 核对字体引用和删除后的回退，调用方必须在 IO 线程执行。 */
internal object FontUsageInspector {
    fun deletionUsages(paths: Collection<String>): List<String> =
        deletionUsageReferences(paths).map(FontUsageReference::location)

    fun deletionUsageReferences(paths: Collection<String>): List<FontUsageReference> {
        val matches = matchesPaths(paths)
        check(themeFontReferences().none { matches(it.path) }) { "字体属于主题包，请在主题管理中处理" }
        return readUsageReferences(mapOf("font" to paths)).getValue("font")
    }

    fun verifyDeletion(paths: Collection<String>, confirmedUsages: List<String>?) {
        val current = deletionUsages(paths)
        if (confirmedUsages == null) {
            check(current.isEmpty()) { "字体正在使用，请先更换：\n${current.joinToString("\n")}" }
        } else {
            check(current.toSet() == confirmedUsages.toSet()) { "字体使用情况已变化，请重新确认" }
        }
    }

    /** After the exact file is confirmed gone, remove only references to that file. Safe to retry. */
    fun resetAfterDeletion(paths: Collection<String>) {
        val matches = matchesPaths(paths)
        val readerChanged = ReadBookConfig.resetDeletedFont(matches)
        val rulesChanged = ReadHighlightRuleStore.resetDeletedFont(matches)
        if (readerChanged || rulesChanged) postEvent(EventBus.UP_CONFIG, arrayListOf(2, 5))
        val choice = NgInterfaceFontStore.choice(appCtx)
        if (choice !in NgInterfaceFontStore.systemChoices &&
            (matches(choice) || matches(appCtx.getPrefString("ngInterfaceFont.file")))
        ) {
            NgInterfaceFontStore.save(appCtx, SYSTEM_DEFAULT_FONT, "", null)
        }
    }

    private fun matchesPaths(paths: Collection<String>): (String?) -> Boolean {
        val targets = paths.mapNotNull(::normalizePath).toSet()
        return { normalizePath(it) in targets }
    }

    /** 管理页一次读取全库，避免每行字体重复查询全部书籍。 */
    fun usagesByPaths(groups: Map<String, Collection<String>>): Map<String, List<String>> =
        readUsageReferences(groups).mapValues { (_, references) -> references.map(FontUsageReference::location) }

    private fun readUsageReferences(groups: Map<String, Collection<String>>): Map<String, List<FontUsageReference>> {
        if (groups.values.all { it.isEmpty() }) return groups.mapValues { emptyList() }
        return try {
            matchUsageReferences(groups, references())
        } catch (error: Exception) {
            throw IllegalStateException("无法检查字体使用情况，请检查阅读预设后重试", error)
        }
    }

    private fun references(): List<FontUsageReference> = buildList {
        val seen = Collections.newSetFromMap(IdentityHashMap<ReadBookConfig.Config, Boolean>())
        fun addConfig(label: String, config: ReadBookConfig.Config, displayLabel: String = label) {
            if (seen.add(config)) addAll(referencesForConfig(label, config, displayLabel))
        }
        fun addPreset(index: Int, config: ReadBookConfig.Config) {
            val name = config.name.ifBlank { "预设 ${index + 1}" }
            addConfig("阅读预设「$name」", config, "「$name」预设")
        }
        ReadBookConfig.configList.toList().forEachIndexed(::addPreset)
        addConfig("共享排版", ReadBookConfig.shareConfig)
        // 当前书的独立预设可能还没有落盘，不能仅以数据库记录判断占用。
        addConfig("当前书籍", ReadBookConfig.durConfig)
        addConfig("当前阅读", ReadBookConfig.config)
        ReadBookConfig.pendingBookStyleConfigs().forEach { addConfig("本书预设", it) }

        // 正常读取会对损坏文件回退默认值；删除检查不能把回退结果当成没有引用。
        File(ReadBookConfig.configFilePath).takeIf(File::exists)?.let { file ->
            decodePresets(file.readText()).forEachIndexed(::addPreset)
        }
        File(ReadBookConfig.shareConfigFilePath).takeIf(File::exists)?.let { file ->
            addAll(referencesForConfig("共享排版", decodePreset(file.readText())))
        }
        appDb.bookDao.all.forEach { book ->
            listOfNotNull(book.readConfig?.independentReadStyle, book.readConfig?.savedIndependentReadStyle)
                .forEach { serialized ->
                    BookReadStyleSession.decode(serialized)?.let { config ->
                        addAll(referencesForConfig("书籍《${book.name}》", config, "《${book.name}》"))
                    }
                }
        }

        addAll(referencesForRules("高亮规则", ReadHighlightRuleStore.allRules()))
        File(ReadHighlightRuleStore.filePath).takeIf(File::exists)?.let { file ->
            val rules = requireNotNull(GSON.fromJson(file.readText(), Array<ReadHighlightRule>::class.java)) {
                "高亮规则无法读取"
            }
            addAll(referencesForRules("高亮规则", rules.asList()))
        }

        val choice = NgInterfaceFontStore.choice(appCtx)
        if (choice !in NgInterfaceFontStore.systemChoices) {
            add(FontUsageReference(choice, "界面字体", "应用界面"))
            add(FontUsageReference(appCtx.getPrefString("ngInterfaceFont.file"), "界面字体", "应用界面"))
        }
        addAll(themeFontReferences())
    }

    private fun themeFontReferences(): List<FontUsageReference> = NgThemeLibraryStore.allThemes(appCtx).mapNotNull { theme ->
        theme.resolvePackageAsset(theme.resourceProfile?.appFont)?.let { file ->
            FontUsageReference(file.absolutePath, "主题「${theme.name}」·界面字体")
        }
    }

    internal fun referencesForConfig(
        label: String,
        config: ReadBookConfig.Config,
        displayLabel: String = label,
    ): List<FontUsageReference> = listOf(
        FontUsageReference(config.textFont, "$label·正文", "${displayLabel}的正文"),
        FontUsageReference(config.titleFont, "$label·标题", "${displayLabel}的章节标题"),
        FontUsageReference(config.headerFont, "$label·页眉", "${displayLabel}的页眉"),
        FontUsageReference(config.footerFont, "$label·页脚", "${displayLabel}的页脚"),
    ) + referencesForRules("$label·高亮规则", config.highlightRules, "${displayLabel}的高亮规则")

    private fun referencesForRules(
        label: String,
        rules: List<ReadHighlightRule>,
        displayLabel: String = label,
    ): List<FontUsageReference> = rules.map { rule ->
        val name = rule.name.ifBlank { "未命名规则" }
        FontUsageReference(rule.fontPath, "$label「$name」", "$displayLabel「$name」")
    }

    internal fun decodePresets(json: String): List<ReadBookConfig.Config> =
        requireNotNull(GSON.fromJson(json, Array<ReadBookConfig.Config>::class.java)) {
            "阅读预设无法读取"
        }.asList().also { configs ->
            // Gson 可向非空 Kotlin 数组注入 null；主动访问字段令损坏记录阻止删除。
            configs.forEach { it.name.length }
        }

    internal fun decodePreset(json: String): ReadBookConfig.Config =
        requireNotNull(BookReadStyleSession.decode(json)) { "阅读预设无法读取" }

    internal fun matchReferences(
        groups: Map<String, Collection<String>>,
        references: Collection<FontUsageReference>,
        normalize: (String?) -> String? = ::normalizePath,
    ): Map<String, List<String>> = matchUsageReferences(groups, references, normalize)
        .mapValues { (_, usages) -> usages.map(FontUsageReference::location) }

    internal fun matchUsageReferences(
        groups: Map<String, Collection<String>>,
        references: Collection<FontUsageReference>,
        normalize: (String?) -> String? = ::normalizePath,
    ): Map<String, List<FontUsageReference>> {
        val targets = groups.mapValues { (_, paths) -> paths.mapNotNull(normalize).toSet() }
        val locations = groups.mapValues { linkedMapOf<String, FontUsageReference>() }
        references.forEach { reference ->
            val path = normalize(reference.path) ?: return@forEach
            check(!path.startsWith("document:com.android.externalstorage.documents:")) {
                "无法核对部分存储卷中的字体引用，请连接存储设备并重新授权后重试"
            }
            targets.forEach { (id, paths) ->
                if (path in paths) locations.getValue(id).putIfAbsent(reference.location, reference)
            }
        }
        return locations.mapValues { it.value.values.toList() }
    }

    /** 仅统一同一文件的路径表达，不按名字或内容摘要认领外部文件／主题副本。 */
    internal fun normalizePath(value: String?): String? {
        return FontStoragePaths.normalize(value)
    }
}
