package io.legado.app.ui.book.character

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class StoryboardCacheUiRow(
    val chapterIndex: Int,
    val chapterNumber: String,
    val title: String,
    val meta: String,
    val isCurrent: Boolean,
    val deletable: Boolean,
)

@Composable
internal fun BookStoryboardCacheScreen(
    rows: List<StoryboardCacheUiRow>,
    loading: Boolean,
    loadingMessage: String,
    errorMessage: String?,
    selectionMode: Boolean,
    selectedChapterIndexes: Set<Int>,
    onBack: () -> Unit,
    onExitSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onEnterSelectionWithChapter: (Int) -> Unit,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRowClick: (Int) -> Unit,
    onDeleteRequested: (Int) -> Unit,
) {
    BackHandler(enabled = selectionMode, onBack = onExitSelection)
    val selectableCount = rows.count { it.deletable }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        StoryboardCacheTopBar(
            batchManageEnabled = !loading && selectableCount > 0,
            selectionMode = selectionMode,
            onBack = if (selectionMode) onExitSelection else onBack,
            onEnterSelection = onEnterSelection,
        )
        when {
            loading -> StoryboardCacheMessage(
                message = loadingMessage,
                loading = true,
                modifier = Modifier.weight(1f),
            )

            errorMessage != null -> StoryboardCacheMessage(
                message = errorMessage,
                modifier = Modifier.weight(1f),
            )

            rows.isEmpty() -> StoryboardCacheMessage(
                message = stringResource(R.string.book_storyboard_empty),
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    top = 4.dp,
                    end = 14.dp,
                    bottom = 24.dp,
                ),
            ) {
                items(
                    items = rows,
                    key = { row -> row.chapterIndex },
                ) { row ->
                    StoryboardCacheRow(
                        row = row,
                        selectionMode = selectionMode,
                        selected = row.chapterIndex in selectedChapterIndexes,
                        onClick = {
                            if (selectionMode) {
                                if (row.deletable) onToggleSelection(row.chapterIndex)
                            } else {
                                onRowClick(row.chapterIndex)
                            }
                        },
                        onLongClick = {
                            if (row.deletable) onEnterSelectionWithChapter(row.chapterIndex)
                        },
                        onDeleteRequested = { onDeleteRequested(row.chapterIndex) },
                    )
                }
            }
        }
        if (selectionMode) {
            StoryboardSelectionDock(
                selectedCount = selectedChapterIndexes.size,
                totalCount = selectableCount,
                onSelectAll = onSelectAll,
                onInvertSelection = onInvertSelection,
                onDelete = onDeleteSelected,
            )
        }
    }
}

@Composable
private fun StoryboardCacheTopBar(
    batchManageEnabled: Boolean,
    selectionMode: Boolean,
    onBack: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            color = colorResource(R.color.ng_surface_card),
            shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(36.dp)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left_search),
                        contentDescription = stringResource(R.string.back),
                        tint = Color(NgTheme.colors.onTopBar),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (selectionMode) {
                            R.string.book_storyboard_selection_title
                        } else {
                            R.string.book_storyboard
                        },
                    ),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    color = Color(NgTheme.colors.onTopBar),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(36.dp)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.book_storyboard_selection_done),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                enabled = batchManageEnabled,
                                role = Role.Button,
                                onClick = onEnterSelection,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.character_batch_manage),
                            tint = Color(NgTheme.colors.onSurface).copy(
                                alpha = if (batchManageEnabled) 1f else 0.38f,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryboardCacheRow(
    row: StoryboardCacheUiRow,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    val corner = NgTheme.shapes.smallDp.dp
    val shape = RoundedCornerShape(corner)
    NgSwipeToDelete(
        deletable = row.deletable && !selectionMode,
        reordering = false,
        onDeleteRequested = onDeleteRequested,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storyboard_cache_${row.chapterIndex}"),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.border(1.5.dp, Color(NgTheme.colors.primary), shape)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .semantics { this.selected = selected }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            color = colorResource(R.color.ng_surface_card),
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChapterIndex(
                        number = row.chapterNumber,
                        modifier = Modifier.width(42.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = row.title,
                            color = colorResource(R.color.primaryText),
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = row.meta,
                            modifier = Modifier.padding(top = 4.dp),
                            color = colorResource(R.color.tv_text_summary),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (row.isCurrent) {
                        NgStatusTag(
                            text = "当前",
                            variant = NgStatusTagVariant.INFO,
                            style = NgStatusTagStyle.TTS_ROLE,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (selectionMode) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = null,
                            enabled = row.deletable,
                            modifier = Modifier.size(48.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(NgTheme.colors.primary),
                                checkmarkColor = Color.White,
                            ),
                        )
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right_20),
                            contentDescription = null,
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterIndex(
    number: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            color = Color(NgTheme.colors.primary),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StoryboardSelectionDock(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(56.dp),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        border = BorderStroke(0.6.dp, colorResource(R.color.ng_card_stroke)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.character_selected_count, selectedCount),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            StoryboardSelectionActionButton(
                iconRes = R.drawable.ic_select_all,
                label = stringResource(R.string.select_all),
                enabled = totalCount > 0,
                onClick = onSelectAll,
            )
            Spacer(Modifier.width(8.dp))
            StoryboardSelectionActionButton(
                iconRes = R.drawable.ic_refresh_black_24dp,
                label = stringResource(R.string.revert_selection),
                enabled = totalCount > 0,
                onClick = onInvertSelection,
            )
            Spacer(Modifier.width(8.dp))
            StoryboardSelectionActionButton(
                iconRes = R.drawable.ic_book_info_delete,
                label = stringResource(R.string.delete),
                enabled = selectedCount > 0,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun RowScope.StoryboardSelectionActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (danger) Color.White else Color(NgTheme.colors.onSurface)
    NgButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(36.dp),
        enabled = enabled,
        variant = if (danger) NgButtonVariant.DANGER else NgButtonVariant.NEUTRAL,
        shapeVariant = NgButtonShapeVariant.SMALL_ROUNDED,
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun StoryboardCacheMessage(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 3.dp,
                )
            }
            Text(
                text = message,
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
