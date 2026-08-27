@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package io.legado.app.ui.book.info

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.ui.book.search.SearchResultCard
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgActionBarButton
import io.legado.app.ui.design.components.compose.NgActionBarButtonSurfaceVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuWidthVariant
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgVisualIconButton
import io.legado.app.ui.design.components.compose.NgWindowLiquidGlassBackdropHost
import io.legado.app.ui.design.theme.NgTheme
import androidx.compose.ui.window.PopupProperties

internal enum class BookInfoIntroRenderMode {
    TEXT,
    WEB,
}

internal data class BookInfoUiState(
    val book: Book? = null,
    val coverRevision: Int = 0,
    val originText: String = "",
    val latestText: String = "",
    val groupText: String = "",
    val tocText: String = "",
    val tags: List<String> = emptyList(),
    val inBookshelf: Boolean = false,
    val hasCustomButton: Boolean = false,
    val sourceAvailable: Boolean = false,
    val loginAvailable: Boolean = false,
    val showToc: Boolean = true,
    val showCache: Boolean = true,
    val showListen: Boolean = false,
    val primaryActionIsPlay: Boolean = false,
    val cache: BookInfoCacheUiState = BookInfoCacheUiState(),
    val introRevision: Int = 0,
    val introRenderMode: BookInfoIntroRenderMode = BookInfoIntroRenderMode.TEXT,
    val webIntroHeightPx: Int = 0,
    val charactersVisible: Boolean = false,
    val characters: List<BookInfoCharacterUiItem> = emptyList(),
    val characterCount: Int = 0,
    val otherWorksVisible: Boolean = false,
    val autoLoadOtherWorks: Boolean = false,
    val otherWorksLoading: Boolean = false,
    val otherWorksState: BookInfoOtherWorksState = BookInfoOtherWorksState.Idle,
    val otherWorks: List<BookInfoOtherWorkUiItem> = emptyList(),
    val scrollResetToken: Int = 0,
    val deleteDialogVisible: Boolean = false,
    val deleteOriginal: Boolean = false,
    val deleteAlertEnabled: Boolean = true,
    val fileDialog: BookInfoFileDialogState? = null,
)

internal sealed interface BookInfoFileDialogState {
    val items: List<String>

    data class WebFiles(override val items: List<String>) : BookInfoFileDialogState
    data class ArchiveFiles(override val items: List<String>) : BookInfoFileDialogState
    data class UnsupportedFile(val fileName: String) : BookInfoFileDialogState {
        override val items: List<String> = emptyList()
    }
}

internal data class BookInfoCacheUiState(
    val cached: Int = 0,
    val total: Int = 0,
    val enabled: Boolean = false,
)

internal data class BookInfoCharacterUiItem(
    val id: Long,
    val name: String,
    val intro: String,
    val avatarColorRes: Int,
)

internal data class BookInfoOtherWorkUiItem(
    val book: SearchBook,
    val inBookshelf: Boolean,
    val originCount: Int,
    val canExpand: Boolean,
)

internal sealed interface BookInfoOtherWorksState {
    data object Idle : BookInfoOtherWorksState
    data object Loading : BookInfoOtherWorksState
    data object Empty : BookInfoOtherWorksState
    data object Success : BookInfoOtherWorksState
    data class Error(val message: String) : BookInfoOtherWorksState
}

internal enum class BookInfoMenuAction(val itemId: Int) {
    Edit(0x7301),
    Refresh(0x7302),
    Share(0x7303),
    Upload(0x7304),
    Login(0x7305),
    Top(0x7306),
    SetSourceVariable(0x7307),
    SetBookVariable(0x7308),
    CopyBookUrl(0x7309),
    CopyTocUrl(0x730A),
    CanUpdate(0x730B),
    SplitLongChapter(0x730C),
    DeleteAlert(0x730D),
    ClearCache(0x730E),
    Log(0x730F),
    NetworkLog(0x7310),
    AutoLoadOtherWorks(0x7311),
    ;

