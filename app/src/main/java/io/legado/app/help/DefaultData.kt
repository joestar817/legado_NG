package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.help.config.ReadHighlightRuleStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.BookCover
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.splitNotBlank
import splitties.init.appCtx
import java.io.File

object DefaultData {

    private const val legacySourceRepositoryUrl = "https://www.yckceo.com"
    private const val sourceRepositoryName = "源仓库"
    private const val legacySourceRepositoryGroup = "ledegao"

    private val legacyDefaultRssGroups = listOf(
        LegacyDefaultRssGroup(
            sourceUrl = "https://shuyuan.yiove.com",
            sourceName = "Yiove 书源仓库",
            group = "书源",
        ),
        LegacyDefaultRssGroup(
            sourceUrl = "https://pan.miaogongzi.net",
            sourceName = "Meow云",
            group = "legado",
        ),
        LegacyDefaultRssGroup(
            sourceUrl = "https://ycoo.net",
            sourceName = "源社区",
            group = "ledegao",
        ),
    )

    fun upVersion() {
        val isAppUpgrade = LocalConfig.versionCode < AppConst.appInfo.versionCode
        val isFreshInstall = LocalConfig.versionCode == 0L
        val shouldUpgradeRssSources = LocalConfig.needUpRssSources
        if (isAppUpgrade || shouldUpgradeRssSources) {
            Coroutine.async {
                if (isAppUpgrade && LocalConfig.needUpTxtTocRule) {
                    importDefaultTocRules()
                }
                if (shouldUpgradeRssSources) {
                    if (isFreshInstall && appDb.rssSourceDao.size == 0) {
                        importDefaultRssSources()
                    } else {
                        upgradeDefaultRssSources()
                    }
                    LocalConfig.markRssSourcesUpdated()
                }
                if (isAppUpgrade && LocalConfig.needUpDictRule) {
                    importDefaultDictRules()
                }
            }.onError {
                it.printOnDebug()
            }
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadBookConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val readHighlightRules: List<ReadHighlightRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ReadHighlightRuleStore.fileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ReadHighlightRule>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}txtTocRule.json")
                .readBytes()
        )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfig.Config> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}${ThemeConfig.configFileName}")
                .readBytes()
        )
        GSON.fromJsonArray<ThemeConfig.Config>(json).getOrNull() ?: emptyList()
    }

    val rssSources: List<RssSource> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}rssSources.json")
                .readBytes()
        )
        GSON.fromJsonArray<RssSource>(json).getOrDefault(emptyList())
    }

    val coverRule: BookCover.CoverRule by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}coverRule.json")
                .readBytes()
        )
        GSON.fromJsonObject<BookCover.CoverRule>(json).getOrThrow()
    }

    val dictRules: List<DictRule> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}dictRules.json")
                .readBytes()
        )
        GSON.fromJsonArray<DictRule>(json).getOrThrow()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json = String(
            appCtx.assets.open("defaultData${File.separator}keyboardAssists.json")
                .readBytes()
        )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }

    fun importDefaultRssSources() {
        appDb.rssSourceDao.deleteDefault()
        appDb.rssSourceDao.insert(*rssSources.toTypedArray())
    }

    private fun upgradeDefaultRssSources() {
        val replacement = rssSources.singleOrNull { it.sourceName == sourceRepositoryName }
            ?: return
        val dao = appDb.rssSourceDao
        appDb.runInTransaction {
            val legacy = dao.getByKey(legacySourceRepositoryUrl)
            if (legacy?.sourceName == sourceRepositoryName) {
                if (!dao.has(replacement.sourceUrl)) {
                    dao.insert(
                        replacement.copy(
                            enabled = legacy.enabled,
                            customOrder = legacy.customOrder,
                            sourceGroup = removeLegacyGroup(
                                legacy.sourceGroup,
                                legacySourceRepositoryGroup,
                            ),
                        )
                    )
                }
                dao.delete(legacySourceRepositoryUrl)
            }
            legacyDefaultRssGroups.forEach { default ->
                val source = dao.getByKey(default.sourceUrl) ?: return@forEach
                if (source.sourceName != default.sourceName) return@forEach
                val sourceGroup = removeLegacyGroup(source.sourceGroup, default.group)
                if (sourceGroup != source.sourceGroup) {
                    dao.update(source.copy(sourceGroup = sourceGroup))
                }
            }
        }
    }

    private fun removeLegacyGroup(sourceGroup: String?, legacyGroup: String): String? {
        return sourceGroup
            ?.splitNotBlank(AppPattern.splitGroupRegex)
            ?.filterNot { it == legacyGroup }
            ?.joinToString(",")
            ?.ifBlank { null }
    }

    fun importDefaultDictRules() {
        appDb.dictRuleDao.insert(*dictRules.toTypedArray())
    }

    private data class LegacyDefaultRssGroup(
        val sourceUrl: String,
        val sourceName: String,
        val group: String,
    )

}
