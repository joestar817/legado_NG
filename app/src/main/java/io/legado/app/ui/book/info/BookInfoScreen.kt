@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package io.legado.app.ui.book.info

import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.menuCapabilities
import io.legado.app.ui.book.character.BookCharacterLabels
import io.legado.app.ui.book.character.BookCharacterPortraitImage
import io.legado.app.ui.book.character.BookCharacterPortraits
import io.legado.app.ui.book.character.BookCharacterSilhouetteStyle
import io.legado.app.ui.book.character.forRole
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
import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val hasClearableCache: Boolean = false,
    val showListen: Boolean = false,
    val primaryActionIsPlay: Boolean = false,
    val cache: BookInfoCacheUiState = BookInfoCacheUiState(),
    val introRevision: Int = 0,
    val introRenderMode: BookInfoIntroRenderMode = BookInfoIntroRenderMode.TEXT,
    val webIntroHeightPx: Int = 0,
    val charactersVisible: Boolean = false,
    val characters: List<BookInfoCharacterUiItem> = emptyList(),
    val characterCount: Int = 0,
    val characterSilhouetteStyle: BookCharacterSilhouetteStyle = BookCharacterSilhouetteStyle.GENERIC,
    val otherWorksVisible: Boolean = false,
    val autoLoadOtherWorks: Boolean = BookInfoOtherWorksPreferences.DEFAULT_AUTO_LOAD,
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
    val roleTag: String,
    val intro: String?,
    val portraitUri: String?,
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
    Settings(0x7312),
    EditSource(0x7313),
    ChangeSource(0x7314),
    ChangeGroup(0x7315),
    ViewToc(0x7316),
    CacheBook(0x7317),
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
    data object ChangeGroup : BookInfoUiEvent
    data object OpenToc : BookInfoUiEvent
    data object CacheBook : BookInfoUiEvent
    data object OpenCharacters : BookInfoUiEvent
    data class OpenCharacterDetail(val characterId: Long) : BookInfoUiEvent
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
    layoutPreview: BookInfoLayout?,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val prefs = LocalContext.current.defaultSharedPreferences
    var savedLayout by remember(prefs) { mutableStateOf(BookInfoLayoutStore.read(prefs)) }
    var carouselPreferences by remember(prefs) {
        mutableStateOf(BookInfoCarouselPreferences.read(prefs))
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == BookInfoLayoutStore.KEY) {
                savedLayout = BookInfoLayoutStore.read(prefs)
            }
            if (key == null || key == BookInfoCarouselPreferences.KEY_AUTO_PLAY ||
                key == BookInfoCarouselPreferences.KEY_INTERVAL
            ) {
                carouselPreferences = BookInfoCarouselPreferences.read(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        savedLayout = BookInfoLayoutStore.read(prefs)
        carouselPreferences = BookInfoCarouselPreferences.read(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val layout = layoutPreview ?: savedLayout
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    NgWindowLiquidGlassBackdropHost(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (isLandscape) {
            BookInfoLandscape(state, introView, layout, carouselPreferences, onEvent)
        } else {
            BookInfoPortrait(state, introView, layout, carouselPreferences, onEvent)
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
    layout: BookInfoLayout,
    carouselPreferences: BookInfoCarouselPreferences,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollResetToken) {
        listState.scrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        BookInfoTopBar(
            state = state,
            layout = layout,
            onEvent = onEvent,
        )
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
                    top = 4.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.book?.let { book ->
                    item(key = "hero") {
                        BookInfoHeroCard(state, book, onEvent, coverPercent = layout.coverPercent)
                    }
                    bookInfoSections(
                        sections = layout.visibleSections(state.charactersVisible, state.otherWorksVisible),
                        state = state,
                        introView = introView,
                        listState = listState,
                        carouselPreferences = carouselPreferences,
                        onEvent = onEvent,
                    )
                }
            }
        }
        BookInfoBottomActions(state = state, onEvent = onEvent)
    }
}

@Composable
private fun BookInfoLandscape(
    state: BookInfoUiState,
    introView: View,
    layout: BookInfoLayout,
    carouselPreferences: BookInfoCarouselPreferences,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val leftState = rememberLazyListState()
    val rightState = rememberLazyListState()
    LaunchedEffect(state.scrollResetToken) {
        leftState.scrollToItem(0)
        rightState.scrollToItem(0)
    }
    val sections = layout.visibleSections(state.charactersVisible, state.otherWorksVisible)
    // Keep the default two-pane summary, while a later metadata card follows the chosen order.
    val metadataInSummary = sections.firstOrNull() == BookInfoSection.METADATA
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentWidth = maxWidth.coerceAtMost(1280.dp)
        val horizontalPadding = if (contentWidth >= 840.dp) 24.dp else 16.dp
        val paneSpacing = if (contentWidth >= 840.dp) 20.dp else 16.dp
        val paneWidth = (contentWidth - horizontalPadding * 2 - paneSpacing).coerceAtLeast(0.dp)
        val summaryWidth = (paneWidth * 0.38f)
            .coerceIn(280.dp, 400.dp)
            .coerceAtMost(paneWidth * 0.46f)

        Column(modifier = Modifier.fillMaxSize()) {
            BookInfoTopBar(
                state = state,
                layout = layout,
                onEvent = onEvent,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .width(contentWidth)
                    .align(Alignment.CenterHorizontally)
                    .padding(
                        start = horizontalPadding,
                        top = 12.dp,
                        end = horizontalPadding,
                        bottom = 8.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(paneSpacing),
            ) {
                LazyColumn(
                    state = leftState,
                    modifier = Modifier.width(summaryWidth).fillMaxHeight(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.book?.let { book ->
                        item(key = "landCover") {
                            BookInfoHeroCard(
                                state = state,
                                book = book,
                                onEvent = onEvent,
                                variant = BookInfoHeroVariant.LANDSCAPE,
                                coverPercent = layout.coverPercent,
                            )
                        }
                        if (metadataInSummary) {
                            item(key = "landMeta") {
                                BookInfoMetaCard(state = state, onEvent = onEvent)
                            }
                        }
                    }
                }
                LazyColumn(
                    state = rightState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.book != null) {
                        bookInfoSections(
                            sections = if (metadataInSummary) sections.drop(1) else sections,
                            state = state,
                            introView = introView,
                            listState = rightState,
                            carouselPreferences = carouselPreferences,
                            onEvent = onEvent,
                        )
                    }
                }
            }
            BookInfoBottomActions(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.width(contentWidth).align(Alignment.CenterHorizontally),
                horizontalPadding = horizontalPadding,
            )
        }
    }
}

private fun LazyListScope.bookInfoSections(
    sections: List<BookInfoSection>,
    state: BookInfoUiState,
    introView: View,
    listState: LazyListState,
    carouselPreferences: BookInfoCarouselPreferences,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    items(sections, key = { it.key }, contentType = { it }) { section ->
        when (section) {
            BookInfoSection.METADATA -> BookInfoMetaCard(state, onEvent)
            BookInfoSection.INTRO -> BookInfoIntro(state, introView, withSurface = true)
            BookInfoSection.CHARACTERS -> BookInfoCharactersCard(state, listState, carouselPreferences, onEvent)
            BookInfoSection.OTHER_WORKS -> BookInfoOtherWorks(state, onEvent)
        }
    }
}

@Composable
private fun BookInfoTopBar(
    state: BookInfoUiState,
    layout: BookInfoLayout,
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
            iconRes = R.drawable.ic_chevron_left_search,
            contentDescription = stringResource(R.string.back),
            tint = colorResource(R.color.ng_search_icon),
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
        BookInfoMoreMenu(state, layout, onEvent)
    }
}

