package io.legado.app.ui.video

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import kotlinx.coroutines.launch

@Composable
internal fun VideoEpisodeSheet(
    groups: List<VideoEpisodeGroup<BookChapter>>,
    initialVolume: Int,
    currentVolume: Int,
    currentEpisode: Int,
    currentDuration: Long,
    onSelect: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var browsingVolume by rememberSaveable { mutableIntStateOf(initialVolume) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable {
        mutableStateOf(groups.all { group -> group.items.all { it.chapter.title.length <= 10 } })
    }
    var locateRequest by rememberSaveable { mutableIntStateOf(0) }
    val groupIndex = browsingVolume.coerceIn(0, groups.lastIndex.coerceAtLeast(0))
    val group = groups.getOrNull(groupIndex)
    val totalCount = remember(groups) { groups.sumOf { it.items.size } }
    val hasTools = totalCount > 1 || groups.size > 1
    val items = remember(groups, groupIndex, query, descending) {
        filterVideoEpisodes(groups, groupIndex, query, descending) { it.title }
    }
    val selectedIndex = remember(items, currentVolume, currentEpisode) {
        items.indexOfFirst { it.volumeIndex == currentVolume && it.episodeIndex == currentEpisode }
    }
    val showGrid = grid && items.size > 1 && query.isBlank()
    val currentItem = remember(groups, currentVolume, currentEpisode) {
        groups.getOrNull(currentVolume)?.items?.firstOrNull { it.episodeIndex == currentEpisode }
    }
    val listState = rememberLazyListState(selectedIndex.coerceAtLeast(0))
    val gridState = rememberLazyGridState(selectedIndex.coerceAtLeast(0))
    val volumeState = rememberLazyListState(groupIndex)
    val scope = rememberCoroutineScope()
    val colors = videoContentColors()
    val accent = colors.accent
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val scrollRequest = "$groupIndex:$descending:$locateRequest:$query"
    var handledScrollRequest by rememberSaveable { mutableStateOf(scrollRequest) }
    LaunchedEffect(groupIndex, query, descending, locateRequest, showGrid) {
        // The list states restore their own viewport after configuration changes.
        if (handledScrollRequest == scrollRequest) return@LaunchedEffect
        val index = selectedIndex.coerceAtLeast(0)
        if (items.isNotEmpty()) {
            if (showGrid) gridState.scrollToItem(index) else listState.scrollToItem(index)
        }
        if (query.isBlank() && groups.size > 1) volumeState.animateScrollToItem(groupIndex)
        handledScrollRequest = scrollRequest
    }
    fun locateCurrent() {
        browsingVolume = currentVolume; query = ""; locateRequest++; focus.clearFocus()
    }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true, decorFitsSystemWindows = false)) {
        // As in the home drawer, measure from the actual window rather than Configuration.
        val window = (LocalView.current.parent as DialogWindowProvider).window
        DisposableEffect(window) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            onDispose { }
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val compactHeight = maxHeight < 280.dp
            val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            // Keep the focused field in one composition position while the keyboard opens/closes.
            val inlineSearch = searching && hasTools && landscape
            Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))
            val shape = if (landscape) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            Column(
                (if (landscape) Modifier.align(Alignment.CenterEnd).width(minOf(420.dp, maxWidth * .55f)).fillMaxHeight()
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .heightIn(max = if (keyboardVisible) maxHeight else maxHeight * .72f))
                    .clip(shape).background(colors.surface)
                    .semantics { dismiss { onDismiss(); true } }
                    .pointerInput(Unit) { detectTapGestures {} },
            ) {
                val dismissOnDrag = if (landscape) Modifier else Modifier.pointerInput(onDismiss) {
                    var drag = 0f
                    detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
                        onDragEnd = { if (drag > 48.dp.toPx()) onDismiss() }) { change, amount ->
                        change.consume(); drag += amount
                    }
                }
                Column(dismissOnDrag) {
                    if (!landscape) NgDrawerDragHandle(Modifier.padding(top = 8.dp), variant = NgDrawerDragHandleVariant.COMPACT)
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 18.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (inlineSearch) {
                            EpisodeSearchInput(query, { query = it }, colors, searchFocus, Modifier.weight(1f))
                        } else {
                            Text("选集", color = colors.content, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(if (query.isBlank()) "${group?.items?.size ?: 0}集" else "${items.size}/$totalCount",
                                Modifier.padding(start = 10.dp), color = colors.muted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                        }
                        if (hasTools) {
                            EpisodeIcon(R.drawable.ic_search, if (searching) "收起搜索" else "搜索剧集", {
                                searching = !searching
                                if (!searching) { query = ""; focus.clearFocus() }
                            }, active = searching)
                            Box {
                                EpisodeIcon(if (descending) R.drawable.ic_catalog_sort_descending else R.drawable.ic_catalog_sort_ascending,
                                    "选集选项", { menu = true; focus.clearFocus() })
                                DropdownMenu(menu, { menu = false }, containerColor = colors.cell) {
                                    DropdownMenuItem(text = { Text(if (descending) "正序" else "倒序", color = colors.content) },
                                        onClick = { descending = !descending; menu = false })
                                    DropdownMenuItem(text = { Text(if (grid) "列表" else "网格", color = colors.content) },
                                        onClick = {
                                            val index = if (showGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                                            grid = !grid; menu = false
                                            val useGrid = grid && items.size > 1 && query.isBlank()
                                            scope.launch {
                                                if (items.isNotEmpty()) {
                                                    if (useGrid) gridState.scrollToItem(index) else listState.scrollToItem(index)
                                                }
                                            }
                                        })
                                    DropdownMenuItem(text = { Text("定位当前集", color = colors.content) },
                                        onClick = { menu = false; locateCurrent() })
                                }
                            }
                        }
                    }
                }
                if (query.isBlank() && !inlineSearch && groups.any { it.volume != null }) {
                    LazyRow(state = volumeState, contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        itemsIndexed(groups, key = { index, value -> "${index}:${value.volume?.url}" }) { index, value ->
                            val active = groupIndex == index
                            Box(Modifier.height(48.dp).widthIn(min = 48.dp, max = 200.dp)
                                .semantics { selected = active }
                                .clickable(role = Role.Tab) { browsingVolume = index; focus.clearFocus() }, contentAlignment = Alignment.Center) {
                                Text(value.volume?.title.orEmpty(), Modifier.padding(vertical = 10.dp), color = if (active) colors.content else colors.muted,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (active) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(accent))
                            }
                        }
                    }
                }
                if (searching && hasTools) {
                    LaunchedEffect(inlineSearch) { searchFocus.requestFocus() }
                    if (!inlineSearch) Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = colors.muted)
                        EpisodeSearchInput(query, { query = it }, colors, searchFocus, Modifier.weight(1f).padding(start = 8.dp))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = colors.content.copy(alpha = .12f))
                }
                if (items.isEmpty()) Box(Modifier.weight(1f, fill = landscape).fillMaxWidth()
                    .heightIn(min = if (compactHeight) 48.dp else 120.dp), contentAlignment = Alignment.Center) {
                    Text(if (query.isBlank()) "暂无可播放剧集" else "未找到相关剧集", color = colors.muted, fontSize = 14.sp)
                } else if (showGrid) {
                    BoxWithConstraints(Modifier.weight(1f, fill = landscape).fillMaxWidth()) {
                    val columns = minOf(4, ((maxWidth - 28.dp) / 88.dp).toInt().coerceAtLeast(1))
                    LazyVerticalGrid(columns = GridCells.Fixed(columns), state = gridState,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        gridItems(items, key = { "${it.chapter.index}:${it.chapter.url}" }) { item ->
                            EpisodeTile(item, item.volumeIndex == currentVolume && item.episodeIndex == currentEpisode,
                                colors, true, 0, null
                            ) { onSelect(item.volumeIndex, item.episodeIndex); onDismiss() }
                        }
                    }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = landscape).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = if (hasTools) 12.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items, key = { "${it.chapter.index}:${it.chapter.url}" }) { item ->
                            EpisodeTile(item, item.volumeIndex == currentVolume && item.episodeIndex == currentEpisode,
                                colors, false, if (item.volumeIndex == currentVolume && item.episodeIndex == currentEpisode) currentDuration else 0,
                                if (query.isNotBlank()) groups.getOrNull(item.volumeIndex)?.volume?.title else null
                            ) { onSelect(item.volumeIndex, item.episodeIndex); onDismiss() }
                        }
                    }
                }
                if (hasTools && currentItem != null && !compactHeight) {
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = colors.content.copy(alpha = .1f))
                    Text("第${currentItem.episodeIndex + 1}集 · ${currentItem.chapter.title}",
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "定位当前集", onClick = ::locateCurrent)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        color = colors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EpisodeTile(item: VideoEpisodeItem<BookChapter>, current: Boolean, colors: VideoContentColors,
                        grid: Boolean, duration: Long, volumeTitle: String?, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = if (grid) 72.dp else 56.dp)
        .clip(RoundedCornerShape(if (grid) 8.dp else 12.dp))
        .background(if (current && grid) colors.selectedContainer else colors.cell)
        .semantics { selected = current }.clickable(role = Role.Button, onClick = onClick)
    if (grid) Box(modifier.semantics { contentDescription = "第${item.episodeIndex + 1}集 ${item.chapter.title}" },
        contentAlignment = Alignment.Center) {
        val ink = if (current) colors.onAccent else colors.content
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text((item.episodeIndex + 1).toString().padStart(2, '0'), color = ink, fontSize = 15.sp,
                fontWeight = FontWeight.Medium)
            Text(item.chapter.title, Modifier.padding(top = 4.dp), color = ink,
                fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
        }
        if (item.chapter.isVip && !item.chapter.isPay) Icon(painterResource(R.drawable.ic_lock_outline), "VIP",
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(10.dp), tint = ink)
    } else Row(modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((item.episodeIndex + 1).toString().padStart(2, '0'), color = if (current) colors.accent else colors.muted,
            fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(20.dp).background(colors.content.copy(alpha = .16f)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EpisodeAccess(item.chapter, colors.muted)
                Text(item.chapter.title, color = colors.content, fontSize = 14.sp, lineHeight = 20.sp,
                    fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            listOfNotNull(volumeTitle, item.chapter.tag).filter { it.isNotBlank() }
                .joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.padding(top = 3.dp), color = colors.muted, fontSize = 10.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (duration > 0 && item.chapter.tag.isNullOrBlank()) Text(videoTime(duration), Modifier.padding(start = 12.dp),
            color = colors.muted, fontSize = 12.sp)
        if (current) Icon(Icons.Default.Equalizer, "当前集", Modifier.padding(start = 12.dp).size(20.dp), tint = colors.accent)
    }
}

@Composable
private fun EpisodeAccess(chapter: BookChapter, tint: Color) {
    if (chapter.isVip && !chapter.isPay) {
        Icon(painterResource(R.drawable.ic_lock_outline), "VIP", Modifier.padding(end = 4.dp).size(16.dp), tint = tint)
    }
}

@Composable
private fun EpisodeIcon(icon: Int, label: String, onClick: () -> Unit, active: Boolean = false) {
    IconButton(onClick, Modifier.size(48.dp)) {
        val colors = videoContentColors()
        Icon(painterResource(icon), label, Modifier.size(22.dp), tint = if (active) colors.accent else colors.content)
    }
}

@Composable
private fun EpisodeSearchInput(query: String, onQueryChange: (String) -> Unit, colors: VideoContentColors,
                               focusRequester: FocusRequester, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Row(modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(query, onQueryChange, Modifier.weight(1f).focusRequester(focusRequester)
            .semantics { contentDescription = "搜索集名或集数" },
            textStyle = TextStyle(color = colors.content, fontSize = 14.sp), singleLine = true,
            cursorBrush = SolidColor(colors.accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { field -> Box {
                if (query.isEmpty()) Text("搜索集名或集数", color = colors.muted, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                field()
            } })
        if (query.isNotEmpty()) TextButton(onClick = { onQueryChange("") }, modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("清空", color = colors.muted, fontSize = 12.sp)
        }
    }
}
