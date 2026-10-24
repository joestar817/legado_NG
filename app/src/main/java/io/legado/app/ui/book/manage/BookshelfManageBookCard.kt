package io.legado.app.ui.book.manage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.menuCapabilities
import io.legado.app.help.config.BookshelfCardStyle
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgBookshelfManageCard
import io.legado.app.ui.design.components.compose.NgBookshelfManageCardVariant
import io.legado.app.utils.ConvertUtils

internal fun formatBookshelfCacheSize(bytes: Long): String =
    if (bytes == 0L) "0 B" else ConvertUtils.formatFileSize(bytes)

@Composable
internal fun BookshelfManageBookCard(
    book: Book,
    appearance: BookshelfCardStyle,
    selected: Boolean,
    groupNames: String,
    cachedChapterCount: Int?,
    cacheChapterTotal: Int?,
    cacheBytes: Long?,
    dragHandleEnabled: Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onToggleSelected: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val sourceName = if (book.isLocal) {
        stringResource(R.string.local_book)
    } else {
        book.originName
    }
    val compactStyle = NgStatusTagStyle.COMPACT
    val inlineStyle = NgStatusTagStyle.INLINE
    val cachedCountText = cachedChapterCount?.toString() ?: "—"

    NgBookshelfManageCard(
        title = book.name,
        supportingText = book.author,
        metadataText = sourceName,
        modifier = modifier,
        variant = NgBookshelfManageCardVariant.COVER_CARD,
        appearance = appearance,
        cacheText = when {
            !book.menuCapabilities.showCache -> null
            book.isLocal -> cacheBytes?.let(::formatBookshelfCacheSize)
            else -> listOfNotNull(
                "$cachedCountText/${cacheChapterTotal ?: book.totalChapterNum}",
                cacheBytes?.let(::formatBookshelfCacheSize),
            ).joinToString(" · ")
        },
        groupTag = NgStatusTagSpec(
            text = groupNames.ifBlank { stringResource(R.string.no_group) },
            variant = NgStatusTagVariant.NEUTRAL,
            style = inlineStyle
        ),
        updateTag = if (!book.menuCapabilities.update) null else NgStatusTagSpec(
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
        coverContent = {
            NgBookCover(
                book = book,
                modifier = Modifier.fillMaxSize(),
                contentDescription = stringResource(R.string.img_cover),
            )
        },
        dragHandleContentDescription = stringResource(R.string.bookshelf_px_3),
        dragHandleModifier = dragHandleModifier,
        dragHandleEnabled = dragHandleEnabled,
        onClick = onToggleSelected,
        onLongClick = onOpenDetails
    )
}
