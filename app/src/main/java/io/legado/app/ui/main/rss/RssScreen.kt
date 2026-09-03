package io.legado.app.ui.main.rss

import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.ngBackdropPrimaryTextStyle

@Composable
internal fun RssScreen(
    sources: List<RssSource>,
    query: String,
    groups: List<String>,
    selectedGroup: String?,
    bottomInsetPx: Int,
    transparentTopBar: Boolean,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onOpenRuleSubscription: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSourceManage: () -> Unit,
    onOpenSource: (RssSource) -> Unit,
    onSourceAction: (RssSource, Int) -> Unit
) {
    val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        RssTopBar(
            query = query,
            groups = groups,
            selectedGroup = selectedGroup,
            onQueryChange = onQueryChange,
            onGroupSelected = onGroupSelected,
            onOpenFavorites = onOpenFavorites,
            onOpenSourceManage = onOpenSourceManage
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(bottom = bottomInset + 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "rule_subscription") {
                RssTile(
                    name = stringResource(R.string.rule_subscription),
                    onClick = onOpenRuleSubscription,
                    onLongClick = {}
                ) {
                    Image(
                        painter = painterResource(R.drawable.image_legado),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
            items(sources, key = { it.sourceUrl }) { source ->
                RssSourceItem(
                    source = source,
                    onClick = { onOpenSource(source) },
                    onAction = { onSourceAction(source, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssSourceItem(
    source: RssSource,
    onClick: () -> Unit,
    onAction: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        RssTile(
            name = source.sourceName,
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        ) {
            RssSourceIcon(source)
        }
        RssSourceMenu(
            source = source,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAction = {
                menuExpanded = false
                onAction(it)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssTile(
    name: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(
            text = name,
            style = ngBackdropPrimaryTextStyle(
                fallbackColor = Color(NgTheme.colors.onSurfaceVariant),
            ),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 34.dp)
        )
    }
}

@Composable
private fun RssSourceIcon(source: RssSource) {
    AndroidView(
        factory = { context ->
            AppCompatImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.image_rss)
            }
        },
        update = { imageView ->
            imageView.contentDescription = source.sourceName
            val requestKey = source.sourceUrl + '\u0000' + source.sourceIcon.orEmpty()
            if (imageView.tag != requestKey) {
                imageView.tag = requestKey
                val options = RequestOptions().set(
                    OkHttpModelLoader.sourceOriginOption,
                    source.sourceUrl
                )
                ImageLoader.load(imageView.context, source.sourceIcon)
                    .apply(options)
                    .centerCrop()
                    .placeholder(R.drawable.image_rss)
                    .error(R.drawable.image_rss)
                    .into(imageView)
            }
        },
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
private fun RssSourceMenu(
    source: RssSource,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit
) {
    val items = remember(source.loginUrl) {
        buildList {
            add(NgExpandableActionMenuItem(R.id.menu_edit, R.string.edit, R.drawable.ic_edit))
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_top,
                    R.string.to_top,
                    R.drawable.ic_arrow_drop_up
                )
            )
            if (!source.loginUrl.isNullOrBlank()) {
                add(
                    NgExpandableActionMenuItem(
                        R.id.menu_login,
                        R.string.login,
                        R.drawable.ic_lock_outline
                    )
                )
            }
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_disable,
                    R.string.disable_source,
                    R.drawable.ic_baseline_close,
                    dividerBefore = true
                )
            )
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_del,
                    R.string.delete,
                    R.drawable.ic_outline_delete
                )
            )
        }
    }
    NgExpandableActionMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = items,
        onItemClick = { onAction(it.itemId) },
        width = 168.dp
    )
}
