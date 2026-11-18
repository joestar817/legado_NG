package io.legado.app.ui.rss.source.edit

import android.app.Application
import android.content.Intent
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.removeSortCache
import io.legado.app.help.source.clearSourceVariablesFromMemory
import io.legado.app.help.source.deleteStoredSourceVariables
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.source.edit.SourceEditorSession
import io.legado.app.ui.source.edit.hasSameEditorConfiguration
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


class RssSourceEditViewModel(application: Application) : BaseViewModel(application) {
    internal val editor = RssSourceEditState()
    internal val session = SourceEditorSession(application, "rss", RssSourceEditorDraft::class.java, viewModelScope)
    var autoComplete by mutableStateOf(false)
    var rssSource: RssSource? = null
        private set
    internal var persistedSourceUrl: String? = null
        private set
    private var sourceLoaded = false
    private var saving = false

    internal fun restoreOriginal(url: String?, original: RssSource?) {
        persistedSourceUrl = url
        rssSource = original
        sourceLoaded = true
    }

    @MainThread
    fun initData(intent: Intent, onFinally: () -> Unit) {
        if (sourceLoaded) {
            onFinally()
            return
        }
        val sourceUrl = intent.getStringExtra("sourceUrl")
        execute {
            sourceUrl?.let { appDb.rssSourceDao.getByKey(it) }
        }.onSuccess {
            if (!sourceLoaded) {
                persistedSourceUrl = sourceUrl
                rssSource = it
                sourceLoaded = true
            }
        }.onError {
            getApplication<Application>().toastOnUi(it.localizedMessage)
        }.onFinally {
            onFinally()
        }
    }

    @MainThread
    fun save(source: RssSource, success: ((RssSource) -> Unit)) {
        if (!sourceLoaded || saving) return
        val originalUrl = persistedSourceUrl
        val original = rssSource
        val application = getApplication<Application>()
        saving = true
        execute {
            if (source.sourceUrl.isBlank() || source.sourceName.isBlank()) {
                throw NoStackTraceException(application.getString(R.string.non_null_name_url))
            }
            val dao = appDb.rssSourceDao
            val (saved, previous) = appDb.runInTransaction<Pair<RssSource, RssSource?>> {
                val old = originalUrl?.let {
                    dao.getByKey(it) ?: throw NoStackTraceException(
                        application.getString(R.string.source_editor_original_missing)
                    )
                }
                val sameAddress = old?.sourceUrl == source.sourceUrl
                if (!sameAddress && dao.getByKey(source.sourceUrl) != null) {
                    throw NoStackTraceException(application.getString(R.string.source_editor_address_exists))
                }
                if (old != null && (original == null || !old.hasSameEditorConfiguration(original))) {
                    throw NoStackTraceException(application.getString(R.string.source_editor_original_changed))
                }
                val updated = source.copy(
                    customOrder = old?.customOrder ?: source.customOrder,
                    lastUpdateTime = if (old != null && source.hasSameEditorConfiguration(old)) {
                        old.lastUpdateTime
                    } else {
                        System.currentTimeMillis()
                    },
                )
                if (sameAddress) {
                    dao.update(updated)
                } else {
                    dao.insertNew(updated)
                    old?.let {
                        appDb.rssStarDao.updateOrigin(updated.sourceUrl, it.sourceUrl)
                        appDb.rssArticleDao.updateOrigin(updated.sourceUrl, it.sourceUrl)
                        dao.delete(it)
                        appDb.cacheDao.deleteStoredSourceVariables(listOf(it.sourceUrl))
                    }
                }
                updated to old
            }
            withContext(NonCancellable) {
                runCatching {
                    previous?.let {
                        val renamed = it.sourceUrl != saved.sourceUrl
                        if (renamed || it.sortUrl != saved.sortUrl) it.removeSortCache()
                        if (it.jsLib != saved.jsLib) SharedJsScope.remove(it.jsLib)
                        if (renamed) {
                            appDb.runInTransaction {
                                if (dao.getByKey(it.sourceUrl) == null) {
                                    appDb.cacheDao.clearSourceVariablesFromMemory(listOf(it.sourceUrl))
                                }
                            }
                        }
                    }
                }.onFailure { AppLog.put("清理已保存订阅源的缓存失败", it) }
                concurrentRecordMap.remove(saved.sourceUrl)
            }
            saved
        }.onSuccess {
            persistedSourceUrl = it.sourceUrl
            rssSource = it
            success(it)
        }.onError {
            application.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }.onFinally {
            saving = false
        }
    }

    fun pasteSource(onSuccess: (source: RssSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            var source: RssSource? = null
            context.getClipText()?.let { json ->
                source = GSON.fromJsonObject<RssSource>(json).getOrThrow()
            }
            source
        }.onError {
            context.toastOnUi(it.localizedMessage)
        }.onSuccess {
            if (it != null) {
                onSuccess(it)
            } else {
                context.toastOnUi("格式不对")
            }
        }
    }

    fun importSource(text: String, finally: (source: RssSource) -> Unit) {
        execute {
            val text1 = text.trim()
            GSON.fromJsonObject<RssSource>(text1).getOrThrow().let {
                finally.invoke(it)
            }
        }.onError {
            context.toastOnUi(it.stackTraceStr)
        }
    }

    fun clearCookie(url: String) {
        execute {
            CookieStore.removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}
