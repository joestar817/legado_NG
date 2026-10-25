package io.legado.app.ui.book.bgm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.help.bgm.MusicTrack
import io.legado.app.ui.design.theme.NgTheme

/** Catalogue metadata only: a badge never grants or denies playback. */
@Composable
internal fun BgmTrackVipBadge(track: MusicTrack) {
    if (track.isVip != true || track.platform == MusicPlatform.LOCAL) return
    val snapshot = NgTheme.snapshot
    val color = when {
        snapshot.isEInk -> Color(NgTheme.colors.onSurface)
        track.platform == MusicPlatform.QQ -> Color(if (snapshot.isDark) 0xFF6DDBB6 else 0xFF008B65)
        else -> Color(if (snapshot.isDark) 0xFFFF969D else 0xFFD93E49)
    }
    val shape = RoundedCornerShape(2.dp)
    val decoration = if (track.platform == MusicPlatform.QQ) {
        Modifier.background(color.copy(alpha = .1f), shape)
    } else Modifier.border(.6.dp, color.copy(alpha = .8f), shape)
    Box(Modifier.height(14.dp).then(decoration).padding(horizontal = 4.dp)
        .clearAndSetSemantics { contentDescription = "VIP歌曲" }, Alignment.Center) {
        Text("VIP", color = color, fontSize = 10.sp, lineHeight = 12.sp,
            maxLines = 1, softWrap = false)
    }
}
