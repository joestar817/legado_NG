package io.legado.app.ui.main.bookshelf

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgCompactDrawerPanel
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgThemedActionIcon
import io.legado.app.ui.design.components.compose.NgThemedActionIconKind
import io.legado.app.ui.design.components.compose.NgThemedActionIconTone
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookshelfBookActionSheet(
    private val fragment: Fragment,
    private val book: Book,
    private val callback: Callback,
    ) {

    interface Callback {
        fun onDetail(book: Book)
        fun onChapterList(book: Book)
        fun onCharacters(book: Book)
        fun onGroup(book: Book)
        fun onExport(book: Book)
        fun onListen(book: Book)
        fun onDownload(book: Book)
        fun onChangeSource(book: Book)
        fun onSimulatedReading(book: Book)
        fun onBookScan(book: Book)
        fun onAllowUpdateChanged(book: Book, allowUpdate: Boolean)
        fun onClearCache(book: Book)
        fun onDelete(book: Book)
    }

    private data class CacheProgress(
        val cached: Int?,
        val total: Int,
    )

    private data class QuickAction(
        val titleRes: Int,
        val iconKind: NgThemedActionIconKind,
        val onClick: () -> Unit,
    )

    private val context: Context get() = fragment.requireContext()
    private val dialog by lazy { BottomSheetDialog(context) }
    private var cacheProgress by mutableStateOf(
        CacheProgress(cached = null, total = book.totalChapterNum.coerceAtLeast(0))
    )
    private var allowUpdate by mutableStateOf(book.canUpdate)

    fun show() {
        val contentView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    SheetContent()
                }
            }
        }
        dialog.setContentView(contentView)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<android.view.View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return@setOnShowListener
            sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                isFitToContents = true
                skipCollapsed = true
                isDraggable = true
                isDraggableOnNestedScroll = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
        loadCacheProgress()
    }

    @Composable
    private fun SheetContent() {
        val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 23.dp),
            ) {
                NgDrawerDragHandle(variant = NgDrawerDragHandleVariant.COMPACT)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BookHeader()
                    QuickActionPanel()
                    ManagementPanel()
                    DeleteBookRow()
                }
            }
        }
    }

    @Composable
    private fun BookHeader() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(102.dp)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgBookCover(
                book = book,
                fragment = fragment,
                lifecycle = fragment.viewLifecycleOwner.lifecycle,
                modifier = Modifier
                    .width(76.dp)
                    .height(102.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(15.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-3).dp)
                    .clickable { dismissThen { callback.onDetail(book) } },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = book.name,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.getRealAuthor(),
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = stringResource(R.string.action_detail),
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = headerSummary(),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    private fun QuickActionPanel() {
        val actions = remember(book.bookUrl) {
            listOf(
                QuickAction(
                    R.string.chapter_list,
                    NgThemedActionIconKind.CONTENTS,
                ) {
                    dismissThen { callback.onChapterList(book) }
                },
                QuickAction(
                    R.string.book_cache,
                    NgThemedActionIconKind.DOWNLOAD,
                ) {
                    dismissThen { callback.onDownload(book) }
                },
                QuickAction(
                    R.string.change_origin,
                    NgThemedActionIconKind.CHANGE_SOURCE,
                ) {
                    dismissThen { callback.onChangeSource(book) }
                },
                QuickAction(
                    R.string.book_info_listen,
                    NgThemedActionIconKind.LISTEN,
                ) {
                    dismissThen { callback.onListen(book) }
                },
                QuickAction(
                    R.string.simulated_reading,
                    NgThemedActionIconKind.SIMULATED_READING,
                ) {
                    dismissThen { callback.onSimulatedReading(book) }
                },
                QuickAction(
                    R.string.bookshelf_book_action_scan,
                    NgThemedActionIconKind.BOOK_SCAN,
                ) {
                    dismissThen { callback.onBookScan(book) }
                },
                QuickAction(
                    R.string.bookshelf_character_profile,
                    NgThemedActionIconKind.CHARACTER_PROFILE,
                ) {
                    dismissThen { callback.onCharacters(book) }
                },
                QuickAction(
                    R.string.bookshelf_book_action_move_group,
                    NgThemedActionIconKind.MOVE_TO_GROUP,
                ) {
                    dismissThen { callback.onGroup(book) }
                },
            )
        }
        NgCompactDrawerPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 3.5.dp),
        ) {
            actions.chunked(4).forEach { rowActions ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowActions.forEach { action ->
                        QuickActionCell(
                            action = action,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun QuickActionCell(
        action: QuickAction,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier
                .height(74.dp)
                .clickable(onClick = action.onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NgThemedActionIcon(
                kind = action.iconKind,
                contentDescription = stringResource(action.titleRes),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = stringResource(action.titleRes),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    private fun ManagementPanel() {
        NgCompactDrawerPanel(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ManagementRow(
                iconKind = NgThemedActionIconKind.EXPORT,
                title = stringResource(R.string.export),
                onClick = { dismissThen { callback.onExport(book) } },
            )
            if (!book.isLocal) {
                DrawerDivider(modifier = Modifier.padding(start = 56.dp, end = 20.dp))
                ManagementRow(
                    iconKind = NgThemedActionIconKind.REFRESH,
                    title = stringResource(R.string.allow_update),
                    switchValue = allowUpdate,
                    onSwitchChanged = { enabled ->
                        allowUpdate = enabled
                        callback.onAllowUpdateChanged(book, enabled)
                    },
                )
            }
            DrawerDivider(modifier = Modifier.padding(start = 56.dp, end = 20.dp))
            ManagementRow(
                iconKind = NgThemedActionIconKind.CLEAR_CACHE,
                title = stringResource(R.string.clear_cache),
                value = cacheProgress.cached?.let {
                    stringResource(R.string.all_chapter_num, it)
                }.orEmpty(),
                onClick = { dismissThen { callback.onClearCache(book) } },
            )
        }
    }

    @Composable
    private fun ManagementRow(
        iconKind: NgThemedActionIconKind,
        title: String,
        value: String = "",
        switchValue: Boolean? = null,
        onSwitchChanged: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 16.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgThemedActionIcon(
                kind = iconKind,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tone = NgThemedActionIconTone.MUTED,
            )
            Spacer(Modifier.width(15.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (switchValue != null) {
                NgSwitchControl(
                    checked = switchValue,
                    onCheckedChange = onSwitchChanged,
                )
            } else {
                if (value.isNotBlank()) {
                    Text(
                        text = value,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    @Composable
    private fun DeleteBookRow() {
        val error = Color(NgTheme.colors.error)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { dismissThen { callback.onDelete(book) } },
            color = ngDrawerContentCardColor(),
            shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_book_info_delete),
                    contentDescription = null,
                    tint = error,
                    modifier = Modifier
                        .offset(x = (-4).dp)
                        .size(23.dp),
                )
                Spacer(Modifier.width(13.dp))
                Text(
                    text = stringResource(R.string.bookshelf_delete_book),
                    modifier = Modifier.weight(1f),
                    color = error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    @Composable
    private fun DrawerDivider(modifier: Modifier = Modifier) {
        HorizontalDivider(
            modifier = modifier,
            thickness = 0.6.dp,
            color = Color(NgTheme.colors.outlineVariant).copy(
                alpha = if (NgTheme.snapshot.isEInk) 1f else 0.26f
            ),
        )
    }

    @Composable
    private fun headerSummary(): String {
        val unread = stringResource(
            R.string.bookshelf_unread_chapters,
            book.getUnreadChapterNum(),
        )
        return "$unread · ${cacheProgressText(cacheProgress.cached, cacheProgress.total)}"
    }

    private fun dismissThen(block: () -> Unit) {
        dialog.dismiss()
        block()
    }

    private fun loadCacheProgress() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val progress = withContext(IO) {
                val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
                val total = chapters.size.takeIf { it > 0 } ?: book.totalChapterNum
                if (book.isLocal) {
                    CacheProgress(cached = total, total = total)
                } else {
                    val cacheFileNames = BookHelp.getChapterFiles(book)
                    val cached = chapters.count {
                        it.isVolume || cacheFileNames.contains(it.getFileName())
                    }
                    CacheProgress(cached = cached, total = total)
                }
            }
            if (dialog.isShowing) {
                cacheProgress = progress
            }
        }
    }

    private fun cacheProgressText(cached: Int?, total: Int): String {
        val progress = if (cached == null) {
            "--/${total.coerceAtLeast(0)}"
        } else {
            context.getString(R.string.book_cache_progress, cached, total.coerceAtLeast(cached))
        }
        val label = context.getString(R.string.book_cache_label).trimEnd(' ', ':', '：')
        return label + progress
    }
}
