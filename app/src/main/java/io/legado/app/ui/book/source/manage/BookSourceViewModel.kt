package io.legado.app.ui.book.source.manage

import android.app.Application
import android.text.TextUtils
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.toBookSource
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.cnCompare
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.outputStream
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import splitties.init.appCtx
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

    fun enableSelection(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(true, sources)
        }
    }

    fun disableSelection(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enable(false, sources)
        }
    }

    fun enableExplore(enable: Boolean, items: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(enable, items)
        }
    }

    fun enableSelectExplore(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(true, sources)
        }
    }

    fun disableSelectExplore(sources: List<BookSourcePart>) {
        execute {
            appDb.bookSourceDao.enableExplore(false, sources)
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

    fun selectionRemoveFromGroups(sources: List<BookSourcePart>, groups: String) {
        execute {
            val array = sources.map {
                it.copy().apply {
                    removeGroup(groups)
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

    fun selectionAutoGroup(sources: List<BookSourcePart>) {
        execute {
            val array = sources.map {
                val fullSource = appDb.bookSourceDao.getBookSource(it.bookSourceUrl)
                it.copy(bookSourceGroup = autoGroupNames(it, fullSource))
            }
            appDb.bookSourceDao.upGroup(array)
        }
    }

    private fun autoGroupNames(source: BookSourcePart, fullSource: BookSource?): String {
        val groups = arrayListOf(
            when (source.bookSourceType) {
                BookSourceType.image -> "漫画"
                BookSourceType.audio -> "音频"
                BookSourceType.video -> "视频"
                BookSourceType.file -> "其它"
                else -> "小说"
            }
        )
        if (source.hasLoginUrl) {
            groups.add("有登录入口")
        }
        if (!source.hasSearchUrl) {
            groups.add("无搜索")
        }
        if (source.hasExploreUrl) {
            groups.add("有发现")
        }
        if (source.eventListener) {
            groups.add("事件监听")
        }
        if (usesWebView(fullSource)) {
            groups.add("WebView")
        }
        if (usesVerificationCode(fullSource)) {
            groups.add("有验证码")
        }
        return TextUtils.join(",", groups)
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
            source.ruleReview?.let { GSON.toJson(it) }
        )
    }

    private fun verificationCodeRuleTexts(source: BookSource?): List<String> {
        source ?: return emptyList()
        return listOfNotNull(
            source.ruleSearch?.let { GSON.toJson(it) },
            source.ruleToc?.let { GSON.toJson(it) },
            source.ruleContent?.let { GSON.toJson(it) }
        )
    }

    private fun BookSource?.hasVerificationCodeText(): Boolean {
        this ?: return false
        return listOfNotNull(
            bookSourceName,
            bookSourceComment,
            variableComment,
            searchUrl,
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
            val path = "${context.filesDir}/shareBookSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, sources)
            }
            file
        }.onSuccess {
            success.invoke(it, name)
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun saveToFile(
        adapter: BookSourceAdapter,
        searchKey: String?,
        sortAscending: Boolean,
        sort: BookSourceSort,
        success: (file: File, name: String) -> Unit
    ) {
        execute {
            val selection = adapter.selection
            val selectionSize = selection.size
            val sourceCount = adapter.sourceCount
            val selectedRate = if (sourceCount == 0) 0f else selectionSize.toFloat() / sourceCount.toFloat()
            val sources = if (selectedRate == 1f) {
                getBookSources(searchKey, sortAscending, sort)
            } else if (selectedRate < 0.3) {
                selection.toBookSource()
            } else {
                val keys = selection.map { it.bookSourceUrl }.toHashSet()
                val bookSources = getBookSources(searchKey, sortAscending, sort)
                bookSources.filter {
                    keys.contains(it.bookSourceUrl)
                }
            }
            val name = if (selectionSize == 1) {
                "bookSource_${selection.first().bookSourceName.normalizeFileName()}.json"
            } else {
                val timestamp = java.text.SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault()).format(Date())
                "bookSource_$timestamp.json"
            }
            saveToFile(sources, name, success)
        }
    }

    private fun getBookSources(
        searchKey: String?,
        sortAscending: Boolean,
        sort: BookSourceSort
    ): List<BookSource> {
        return when {
            searchKey.isNullOrEmpty() -> {
                appDb.bookSourceDao.all
            }

            searchKey == appCtx.getString(R.string.enabled) -> {
                appDb.bookSourceDao.allEnabled
            }

            searchKey == appCtx.getString(R.string.disabled) -> {
                appDb.bookSourceDao.allDisabled
            }

            searchKey == appCtx.getString(R.string.need_login) -> {
                appDb.bookSourceDao.allLogin
            }

            searchKey == appCtx.getString(R.string.no_group) -> {
                appDb.bookSourceDao.allNoGroup
            }

            searchKey == appCtx.getString(R.string.enabled_explore) -> {
                appDb.bookSourceDao.allEnabledExplore
            }

            searchKey == appCtx.getString(R.string.disabled_explore) -> {
                appDb.bookSourceDao.allDisabledExplore
            }

            searchKey.startsWith("group:") -> {
                val key = searchKey.substringAfter("group:")
                appDb.bookSourceDao.groupSearch(key)
            }

            else -> {
                appDb.bookSourceDao.search(searchKey)
            }
        }.let { data ->
            if (sortAscending) when (sort) {
                BookSourceSort.Weight -> data.sortedBy { it.weight }
                BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                    o1.bookSourceName.cnCompare(o2.bookSourceName)
                }

                BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                    var sortNum = -o1.enabled.compareTo(o2.enabled)
                    if (sortNum == 0) {
                        sortNum = o1.bookSourceName.cnCompare(o2.bookSourceName)
                    }
                    sortNum
                }

                else -> data
            }
            else when (sort) {
                BookSourceSort.Weight -> data.sortedByDescending { it.weight }
                BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                    o2.bookSourceName.cnCompare(o1.bookSourceName)
                }

                BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                    var sortNum = o1.enabled.compareTo(o2.enabled)
                    if (sortNum == 0) {
                        sortNum = o1.bookSourceName.cnCompare(o2.bookSourceName)
                    }
                    sortNum
                }

                else -> data.reversed()
            }
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
            execute {
                val sources = appDb.bookSourceDao.getByGroup(group)
                sources.forEach { source ->
                    source.removeGroup(group)
                }
                appDb.bookSourceDao.update(*sources.toTypedArray())
            }
        }
    }

}