@Composable
private fun BookInfoMoreMenu(
    state: BookInfoUiState,
    layout: BookInfoLayout,
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
            items = bookInfoMenuItems(
                state = state,
                metadataVisible = BookInfoSection.METADATA !in layout.hidden,
            ),
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

internal fun bookInfoMenuItems(
    state: BookInfoUiState,
    metadataVisible: Boolean = true,
): List<NgExpandableActionMenuItem> {
    val book = state.book
    return buildList {
        if (state.inBookshelf) {
            add(BookInfoMenuAction.Edit.item(R.string.edit, R.drawable.ic_bgm_menu_edit))
        }
        add(BookInfoMenuAction.Refresh.item(R.string.refresh, R.drawable.ic_bgm_menu_refresh))
        add(BookInfoMenuAction.Share.item(R.string.share, R.drawable.ic_book_info_menu_share))
        add(BookInfoMenuAction.Top.item(R.string.to_top, R.drawable.ic_book_info_menu_top))
        add(
            NgExpandableActionMenuItem(
                itemId = BookInfoMenuAction.Settings.itemId,
                titleRes = R.string.book_info_settings,
                iconRes = R.drawable.ic_book_info_menu_settings,
                dividerBefore = true,
            ),
        )
        if (book != null && !metadataVisible) {
            add(bookInfoMetadataMenu(state, book))
        }
        add(
            NgExpandableActionMenuItem(
                itemId = MENU_SOURCE_AND_LINKS,
                titleRes = R.string.book_info_menu_source_and_links,
                iconRes = R.drawable.ic_web_outline,
                children = buildList {
                    if (state.loginAvailable) {
                        add(BookInfoMenuAction.Login.item(R.string.login, R.drawable.ic_book_info_menu_login))
                    }
                    add(
                        BookInfoMenuAction.CopyBookUrl.item(
                            R.string.copy_book_url,
                            R.drawable.ic_book_info_menu_copy,
                        ),
                    )
                    add(
                        BookInfoMenuAction.CopyTocUrl.item(
                            R.string.copy_toc_url,
                            R.drawable.ic_book_info_menu_copy,
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
                                R.drawable.ic_book_info_menu_upload,
                            ),
                        )
                    }
                    add(
                        BookInfoMenuAction.ClearCache.item(
                            R.string.clear_cache,
                            R.drawable.ic_book_info_menu_clear_cache,
                            enabled = state.hasClearableCache,
                        ),
                    )
                    add(BookInfoMenuAction.Log.item(R.string.log, R.drawable.ic_cfg_about))
                    add(
                        BookInfoMenuAction.NetworkLog.item(
                            R.string.network_request_log,
                            R.drawable.ic_book_info_menu_network,
                        ),
                    )
                },
            ),
        )
    }
}

