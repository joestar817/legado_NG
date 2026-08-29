package io.legado.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.toBookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.jsSource.isJsSource
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.outputStream
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * 书源管理数据修改
 * 修改数据要copy,直接修改会导致界面不刷新
 */
class BookSourceViewModel(application: Application) : BaseViewModel(application) {

    fun topSource(vararg sources: BookSourcePart) {
        execute {
            sources.sortBy { it.customOrder }
            val minOrder = appDb.bookSourceDao.minOrder - 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = minOrder - index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun bottomSource(vararg sources: BookSourcePart) {
        execute {
            sources.sortBy { it.customOrder }
            val maxOrder = appDb.bookSourceDao.maxOrder + 1
            val array = sources.mapIndexed { index, it ->
                it.copy(customOrder = maxOrder + index)
            }
            appDb.bookSourceDao.upOrder(array)
        }
    }

    fun del(sources: List<BookSourcePart>) {
        execute {
            SourceHelp.deleteBookSourceParts(sources)
        }
    }

    fun update(vararg bookSource: BookSource) {
        execute { appDb.bookSourceDao.update(*bookSource) }
    }

    fun upOrder(items: List<BookSourcePart>) {
        if (items.isEmpty()) return
        execute {
            appDb.bookSourceDao.upOrder(items)
        }
    }

    fun enable(enable: Boolean, items: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(enable, items)
        }
    }

    fun enableExplore(enable: Boolean, items: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(enable, items)
        }
    }

    fun updateSourceSwitches(
        source: BookSourcePart,
        searchEnabled: Boolean,
        exploreEnabled: Boolean,
    ) {
        execute {
            appDb.bookSourceDao.enable(source.bookSourceUrl, searchEnabled)
            if (source.hasExploreUrl) {
                appDb.bookSourceDao.enableExplore(source.bookSourceUrl, exploreEnabled)
            }
        }
    }

    fun updateSelectionCapabilities(
        sources: List<BookSourcePart>,
        searchEnabled: Boolean,
        exploreEnabled: Boolean,
    ) {
        execute {
            appDb.bookSourceDao.enable(searchEnabled, sources)
            appDb.bookSourceDao.enableExplore(
                exploreEnabled,
                sources.filter(BookSourcePart::hasExploreUrl),
            )
        }
    }

