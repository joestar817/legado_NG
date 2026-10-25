package io.legado.app.ui.book.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.bgm.MusicPlaylist
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

/** Picking a destination keeps the existing platform mutation and editor callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BgmPlaylistPickerSheet(
    state: BgmPlaylistDialogState,
    onChoose: (MusicPlaylist) -> Unit,
    onNew: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy by rememberUpdatedState(state.busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    val enabled = !state.busy && !state.loading
    val hasError = state.error.isNotBlank()
    val hasMore = state.nextOffset != null
    val statusMessage = when {
        state.loading -> "加载中…"
        !state.busy && state.choices.isEmpty() && !hasError -> "还没有可添加的歌单"
        else -> null
    }
    val drawerHeight = (LocalConfiguration.current.screenHeightDp * .88f).dp
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(NgTheme.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgPaneWindowEffect()
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().height(drawerHeight),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(top = 6.dp)) {
                NgLongDrawerHeader(
                    title = "加入歌单",
                    statusText = "添加中…".takeIf { state.busy },
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = .5.dp,
                    color = Color(NgTheme.colors.onSurface).copy(alpha = .08f),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(NgTheme.shapes.largeDp.dp)),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                ) {
                    item("new") {
                        PlaylistPickerGroupItem(first = true,
                            last = state.choices.isEmpty() && !hasError && statusMessage == null && !hasMore) {
                            PlaylistChoiceRow("新建歌单", enabled = enabled, onClick = onNew) {
                                Box(
                                    Modifier.size(60.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color(NgTheme.colors.onSurface).copy(alpha = .05f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = null,
                                        modifier = Modifier.size(28.dp), tint = Color(NgTheme.colors.primary))
                                }
                            }
                        }
                    }
                    if (hasError) item("error") {
                        PlaylistPickerGroupItem(first = false,
                            last = state.choices.isEmpty() && statusMessage == null && !hasMore) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                BgmText(state.error, secondary = true, maxLines = 4)
                                NgDialogTextActionButton("重新加载歌单", onRetry, enabled = enabled)
                            }
                        }
                    }
                    itemsIndexed(state.choices, key = { _, playlist -> playlist.key }) { index, playlist ->
                        PlaylistPickerGroupItem(first = false,
                            last = index == state.choices.lastIndex && statusMessage == null && !hasMore) {
                            PlaylistChoiceRow(playlist.title, "${playlist.trackCount} 首", enabled,
                                onClick = { onChoose(playlist) }) {
                                BgmCover(playlist.coverUrl, Modifier.size(60.dp),
                                    cornerRadius = 10.dp, placeholder = Icons.Rounded.MusicNote)
                            }
                        }
                    }
                    if (statusMessage != null) item(if (state.loading) "loading" else "empty") {
                        PlaylistPickerGroupItem(first = false, last = !hasMore) {
                            PlaylistPickerMessage(statusMessage)
                        }
                    }
                    if (hasMore) item("more") {
                        PlaylistPickerGroupItem(first = false, last = true) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                                NgDialogTextActionButton("加载更多", onLoadMore, enabled = enabled)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerGroupItem(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val corner = NgTheme.shapes.largeDp.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )
    Box(Modifier.fillMaxWidth().clip(shape).background(ngDrawerContentCardColor())) { content() }
}

@Composable
private fun PlaylistChoiceRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean,
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp).alpha(if (enabled) 1f else .45f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        cover()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BgmText(title, strong = true)
            if (subtitle != null) BgmText(subtitle, secondary = true)
        }
    }
}

@Composable
private fun PlaylistPickerMessage(message: String) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        color = Color(NgTheme.colors.onSurfaceVariant), style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center)
}