private fun bookInfoMetadataMenu(state: BookInfoUiState, book: Book) =
    NgExpandableActionMenuItem(
        itemId = MENU_BOOK_METADATA,
        titleRes = R.string.book_info,
        iconRes = R.drawable.ic_book_info_read,
        children = buildList {
            if (state.sourceAvailable) {
                add(BookInfoMenuAction.EditSource.item(R.string.edit_book_source, R.drawable.ic_web_outline))
            }
            if (book.menuCapabilities.changeSource) {
                add(BookInfoMenuAction.ChangeSource.item(R.string.change_origin, R.drawable.ic_exchange))
            }
            add(BookInfoMenuAction.ChangeGroup.item(R.string.bookshelf_move_to_group, R.drawable.ic_groups))
            if (state.showToc) {
                add(BookInfoMenuAction.ViewToc.item(R.string.view_toc, R.drawable.ic_cfg_file_manage))
            }
            if (state.showCache) {
                add(
                    BookInfoMenuAction.CacheBook.item(
                        R.string.book_cache, R.drawable.ic_download_line, enabled = state.cache.enabled,
                    ),
                )
            }
        },
    )

private fun BookInfoMenuAction.item(
    titleRes: Int,
    iconRes: Int,
    checked: Boolean = false,
    enabled: Boolean = true,
): NgExpandableActionMenuItem = NgExpandableActionMenuItem(
    itemId = itemId,
    titleRes = titleRes,
    iconRes = iconRes,
    checked = checked,
    enabled = enabled,
)