    fun selectionAddToGroups(sources: List<BookSourcePart>, groups: String) {
        execute {
            val array = sources.map {
                it.copy().apply {
                    addGroup(groups)
                }
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    fun selectionClearGroups(sources: List<BookSourcePart>) {
        execute {
            val array = sources.map {
                it.copy(bookSourceGroup = "")
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    internal fun selectionAutoGroup(
        sources: List<BookSourcePart>,
        selectedRuleTypes: Set<BookSourceAutoGroupRuleType>,
    ) {
        execute {
            val sharedBaseUrlGroups = if (
                BookSourceAutoGroupRuleType.URL_CATEGORY in selectedRuleTypes
            ) {
                BookSourceAutoGroup.sharedBaseUrlGroups(
                    appDb.bookSourceDao.allPart.map { it.bookSourceUrl }
                )
            } else {
                emptyMap()
            }
            val needsDebugSource = BookSourceAutoGroupRuleType.DEBUG_FEATURES in selectedRuleTypes
            val array = sources.map {
                val fullSource = if (needsDebugSource) {
                    appDb.bookSourceDao.getBookSource(it.bookSourceUrl)
                } else {
                    null
                }
                it.copy(
                    bookSourceGroup = autoGroupNames(
                        source = it,
                        fullSource = fullSource,
                        sharedBaseUrlGroup = sharedBaseUrlGroups[it.bookSourceUrl],
                        selectedRuleTypes = selectedRuleTypes,
                    ).joinToString(",")
                )
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    private fun autoGroupNames(
        source: BookSourcePart,
        fullSource: BookSource?,
        sharedBaseUrlGroup: String?,
        selectedRuleTypes: Set<BookSourceAutoGroupRuleType>,
    ): List<String> {
        return buildList {
            if (BookSourceAutoGroupRuleType.SOURCE_CATEGORY in selectedRuleTypes) {
                add(
                    when (source.bookSourceType) {
                        BookSourceType.image -> "漫画"
                        BookSourceType.audio -> "音频"
                        BookSourceType.video -> "视频"
                        BookSourceType.file -> "其它"
                        else -> "小说"
                    }
                )
            }
            if (BookSourceAutoGroupRuleType.DEBUG_FEATURES in selectedRuleTypes) {
                if (source.hasLoginUrl) add("有登录入口")
                if (!source.hasSearchUrl) add("无搜索")
                if (source.hasExploreUrl) add("有发现")
                if (source.eventListener) add("事件监听")
                if (usesWebView(fullSource)) add("WebView")
                if (usesVerificationCode(fullSource)) add("有验证码")
            }
            if (BookSourceAutoGroupRuleType.URL_CATEGORY in selectedRuleTypes) {
                sharedBaseUrlGroup?.let(::add)
            }
        }
    }

    private fun usesWebView(source: BookSource?): Boolean {
        return autoGroupRuleTexts(source).any { webViewRuleRegex.containsMatchIn(it) }
    }

    private fun usesVerificationCode(source: BookSource?): Boolean {
        return source.hasVerificationCodeText() &&
                verificationCodeRuleTexts(source).any { hasActiveVerificationCodeCall(it) }
    }

    private fun autoGroupRuleTexts(source: BookSource?): List<String> {
        source ?: return emptyList()
        return listOfNotNull(
            source.bookUrlPattern,
            source.jsLib,
            source.header,
            source.loginUrl,
            source.loginUi,
            source.loginCheckJs,
            source.coverDecodeJs,
            source.exploreUrl,
            source.exploreScreen,
            source.searchUrl,
            source.ruleExplore?.let { GSON.toJson(it) },
            source.ruleSearch?.let { GSON.toJson(it) },
            source.ruleBookInfo?.let { GSON.toJson(it) },
            source.ruleToc?.let { GSON.toJson(it) },
            source.ruleContent?.let { GSON.toJson(it) },
            source.ruleReview?.let { GSON.toJson(it) },
            source.mainJs
        )
    }

    private fun verificationCodeRuleTexts(source: BookSource?): List<String> {
        source ?: return emptyList()
        return listOfNotNull(
            source.ruleSearch?.let { GSON.toJson(it) },
            source.ruleToc?.let { GSON.toJson(it) },
            source.ruleContent?.let { GSON.toJson(it) },
            source.mainJs,
        )
    }

    private fun BookSource?.hasVerificationCodeText(): Boolean {
        this ?: return false
        return listOfNotNull(
            bookSourceName,
            bookSourceComment,
            variableComment,
            searchUrl,
            mainJs,
            ruleSearch?.let { GSON.toJson(it) },
            ruleToc?.let { GSON.toJson(it) },
            ruleContent?.let { GSON.toJson(it) }
        ).any { it.contains("验证码") }
    }

    private fun hasActiveVerificationCodeCall(text: String): Boolean {
        return verificationCodeRuleRegex.findAll(text).any {
            !isInBlockComment(text, it.range.first) && !isInLineComment(text, it.range.first)
        }
    }

    private fun isInBlockComment(text: String, index: Int): Boolean {
        val openIndex = text.lastIndexOf("/*", index)
        if (openIndex < 0) return false
        val closeIndex = text.lastIndexOf("*/", index)
        return closeIndex < openIndex
    }

    private fun isInLineComment(text: String, index: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 }
        var commentIndex = text.indexOf("//", lineStart)
        while (commentIndex in 0..<index) {
            if (commentIndex == 0 || text[commentIndex - 1] != ':') {
                return true
            }
            commentIndex = text.indexOf("//", commentIndex + 2)
        }
        return false
    }

    companion object {
        private val webViewRuleRegex = Regex(
            """@webjs:|["']?webView["']?\s*[:=]\s*(true|1|"true"|'true')|java\.webView(?:GetSource|GetOverrideUrl)?\s*\(|\bwebView(?:Await|GetSourceAwait|GetOverrideUrlAwait)?\s*\(""",
            RegexOption.IGNORE_CASE
        )
        private val verificationCodeRuleRegex = Regex("""java\.getVerificationCode\s*\(""")
    }

    private fun saveToFile(sources: List<BookSource>, name: String, success: (file: File, name: String) -> Unit) {
        execute {
            val single = sources.singleOrNull()
            if (single != null && single.isJsSource()) {
                val outputName = "${single.bookSourceName.normalizeFileName()}.js"
                val file = File(context.cacheDir, outputName)
                file.writeText(single.mainJs.orEmpty())
                file to outputName
            } else {
                val path = "${context.filesDir}/shareBookSource.json"
                FileUtils.delete(path)
                val file = FileUtils.createFileWithReplace(path)
                file.outputStream().buffered().use {
                    GSON.writeToOutputStream(it, sources)
                }
                file to name
            }
        }.onSuccess {
            success.invoke(it.first, it.second)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun saveToFile(
        selection: List<BookSourcePart>,
        success: (file: File, name: String) -> Unit
    ) {
        execute {
            selection.toBookSource()
        }.onSuccess { sources ->
            val name = if (selection.size == 1) {
                "bookSource_${selection.first().bookSourceName.normalizeFileName()}.json"
            } else {
                val timestamp = java.text.SimpleDateFormat(
                    "yyyyMMddHHmm",
                    Locale.getDefault()
                ).format(Date())
                "bookSource_$timestamp.json"
            }
            saveToFile(sources, name, success)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun addGroup(group: String) {
        execute {
            val sources = appDb.bookSourceDao.noGroup
            sources.forEach { source ->
                source.bookSourceGroup = group
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun upGroup(oldGroup: String, newGroup: String?) {
        execute {
            val sources = appDb.bookSourceDao.getByGroup(oldGroup)
            sources.forEach { source ->
                source.bookSourceGroup?.splitNotBlank(",")?.toHashSet()?.let {
                    it.remove(oldGroup)
                    if (!newGroup.isNullOrEmpty())
                        it.add(newGroup)
                    source.bookSourceGroup = TextUtils.join(",", it)
                }
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun delGroup(group: String) {
        execute {
            val sources = appDb.bookSourceDao.getExactByGroup(group)
            sources.forEach { source ->
                source.removeGroup(group)
            }
            appDb.bookSourceDao.update(*sources.toTypedArray())
        }
    }

    fun delGroupAndSources(group: String) {
        execute {
            SourceHelp.deleteBookSources(appDb.bookSourceDao.getExactByGroup(group))
        }
    }

}
