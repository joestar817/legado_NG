package io.legado.app.ui.association

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.isEmptyConfiguration
import io.legado.app.model.RuleUpdate
import io.legado.app.utils.GSON
import io.legado.app.utils.inputStream
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isUri
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.toastOnUi
import java.io.StringReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


class ImportBookSourceViewModel(app: Application) : BaseViewModel(app) {
    var isAddGroup = false
    var groupName: String? = null
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()
    private var skippedSourceCount = 0

    internal val allSources = arrayListOf<BookSourceImportItem>()
    private val sourceStore = lazy { BookSourceImportStore(app.cacheDir) }
    val checkSources = arrayListOf<BookSourcePart?>()
    val selectStatus = arrayListOf<Boolean>()
    val newSourceStatus = arrayListOf<Boolean>()
    val updateSourceStatus = arrayListOf<Boolean>()
    var selectableIndices: Set<Int> = emptySet()
        private set

    val isSelectAll: Boolean
        get() = selectableIndices.isNotEmpty() && selectableIndices.all { selectStatus[it] }

    val isSelectAllNew: Boolean
        get() {
            newSourceStatus.forEachIndexed { index, b ->
                if (b && index in selectableIndices && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val isSelectAllUpdate: Boolean
        get() {
            updateSourceStatus.forEachIndexed { index, b ->
                if (b && index in selectableIndices && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val selectCount: Int
        get() {
            var count = 0
            selectStatus.forEachIndexed { index, selected ->
                if (selected && index in selectableIndices) {
                    count++
                }
            }
            return count
        }

    fun importSelect(finally: () -> Unit) {
        val indices = selectedImportIndices()
        execute {
            val group = groupName?.trim()
            val keepName = AppConfig.importKeepName
            val keepGroup = AppConfig.importKeepGroup
            val keepEnable = AppConfig.importKeepEnable
            val coroutineContext = currentCoroutineContext()
            val batches = sequence {
                var batch = arrayListOf<BookSource>()
                var bytes = 0L
                for ((index, source) in selectedImportRecords(indices)) {
                    coroutineContext.ensureActive()
                    val size = sourceStore.value.recordSize(index)
                    if (batch.isNotEmpty() && (batch.size >= 32 || bytes + size > 1024 * 1024)) {
                        yield(batch)
                        batch = arrayListOf()
                        bytes = 0
                    }
                    checkSources[index]?.let {
                        if (keepName) source.bookSourceName = it.bookSourceName
                        if (keepGroup) source.bookSourceGroup = it.bookSourceGroup
                        if (keepEnable) {
                            source.enabled = it.enabled
                            source.enabledExplore = it.enabledExplore
                        }
                        source.customOrder = it.customOrder
                    }
                    if (!group.isNullOrEmpty()) {
                        if (isAddGroup) {
                            val groups = linkedSetOf<String>()
                            source.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                                groups.addAll(it)
                            }
                            groups.add(group)
                            source.bookSourceGroup = groups.joinToString(",")
                        } else {
                            source.bookSourceGroup = group
                        }
                    }
                    batch.add(source)
                    bytes += size
                }
                if (batch.isNotEmpty()) yield(batch)
            }
            SourceHelp.insertBookSourceBatches(batches)
            ContentProcessor.upReplaceRules()
        }.onError {
            AppLog.put("导入书源失败", it)
            context.toastOnUi("导入书源失败：${it.localizedMessage}")
        }.onFinally {
            finally.invoke()
        }
    }

    fun importSource(text: String) {
        execute {
            skippedSourceCount = 0
            importSourceText(text.trim(), allowSourceUrls = true)
        }.onError {
            allSources.clear()
            releaseSourceStore()
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it)
        }.onSuccess {
            comparisonSource()
        }
    }

    private suspend fun importSourceText(text: String, allowSourceUrls: Boolean) {
        val content = text.trim()
        when {
            allowSourceUrls && !content.startsWith("[") && !content.startsWith("{") && content.isAbsUrl() ->
                importSourceUrl(content)
            allowSourceUrls && !content.startsWith("[") && !content.startsWith("{") && content.isUri() -> {
                val uri = Uri.parse(content)
                uri.inputStream(context).getOrThrow().bufferedReader().use {
                    skippedSourceCount += readBookSourceImport(it, false, ::appendPreviewSource, ::importSourceUrl)
                }
            }
            else -> StringReader(content).use {
                val skipped = readBookSourceImport(it, allowSourceUrls, ::appendPreviewSource, ::importSourceUrl)
                skippedSourceCount += skipped
            }
        }
    }

    private suspend fun importSourceUrl(url: String) {
        RuleUpdate.cacheBookSourceMap.remove(url)?.also {
            it.forEach { source ->
                currentCoroutineContext().ensureActive()
                appendPreviewSource(source)
            }
            return
        }
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().byteStream().bufferedReader().use {
            skippedSourceCount += readBookSourceImport(it, false, ::appendPreviewSource, ::importSourceUrl)
        }
    }

    private fun comparisonSource() {
        execute {
            val selectable = linkedSetOf<Int>()
            allSources.forEachIndexed { index, it ->
                val source = appDb.bookSourceDao.getBookSourcePart(it.bookSourceUrl)
                currentCoroutineContext().ensureActive()
                val canSelect = !it.emptyConfiguration
                if (canSelect) selectable.add(index)
                checkSources.add(source)
                selectStatus.add(canSelect && (source == null || source.lastUpdateTime < it.lastUpdateTime))
                newSourceStatus.add(source == null)
                updateSourceStatus.add(source != null && source.lastUpdateTime < it.lastUpdateTime)
            }
            selectableIndices = selectable
            successLiveData.postValue(allSources.size)
            if (skippedSourceCount > 0) {
                context.toastOnUi("已跳过 $skippedSourceCount 条书源地址为空的记录")
            }
        }
    }

    suspend fun updatePreviewSource(index: Int, source: BookSource) {
        if (index !in allSources.indices) return
        val item = withContext(IO) { sourceStore.value.replace(index, source) }
        allSources[index] = item
        if (item.emptyConfiguration) {
            selectableIndices = selectableIndices - index
            selectStatus[index] = false
        } else {
            selectableIndices = selectableIndices + index
        }
    }

    fun applySelection(indices: Set<Int>): Set<Int> {
        val selection = indices.intersect(selectableIndices)
        selectStatus.indices.forEach { index ->
            selectStatus[index] = index in selection
        }
        return selection
    }

    /** 选择只读轻量索引；提交逐条读完整配置后仍会再次检查。 */
    internal fun selectedImportIndices(): List<Int> = selectStatus.indices.filter { index ->
        selectStatus[index] && !allSources[index].emptyConfiguration
    }

    internal fun selectedImportRecords(indices: List<Int>): Sequence<Pair<Int, BookSource>> = sequence {
        for (index in indices) {
            val source = sourceStore.value.read(index)
            if (!source.isEmptyConfiguration()) yield(index to source)
        }
    }

    internal fun appendPreviewSource(source: BookSource) {
        allSources.add(sourceStore.value.append(source))
    }

    fun previewSourceCode(index: Int): String = GSON.toJson(sourceStore.value.read(index))

    private fun releaseSourceStore() {
        if (sourceStore.isInitialized()) Coroutine.async { sourceStore.value.close() }
    }

    override fun onCleared() {
        super.onCleared()
        releaseSourceStore()
    }

}
