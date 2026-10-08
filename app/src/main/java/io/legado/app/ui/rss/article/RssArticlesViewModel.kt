package io.legado.app.ui.rss.article

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.model.rss.Rss
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class RssArticlesViewModel(application: Application) : BaseViewModel(application) {
    private val mutableLoadState = MutableLiveData(RssArticlesLoadState())
    internal val loadState: LiveData<RssArticlesLoadState> = mutableLoadState
    var sortName: String = ""
    var sortUrl: String = ""
    var searchKey: String? = null

    private val paging = RssArticlesPaging(
        scope = viewModelScope,
        fetch = { request ->
            withContext(IO) {
                Rss.getArticlesAwait(
                    request.query.sortName, request.url, request.query.source,
                    request.page, request.query.searchKey
                )
            }
        },
        store = { request, articles ->
            withContext(IO) {
                var nextOrder = request.order
                // Preserve refresh REPLACE order when a feed repeats the same article.
                val orderedArticles = articles.map { it.copy(order = nextOrder--) }
                var addedCount = 0
                val requestContext = coroutineContext
                appDb.runInTransaction {
                    requestContext.ensureActive()
                    if (request.refreshing) {
                        appDb.rssArticleDao.insert(*orderedArticles.toTypedArray())
                        if (!request.query.source.ruleNextPage.isNullOrEmpty()) {
                            appDb.rssArticleDao.clearOld(
                                request.query.source.sourceUrl, request.query.sortName, nextOrder
                            )
                        }
                        addedCount = orderedArticles.size
                    } else {
                        // IGNORE keeps existing entries in place. Inspect every insertion result,
                        // including new entries between a repeated first and last article.
                        addedCount = appDb.rssArticleDao.append(*orderedArticles.toTypedArray())
                            .count { it != -1L }
                    }
                    requestContext.ensureActive()
                }
                RssArticlesStoredPage(addedCount, nextOrder)
            }
        },
        onStateChanged = { mutableLoadState.value = it },
        errorMessage = {
            AppLog.put("rss获取内容失败", it)
            it.stackTraceStr
        }
    )

    fun init(bundle: Bundle?) {
        bundle?.let {
            sortName = it.getString("sortName") ?: ""
            sortUrl = it.getString("sortUrl") ?: ""
            searchKey = it.getString("searchKey")
        }
    }

    fun loadArticles(rssSource: RssSource) = paging.refresh(query(rssSource))

    fun loadMore(rssSource: RssSource) = paging.loadMore(query(rssSource))

    fun retry(rssSource: RssSource) = paging.retry(query(rssSource))

    private fun query(source: RssSource) = RssArticlesQuery(source, sortName, sortUrl, searchKey)
}