    companion object {
        fun fromItemId(itemId: Int): BookInfoMenuAction? = entries.firstOrNull {
            it.itemId == itemId
        }
    }
}

internal sealed interface BookInfoUiEvent {
    data object Back : BookInfoUiEvent
    data object Refresh : BookInfoUiEvent
    data object CustomButton : BookInfoUiEvent
    data class Menu(val action: BookInfoMenuAction) : BookInfoUiEvent
    data object CoverClick : BookInfoUiEvent
    data object CoverLongClick : BookInfoUiEvent
    data object NameClick : BookInfoUiEvent
    data object NameLongClick : BookInfoUiEvent
    data object AuthorClick : BookInfoUiEvent
    data object AuthorLongClick : BookInfoUiEvent
    data class TagClick(val tag: String) : BookInfoUiEvent
    data class TagLongClick(val tag: String) : BookInfoUiEvent
    data object OriginClick : BookInfoUiEvent
    data object ChangeSource : BookInfoUiEvent
    data object OpenToc : BookInfoUiEvent
    data object CacheBook : BookInfoUiEvent
    data object CharacterAi : BookInfoUiEvent
    data object OpenCharacters : BookInfoUiEvent
    data object RefreshOtherWorks : BookInfoUiEvent
    data class OpenOtherWork(val book: SearchBook) : BookInfoUiEvent
    data class ExpandOtherWork(val book: SearchBook) : BookInfoUiEvent
    data object Shelf : BookInfoUiEvent
    data object Listen : BookInfoUiEvent
    data object Read : BookInfoUiEvent
    data object DeleteDialogDismiss : BookInfoUiEvent
    data class DeleteOriginalChange(val checked: Boolean) : BookInfoUiEvent
    data object DeleteConfirm : BookInfoUiEvent
    data object FileDialogDismiss : BookInfoUiEvent
    data class FileDialogItemClick(val index: Int) : BookInfoUiEvent
    data object UnsupportedFileOpen : BookInfoUiEvent
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
internal fun BookInfoScreen(
    state: BookInfoUiState,
    introView: View,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    NgWindowLiquidGlassBackdropHost(
        modifier = Modifier.fillMaxSize(),
        backgroundOverlay = if (isLandscape) {
            Color(NgTheme.colors.background)
        } else {
            Color.Transparent
        },
    ) {
        if (isLandscape) {
            BookInfoLandscape(state = state, introView = introView, onEvent = onEvent)
        } else {
            BookInfoPortrait(state = state, introView = introView, onEvent = onEvent)
        }
    }
    if (state.deleteDialogVisible) {
        BookInfoDeleteDialog(state = state, onEvent = onEvent)
    }
    state.fileDialog?.let {
        BookInfoFileDialog(state = it, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoPortrait(
    state: BookInfoUiState,
    introView: View,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollResetToken) {
        listState.scrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BookInfoTopBar(state = state, onEvent = onEvent)
        NgPullRefreshBox(
            isRefreshing = false,
            onRefresh = { onEvent(BookInfoUiEvent.Refresh) },
            modifier = Modifier.weight(1f),
            enabled = state.book != null,
            showIndicator = false,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.book?.let { book ->
                    item(key = "hero") {
                        BookInfoHeroCard(state = state, book = book, onEvent = onEvent)
                    }
                    item(key = "meta") {
                        BookInfoMetaCard(state = state, onEvent = onEvent)
                    }
                    item(key = "intro") {
                        BookInfoIntro(state = state, introView = introView, withSurface = true)
                    }
                    if (state.charactersVisible) {
                        item(key = "characters") {
                            BookInfoCharactersCard(state = state, onEvent = onEvent)
                        }
                    }
                    if (state.otherWorksVisible) {
                        item(key = "otherWorks") {
                            BookInfoOtherWorks(state = state, onEvent = onEvent)
                        }
                    }
                }
            }
        }
        BookInfoBottomActions(state = state, isLandscape = false, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoLandscape(
    state: BookInfoUiState,
    introView: View,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val leftState = rememberLazyListState()
    val rightState = rememberLazyListState()
    LaunchedEffect(state.scrollResetToken) {
        leftState.scrollToItem(0)
        rightState.scrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BookInfoTopBar(state = state, onEvent = onEvent)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(NgTheme.colors.background)),
        ) {
            LazyColumn(
                state = leftState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.book?.let { book ->
                    item(key = "landCover") {
                        BookInfoLandscapeHeader(state = state, book = book, onEvent = onEvent)
                    }
                    item(key = "landMeta") {
                        BookInfoLandscapeMeta(state = state, onEvent = onEvent)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .width(1.dp)
                    .background(colorResource(R.color.bg_divider_line)),
            )
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
            ) {
                LazyColumn(
                    state = rightState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.book?.let {
                        item(key = "landIntro") {
                            BookInfoIntro(state = state, introView = introView, withSurface = false)
                        }
                        if (state.charactersVisible) {
                            item(key = "landCharacters") {
                                BookInfoCharactersCard(state = state, onEvent = onEvent)
                            }
                        }
                        if (state.otherWorksVisible) {
                            item(key = "landOtherWorks") {
                                BookInfoOtherWorks(state = state, onEvent = onEvent)
                            }
                        }
                    }
                }
                BookInfoBottomActions(state = state, isLandscape = true, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun BookInfoTopBar(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookInfoToolbarIcon(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = { onEvent(BookInfoUiEvent.Back) },
        )
        Spacer(Modifier.weight(1f))
        if (state.hasCustomButton) {
            BookInfoToolbarIcon(
                iconRes = R.drawable.ic_custom,
                contentDescription = stringResource(R.string.custom_button),
                onClick = { onEvent(BookInfoUiEvent.CustomButton) },
            )
        }
        BookInfoMoreMenu(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoMoreMenu(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BookInfoToolbarIcon(
            iconRes = R.drawable.ic_more_vert,
            contentDescription = stringResource(R.string.more),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = bookInfoMenuItems(state),
            variant = NgExpandableActionMenuVariant.DROPDOWN,
            widthVariant = NgExpandableActionMenuWidthVariant.GROUPED_LABELS,
            rowMinHeight = 44.dp,
            offset = DpOffset(0.dp, 4.dp),
            menuContainerColor = colorResource(R.color.ng_surface_card),
            properties = PopupProperties(focusable = true, clippingEnabled = false),
            onItemClick = { item ->
                expanded = false
                BookInfoMenuAction.fromItemId(item.itemId)?.let {
                    onEvent(BookInfoUiEvent.Menu(it))
                }
            },
        )
    }
}

@Composable
private fun bookInfoMenuItems(state: BookInfoUiState): List<NgExpandableActionMenuItem> {
    val book = state.book
    return buildList {
        if (state.inBookshelf) {
            add(BookInfoMenuAction.Edit.item(R.string.edit, R.drawable.ic_edit))
        }
        add(BookInfoMenuAction.Refresh.item(R.string.refresh, R.drawable.ic_refresh_black_24dp))
        add(BookInfoMenuAction.Share.item(R.string.share, R.drawable.ic_share))
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_BOOK_MANAGEMENT,
                titleRes = R.string.book_info_menu_book_management,
                iconRes = R.drawable.ic_settings,
                dividerBefore = true,
                children = buildList {
                    add(BookInfoMenuAction.Top.item(R.string.to_top, R.drawable.ic_arrow_drop_up))
                    if (state.sourceAvailable) {
                        add(
                            BookInfoMenuAction.CanUpdate.item(
                                R.string.allow_update,
                                R.drawable.ic_update,
                                checked = book?.canUpdate ?: true,
                            ),
                        )
                    }
                    if (book?.isLocalTxt == true) {
                        add(
                            BookInfoMenuAction.SplitLongChapter.item(
                                R.string.split_long_chapter,
                                R.drawable.ic_chapter_list,
                                checked = book.getSplitLongChapter(),
                            ),
                        )
                    }
                    add(
                        BookInfoMenuAction.DeleteAlert.item(
                            R.string.delete_alert,
                            R.drawable.ic_outline_delete,
                            checked = state.deleteAlertEnabled,
                        ),
                    )
                    if (state.otherWorksVisible) {
                        add(
                            BookInfoMenuAction.AutoLoadOtherWorks.item(
                                R.string.auto_load_book_other_works,
                                R.drawable.ic_refresh_black_24dp,
                                checked = state.autoLoadOtherWorks,
                            ),
                        )
                    }
                },
            ),
        )
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_SOURCE_AND_LINKS,
                titleRes = R.string.book_info_menu_source_and_links,
                iconRes = R.drawable.ic_web_outline,
                children = buildList {
                    if (state.loginAvailable) {
                        add(BookInfoMenuAction.Login.item(R.string.login, R.drawable.ic_lock_outline))
                    }
                    if (state.sourceAvailable) {
                        add(
                            BookInfoMenuAction.SetSourceVariable.item(
                                R.string.set_source_variable,
                                R.drawable.ic_code,
                            ),
                        )
                        add(
                            BookInfoMenuAction.SetBookVariable.item(
                                R.string.set_book_variable,
                                R.drawable.ic_code,
                            ),
                        )
                    }
                    add(
                        BookInfoMenuAction.CopyBookUrl.item(
                            R.string.copy_book_url,
                            R.drawable.ic_copy,
                        ),
                    )
                    add(
                        BookInfoMenuAction.CopyTocUrl.item(
                            R.string.copy_toc_url,
                            R.drawable.ic_copy,
                        ),
                    )
                },
            ),
        )
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_DATA_AND_DIAGNOSTICS,
                titleRes = R.string.book_info_menu_data_and_diagnostics,
                iconRes = R.drawable.ic_cfg_about,
                children = buildList {
                    if (book?.isLocal == true) {
                        add(
                            BookInfoMenuAction.Upload.item(
                                R.string.upload_to_remote,
                                R.drawable.ic_outline_cloud_24,
                            ),
                        )
                    }
                    add(
                        BookInfoMenuAction.ClearCache.item(
                            R.string.clear_cache,
                            R.drawable.ic_clear_all,
                        ),
                    )
                    add(BookInfoMenuAction.Log.item(R.string.log, R.drawable.ic_cfg_about))
                    add(
                        BookInfoMenuAction.NetworkLog.item(
                            R.string.network_request_log,
                            R.drawable.ic_network_check,
                        ),
                    )
                },
            ),
        )
    }
}

private fun BookInfoMenuAction.item(
    titleRes: Int,
    iconRes: Int,
    checked: Boolean = false,
): NgExpandableActionMenuItem = NgExpandableActionMenuItem(
    itemId = itemId,
    titleRes = titleRes,
    iconRes = iconRes,
    checked = checked,
)

@Composable
private fun BookInfoToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    NgVisualIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color(NgTheme.colors.onTopBar),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BookInfoHeroCard(
    state: BookInfoUiState,
    book: Book,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    BookInfoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            NgBookCover(
                book = book,
                coverRadius = 8,
                contentDescription = stringResource(R.string.img_cover),
                revision = state.coverRevision,
                modifier = Modifier
                    .size(width = 78.dp, height = 104.dp)
                    .combinedClickable(
                        onClick = { onEvent(BookInfoUiEvent.CoverClick) },
                        onLongClick = { onEvent(BookInfoUiEvent.CoverLongClick) },
                    ),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                        .combinedClickable(
                            onClick = { onEvent(BookInfoUiEvent.NameClick) },
                            onLongClick = { onEvent(BookInfoUiEvent.NameLongClick) },
                        ),
                    color = colorResource(R.color.primaryText),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = book.getRealAuthor(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                        .combinedClickable(
                            onClick = { onEvent(BookInfoUiEvent.AuthorClick) },
                            onLongClick = { onEvent(BookInfoUiEvent.AuthorLongClick) },
                        ),
                    color = colorResource(R.color.tv_text_summary),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BookInfoTags(tags = state.tags, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun BookInfoLandscapeHeader(
    state: BookInfoUiState,
    book: Book,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(5.dp),
            color = Color(NgTheme.colors.background),
            shadowElevation = 8.dp,
        ) {
            NgBookCover(
                book = book,
                coverRadius = 5,
                contentDescription = stringResource(R.string.img_cover),
                revision = state.coverRevision,
                modifier = Modifier
                    .size(width = 165.dp, height = 240.dp)
                    .combinedClickable(
                        onClick = { onEvent(BookInfoUiEvent.CoverClick) },
                        onLongClick = { onEvent(BookInfoUiEvent.CoverLongClick) },
                    ),
            )
        }
        Text(
            text = book.name,
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = 260.dp)
                .basicMarquee()
                .combinedClickable(
                    onClick = { onEvent(BookInfoUiEvent.NameClick) },
                    onLongClick = { onEvent(BookInfoUiEvent.NameLongClick) },
                ),
            color = colorResource(R.color.primaryText),
            fontSize = 20.sp,
            lineHeight = 25.sp,
            maxLines = 1,
        )
        Text(
            text = book.getRealAuthor(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp)
                .combinedClickable(
                    onClick = { onEvent(BookInfoUiEvent.AuthorClick) },
                    onLongClick = { onEvent(BookInfoUiEvent.AuthorLongClick) },
                ),
            color = colorResource(R.color.tv_text_summary),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BookInfoTags(tags = state.tags, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoTags(
    tags: List<String>,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .defaultMinSize(minWidth = 28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(NgTheme.colors.primary))
                    .combinedClickable(
                        onClick = { onEvent(BookInfoUiEvent.TagClick(tag)) },
                        onLongClick = { onEvent(BookInfoUiEvent.TagLongClick(tag)) },
                    )
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tag,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BookInfoMetaCard(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    BookInfoCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 12.dp,
        ),
    ) {
        BookInfoMetaRows(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoLandscapeMeta(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        BookInfoMetaRows(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoMetaRows(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    BookInfoMetaRow(
        iconRes = R.drawable.ic_web_outline,
        text = state.originText,
        textModifier = Modifier.clickable { onEvent(BookInfoUiEvent.OriginClick) },
        trailingIconRes = R.drawable.ic_exchange,
        trailingDescription = stringResource(R.string.change_origin),
        onTrailingClick = { onEvent(BookInfoUiEvent.ChangeSource) },
    )
    BookInfoMetaRow(iconRes = R.drawable.ic_book_last, text = state.latestText)
    BookInfoMetaRow(iconRes = R.drawable.ic_groups, text = state.groupText)
    if (state.showToc) {
        BookInfoMetaRow(
            iconRes = R.drawable.ic_folder_open,
            text = state.tocText,
            trailingIconRes = R.drawable.ic_folder_open,
            trailingDescription = stringResource(R.string.view_toc),
            onTrailingClick = { onEvent(BookInfoUiEvent.OpenToc) },
        )
    }
    if (state.showCache) {
        BookInfoCacheRow(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoMetaRow(
    iconRes: Int,
    text: String,
    textModifier: Modifier = Modifier,
    trailingIconRes: Int? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colorResource(R.color.tv_text_summary),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            modifier = textModifier
                .weight(1f)
                .padding(start = 8.dp),
            color = colorResource(R.color.tv_text_summary),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIconRes != null && onTrailingClick != null) {
            IconButton(onClick = onTrailingClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(trailingIconRes),
                    contentDescription = trailingDescription,
                    tint = colorResource(R.color.tv_text_summary),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun BookInfoCacheRow(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_cache_octicon),
            contentDescription = null,
            tint = colorResource(R.color.tv_text_summary),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.book_cache_label),
            modifier = Modifier.padding(start = 8.dp),
            color = colorResource(R.color.tv_text_summary),
            fontSize = 14.sp,
            maxLines = 1,
        )
        BookInfoCacheProgress(
            cache = state.cache,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, end = 8.dp),
        )
        IconButton(
            onClick = { onEvent(BookInfoUiEvent.CacheBook) },
            enabled = state.cache.enabled,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_download_line),
                contentDescription = stringResource(R.string.book_cache),
                tint = colorResource(R.color.tv_text_summary).copy(
                    alpha = if (state.cache.enabled) 1f else 0.45f,
                ),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BookInfoCacheProgress(
    cache: BookInfoCacheUiState,
    modifier: Modifier = Modifier,
) {
    val progress = if (cache.total > 0) {
        (cache.cached.toFloat() / cache.total).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(R.color.ng_surface_card))
            .border(
                BorderStroke(0.8.dp, colorResource(R.color.ng_settings_item_stroke)),
                RoundedCornerShape(12.dp),
            ),
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(NgTheme.colors.primary).copy(alpha = 96f / 255f)),
            )
        }
        Text(
            text = stringResource(R.string.book_cache_progress, cache.cached, cache.total),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 8.dp),
            color = colorResource(R.color.tv_text_summary),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BookInfoIntro(
    state: BookInfoUiState,
    introView: View,
    withSurface: Boolean,
) {
    val isWebIntro = state.introRenderMode == BookInfoIntroRenderMode.WEB
    val webIntroHeight = with(LocalDensity.current) {
        state.webIntroHeightPx.takeIf { it > 0 }?.toDp() ?: 48.dp
    }
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (withSurface && !isWebIntro) {
                Text(
                    text = stringResource(R.string.book_intro_short),
                    color = colorResource(R.color.primaryText),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            state.introRevision
            AndroidView(
                factory = { introView },
                update = { it.requestLayout() },
                modifier = if (isWebIntro) {
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(webIntroHeight, 48.dp))
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                },
            )
        }
    }
    if (withSurface) {
        BookInfoCard(
            contentPadding = if (isWebIntro) {
                androidx.compose.foundation.layout.PaddingValues(0.dp)
            } else {
                androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 12.dp,
                )
            },
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun BookInfoCharactersCard(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    BookInfoCard(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.book_characters),
                modifier = Modifier.weight(1f),
                color = colorResource(R.color.primaryText),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = { onEvent(BookInfoUiEvent.CharacterAi) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai),
                    contentDescription = stringResource(R.string.ai_assistant),
                    tint = colorResource(R.color.tv_text_summary),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (state.characters.isEmpty()) {
            Text(
                text = stringResource(R.string.book_character_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(BookInfoUiEvent.OpenCharacters) }
                    .padding(vertical = 10.dp),
                color = colorResource(R.color.tv_text_summary),
                fontSize = 13.sp,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.characters.forEach { character ->
                    BookInfoCharacterCard(character = character) {
                        onEvent(BookInfoUiEvent.OpenCharacters)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable { onEvent(BookInfoUiEvent.OpenCharacters) },
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.character_count_format, state.characterCount),
                color = colorResource(R.color.tv_text_summary),
                fontSize = 13.sp,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_20),
                contentDescription = null,
                tint = colorResource(R.color.tv_text_summary),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BookInfoCharacterCard(
    character: BookInfoCharacterUiItem,
    onClick: () -> Unit,
) {
    BookInfoCard(
        modifier = Modifier.size(width = 150.dp, height = 64.dp).clickable(onClick = onClick),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 10.dp,
            bottom = 8.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colorResource(character.avatarColorRes)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.name.firstOrNull()?.toString().orEmpty(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    color = colorResource(R.color.primaryText),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = character.intro,
                    modifier = Modifier.padding(top = 5.dp),
                    color = colorResource(R.color.tv_text_summary),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookInfoOtherWorks(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BookInfoCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.book_other_works),
                    modifier = Modifier.weight(1f),
                    color = colorResource(R.color.primaryText),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { onEvent(BookInfoUiEvent.RefreshOtherWorks) },
                    enabled = !state.otherWorksLoading,
                    modifier = Modifier.size(32.dp),
                ) {
                    if (state.otherWorksLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(NgTheme.colors.primary),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh_black_24dp),
                            contentDescription = stringResource(R.string.refresh),
                            tint = colorResource(R.color.tv_text_summary),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        if (state.otherWorksLoading) {
            BookInfoOtherWorksLoading()
        }
        when (val otherState = state.otherWorksState) {
            BookInfoOtherWorksState.Idle -> Unit
            BookInfoOtherWorksState.Loading -> Unit
            BookInfoOtherWorksState.Empty -> if (!state.otherWorksLoading) {
                BookInfoOtherWorksMessage(stringResource(R.string.book_other_works_empty))
            }
            is BookInfoOtherWorksState.Error -> if (!state.otherWorksLoading) {
                BookInfoOtherWorksMessage(
                    stringResource(R.string.book_other_works_error, otherState.message),
                )
            }
            BookInfoOtherWorksState.Success -> {
                state.otherWorks.forEach { item ->
                    SearchResultCard(
                        book = item.book,
                        inBookshelf = item.inBookshelf,
                        originCount = item.originCount,
                        onClick = { onEvent(BookInfoUiEvent.OpenOtherWork(item.book)) },
                        onLongClick = {
                            if (item.canExpand) {
                                onEvent(BookInfoUiEvent.ExpandOtherWork(item.book))
                            }
                        },
                        outerHorizontalPadding = 0.dp,
                        outerVerticalPadding = 4.dp,
                        cardCornerRadius = 10.dp,
                        cardBackgroundColorRes = R.color.ng_book_detail_card_surface,
                        cardStrokeColorRes = R.color.ng_book_detail_card_stroke,
                        cardBorderWidth = 0.6.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookInfoOtherWorksLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = Color(NgTheme.colors.primary),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.book_other_works_loading),
            color = colorResource(R.color.tv_text_summary),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BookInfoOtherWorksMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        color = colorResource(R.color.tv_text_summary),
        fontSize = 13.sp,
    )
}

@Composable
private fun BookInfoBottomActions(
    state: BookInfoUiState,
    isLandscape: Boolean,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val surfaceVariant = if (isLandscape) {
        NgActionBarButtonSurfaceVariant.THEMED
    } else {
        NgActionBarButtonSurfaceVariant.LIGHT_GLASS
    }
    val barModifier = if (isLandscape) {
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(44.dp)
            .background(colorResource(R.color.background_menu))
    } else {
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(58.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    }
    Row(
        modifier = barModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgActionBarButton(
            text = stringResource(if (state.inBookshelf) R.string.delete else R.string.bookshelf),
            icon = ImageVector.vectorResource(
                if (state.inBookshelf) R.drawable.ic_book_info_delete else R.drawable.ic_add,
            ),
            onClick = { onEvent(BookInfoUiEvent.Shelf) },
            modifier = Modifier.weight(1f),
            surfaceVariant = surfaceVariant,
        )
        if (state.showListen) {
            NgActionBarButton(
                text = stringResource(R.string.book_info_listen),
                icon = ImageVector.vectorResource(R.drawable.ic_tts_headphones),
                onClick = { onEvent(BookInfoUiEvent.Listen) },
                modifier = Modifier.weight(1f),
                surfaceVariant = surfaceVariant,
            )
        }
        NgActionBarButton(
            text = stringResource(
                if (state.primaryActionIsPlay) R.string.book_info_play else R.string.reading,
            ),
            icon = ImageVector.vectorResource(
                if (state.primaryActionIsPlay) {
                    R.drawable.ic_play_circle_outline_24dp
                } else {
                    R.drawable.ic_book_info_read
                },
            ),
            onClick = { onEvent(BookInfoUiEvent.Read) },
            modifier = Modifier.weight(1.18f),
            variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
            surfaceVariant = surfaceVariant,
        )
    }
}

@Composable
private fun BookInfoCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(0.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    NgGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
        style = NgGlassDefaults.bookDetailStyle(),
        role = NgMaterialRole.CONTENT,
        liquidCornerRadius = NgTheme.shapes.smallDp.dp,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun BookInfoDeleteDialog(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Dialog(
        onDismissRequest = { onEvent(BookInfoUiEvent.DeleteDialogDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.draw),
            modifier = Modifier.padding(horizontal = 24.dp),
            variant = NgDialogVariant.COMPACT_CONFIRMATION,
            actions = {
                NgButton(
                    onClick = { onEvent(BookInfoUiEvent.DeleteDialogDismiss) },
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
                NgButton(
                    onClick = { onEvent(BookInfoUiEvent.DeleteConfirm) },
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.DANGER,
                ) {
                    Text(stringResource(R.string.delete), color = Color.White, fontSize = 14.sp)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.sure_del),
                color = colorResource(R.color.primaryText),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            if (state.book?.isLocal == true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEvent(BookInfoUiEvent.DeleteOriginalChange(!state.deleteOriginal))
                        }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = state.deleteOriginal,
                        onCheckedChange = {
                            onEvent(BookInfoUiEvent.DeleteOriginalChange(it))
                        },
                    )
                    Text(
                        text = stringResource(R.string.delete_book_file),
                        color = colorResource(R.color.primaryText),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookInfoFileDialog(
    state: BookInfoFileDialogState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    if (state is BookInfoFileDialogState.UnsupportedFile) {
        BookInfoUnsupportedFileDialog(state = state, onEvent = onEvent)
        return
    }
    val titleRes = when (state) {
        is BookInfoFileDialogState.WebFiles -> R.string.download_and_import_file
        is BookInfoFileDialogState.ArchiveFiles -> R.string.import_select_book
        is BookInfoFileDialogState.UnsupportedFile -> return
    }
    Dialog(
        onDismissRequest = { onEvent(BookInfoUiEvent.FileDialogDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(titleRes),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .heightIn(max = 620.dp),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgButton(
                    onClick = { onEvent(BookInfoUiEvent.FileDialogDismiss) },
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
            ) {
                itemsIndexed(state.items) { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onEvent(BookInfoUiEvent.FileDialogItemClick(index))
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (index != state.items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(
                                    Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoUnsupportedFileDialog(
    state: BookInfoFileDialogState.UnsupportedFile,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    Dialog(
        onDismissRequest = { onEvent(BookInfoUiEvent.FileDialogDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.draw),
            modifier = Modifier.padding(horizontal = 24.dp),
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = { onEvent(BookInfoUiEvent.FileDialogDismiss) },
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
                NgButton(
                    onClick = { onEvent(BookInfoUiEvent.UnsupportedFileOpen) },
                    modifier = Modifier.width(92.dp).height(42.dp),
                    variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                ) {
                    Text(stringResource(R.string.open_fun), color = Color.White, fontSize = 14.sp)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.file_not_supported, state.fileName),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

private const val MENU_BOOK_MANAGEMENT = 0x7381
private const val MENU_SOURCE_AND_LINKS = 0x7382
private const val MENU_DATA_AND_DIAGNOSTICS = 0x7383
