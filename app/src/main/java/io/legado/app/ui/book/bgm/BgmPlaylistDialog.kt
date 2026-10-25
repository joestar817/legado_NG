package io.legado.app.ui.book.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.bgm.MusicPlaylist
import io.legado.app.help.bgm.MusicTrack
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme

internal enum class BgmPlaylistAction { CREATE, EDIT, PICK, DELETE, UNSUBSCRIBE, REMOVE_TRACKS }

/** Transient editor state belongs to the ViewModel, including the original mutation target. */
internal data class BgmPlaylistDialogState(
    val action: BgmPlaylistAction,
    val playlist: MusicPlaylist? = null,
    val tracks: List<MusicTrack> = emptyList(),
    val name: String = "",
    val description: String = "",
    val choices: List<MusicPlaylist> = emptyList(),
    val nextOffset: Int? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String = "",
) {
    val isDirectAddition: Boolean get() = action == BgmPlaylistAction.PICK && playlist != null
}

@Composable
internal fun BgmPlaylistDialog(
    state: BgmPlaylistDialogState,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onSubmit: () -> Unit,
    onChoose: (MusicPlaylist) -> Unit,
    onNew: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state.action == BgmPlaylistAction.PICK) {
        BgmPlaylistPickerSheet(state, onChoose, onNew, onLoadMore, onRetry, onDismiss)
        return
    }
    val editing = state.action == BgmPlaylistAction.CREATE || state.action == BgmPlaylistAction.EDIT
    val confirming = !editing
    val deleting = state.action == BgmPlaylistAction.DELETE
    val removing = state.action == BgmPlaylistAction.REMOVE_TRACKS
    val title = when (state.action) {
        BgmPlaylistAction.CREATE -> "新建歌单"
        BgmPlaylistAction.EDIT -> "编辑歌单"
        BgmPlaylistAction.PICK -> "加入歌单"
        BgmPlaylistAction.DELETE -> "删除歌单"
        BgmPlaylistAction.UNSUBSCRIBE -> "取消收藏"
        BgmPlaylistAction.REMOVE_TRACKS -> "移出歌单"
    }
    val confirmLabel = when {
        deleting -> "删除"
        removing -> "移出"
        else -> title
    }
    NgPaneDialog(onDismissRequest = onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
            val bodyHeight = if (confirming) (maxHeight - 128.dp).coerceIn(0.dp, 400.dp)
                else (maxHeight - 144.dp).coerceIn(80.dp, 400.dp)
            NgDialog(
                title = title,
                variant = if (confirming) NgDialogVariant.COMPACT_CONFIRMATION else NgDialogVariant.STANDARD,
                titleFontWeight = if (confirming) FontWeight.SemiBold else FontWeight.Bold,
                actions = {
                    if (confirming) {
                        NgFormActionButton("取消", onDismiss, modifier = Modifier.weight(1f),
                            enabled = !state.busy, variant = NgButtonVariant.NEUTRAL,
                            appearance = NgFormActionButtonAppearance.DIALOG,
                            buttonHeight = 44.dp, textSize = 15.sp, textLineHeight = 20.sp)
                        NgFormActionButton(if (state.busy) when {
                            deleting -> "删除中…"
                            removing -> "移出中…"
                            else -> "处理中…"
                        } else confirmLabel, onSubmit,
                            modifier = Modifier.weight(1f), enabled = !state.busy,
                            variant = if (deleting || removing) NgButtonVariant.DANGER else NgButtonVariant.PRIMARY,
                            appearance = NgFormActionButtonAppearance.DIALOG,
                            buttonHeight = 44.dp, textSize = 15.sp, textLineHeight = 20.sp,
                            leadingIcon = { BgmIcon(if (deleting || removing) R.drawable.ic_book_info_delete else R.drawable.ic_star_border,
                                modifier = Modifier.size(18.dp), tint = LocalContentColor.current) })
                    } else {
                        NgDialogTextActionButton("取消", onDismiss, secondary = true, enabled = !state.busy)
                        NgDialogTextActionButton(if (state.busy) "处理中…" else "保存",
                            onSubmit, enabled = !state.busy && state.name.isNotBlank())
                    }
                },
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = bodyHeight)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (confirming) {
                        PlaylistConfirmationPreview(state)
                    } else {
                        NgFormField(label = "歌单名称", value = state.name, onValueChange = onName, placeholder = "歌单名称",
                            enabled = !state.busy, variant = NgFormFieldVariant.DIALOG_UNDERLINE)
                        if (state.action == BgmPlaylistAction.EDIT) {
                            NgFormField(label = "简介", value = state.description, onValueChange = onDescription, placeholder = "简介",
                                enabled = !state.busy,
                                variant = NgFormFieldVariant.DIALOG_UNDERLINE)
                        }
                    }
                    if (state.error.isNotBlank()) DialogMessage(state.error)
                }
            }
        }
    }
}

@Composable
private fun PlaylistConfirmationPreview(state: BgmPlaylistDialogState) {
    val playlist = state.playlist
    val summary = if (state.action == BgmPlaylistAction.REMOVE_TRACKS) "已选 ${state.tracks.size} 首"
        else playlist?.let { "${it.trackCount} 首" }.orEmpty()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(NgTheme.shapes.mediumDp.dp))
            .background(Color(NgTheme.colors.onSurface).copy(alpha = if (NgTheme.snapshot.isEInk) .08f else .035f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BgmCover(playlist?.coverUrl.orEmpty(), Modifier.size(48.dp),
            cornerRadius = 8.dp, placeholder = Icons.Rounded.MusicNote)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BgmText(playlist?.title.orEmpty(), strong = true, maxLines = 2)
            if (summary.isNotEmpty()) Text(summary, color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun DialogMessage(message: String) {
    Text(message, Modifier.padding(vertical = 8.dp),
        color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp)
}
