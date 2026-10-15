package io.legado.app.ui.book.manage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgBookshelfManageCard
import io.legado.app.ui.design.components.compose.NgBookshelfManageCardVariant

@Composable
internal fun BookshelfManageBookCard(
    book: Book,
    selected: Boolean,
    selectionMode: Boolean,
    selectionDimmed: Boolean,
    groupNames: String,
    cachedChapterCount: Int?,
    cacheSizeBytes: Long?,
    cacheState: CacheBook.BookState,
    onCacheClick: () -> Unit,
    onCacheLongPress: () -> Unit,
    onToggleSelected: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val sourceName = if (book.isLocal) {
        stringResource(R.string.local_book)
    } else {
        book.originName
    }
    val compactStyle = NgStatusTagStyle.COMPACT
    val inlineStyle = NgStatusTagStyle.INLINE
    val cachedCountText = cachedChapterCount?.toString() ?: "—"
    // 卡内不再画进度条:同一进度已由右侧环钮表达,两处并列只是重复。
    val cacheFraction = cachedChapterCount
        ?.takeIf { !book.isLocal && book.totalChapterNum > 0 }
        ?.let { (it.toFloat() / book.totalChapterNum).coerceIn(0f, 1f) }
    val ringState = when {
        book.isLocal -> BookshelfCacheRingState.LOCAL
        cacheState == CacheBook.BookState.PAUSED -> BookshelfCacheRingState.PAUSED
        cacheState == CacheBook.BookState.RUNNING -> BookshelfCacheRingState.RUNNING
        cacheState == CacheBook.BookState.WAITING -> BookshelfCacheRingState.QUEUED
        cacheFraction == 1f -> BookshelfCacheRingState.CACHED
        else -> BookshelfCacheRingState.EMPTY
    }

    NgBookshelfManageCard(
        title = book.name,
        supportingText = book.author,
        metadataText = sourceName,
        modifier = modifier,
        variant = NgBookshelfManageCardVariant.COVER_CARD,
        cacheText = if (book.isLocal) {
            null
        } else {
            "$cachedCountText/${book.totalChapterNum}"
        },
        cacheSizeText = cacheSizeBytes
            ?.takeIf { it > 0L && !book.isLocal }
            ?.let(::formatCacheSize),
        // 未分组不出标签:整列「未分组」药丸不提供信息,只挤占书名与元数据的宽度。
        groupTag = groupNames.takeIf { it.isNotBlank() }?.let { names ->
            NgStatusTagSpec(
                text = names,
                variant = NgStatusTagVariant.NEUTRAL,
                style = inlineStyle
            )
        },
        updateTag = NgStatusTagSpec(
            text = stringResource(
                if (book.canUpdate) R.string.allow_update else R.string.disable_update
            ),
            variant = if (book.canUpdate) {
                NgStatusTagVariant.SUCCESS
            } else {
                NgStatusTagVariant.ERROR
            },
            style = compactStyle
        ),
        selected = selected,
        dimmed = selectionDimmed,
        // 手柄只在选中态出现:默认态它只能拖拽排序,却容易被当成菜单入口。
        // 选中态里未选中的卡也显示手柄(灰色),让「现在可以拖动排序」这件事对整屏成立。
        showDragHandle = selectionMode,
        coverContent = {
            NgBookCover(
                book = book,
                modifier = Modifier.fillMaxSize(),
                contentDescription = stringResource(R.string.img_cover),
            )
        },
        cacheActionContent = {
            BookshelfCacheRingButton(
                state = ringState,
                progress = cacheFraction ?: 0f,
                contentDescription = ringContentDescription(
                    state = ringState,
                    cachedCount = cachedChapterCount ?: 0,
                    totalCount = book.totalChapterNum,
                ),
                onClick = onCacheClick,
                onLongClick = onCacheLongPress,
            )
        },
        dragHandleContentDescription = stringResource(R.string.bookshelf_px_3),
        dragHandleModifier = dragHandleModifier,
        onClick = onToggleSelected,
        onLongClick = onOpenDetails
    )
}

@Composable
private fun ringContentDescription(
    state: BookshelfCacheRingState,
    cachedCount: Int,
    totalCount: Int,
): String = when (state) {
    BookshelfCacheRingState.LOCAL -> stringResource(R.string.cache_ring_local)
    BookshelfCacheRingState.CACHED -> stringResource(
        R.string.cache_ring_cached,
        totalCount,
        totalCount,
    )

    BookshelfCacheRingState.RUNNING,
    BookshelfCacheRingState.PAUSED,
    BookshelfCacheRingState.QUEUED,
    BookshelfCacheRingState.EMPTY -> stringResource(
        R.string.cache_ring_cached,
        cachedCount,
        totalCount,
    )
}
