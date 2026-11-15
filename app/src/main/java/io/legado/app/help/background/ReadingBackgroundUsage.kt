package io.legado.app.help.background

import io.legado.app.data.appDb
import io.legado.app.help.config.BookReadStyleSession
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.font.FontStoragePaths
import io.legado.app.utils.GSON
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** Read-only, on IO; never infer an unused image from a fallback/default configuration. */
internal object ReadingBackgroundUsage {
    fun pathsByLocation(): List<Pair<String, String>> = try {
        buildList {
            val seen = Collections.newSetFromMap(IdentityHashMap<ReadBookConfig.Config, Boolean>())
            fun addRules(label: String, rules: List<ReadHighlightRule>) {
                rules.forEach { rule ->
                    normalize(rule.bgImage)?.let { add(it to "$label·高亮规则「${rule.name}」") }
                }
            }
            fun addConfig(label: String, config: ReadBookConfig.Config) {
                if (!seen.add(config)) return
                addAll(configReferences(label, config))
                addRules(label, config.highlightRules)
            }
            ReadBookConfig.configList.toList().forEachIndexed { index, config ->
                addConfig("预设「${config.name.ifBlank { "${index + 1}" }}」", config)
            }
            addConfig("共享排版", ReadBookConfig.shareConfig)
            addConfig("当前书籍", ReadBookConfig.durConfig)
            addConfig("当前阅读", ReadBookConfig.config)
            ReadBookConfig.pendingBookStyleConfigs().forEach { addConfig("本书预设", it) }
            File(ReadBookConfig.configFilePath).takeIf(File::exists)?.let { file ->
                requireNotNull(GSON.fromJson(file.readText(), Array<ReadBookConfig.Config>::class.java))
                    .forEach { addConfig("预设「${it.name}」", it) }
            }
            File(ReadBookConfig.shareConfigFilePath).takeIf(File::exists)?.let { file ->
                addConfig("共享排版", requireNotNull(BookReadStyleSession.decode(file.readText())))
            }
            appDb.bookDao.all.forEach { book ->
                listOfNotNull(book.readConfig?.independentReadStyle, book.readConfig?.savedIndependentReadStyle)
                    .filter(String::isNotBlank).forEach { json ->
                        addConfig("书籍《${book.name}》", requireNotNull(BookReadStyleSession.decode(json)))
                    }
            }
            addRules("阅读", ReadHighlightRuleStore.allRules())
            File(ReadHighlightRuleStore.filePath).takeIf(File::exists)?.let { file ->
                addRules("阅读", requireNotNull(GSON.fromJson(file.readText(), Array<ReadHighlightRule>::class.java)).asList())
            }
        }
    } catch (error: Exception) {
        throw IllegalStateException("无法检查背景使用情况，请检查阅读预设后重试", error)
    }

    internal fun configReferences(label: String, config: ReadBookConfig.Config): List<Pair<String, String>> = buildList {
        ReadAppearanceMode.entries.forEach { mode ->
            if (config.backgroundTypeForMode(mode) == 2) {
                val reference = config.backgroundForMode(mode)
                val path = if (reference.contains(File.separator)) reference else ReadingBackgroundLibrary.resolveFile(reference).path
                normalize(path)?.let { add(it to "$label·${listOf("日间", "夜间", "墨水屏")[mode.ordinal]}") }
            }
        }
        normalize(config.headerImage)?.let { add(it to "$label·页眉") }
        normalize(config.footerImage)?.let { add(it to "$label·页脚") }
    }

    fun normalize(value: String?): String? = FontStoragePaths.normalize(value)?.also {
        check(!it.startsWith("document:com.android.externalstorage.documents:")) {
            "无法核对部分存储卷中的背景引用，请连接存储设备后重试"
        }
    }

    fun match(paths: Collection<String>, references: List<Pair<String, String>>): Map<String, List<String>> {
        val byPath = references.groupBy({ it.first }, { it.second })
        return paths.associateWith { byPath[it].orEmpty().distinct() }
    }
}