@Composable
private fun BookInfoToolbarIcon(
    iconRes: Int,
    contentDescription: String,
    tint: Color = Color(NgTheme.colors.onTopBar),
    onClick: () -> Unit,
) {
    NgVisualIconButton(onClick = onClick) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

private enum class BookInfoHeroVariant { PORTRAIT, LANDSCAPE }

@Composable
private fun BookInfoHeroCard(
    state: BookInfoUiState,
    book: Book,
    onEvent: (BookInfoUiEvent) -> Unit,
    variant: BookInfoHeroVariant = BookInfoHeroVariant.PORTRAIT,
    coverPercent: Int,
) {
    val landscape = variant == BookInfoHeroVariant.LANDSCAPE
    val baseCoverWidth = if (landscape) 96.dp else 78.dp
    val coverSpacing = 16.dp
    BookInfoCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val coverWidthLimit = maxOf(baseCoverWidth, maxWidth * 0.45f)
                .coerceAtMost((maxWidth - coverSpacing).coerceAtLeast(0.dp))
            val coverWidth = (baseCoverWidth * (coverPercent / 100f)).coerceAtMost(coverWidthLimit)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                NgBookCover(
                    book = book,
                    coverRadius = 8,
                    contentDescription = stringResource(R.string.img_cover),
                    revision = state.coverRevision,
                    modifier = Modifier
                        .size(width = coverWidth, height = coverWidth * (4f / 3f))
                        .combinedClickable(
                            onClick = { onEvent(BookInfoUiEvent.CoverClick) },
                            onLongClick = { onEvent(BookInfoUiEvent.CoverLongClick) },
                        ),
                )
                Spacer(Modifier.width(coverSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (landscape) Modifier else Modifier.basicMarquee())
                            .combinedClickable(
                                onClick = { onEvent(BookInfoUiEvent.NameClick) },
                                onLongClick = { onEvent(BookInfoUiEvent.NameLongClick) },
                            ),
                        color = colorResource(R.color.primaryText),
                        fontSize = if (landscape) 18.sp else 17.sp,
                        lineHeight = if (landscape) 24.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (landscape) 2 else 1,
                        overflow = if (landscape) TextOverflow.Ellipsis else TextOverflow.Clip,
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
private fun BookInfoMetaRows(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    BookInfoMetaRow(
        iconRes = R.drawable.ic_web_outline,
        text = state.originText,
        textModifier = Modifier.clickable(enabled = state.sourceAvailable) { onEvent(BookInfoUiEvent.OriginClick) },
        trailingIconRes = R.drawable.ic_exchange.takeIf { state.book?.menuCapabilities?.changeSource == true },
        trailingDescription = stringResource(R.string.change_origin),
        onTrailingClick = { onEvent(BookInfoUiEvent.ChangeSource) },
    )
    BookInfoMetaRow(iconRes = R.drawable.ic_book_last, text = state.latestText)
    BookInfoMetaRow(
        iconRes = R.drawable.ic_groups,
        text = state.groupText,
        onClick = { onEvent(BookInfoUiEvent.ChangeGroup) },
        trailingIconRes = R.drawable.ic_groups,
        trailingDescription = stringResource(R.string.bookshelf_move_to_group),
        onTrailingClick = { onEvent(BookInfoUiEvent.ChangeGroup) },
    )
    if (state.showToc) {
        BookInfoMetaRow(
            iconRes = R.drawable.ic_cfg_file_manage,
            text = state.tocText,
            trailingIconRes = R.drawable.ic_cfg_file_manage,
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
    onClick: (() -> Unit)? = null,
    trailingIconRes: Int? = null,
    trailingDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
        if (state.book?.isEpub == true) {
            Text(
                text = stringResource(R.string.epub_parsed_cache_count, state.cache.cached.toString()),
                modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
                color = colorResource(R.color.tv_text_summary),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
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
        }
        IconButton(
            onClick = { onEvent(BookInfoUiEvent.CacheBook) },
            enabled = state.cache.enabled,
            modifier = Modifier.size(32.dp),
        ) {
            val summaryTint = colorResource(R.color.tv_text_summary)
            val stateAlpha = if (state.cache.enabled) 1f else 0.45f
            Icon(
                painter = painterResource(R.drawable.ic_download_line),
                contentDescription = stringResource(R.string.book_cache),
                tint = summaryTint.copy(alpha = summaryTint.alpha * stateAlpha),
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
internal fun BookInfoIntro(
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
                factory = {
                    // 简介容器由 Activity 持有，列表重建时旧宿主可能仍是它的父容器。
                    (introView.parent as? ViewGroup)?.removeView(introView)
                    introView
                },
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
    listState: LazyListState,
    carouselPreferences: BookInfoCarouselPreferences,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val openCharacters = { onEvent(BookInfoUiEvent.OpenCharacters) }
    if (state.characters.isNotEmpty()) {
        key(state.book?.bookUrl) {
            BookInfoCharacterCarousel(
                characters = state.characters,
                characterCount = state.characterCount,
                silhouetteStyle = state.characterSilhouetteStyle,
                listState = listState,
                carouselPreferences = carouselPreferences,
                onOpenCharacters = openCharacters,
                onOpenCharacter = { onEvent(BookInfoUiEvent.OpenCharacterDetail(it)) },
            )
        }
    } else {
        BookInfoCard(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 4.dp),
        ) {
            BookInfoCharacterHeader(state.characterCount, openCharacters)
            Text(
                text = stringResource(R.string.book_info_characters_empty),
                modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClick = openCharacters)
                    .padding(bottom = 12.dp),
                color = colorResource(R.color.tv_text_summary),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun BookInfoCharacterHeader(
    characterCount: Int,
    onOpenCharacters: () -> Unit,
    modifier: Modifier = Modifier,
    overArtwork: Boolean = false,
) {
    val primary = NgTheme.colors.primary
    val chipColor = Color(0xFFF7FCFF).copy(alpha = 0.76f)
    val chip = if (overArtwork) {
        Modifier.background(chipColor, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp)
    } else {
        Modifier
    }
    val titleColor = if (overArtwork) Color(0xFF344E60) else colorResource(R.color.primaryText)
    val linkColor = if (overArtwork) {
        Color(
            NgColorMath.readableContentColor(
                background = chipColor.compositeOver(Color.Black).toArgb(),
                preferred = NgColorMath.blend(primary, Color.Black.toArgb(), 0.34f),
            ),
        )
    } else {
        Color(primary)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = if (overArtwork) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.book_characters),
                modifier = chip,
                color = titleColor,
                fontSize = if (overArtwork) 10.sp else 15.sp,
                lineHeight = if (overArtwork) 12.sp else 18.sp,
                fontWeight = if (overArtwork) FontWeight.Medium else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.heightIn(min = if (overArtwork) 48.dp else 44.dp)
                .clickable(role = Role.Button, onClick = onOpenCharacters),
            contentAlignment = if (overArtwork) Alignment.TopCenter else Alignment.Center,
        ) {
            Row(
                modifier = if (overArtwork) chip else Modifier.padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (overArtwork) 4.dp else 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.character_count_format, characterCount),
                    color = linkColor,
                    fontSize = if (overArtwork) 10.sp else 13.sp,
                    lineHeight = if (overArtwork) 12.sp else 16.sp,
                    fontWeight = if (overArtwork) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = 1,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = linkColor,
                    modifier = Modifier.size(if (overArtwork) 12.dp else 18.dp),
                )
            }
        }
    }
}

@Composable
private fun BookInfoCharacterCarousel(
    characters: List<BookInfoCharacterUiItem>,
    characterCount: Int,
    silhouetteStyle: BookCharacterSilhouetteStyle,
    listState: LazyListState,
    carouselPreferences: BookInfoCarouselPreferences,
    onOpenCharacters: () -> Unit,
    onOpenCharacter: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { characters.size })
    val cardInteractions = remember { MutableInteractionSource() }
    BookInfoCharacterAutoplay(pagerState, listState, carouselPreferences, cardInteractions)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NgTheme.shapes.smallDp.dp)),
    ) {
        val height = (maxWidth * 0.60f).coerceIn(172.dp, 224.dp)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(height),
            key = { characters[it].id },
        ) { page ->
            val character = characters[page]
            BookInfoCharacterCard(
                character = character,
                silhouetteStyle = silhouetteStyle,
                page = page,
                pageCount = characters.size,
                interactionSource = cardInteractions,
                onClick = { onOpenCharacter(character.id) },
            )
        }
        BookInfoCharacterHeader(
            characterCount = characterCount,
            onOpenCharacters = onOpenCharacters,
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 8.dp, top = 7.dp),
            overArtwork = true,
        )
    }
}

@Composable
private fun BookInfoCharacterAutoplay(
    pagerState: PagerState,
    listState: LazyListState,
    preferences: BookInfoCarouselPreferences,
    cardInteractions: MutableInteractionSource,
) {
    if (!preferences.autoPlay || pagerState.pageCount < 2) return
    val isVisible by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == BookInfoSection.CHARACTERS.key }
        }
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    if (!isVisible || lifecycleState != Lifecycle.State.RESUMED || !windowFocused) return

    val isPressed by cardInteractions.collectIsPressedAsState()
    LaunchedEffect(pagerState, listState, preferences.intervalSeconds) {
        fun isInteracting() = isPressed || pagerState.isScrollInProgress || listState.isScrollInProgress

        while (true) {
            snapshotFlow(::isInteracting).first { !it }
            val interrupted = withTimeoutOrNull(preferences.intervalSeconds * 1_000L) {
                snapshotFlow(::isInteracting).first { it }
            } == true
            if (interrupted || isInteracting()) continue

            // A manual swipe can cancel the pager animation without cancelling the timer loop.
            launch {
                val lastPage = pagerState.pageCount - 1
                if (lastPage < 1) return@launch
                if (pagerState.settledPage >= lastPage) {
                    pagerState.scrollToPage(0)
                } else {
                    pagerState.animateScrollToPage(pagerState.settledPage + 1)
                }
            }.join()
        }
    }
}

