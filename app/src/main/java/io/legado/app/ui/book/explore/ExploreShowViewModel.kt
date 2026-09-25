package io.legado.app.ui.book.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.isOpenableExploreCategory
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal data class ExploreShowUiState(
    val source: BookSource? = null,
    val sourceName: String = "",
    val kinds: List<io.legado.app.data.entities.rule.ExploreKind> = emptyList(),
    val selectedKind: io.legado.app.data.entities.rule.ExploreKind? = null,
    val books: List<SearchBook> = emptyList(),
    val bookPages: Map<String, Int> = emptyMap(),
    val bookshelfKeys: Set<String> = emptySet(),
    val isKindsLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContentLoading: Boolean = false,
    val isLoadingPrevious: Boolean = false,
    val kindsError: String? = null,
    val contentError: String? = null,
    val firstLoadedPage: Int = 1,
    val lastLoadedPage: Int = 0,
    val hasMore: Boolean = true,
    val selectionRevision: Long = 0L
) {
    val displayPage: Int
        get() = lastLoadedPage.takeIf { it > 0 } ?: firstLoadedPage
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreShowViewModel(application: Application) : BaseViewModel(application) {

    private enum class Placement {
        RESET,
        APPEND,
        PREPEND
    }

    private val _uiState = MutableStateFlow(ExploreShowUiState())
    internal val uiState: StateFlow<ExploreShowUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var contentJob: Job? = null
    private var contentRequestId = 0L
    private data class FailedPage(val page: Int, val placement: Placement, val kindUrl: String)
    private var failedPage: FailedPage? = null

    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                buildSet {
                    books.filterNot { it.isNotShelf }.forEach { book ->
                        add("${book.name}-${book.author}")
                        add(book.name)
                        add(book.bookUrl)
                    }
                }
            }.catch {
                AppLog.put("发现列表界面获取书籍数据失败\n${it.localizedMessage}", it)
            }.collect { keys ->
                _uiState.update { it.copy(bookshelfKeys = keys) }
            }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    fun initData(
        sourceUrl: String?,
        sourceName: String?,
        initialExploreUrl: String?,
        initialExploreName: String?
    ) {
        if (initialized) return
        initialized = true
        _uiState.update {
            it.copy(sourceName = sourceName.orEmpty(), isKindsLoading = true)
        }
        viewModelScope.launch {
            val source = withContext(IO) {
                sourceUrl?.let { appDb.bookSourceDao.getBookSource(it) }
            }
            if (source == null) {
                _uiState.update {
                    it.copy(
                        isKindsLoading = false,
                        kindsError = "Book source not found"
                    )
                }
                return@launch
            }
            val kinds = source.exploreKinds()
            val errorKind = kinds.firstOrNull { it.title.startsWith("ERROR:") }
            val selectedKind = initialExploreUrl?.takeIf { it.isNotBlank() }?.let { url ->
                kinds.firstOrNull { it.url == url }
                    ?: io.legado.app.data.entities.rule.ExploreKind(
                        title = initialExploreName.orEmpty(),
                        url = url
                    )
            } ?: kinds.firstOrNull { it.isOpenableExploreCategory() }

            _uiState.update {
                it.copy(
                    source = source,
                    sourceName = source.bookSourceName,
                    kinds = kinds,
                    selectedKind = selectedKind,
                    isKindsLoading = false,
                    kindsError = errorKind?.url
                )
            }
            if (errorKind == null && selectedKind != null) {
                loadPage(1, Placement.RESET)
            }
        }
    }

    fun selectKind(kind: io.legado.app.data.entities.rule.ExploreKind) {
        if (!kind.isOpenableExploreCategory()) return
        contentJob?.cancel()
        contentRequestId++
        _uiState.update {
            it.copy(
                selectedKind = kind,
                books = emptyList(),
                bookPages = emptyMap(),
                isContentLoading = false,
                isLoadingPrevious = false,
                contentError = null,
                firstLoadedPage = 1,
                lastLoadedPage = 0,
                hasMore = true,
                selectionRevision = it.selectionRevision + 1
            )
        }
        loadPage(1, Placement.RESET)
    }

    fun reloadKinds() {
        val source = _uiState.value.source ?: return
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isKindsLoading = true, kindsError = null) }
            source.clearExploreKindsCache()
            val kinds = source.exploreKinds()
            val errorKind = kinds.firstOrNull { it.title.startsWith("ERROR:") }
            val current = _uiState.value.selectedKind
            val selected = current?.url?.let { url -> kinds.firstOrNull { it.url == url } }
                ?: kinds.firstOrNull { it.isOpenableExploreCategory() }
            val selectionChanged = selected?.url != current?.url
            _uiState.update {
                it.copy(
                    kinds = kinds,
                    selectedKind = selected,
                    isKindsLoading = false,
                    kindsError = errorKind?.url,
                    selectionRevision = if (selectionChanged) {
                        it.selectionRevision + 1
                    } else {
                        it.selectionRevision
                    }
                )
            }
            if (errorKind == null && selectionChanged && selected != null) {
                contentJob?.cancel()
                contentRequestId++
                _uiState.update {
                    it.copy(
                        books = emptyList(),
                        bookPages = emptyMap(),
                        firstLoadedPage = 1,
                        lastLoadedPage = 0,
                        hasMore = true
                    )
                }
                loadPage(1, Placement.RESET)
            }
        }
    }

    fun refresh() {
        val source = _uiState.value.source ?: return
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isKindsLoading = true,
                    kindsError = null,
                    contentError = null
                )
            }
            try {
                source.clearExploreKindsCache()
                val kinds = source.exploreKinds()
                val errorKind = kinds.firstOrNull { it.title.startsWith("ERROR:") }
                val current = _uiState.value.selectedKind
                val selected = current?.url?.let { url -> kinds.firstOrNull { it.url == url } }
                    ?: kinds.firstOrNull { it.isOpenableExploreCategory() }

                contentJob?.cancel()
                contentRequestId++
                _uiState.update {
                    it.copy(
                        kinds = kinds,
                        selectedKind = selected,
                        books = emptyList(),
                        bookPages = emptyMap(),
                        isKindsLoading = false,
                        isContentLoading = false,
                        isLoadingPrevious = false,
                        kindsError = errorKind?.url,
                        contentError = null,
                        firstLoadedPage = 1,
                        lastLoadedPage = 0,
                        hasMore = true,
                        selectionRevision = it.selectionRevision + 1
                    )
                }
                if (errorKind == null && selected != null) {
                    loadPage(1, Placement.RESET)
                    contentJob?.join()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printOnDebug()
                _uiState.update {
                    it.copy(
                        isKindsLoading = false,
                        kindsError = e.stackTraceStr
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isKindsLoading = false
                    )
                }
            }
        }
    }

    fun retryContent() {
        val state = _uiState.value
        if (state.selectedKind == null || state.isContentLoading) return
        val failed = failedPage ?: return
        if (failed.kindUrl != state.selectedKind.url) return
        loadPage(failed.page, failed.placement)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.selectedKind == null || state.isContentLoading || !state.hasMore) return
        val page = (state.lastLoadedPage + 1).coerceAtLeast(1)
        loadPage(page, if (state.books.isEmpty()) Placement.RESET else Placement.APPEND)
    }

    fun loadPreviousPage() {
        val state = _uiState.value
        if (state.selectedKind == null || state.isContentLoading || state.firstLoadedPage <= 1) return
        loadPage(state.firstLoadedPage - 1, Placement.PREPEND)
    }

    fun navigateToPage(page: Int) {
        val state = _uiState.value
        if (state.isContentLoading || state.isKindsLoading || state.isRefreshing) return
        if (page > state.lastLoadedPage && !state.hasMore) return
        jumpToPage(page)
    }

    fun jumpToPage(page: Int) {
        if (page <= 0 || _uiState.value.selectedKind == null) return
        // Keep the accepted page until the requested page proves it has content.
        // A duplicate/empty response or a failed request must not invent a new page.
        loadPage(page, Placement.RESET)
    }

    private fun loadPage(page: Int, placement: Placement) {
        val state = _uiState.value
        val source = state.source ?: return
        val selected = state.selectedKind ?: return
        val url = selected.url ?: return
        failedPage = null
        val requestId = ++contentRequestId
        contentJob?.cancel()
        _uiState.update {
            it.copy(
                isContentLoading = true,
                isLoadingPrevious = placement == Placement.PREPEND,
                contentError = null
            )
        }
        contentJob = viewModelScope.launch(IO) {
            try {
                val searchBooks = if (BuildConfig.DEBUG) {
                    WebBook.exploreBookAwait(source, url, page)
                } else {
                    withTimeout(60_000L) {
                        WebBook.exploreBookAwait(source, url, page)
                    }
                }
                if (requestId != contentRequestId || _uiState.value.selectedKind?.url != url) {
                    return@launch
                }
                val accepted = _uiState.value
                val before = accepted.books
                val decision = evaluateExplorePage(
                    knownUrls = before.mapTo(hashSetOf()) { it.bookUrl },
                    incomingUrls = searchBooks.map { it.bookUrl },
                    targetPage = page,
                    lastAcceptedPage = accepted.lastLoadedPage
                )
                if (decision != ExplorePageDecision.ACCEPT) {
                    _uiState.update {
                        it.copy(
                            isContentLoading = false,
                            isLoadingPrevious = false,
                            contentError = null,
                            hasMore = if (decision == ExplorePageDecision.END) false else it.hasMore
                        )
                    }
                    return@launch
                }
                val merged = when (placement) {
                    Placement.RESET -> searchBooks
                    Placement.APPEND -> before + searchBooks
                    Placement.PREPEND -> searchBooks + before
                }.distinctBy { it.bookUrl }
                _uiState.update {
                    it.copy(
                        books = merged,
                        bookPages = buildMap {
                            if (placement != Placement.RESET) putAll(it.bookPages)
                            searchBooks.forEach { book ->
                                if (placement == Placement.PREPEND || book.bookUrl !in this) {
                                    put(book.bookUrl, page)
                                }
                            }
                        },
                        isContentLoading = false,
                        isLoadingPrevious = false,
                        contentError = null,
                        firstLoadedPage = when (placement) {
                            Placement.RESET, Placement.PREPEND -> page
                            Placement.APPEND -> it.firstLoadedPage
                        },
                        lastLoadedPage = when (placement) {
                            Placement.RESET, Placement.APPEND -> page
                            Placement.PREPEND -> it.lastLoadedPage
                        },
                        hasMore = if (placement == Placement.PREPEND) it.hasMore
                            else searchBooks.isNotEmpty(),
                        selectionRevision = if (placement == Placement.RESET) {
                            it.selectionRevision + 1
                        } else it.selectionRevision
                    )
                }
                appDb.searchBookDao.insert(*searchBooks.toTypedArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.printOnDebug()
                if (requestId == contentRequestId) {
                    failedPage = FailedPage(page, placement, url)
                    _uiState.update {
                        it.copy(
                            isContentLoading = false,
                            isLoadingPrevious = false,
                            contentError = e.stackTraceStr
                        )
                    }
                }
            }
        }
    }
}
