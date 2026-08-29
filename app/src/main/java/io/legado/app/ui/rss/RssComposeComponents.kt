package io.legado.app.ui.rss

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgTheme

class RssComposeBinding private constructor(
    private val composeView: ComposeView
) : ViewBinding {
    override fun getRoot() = composeView

    companion object {
        fun inflate(inflater: LayoutInflater): RssComposeBinding {
            return RssComposeBinding(
                ComposeView(inflater.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            )
        }
    }
}

@Immutable
data class RssToolbarAction(
    val id: Int,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val visible: Boolean = true,
    val dividerBefore: Boolean = false
)

@Composable
fun RssPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: List<RssToolbarAction> = emptyList(),
    onAction: (Int) -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        RssPageTopBar(
            title = title,
            onBack = onBack,
            actions = actions.filter(RssToolbarAction::visible),
            onAction = onAction
        )
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun RssPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: List<RssToolbarAction>,
    onAction: (Int) -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    val contentColor = Color(NgTheme.colors.onTopBar)
    val containerColor = Color(NgTheme.colors.topBarContainer)
    val endActionSlotCount = when (actions.size) {
        0, 1 -> 1
        else -> 2
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            RssToolbarIconButton(
                iconRes = R.drawable.ic_arrow_back,
                description = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = (endActionSlotCount * 40 + 4).dp),
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                when (actions.size) {
                    0 -> Unit
                    1 -> RssToolbarIconButton(
                        iconRes = actions.first().iconRes,
                        description = stringResource(actions.first().titleRes),
                        onClick = { onAction(actions.first().id) },
                    )
                    else -> {
                        RssToolbarIconButton(
                            iconRes = actions.first().iconRes,
                            description = stringResource(actions.first().titleRes),
                            onClick = { onAction(actions.first().id) },
                        )
                        Box {
                            RssToolbarIconButton(
                                iconRes = R.drawable.ic_grid_menu,
                                description = stringResource(R.string.menu),
                                onClick = menuState::onAnchorClick,
                            )
                            NgExpandableActionMenu(
                                expanded = menuState.expanded,
                                onDismissRequest = menuState::onDismissRequest,
                                items = actions.drop(1).map {
                                    NgExpandableActionMenuItem(
                                        itemId = it.id,
                                        titleRes = it.titleRes,
                                        iconRes = it.iconRes,
                                        dividerBefore = it.dividerBefore
                                    )
                                },
                                onItemClick = {
                                    menuState.close()
                                    onAction(it.itemId)
                                },
                                width = 152.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RssToolbarIconButton(
    @DrawableRes iconRes: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = Color(NgTheme.colors.onTopBar),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun RssEmptyState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
fun RssRemoteImage(
    imageUrl: String?,
    sourceOrigin: String?,
    @DrawableRes placeholder: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    centerCrop: Boolean = true
) {
    AndroidView(
        factory = { context ->
            AppCompatImageView(context).apply {
                scaleType = if (centerCrop) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.FIT_CENTER
                }
                setImageResource(placeholder)
            }
        },
        update = { imageView ->
            imageView.contentDescription = contentDescription
            val requestKey = sourceOrigin.orEmpty() + '\u0000' + imageUrl.orEmpty()
            if (imageView.tag != requestKey) {
                imageView.tag = requestKey
                val options = RequestOptions().set(
                    OkHttpModelLoader.sourceOriginOption,
                    sourceOrigin.orEmpty()
                )
                ImageLoader.load(imageView.context, imageUrl)
                    .apply(options)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .let { if (centerCrop) it.centerCrop() else it.fitCenter() }
                    .into(imageView)
            }
        },
        modifier = modifier
    )
}