@Composable
private fun BookInfoCharacterCard(
    character: BookInfoCharacterUiItem,
    silhouetteStyle: BookCharacterSilhouetteStyle,
    page: Int,
    pageCount: Int,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val portrait = remember(character.portraitUri) {
        BookCharacterPortraits.localUri(character.portraitUri)
    }
    val silhouette = silhouetteStyle.forRole(character.roleTag, hasPortrait = portrait != null)
    val artworkBackground = if (portrait == null) {
        silhouette.background
    } else {
        Color(NgTheme.colors.surfaceVariant)
    }
    val artworkWidth = 0.68f
    val artworkAlignment = if (portrait == null) BiasAlignment(0f, -0.7f) else Alignment.TopCenter
    Box(
        Modifier.fillMaxSize()
            .background(artworkBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        BookCharacterPortraitImage(
            portrait = portrait,
            silhouette = silhouette,
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(artworkWidth).fillMaxHeight(),
            alignment = artworkAlignment,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    (1f - artworkWidth) to artworkBackground,
                    0.45f to artworkBackground.copy(alpha = 0.69f),
                    0.62f to artworkBackground.copy(alpha = 0f),
                ),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.12f to Color.Black.copy(alpha = 0.43f),
                        0.65f to Color.Black.copy(alpha = 0.66f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                )
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = character.name,
                        modifier = Modifier.weight(1f, fill = false),
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (character.roleTag != BookCharacter.RoleTag.UNKNOWN) {
                        Text(
                            text = BookCharacterLabels.roleLabel(LocalContext.current, character.roleTag),
                            modifier = Modifier.background(Color(0xA33E567C), CircleShape)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (pageCount > 1) {
                    BookInfoCharacterPosition(page, pageCount)
                }
            }
            character.intro?.let { intro ->
                Text(
                    text = intro,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BookInfoCharacterPosition(page: Int, pageCount: Int) {
    val position = stringResource(R.string.book_info_character_position, page + 1, pageCount)
    val active = lerp(Color(NgTheme.colors.primary), Color.White, 0.56f)
    val inactive = Color.White.copy(alpha = 0.44f)
    Row(
        modifier = Modifier.semantics { contentDescription = position },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == page
            Box(
                Modifier.size(width = if (selected) 9.dp else 3.dp, height = 3.dp)
                    .background(if (selected) active else inactive, CircleShape),
            )
        }
    }
}

@Composable
private fun BookInfoOtherWorks(
    state: BookInfoUiState,
    onEvent: (BookInfoUiEvent) -> Unit,
) {
    val dividerColor = colorResource(R.color.secondaryText).copy(alpha = 0.12f)
    val dividerPadding = Modifier.padding(horizontal = 16.dp)
    val otherState = state.otherWorksState
    BookInfoCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(start = 16.dp, end = 8.dp),
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
        if (state.otherWorksLoading || otherState != BookInfoOtherWorksState.Idle) {
            HorizontalDivider(modifier = dividerPadding, thickness = 0.5.dp, color = dividerColor)
        }
        if (state.otherWorksLoading) {
            BookInfoOtherWorksLoading()
        }
        when (otherState) {
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
                state.otherWorks.forEachIndexed { index, item ->
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
                        outerHorizontalPadding = 8.dp,
                        outerVerticalPadding = 0.dp,
                        cardCornerRadius = 0.dp,
                        cardContentPadding = 8.dp,
                        cardContentTopPadding = if (index == 0) 4.dp else 8.dp,
                        cardBackgroundColorRes = android.R.color.transparent,
                        cardBorderWidth = 0.dp,
                    )
                    if (index < state.otherWorks.lastIndex) {
                        HorizontalDivider(modifier = dividerPadding, thickness = 0.5.dp, color = dividerColor)
                    }
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
    onEvent: (BookInfoUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val surfaceVariant = NgActionBarButtonSurfaceVariant.LIGHT_GLASS
    val secondarySurfaceVariant = if (NgTheme.snapshot.isDark) {
        NgActionBarButtonSurfaceVariant.THEMED_HIGH_CONTRAST
    } else {
        surfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(58.dp)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
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
            surfaceVariant = secondarySurfaceVariant,
        )
        if (state.showListen) {
            NgActionBarButton(
                text = stringResource(R.string.book_info_listen),
                icon = ImageVector.vectorResource(R.drawable.ic_tts_headphones),
                onClick = { onEvent(BookInfoUiEvent.Listen) },
                modifier = Modifier.weight(1f),
                surfaceVariant = secondarySurfaceVariant,
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
    cornerRadius: Dp = NgTheme.shapes.smallDp.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    NgGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        style = NgGlassDefaults.bookDetailStyle(),
        role = NgMaterialRole.CONTENT,
        liquidCornerRadius = cornerRadius,
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
                    Text(
                        stringResource(R.string.open_fun),
                        color = if (NgTheme.snapshot.isDark && !NgTheme.snapshot.isEInk) {
                            LocalContentColor.current
                        } else Color.White,
                        fontSize = 14.sp,
                    )
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

private const val MENU_SOURCE_AND_LINKS = 0x7382
private const val MENU_DATA_AND_DIAGNOSTICS = 0x7383
private const val MENU_BOOK_METADATA = 0x7384
