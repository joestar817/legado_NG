package io.legado.app.ui.book.manage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
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
    groupNames: String,
    cachedChapterCount: Int?,
    coverRevision: Int,
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
        cacheText = if (book.isLocal) {
            null
        } else {
            "$cachedCountText/${book.totalChapterNum}"
        },
        groupTag = NgStatusTagSpec(
            text = groupNames.ifBlank { stringResource(R.string.no_group) },
            variant = NgStatusTagVariant.NEUTRAL,
            style = inlineStyle
        ),
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
        coverContent = {
            NgBookCover(
                book = book,
                modifier = Modifier.fillMaxSize(),
                contentDescription = stringResource(R.string.img_cover),
                revision = coverRevision,
            )
        },
        dragHandleContentDescription = stringResource(R.string.bookshelf_px_3),
        dragHandleModifier = dragHandleModifier,
        onClick = onToggleSelected,
        onLongClick = onOpenDetails
    )
}
