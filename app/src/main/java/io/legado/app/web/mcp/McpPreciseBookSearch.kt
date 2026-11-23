package io.legado.app.web.mcp

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchMedia
import io.legado.app.help.ai.AiBookSourceHit
import io.legado.app.help.ai.AiBookSourceLookup
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.book.BookHelp
import io.legado.app.help.source.SourceInteractionPolicy
import io.legado.app.model.webBook.SearchModel
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.webBook.matchesPreciseBook
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.utils.getPrefBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal object McpPreciseBookSearch {
    const val CAPABILITY = "book_source.search"
    private const val TIMEOUT_MS = 25_000L

    fun sourcesFor(media: AiBookSearchMedia): List<BookSourcePart> =
        appDb.bookSourceDao.allEnabledPart.filter {
            it.hasSearchUrl && it.bookSourceType == media.sourceType &&
                media.matchesAudioLabel("${it.bookSourceName} ${it.bookSourceGroup.orEmpty()}", allowUnclassified = true)
        }

    suspend fun resolveHit(hit: AiBookSourceHit, keyword: String, media: AiBookSearchMedia): SearchBook? {
        val sourcePart = appDb.bookSourceDao.getBookSourcePart(hit.origin)
            ?.takeIf { it.enabled && it.bookSourceType == media.sourceType } ?: return null
        fun matchesHit(book: SearchBook) = book.bookUrl == hit.url && book.origin == hit.origin &&
            matchesPreciseBook(book.name, book.author, hit.title, hit.author) &&
            ChangeSourceFilter.sourceTypeForBook(book.type) == media.sourceType

        appDb.searchBookDao.getSearchBook(hit.url)?.takeIf(::matchesHit)?.let { return it }
        val source = sourcePart.getBookSource()
            ?.takeIf { it.enabled && it.bookSourceType == media.sourceType } ?: return null
        val policy = SourceInteractionPolicy(blockDialogs = appCtx.getPrefBoolean(PreferKey.searchBlockSourceDialogs))
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(policy) {
                WebBook.searchBookAwait(source, keyword, filter = { name, author, _ ->
                    matchesPreciseBook(name, author, hit.title, hit.author)
                }).firstOrNull(::matchesHit)?.also(SearchBook::releaseHtmlData)
            }
        }
    }

    suspend fun run(
        candidate: AiBookSearchCandidate,
        keyword: String,
        scope: String,
        audioScopeConfirmed: Boolean,
        onHits: (List<AiBookSourceHit>) -> Unit,
    ): AiBookSourceLookup = coroutineScope {
        val media = requireNotNull(candidate.media)
        require(candidate.searchable && (keyword == candidate.title || keyword == candidate.alias))
        fun result(status: AiBookSourceStatus) = AiBookSourceLookup(candidate.key, keyword, status)
        val enabled = appDb.bookSourceDao.allEnabledPart
        val inScope = enabled.filter { matchesBookSearchScope(scope, it.bookSourceUrl, it.bookSourceGroup) }
        if (scope.isNotBlank() && inScope.isEmpty()) return@coroutineScope result(AiBookSourceStatus.INVALID_SCOPE)
        val typed = inScope.filter { it.hasSearchUrl && it.bookSourceType == media.sourceType }
        if (typed.isEmpty()) return@coroutineScope result(AiBookSourceStatus.NO_SOURCES)
        val allowUnclassifiedAudio = audioScopeConfirmed && scope.isNotBlank()
        fun acceptsAudio(name: String, groups: String?): Boolean =
            media.matchesAudioLabel("$name ${groups.orEmpty()}", allowUnclassifiedAudio)
        val sources = typed.filter { acceptsAudio(it.bookSourceName, it.bookSourceGroup) }
        if (sources.isEmpty()) return@coroutineScope result(AiBookSourceStatus.AUDIO_SCOPE_REQUIRED)

        val failed = AtomicInteger()
        val namesake = AtomicBoolean()
        val unverified = AtomicBoolean()
        val latest = AtomicReference<List<AiBookSourceHit>>(emptyList())
        val stopped = CompletableDeferred<Boolean>()
        val expectedAuthor = BookHelp.formatBookAuthor(candidate.author)
        if (expectedAuthor.isBlank()) return@coroutineScope result(AiBookSourceStatus.UNVERIFIED)
        val searchModel = SearchModel(this, object : SearchModel.CallBack {
            override fun getSearchScope() = SearchScope(scope)
            override fun onSearchStart() = Unit
            override fun onSearchSourceFailure() { failed.incrementAndGet() }
            override fun onSearchFinish(isEmpty: Boolean, hasMore: Boolean) { stopped.complete(true) }
            override fun onSearchCancel(exception: Throwable?) { stopped.complete(false) }

            override fun onSearchSuccess(searchBooks: List<SearchBook>) {
                val valid = searchBooks.filter { book ->
                    val eligible = ChangeSourceFilter.sourceTypeForBook(book.type) == media.sourceType &&
                        book.bookUrl.isNotBlank()
                    if (!eligible) unverified.set(true)
                    eligible
                }
                val origins = valid.flatMap { it.origins + it.origin }.distinct().size
                val hits = valid.take(1).map { book ->
                    AiBookSourceHit(
                        book.name, book.author, book.bookUrl, book.origin, book.originName, book.coverUrl, origins
                    )
                }
                if (latest.getAndSet(hits) != hits) onHits(hits)
            }
        })
        val done = try {
            searchModel.searchPrecise(System.currentTimeMillis(), keyword, SearchModel.PreciseSearch(
                sources = sources,
                acceptsSource = { source ->
                    source.enabled && source.bookSourceType == media.sourceType &&
                        matchesBookSearchScope(scope, source.bookSourceUrl, source.bookSourceGroup) &&
                        acceptsAudio(source.bookSourceName, source.bookSourceGroup)
                },
                matches = { name, author, _ ->
                    val cleanAuthor = BookHelp.formatBookAuthor(author)
                    val exact = matchesPreciseBook(name, cleanAuthor, keyword, expectedAuthor) ||
                        matchesPreciseBook(name, cleanAuthor, candidate.title, expectedAuthor)
                    if (!exact && (name == keyword || name == candidate.title)) {
                        if (cleanAuthor.isEmpty()) unverified.set(true) else namesake.set(true)
                    }
                    exact
                },
            ))
            withTimeoutOrNull(TIMEOUT_MS) { stopped.await() }
        } finally {
            searchModel.close()
        }
        val hits = latest.get()
        val failureCount = failed.get()
        val status = when {
            done == false || failureCount >= sources.size -> AiBookSourceStatus.FAILED
            done == null || failureCount > 0 -> AiBookSourceStatus.INCOMPLETE
            hits.isEmpty() && unverified.get() -> AiBookSourceStatus.UNVERIFIED
            hits.isEmpty() && namesake.get() -> AiBookSourceStatus.NAMESAKE
            else -> AiBookSourceStatus.COMPLETE
        }
        AiBookSourceLookup(candidate.key, keyword, status, hits, failureCount)
    }
}

internal fun matchesBookSearchScope(scope: String, sourceUrl: String, groups: String?): Boolean = when {
    scope.isBlank() -> true
    "::" in scope -> sourceUrl == scope.substringAfter("::")
    else -> ChangeSourceFilter.splitGroups(groups).any { it in scope.split(',').map(String::trim) }
}
