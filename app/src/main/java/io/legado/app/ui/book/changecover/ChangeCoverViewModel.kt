package io.legado.app.ui.book.changecover

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceInteractionPolicy
import io.legado.app.model.BookCover
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import io.legado.app.ui.book.changesource.ChangeSourceRequestGuard
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executors
import kotlin.math.min

class ChangeCoverViewModel(application: Application) : BaseViewModel(application) {
    private val threadCount = AppConfig.threadCount
    private val searchLock = Any()
    private val cacheLock = Any()
    private val requestGuard = ChangeSourceRequestGuard()
    private val interactionPolicy = SourceInteractionPolicy(
        context.getPrefBoolean(PreferKey.changeCoverBlockSourceDialogs)
    )
    private var task: Job? = null
    private var initialized = false
    private var name = ""
    private var author = ""
    private var filter = ChangeSourceFilter(BookSourceType.default, false, "")
    private var enabledSources = emptyList<BookSourcePart>()
    private var sourceSignature: List<CoverSourceSignature>? = null
    private val searchBooks = arrayListOf<SearchBook>()
    private val defaultCover by lazy {
        SearchBook(
            originName = "默认封面",
            name = name,
            author = author,
            coverUrl = "use_default_cover",
        )
    }

    private var coversRevision = 0L
    private val _covers = MutableStateFlow(CoverResults(coversRevision, emptyList()))
    val dataFlow = _covers.map { it.items }
    private val _searchState = MutableStateFlow(0)
    val searchState = _searchState.asStateFlow()
    private val _progress = MutableStateFlow(ChangeCoverSearchProgress())
    internal val progress = _progress.asStateFlow()
    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups = _groups.asStateFlow()
    private val _selectedGroup = MutableStateFlow("")
    val selectedGroup = _selectedGroup.asStateFlow()
    private val _sameTypeOnly = MutableStateFlow(false)
    val sameTypeOnly = _sameTypeOnly.asStateFlow()
    private val _blockSourceDialogs = MutableStateFlow(interactionPolicy.blockDialogs)
    val blockSourceDialogs = _blockSourceDialogs.asStateFlow()

    fun initData(arguments: Bundle?) {
        if (initialized) return
        initialized = true
        name = arguments?.getString("name").orEmpty()
        author = arguments?.getString("author").orEmpty().replace(AppPattern.authorRegex, "")
        val sourceType = arguments?.getInt("sourceType", BookSourceType.default)
            ?: BookSourceType.default
        val sameType = context.getPrefBoolean(PreferKey.changeCoverSameTypeOnly)
        val group = context.getPrefString(PreferKey.changeCoverSourceGroup).orEmpty()
        synchronized(searchLock) {
            filter = ChangeSourceFilter(sourceType, sameType, group)
            _sameTypeOnly.value = sameType
            _selectedGroup.value = group
        }
        viewModelScope.launch(IO) {
            appDb.bookSourceDao.flowEnabled().collect(::onSourcesChanged)
        }
    }

    private fun onSourcesChanged(sources: List<BookSourcePart>) {
        val signature = sources.map {
            CoverSourceSignature(it.bookSourceUrl, it.bookSourceGroup, it.bookSourceType, it.customOrder)
        }
        val changed = synchronized(searchLock) {
            enabledSources = sources.toList()
            updateGroupsLocked()
            if (sourceSignature == signature) {
                false
            } else {
                sourceSignature = signature
                true
            }
        }
        if (changed) loadCachedCovers()
    }

    private fun updateGroupsLocked() {
        val groups = enabledSources.asSequence()
            .filter { it.enabled }
            .flatMap { ChangeSourceFilter.splitGroups(it.bookSourceGroup).asSequence() }
            .toMutableSet()
        if (filter.group.isNotBlank()) groups.add(filter.group)
        _groups.value = groups.sortedWith { a, b -> a.cnCompare(b) }
    }

    fun setSameTypeOnly(value: Boolean) {
        synchronized(searchLock) {
            if (filter.sameTypeOnly == value) return
            filter = filter.copy(sameTypeOnly = value)
            _sameTypeOnly.value = value
        }
        context.putPrefBoolean(PreferKey.changeCoverSameTypeOnly, value)
        loadCachedCovers()
    }

    fun setGroup(group: String) {
        synchronized(searchLock) {
            if (filter.group == group) return
            filter = filter.copy(group = group)
            _selectedGroup.value = group
            updateGroupsLocked()
        }
        context.putPrefString(PreferKey.changeCoverSourceGroup, group)
        loadCachedCovers()
    }

    fun setBlockSourceDialogs(value: Boolean) {
        context.putPrefBoolean(PreferKey.changeCoverBlockSourceDialogs, value)
        interactionPolicy.updateBlockDialogs(value)
        _blockSourceDialogs.value = value
    }

    private fun loadCachedCovers() {
        launchRequest(clearResults = true) { request, sources ->
            val cached = matchingCachedCovers(
                appDb.searchBookDao.getEnableHasCover(name, author),
                sources,
                request.filter,
            )
            if (!replaceCovers(request, cached)) return@launchRequest
            if (cached.size <= 1) {
                if (!replaceCovers(request, emptyList())) return@launchRequest
                searchWithCoverRule(request, sources)
            } else {
                setSearchState(request, 0)
            }
        }
    }

    fun startOrStopSearch() {
        when (_searchState.value) {
            1 -> stopSearch()
            2 -> launchRequest(clearResults = false) { request, sources ->
                searchSources(request, sources)
            }
            else -> launchRequest(clearResults = true) { request, sources ->
                searchWithCoverRule(request, sources)
            }
        }
    }

