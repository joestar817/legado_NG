package io.legado.app.ui.book.changecover

import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changesource.ChangeSourceFilter
import io.legado.app.utils.mapParallelSafe
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** 缓存结果与新搜索使用同一批可用书源，避免切换范围后出现范围外封面。 */
internal fun matchingCoverSources(
    sources: List<BookSourcePart>,
    filter: ChangeSourceFilter,
): List<BookSourcePart> = sources.filter {
    filter.acceptsSource(it.bookSourceType, it.enabled, it.bookSourceGroup)
}

internal fun matchingCachedCovers(
    covers: List<SearchBook>,
    sources: List<BookSourcePart>,
    filter: ChangeSourceFilter,
): List<SearchBook> {
    val origins = matchingCoverSources(sources, filter).mapTo(HashSet()) { it.bookSourceUrl }
    return covers.filter { it.origin in origins }
}

/** 每个书源结束时更新进度，单个书源失败不影响其余书源。 */
internal suspend fun searchCoverSources(
    sources: List<BookSourcePart>,
    concurrency: Int,
    search: suspend (BookSourcePart) -> Unit,
    onCompleted: (BookSourcePart) -> Unit,
) {
    flow { sources.forEach { emit(it) } }
        .mapParallelSafe(concurrency) { source ->
            try {
                search(source)
            } catch (_: Throwable) {
                currentCoroutineContext().ensureActive()
            }
            source
        }
        .collect { onCompleted(it) }
}
