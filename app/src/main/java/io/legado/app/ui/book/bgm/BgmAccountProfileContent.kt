package io.legado.app.ui.book.bgm

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.legado.app.help.bgm.MusicMembershipInfo
import io.legado.app.help.bgm.MusicPlatform
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BgmAccountProfileContent(
    platform: MusicPlatform,
    state: BgmAccountProfileUiState,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    val profile = state.profile
    val isQq = platform == MusicPlatform.QQ
    val foreground = Color(NgTheme.colors.onSurface)
    val secondary = Color(NgTheme.colors.onSurfaceVariant)
    val divider = foreground.copy(alpha = .08f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (profile != null) {
            val avatarSize = if (isQq) 64.dp else 72.dp
            BgmCover(profile.avatarUrl, Modifier.size(avatarSize).semantics { contentDescription = "用户头像" },
                cornerRadius = avatarSize / 2, placeholder = Icons.Rounded.Person)
            Spacer(Modifier.height(if (isQq) 10.dp else 12.dp))
            Text(
                text = profile.nickname.ifBlank { "用户 ${profile.userId}" },
                modifier = Modifier.fillMaxWidth(),
                color = foreground, fontSize = 20.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (isQq) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.membership?.let { BgmVipBadge(it, compactQqBadge = true) }
                    state.musicLevel?.let {
                        BgmOfficialProfileBadge(state.musicLevelIconUrl, "账号等级 S$it",
                            compactQqBadge = true) {
                            Text("S$it", color = secondary, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (state.membership != null) {
                        BgmVipBadge(state.membership)
                    }
                    profile.level?.let {
                        NgStatusTag("Lv.$it", NgStatusTagVariant.NEUTRAL, style = NgStatusTagStyle.COMPACT)
                    }
                }
            }
            if (profile.signature.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(profile.signature, Modifier.fillMaxWidth(), color = secondary,
                    fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val statistics = listOfNotNull(
                profile.followingCount?.let { "关注" to it },
                profile.followerCount?.let { "粉丝" to it },
                profile.listenSongs?.let { "听歌数" to it },
            )
            if (statistics.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = divider)
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    statistics.forEachIndexed { index, (label, count) ->
                        if (index > 0) Box(Modifier.width(1.dp).height(28.dp).background(divider))
                        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$count $label" },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (count < 100_000) count.toString()
                                    else String.format(Locale.ROOT, "%.1f万", count / 10_000.0),
                                color = foreground, fontSize = 18.sp, lineHeight = 24.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = secondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
        if (state.loading) {
            Column(Modifier.fillMaxWidth().heightIn(min = if (profile == null) 172.dp else 56.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                BgmSearchLoadingIndicator()
                if (profile == null) {
                    Text("正在读取账号资料…", color = secondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        } else if (state.message.isNotBlank()) {
            Column(Modifier.fillMaxWidth().heightIn(min = if (profile == null) 172.dp else 0.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (profile == null) {
                    BgmCover("", Modifier.size(56.dp), cornerRadius = 28.dp, placeholder = Icons.Rounded.Person)
                    Spacer(Modifier.height(12.dp))
                }
                Text(state.message, color = secondary, fontSize = 13.sp, lineHeight = 19.sp,
                    textAlign = TextAlign.Center)
                NgDialogTextActionButton("重试", onRetry, enabled = enabled && !state.membershipLoading)
            }
        }
    }
}

@Composable
private fun BgmVipBadge(membership: MusicMembershipInfo, compactQqBadge: Boolean = false) {
    val label = membership.label.ifBlank { "VIP ${membership.level}" }
    val expired = membership.isActive == false
    val description = if (expired) "$label，已过期" else label
    if (compactQqBadge) {
        val color = Color(if (expired) NgTheme.colors.onSurfaceVariant else NgTheme.colors.primary)
        val shortLabel = membership.label.removeSuffix(" Lv.${membership.level}").ifBlank { "VIP" }
        Row(Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BgmOfficialProfileBadge(membership.iconUrl, description, expired, compactQqBadge = true) {
                Text(shortLabel, color = color, fontSize = 11.sp, lineHeight = 14.sp)
            }
            // The official inactive diamond may contain no number, so keep its reported level visible.
            if (membership.level > 0) {
                Text("Lv.${membership.level}", color = color, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        return
    }
    val variant = if (expired) NgStatusTagVariant.NEUTRAL else NgStatusTagVariant.PRIMARY
    BgmOfficialProfileBadge(membership.iconUrl, description, expired) {
        NgStatusTag(label, variant, style = NgStatusTagStyle.COMPACT)
    }
}

/** QQ uses small original images without a tag background; NetEase retains its existing geometry. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun BgmOfficialProfileBadge(
    iconUrl: String,
    description: String,
    expired: Boolean = false,
    compactQqBadge: Boolean = false,
    fallback: @Composable () -> Unit,
) {
    if (iconUrl.isBlank()) {
        Box(Modifier.clearAndSetSemantics { contentDescription = description }) { fallback() }
        return
    }
    val badgeHeight = if (compactQqBadge) 14.dp else 20.dp
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    var ready by remember(iconUrl) { mutableStateOf(false) }
    var ratio by remember(iconUrl) { mutableStateOf(if (compactQqBadge) 1f else 3f) }
    val listener = remember(iconUrl) {
        object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>,
                isFirstResource: Boolean): Boolean {
                ready = false
                return false
            }

            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean): Boolean {
                if (resource.intrinsicWidth > 0 && resource.intrinsicHeight > 0) {
                    ratio = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight
                }
                ready = true
                return false
            }
        }
    }
    Box((if (compactQqBadge) Modifier.heightIn(min = badgeHeight) else Modifier.height(badgeHeight))
        .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center) {
        GlideImage(model = iconUrl, contentDescription = null,
            modifier = Modifier.width(badgeHeight * ratio.coerceIn(if (compactQqBadge) .5f else 2.1f, 5f))
                .height(badgeHeight)
                .alpha(if (!ready) 0f else if (expired) .65f else 1f),
            contentScale = ContentScale.Fit, colorFilter = if (expired) grayscale else null,
        ) { request -> request.dontAnimate().listener(listener) }
        if (!ready) fallback()
    }
}
