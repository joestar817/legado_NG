package io.legado.app.ui.book.source.edit

import android.app.Application
import android.webkit.CookieManager
import android.content.Intent
import androidx.annotation.MainThread
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonElement
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.storage.ImportOldData
import io.legado.app.model.SharedJsScope
import io.legado.app.ui.source.edit.SourceEditorSession
import io.legado.app.ui.source.edit.hasSameEditorConfiguration
import io.legado.app.utils.GSON
import io.legado.app.utils.getClipText
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.jsonPath
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.removeCookie
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


class BookSourceEditViewModel(application: Application) : BaseViewModel(application) {
    internal val editor = BookSourceEditState()
    internal val session = SourceEditorSession(application, "book", BookSourceEditorDraft::class.java, viewModelScope)
    var autoComplete: Boolean
        get() = editor.autoComplete
        set(value) { editor.autoComplete = value }
    var bookSource: BookSource? = null
        private set
    internal var persistedSourceUrl: String? = null
        private set
    private var sourceLoaded = false
    private var saving = false

    internal fun restoreOriginal(url: String?, original: BookSource?) {
        persistedSourceUrl = url
        bookSource = original
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
            sourceUrl?.let { appDb.bookSourceDao.getBookSource(it) }
        }.onSuccess {
            if (!sourceLoaded) {
                persistedSourceUrl = sourceUrl
                bookSource = it
                sourceLoaded = true
            }
        }.onError {
            getApplication<Application>().toastOnUi(it.localizedMessage)
        }.onFinally {
            onFinally()
        }
    }

    @MainThread
    fun save(source: BookSource, importedWeight: Int?, success: (BookSource) -> Unit) {
        if (!sourceLoaded || saving) return
        val originalUrl = persistedSourceUrl
        val original = bookSource
        val application = getApplication<Application>()
        saving = true
        execute {
            if (source.bookSourceUrl.isBlank() || source.bookSourceName.isBlank()) {
                throw NoStackTraceException(application.getString(R.string.non_null_name_url))
            }
            val dao = appDb.bookSourceDao
            val (saved, previous, cleanup) = appDb.runInTransaction<SavedBookSource> {
                val old = originalUrl?.let {
                    dao.getBookSource(it) ?: throw NoStackTraceException(
                        application.getString(R.string.source_editor_original_missing)
                    )
                }
                val sameAddress = old?.bookSourceUrl == source.bookSourceUrl
                if (!sameAddress && dao.getBookSourcePart(source.bookSourceUrl) != null) {
                    throw NoStackTraceException(application.getString(R.string.source_editor_address_exists))
                }
                if (old != null && (original == null || !old.hasSameEditorConfiguration(original))) {
                    throw NoStackTraceException(application.getString(R.string.source_editor_original_changed))
                }
                val updated = source.copy(
                    customOrder = old?.customOrder ?: source.customOrder,
                    weight = importedWeight ?: old?.weight ?: source.weight,
                    respondTime = old?.respondTime ?: source.respondTime,
                )
                updated.lastUpdateTime = if (
                    old != null && updated.hasSameEditorConfiguration(old) && updated.weight == old.weight
                ) {
                    old.lastUpdateTime
                } else {
                    System.currentTimeMillis()
                }
                if (sameAddress) {
                    dao.update(updated)
                    SavedBookSource(updated, old)
                } else {
                    dao.insertNew(updated)
                    val cleanupOld = old?.let {
                        dao.delete(it)
                        SourceHelp.deleteRenamedBookSourceStorage(it.bookSourceUrl)
                    }
                    SavedBookSource(updated, old, cleanupOld)
                }
            }
            withContext(NonCancellable) {
                runCatching {
                    previous?.let {
                        if (it.bookSourceUrl != saved.bookSourceUrl || it.exploreUrl != saved.exploreUrl) {
                            it.clearExploreKindsCache()
                        }
                        if (it.jsLib != saved.jsLib) SharedJsScope.remove(it.jsLib)
                    }
                    cleanup?.invoke()
                }.onFailure { AppLog.put("清理已保存书源的缓存失败", it) }
                concurrentRecordMap.remove(saved.bookSourceUrl)
            }
            saved
        }.onSuccess {
            persistedSourceUrl = it.bookSourceUrl
            bookSource = it
            success(it)
        }.onError {
            application.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }.onFinally {
            saving = false
        }
    }

    private data class SavedBookSource(
        val source: BookSource,
        val previous: BookSource?,
        val cleanup: (() -> Unit)? = null,
    )

    internal fun pasteSource(onSuccess: (BookSourceEditImport) -> Unit) {
        val application = getApplication<Application>()
        execute(context = Dispatchers.Main) {
            val text = application.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            } else {
                importSource(text, onSuccess)
            }
        }.onError {
            application.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    internal fun importSource(text: String, onSuccess: (BookSourceEditImport) -> Unit) {
        execute {
            importSource(text)
        }.onSuccess {
            onSuccess(it)
        }.onError {
            getApplication<Application>().toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    internal suspend fun importSource(text: String): BookSourceEditImport {
        val content = text.trim().removePrefix("\uFEFF")
        if (content.isAbsUrl()) {
            val body = okHttpClient.newCallStrResponse { url(content) }.body
                ?: throw NoStackTraceException("格式不对")
            return importSource(body)
        }
        if (!content.isJsonArray() && !content.isJsonObject()) throw NoStackTraceException("格式不对")
        val root = GSON.fromJson(content, JsonElement::class.java)
        val entry = if (root.isJsonArray) root.asJsonArray.firstOrNull() else root
        val json = entry?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw NoStackTraceException("格式不对")
        val mainJs = json.get("mainJs")?.takeIf { it.isJsonPrimitive }?.asString
        val source = if (mainJs.isNullOrBlank() && (json.has("ruleSearchUrl") || json.has("ruleFindUrl"))) {
            ImportOldData.fromOldBookSource(jsonPath.parse(json.toString()))
        } else {
            GSON.fromJson(json, BookSource::class.java)
        }
        val weight: Int? = GSON.fromJson(json.get("weight"), Int::class.java)
        return BookSourceEditImport(source, weight)
    }

    fun clearCookie(url: String) {
        execute {
            BookSourceCookieStore(url).removeCookie(url)
            CookieManager.getInstance().removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}

internal data class BookSourceEditImport(val source: BookSource, val weight: Int?)