    private fun launchRequest(
        clearResults: Boolean,
        block: suspend (ChangeSourceRequestGuard.Request, List<BookSourcePart>) -> Unit,
    ) {
        val nextTask = synchronized(searchLock) {
            val request = requestGuard.begin(filter)
            task?.cancel()
            val sources = enabledSources.toList()
            if (clearResults) {
                searchBooks.clear()
                publishCoversLocked()
            }
            _progress.value = ChangeCoverSearchProgress(resultCount = searchBooks.size)
            _searchState.value = 1
            val next = viewModelScope.launch(IO + interactionPolicy, start = CoroutineStart.LAZY) {
                try {
                    block(request, sources)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.put("封面换源搜索出错\n${error.localizedMessage}", error)
                    setSearchState(request, 0)
                }
            }
            task = next
            next
        }
        nextTask.start()
    }

    private suspend fun searchWithCoverRule(
        request: ChangeSourceRequestGuard.Request,
        sources: List<BookSourcePart>,
    ) {
        val coverUrl = try {
            BookCover.searchCover(Book(name = name, author = author))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.put("封面规则搜索出错\n${error.localizedMessage}", error)
            null
        }
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        if (!coverUrl.isNullOrEmpty()) {
            addCover(
                request,
                SearchBook(
                    originName = "封面规则",
                    name = name,
                    author = author,
                    coverUrl = coverUrl,
                    originOrder = -1,
                ),
            )
            setSearchState(request, 2)
        } else {
            searchSources(request, sources)
        }
    }

    private suspend fun searchSources(
        request: ChangeSourceRequestGuard.Request,
        sources: List<BookSourcePart>,
    ) {
        val matchingSources = matchingCoverSources(sources, request.filter)
        synchronized(searchLock) {
            if (!requestGuard.isCurrent(request, filter)) return
            _progress.value = ChangeCoverSearchProgress(
                resultCount = searchBooks.size,
                total = matchingSources.size,
            )
        }
        val dispatcher = Executors.newFixedThreadPool(
            min(threadCount, AppConst.MAX_THREAD)
        ).asCoroutineDispatcher()
        try {
            withContext(dispatcher) {
                searchCoverSources(
                    sources = matchingSources,
                    concurrency = threadCount,
                    search = { source -> withTimeout(60000L) { searchSource(request, source) } },
                    onCompleted = { source -> completeSource(request, source.bookSourceName) },
                )
            }
        } finally {
            dispatcher.close()
            setSearchState(request, 0)
        }
    }

    private suspend fun searchSource(
        request: ChangeSourceRequestGuard.Request,
        part: BookSourcePart,
    ) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        val source = part.getBookSource() ?: return
        if (!request.filter.acceptsSource(source.bookSourceType, source.enabled, source.bookSourceGroup) ||
            source.getSearchRule().coverUrl.isNullOrBlank()
        ) return
        val result = WebBook.searchBookAwait(source, name, shouldBreak = { it > 0 })
            .firstOrNull() ?: return
        if (result.name != name || result.author != author || result.coverUrl.isNullOrEmpty()) return
        currentCoroutineContext().ensureActive()
        val latest = appDb.bookSourceDao.getBookSourcePart(result.origin) ?: return
        if (!request.filter.acceptsSource(latest.bookSourceType, latest.enabled, latest.bookSourceGroup)) return
        synchronized(cacheLock) {
            if (!isCurrent(request)) return
            appDb.searchBookDao.insert(result)
        }
        currentCoroutineContext().ensureActive()
        addCover(request, result)
    }

    private fun isCurrent(request: ChangeSourceRequestGuard.Request): Boolean =
        synchronized(searchLock) { requestGuard.isCurrent(request, filter) }

    private fun replaceCovers(
        request: ChangeSourceRequestGuard.Request,
        covers: List<SearchBook>,
    ): Boolean = synchronized(searchLock) {
        if (!requestGuard.isCurrent(request, filter)) return@synchronized false
        searchBooks.clear()
        searchBooks.addAll(covers)
        publishCoversLocked()
        true
    }

    private fun addCover(request: ChangeSourceRequestGuard.Request, cover: SearchBook) {
        synchronized(searchLock) {
            if (!requestGuard.isCurrent(request, filter)) return
            if (!searchBooks.contains(cover)) {
                searchBooks.add(cover)
                publishCoversLocked()
            }
        }
    }

    private fun publishCoversLocked() {
        _covers.value = CoverResults(
            ++coversRevision,
            listOf(defaultCover) + searchBooks.sortedBy { it.originOrder },
        )
        _progress.value = _progress.value.copy(resultCount = searchBooks.size)
    }

    private fun completeSource(request: ChangeSourceRequestGuard.Request, name: String) {
        synchronized(searchLock) {
            if (!requestGuard.isCurrent(request, filter)) return
            val progress = _progress.value
            _progress.value = progress.copy(
                completed = (progress.completed + 1).coerceAtMost(progress.total),
                sourceName = name,
            )
        }
    }

    private fun setSearchState(request: ChangeSourceRequestGuard.Request, state: Int) {
        synchronized(searchLock) {
            if (requestGuard.isCurrent(request, filter)) _searchState.value = state
        }
    }

    private fun stopSearch() {
        synchronized(searchLock) {
            requestGuard.invalidate()
            task?.cancel()
            task = null
            _searchState.value = 0
        }
    }

    override fun onCleared() {
        stopSearch()
        super.onCleared()
    }
}

private data class CoverSourceSignature(
    val url: String,
    val group: String?,
    val type: Int,
    val order: Int,
)

/** SearchBook 的相等性只比较 URL，修订号确保封面地址变化仍会刷新。 */
private data class CoverResults(val revision: Long, val items: List<SearchBook>)

internal data class ChangeCoverSearchProgress(
    val resultCount: Int = 0,
    val completed: Int = 0,
    val total: Int = 0,
    val sourceName: String = "",
)
