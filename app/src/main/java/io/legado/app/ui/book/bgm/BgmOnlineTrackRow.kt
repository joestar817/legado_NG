package io.legado.app.ui.book.bgm

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.bgm.MusicTrack
import io.legado.app.ui.design.theme.NgTheme

/** Online rows share artwork and audition controls while management keeps its selection semantics. */
@Composable
internal fun BgmOnlineTrackRow(
    track: MusicTrack,
    checked: Boolean,
    onToggle: () -> Unit,
    previewing: Boolean,
    previewLoading: Boolean,
    previewPaused: Boolean,
    onPreview: () -> Unit,
    enabled: Boolean,
    selectable: Boolean,
    query: String,
    selectionAtEnd: Boolean = false,
    alreadyAdded: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val listening = LocalBgmListeningMode.current
    val rowHeight = if (listening) 60.dp else 68.dp
    val primary = Color(NgTheme.colors.primary)
    val content = Color(NgTheme.colors.onSurface)
    val secondary = content.copy(alpha = if (NgTheme.snapshot.isEInk) .8f else .62f)
    val artist = track.artist.ifBlank { track.platform.bgmTitle() }
    val titleText = remember(track.title, query, primary) { highlightQuery(track.title, query, primary) }
    val artistText = remember(artist, query, primary) { highlightQuery(artist, query, primary) }
    val duration = remember(track.durationMs) {
        if (track.durationMs > 0) {
            "${track.durationMs / 60000}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}"
        } else ""
    }
    val previewLabel = when {
        previewLoading -> "取消加载试听"
        previewPaused -> "继续试听"
        previewing -> "暂停试听"
        else -> "试听 ${track.title}"
    }
    val rowAction = if (selectable) {
        Modifier.toggleable(checked, enabled = enabled && !alreadyAdded, role = Role.Checkbox,
            onValueChange = { onToggle() }).semantics {
                selected = checked
                if (alreadyAdded) stateDescription = "已添加"
            }
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button,
            onClickLabel = previewLabel, onClick = onPreview)
    }
    Column(Modifier.bgmListSurface()) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (previewing) primary.copy(alpha = .05f) else Color.Transparent)
                .heightIn(min = rowHeight).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).heightIn(min = rowHeight).then(rowAction)
                    .padding(start = if (listening) 4.dp else 14.dp,
                        top = if (listening) 8.dp else 10.dp, bottom = if (listening) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectable && !selectionAtEnd) {
                    Box(Modifier.size(if (listening) 40.dp else 48.dp), Alignment.Center) {
                        Box(Modifier.size(18.dp).clip(CircleShape).then(
                            if (checked) Modifier.background(primary)
                            else Modifier.border(1.3.dp, secondary, CircleShape)
                        ), Alignment.Center) {
                            if (checked) BgmIcon(R.drawable.ic_check, modifier = Modifier.size(12.dp),
                                tint = if (listening) Color(NgTheme.colors.onPrimary) else Color.White)
                        }
                    }
                } else {
                    BgmCover(track.coverUrl, Modifier.size(if (listening) 40.dp else 48.dp), cornerRadius = 7.dp,
                        placeholder = Icons.Rounded.MusicNote)
                }
                Spacer(Modifier.width(if (listening) 6.dp else 12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(titleText, color = if (previewing) primary else content,
                        fontFamily = NgTheme.fontFamily, fontSize = 15.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(artistText, Modifier.weight(1f, fill = false), color = secondary,
                            fontFamily = NgTheme.fontFamily, fontSize = 12.sp, lineHeight = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BgmTrackVipBadge(track)
                    }
                }
                if (duration.isNotEmpty()) {
                    Text(duration, Modifier.padding(start = 8.dp).widthIn(min = 32.dp),
                        color = secondary, fontFamily = NgTheme.fontFamily,
                        fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1,
                        textAlign = TextAlign.End)
                }
            }
            BgmTrackActionButton(
                icon = when {
                    previewLoading -> R.drawable.ic_stop_black_24dp
                    previewing && !previewPaused -> R.drawable.ic_pause_24dp
                    else -> R.drawable.ic_play_outline_24dp
                },
                description = previewLabel,
                onClick = onPreview,
                enabled = enabled,
                active = previewing,
            )
            if (selectable && selectionAtEnd) {
                if (alreadyAdded) {
                    Box(Modifier.size(width = 40.dp, height = 48.dp), Alignment.Center) {
                        Text("已添加", color = secondary, fontFamily = NgTheme.fontFamily,
                            fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                } else Box(Modifier.size(width = 40.dp, height = 48.dp)
                    .toggleable(checked, enabled = enabled, role = Role.Checkbox,
                        onValueChange = { onToggle() })
                    .semantics { contentDescription = "选择 ${track.title}" }, Alignment.Center) {
                    Box(Modifier.size(18.dp).clip(CircleShape).then(
                        if (checked) Modifier.background(primary)
                        else Modifier.border(1.3.dp, secondary, CircleShape)
                    ), Alignment.Center) {
                        if (checked) BgmIcon(R.drawable.ic_check, modifier = Modifier.size(12.dp),
                            tint = if (listening) Color(NgTheme.colors.onPrimary) else Color.White)
                    }
                }
            } else trailing?.invoke()
        }
        HorizontalDivider(Modifier.padding(start = if (listening) 50.dp else 74.dp, end = 14.dp), thickness = .5.dp,
            color = content.copy(alpha = if (NgTheme.snapshot.isEInk) .18f else .08f))
    }
}

@Composable
internal fun BgmTrackActionButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean,
    active: Boolean = false,
) {
    val tint = Color(if (active) NgTheme.colors.primary else NgTheme.colors.onSurface)
    Box(
        Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, Modifier.size(22.dp),
            tint = tint.copy(alpha = if (!enabled) .38f else if (active) 1f else .7f))
    }
}

private fun highlightQuery(text: String, query: String, color: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isNotEmpty()) {
            var index = text.indexOf(query, ignoreCase = true)
            while (index >= 0) {
                addStyle(SpanStyle(color = color), index, index + query.length)
                index = text.indexOf(query, startIndex = index + query.length, ignoreCase = true)
            }
        }
    }
