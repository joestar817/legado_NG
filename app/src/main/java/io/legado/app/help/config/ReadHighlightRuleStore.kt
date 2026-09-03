package io.legado.app.help.config

import io.legado.app.constant.AppLog
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import splitties.init.appCtx
import java.io.File
import java.util.UUID

private fun ReadHighlightRule.normalizedForStore(): ReadHighlightRule = normalized().let { rule ->
    if (rule.id.isBlank()) rule.copy(id = UUID.randomUUID().toString()) else rule
}

internal data class ReadHighlightRuleMergeResult(
    val rules: List<ReadHighlightRule>,
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
)

internal fun mergeReadHighlightRules(
    existing: List<ReadHighlightRule>,
    incoming: List<ReadHighlightRule>,
    replaceMatchingIds: Boolean,
): ReadHighlightRuleMergeResult {
    val merged = existing.map(ReadHighlightRule::normalizedForStore).toMutableList()
    var addedCount = 0
    var updatedCount = 0
    var skippedCount = 0

    incoming.map(ReadHighlightRule::normalizedForStore).forEach { candidate ->
        val contentKey = candidate.copy(id = "", position = 0)
        val sameContent = merged.any { it.copy(id = "", position = 0) == contentKey }
        if (sameContent) {
            skippedCount++
            return@forEach
        }
        val idIndex = candidate.id.takeIf(String::isNotBlank)?.let { id ->
            merged.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
        if (idIndex != null) {
            if (replaceMatchingIds) {
                merged[idIndex] = candidate.copy(position = merged[idIndex].position)
                updatedCount++
            } else {
                skippedCount++
            }
        } else {
            merged += candidate
            addedCount++
        }
    }

    val normalized = merged.mapIndexed { index, rule -> rule.copy(position = index) }
    return ReadHighlightRuleMergeResult(
        rules = normalized,
        addedCount = addedCount,
        updatedCount = updatedCount,
        skippedCount = skippedCount,
    )
}

internal fun migrateLegacyReadHighlightRules(
    ruleGroups: List<List<ReadHighlightRule>>,
    preferredIndex: Int,
): List<ReadHighlightRule> {
    if (ruleGroups.isEmpty()) return emptyList()
    val orderedIndexes = buildList {
        if (preferredIndex in ruleGroups.indices) add(preferredIndex)
        ruleGroups.indices.filterTo(this) { it != preferredIndex }
    }
    return orderedIndexes.fold(emptyList()) { merged, index ->
        mergeReadHighlightRules(
            existing = merged,
            incoming = ruleGroups[index],
            replaceMatchingIds = false,
        ).rules
    }
}

internal fun restoreBuiltInReadHighlightRules(
    existing: List<ReadHighlightRule>,
    builtIn: List<ReadHighlightRule>,
): ReadHighlightRuleMergeResult {
    val restored = existing.map(ReadHighlightRule::normalizedForStore).toMutableList()
    var addedCount = 0
    var updatedCount = 0
    var skippedCount = 0

    builtIn.map(ReadHighlightRule::normalizedForStore).forEach { defaultRule ->
        val index = restored.indexOfFirst { it.id == defaultRule.id }
        if (index < 0) {
            restored += defaultRule.copy(position = restored.size)
            addedCount++
        } else {
            val replacement = defaultRule.copy(position = restored[index].position)
            if (restored[index] == replacement) {
                skippedCount++
            } else {
                restored[index] = replacement
                updatedCount++
            }
        }
    }

    return ReadHighlightRuleMergeResult(
        rules = restored.mapIndexed { index, rule -> rule.copy(position = index) },
        addedCount = addedCount,
        updatedCount = updatedCount,
        skippedCount = skippedCount,
    )
}

/** 全局阅读高亮规则的单一持久化入口。 */
internal object ReadHighlightRuleStore {

    const val fileName = "highlightRule.json"
    val filePath: String = FileUtils.getPath(appCtx.filesDir, fileName)

    private val rules = arrayListOf<ReadHighlightRule>()
    private var initialized = false
    private var saveGeneration = 0L

    @Synchronized
    fun initialize(
        legacyRuleGroups: List<List<ReadHighlightRule>>,
        preferredIndex: Int,
        defaultRules: List<ReadHighlightRule>,
        useDefaultsWhenLegacyEmpty: Boolean,
    ) {
        if (initialized) return
        val storedRules = loadFromFile()
        val legacyRules = migrateLegacyReadHighlightRules(
            ruleGroups = legacyRuleGroups,
            preferredIndex = preferredIndex,
        )
        val initialRules = when {
            storedRules != null && legacyRules.isNotEmpty() -> mergeReadHighlightRules(
                existing = legacyRules,
                incoming = storedRules,
                replaceMatchingIds = false,
            ).rules

            storedRules != null -> storedRules
            legacyRules.isNotEmpty() -> legacyRules
            useDefaultsWhenLegacyEmpty -> defaultRules
            else -> emptyList()
        }
        rules.clear()
        rules.addAll(initialRules.mapIndexed { index, rule ->
            rule.normalizedForStore().copy(position = index)
        })
        initialized = true
        if (storedRules == null || legacyRules.isNotEmpty()) persistNow()
    }

    @Synchronized
    fun allRules(): List<ReadHighlightRule> {
        ensureInitialized()
        return rules.map(ReadHighlightRule::copy)
    }

    @Synchronized
    fun enabledRules(): List<ReadHighlightRule> = allRules()
        .filter(ReadHighlightRule::enabled)
        .sortedBy(ReadHighlightRule::position)

    @Synchronized
    fun replace(updatedRules: List<ReadHighlightRule>) {
        ensureInitialized()
        rules.clear()
        rules.addAll(updatedRules.mapIndexed { index, rule ->
            rule.normalizedForStore().copy(position = index)
        })
        saveAsync()
    }

    @Synchronized
    fun merge(
        importedRules: List<ReadHighlightRule>,
        replaceMatchingIds: Boolean,
    ): ReadHighlightRuleMergeResult {
        ensureInitialized()
        val result = mergeReadHighlightRules(rules, importedRules, replaceMatchingIds)
        rules.clear()
        rules.addAll(result.rules)
        saveAsync()
        return result
    }

    @Synchronized
    fun restoreBuiltIn(defaultRules: List<ReadHighlightRule>): ReadHighlightRuleMergeResult {
        ensureInitialized()
        val result = restoreBuiltInReadHighlightRules(rules, defaultRules)
        rules.clear()
        rules.addAll(result.rules)
        if (result.addedCount > 0 || result.updatedCount > 0) saveAsync()
        return result
    }

    @Synchronized
    fun reloadFromFile() {
        saveGeneration++
        val restored = loadFromFile() ?: emptyList()
        rules.clear()
        rules.addAll(restored.mapIndexed { index, rule ->
            rule.normalizedForStore().copy(position = index)
        })
        initialized = true
    }

    private fun ensureInitialized() {
        if (!initialized) {
            ReadBookConfig.toString()
        }
        check(initialized) { "阅读高亮规则尚未初始化" }
    }

    private fun loadFromFile(): List<ReadHighlightRule>? {
        val file = File(filePath)
        if (!file.isFile) return null
        return runCatching {
            GSON.fromJsonArray<ReadHighlightRule>(file.readText()).getOrThrow()
        }.onFailure {
            AppLog.put("读取高亮规则文件出错", it)
        }.getOrNull()
    }

    private fun saveAsync() {
        val generation = ++saveGeneration
        Coroutine.async {
            synchronized(this@ReadHighlightRuleStore) {
                if (generation == saveGeneration) persistNow()
            }
        }
    }

    private fun persistNow(snapshot: List<ReadHighlightRule> = rules) {
        val file = FileUtils.createFileIfNotExist(filePath)
        file.writeText(GSON.toJson(snapshot))
    }
}
