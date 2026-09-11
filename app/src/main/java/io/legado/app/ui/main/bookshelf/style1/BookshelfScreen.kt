package io.legado.app.ui.main.bookshelf.style1

import android.graphics.Rect
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.ui.main.bookshelf.BookshelfContentToolbarActionButton
import io.legado.app.ui.main.bookshelf.BookshelfContentToolbarMenuButton
import io.legado.app.ui.main.bookshelf.BookshelfCompactToolbar
import io.legado.app.ui.main.bookshelf.BookshelfDockGroup
import io.legado.app.ui.main.bookshelf.BookshelfFloatingDock
import io.legado.app.ui.main.bookshelf.BookshelfSortMenuHost
import kotlin.math.roundToInt

@Composable
internal fun BookshelfScreen(
    dockGroups: List<BookshelfDockGroup>,
    selectedGroupIndex: Int,
    groupGridMode: Boolean,
    configuredTopBarStyle: BookshelfTopBarStyle,
    dockTopDistancePx: Int,
    dockContentTopInsetPx: Int,
    dockTransparency: Int,
    dockSearchPosition: BookshelfFloatingDockSearchPosition,
    onSearchClick: () -> Unit,
    onGroupClick: (Int) -> Unit,
    onGroupLongClick: (Int) -> Unit,
    onManageClick: () -> Unit,
    onOpenSortMenu: () -> List<NgExpandableActionMenuItem>,
    onSortMenuItemClick: (Int) -> Unit,
    onMenuItemClick: (Int) -> Unit,
    onFloatingDockBoundsChanged: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val backdropSource = remember(hostView) {
        hostView.rootView.findViewById<View>(R.id.bookshelf_content_panel)
    }
    val dockProgress = remember { Animatable(0f) }
    val topInset = with(density) { dockContentTopInsetPx.toDp() }
    val dockTranslation = with(density) { (-4).dp.toPx() }
    val resolvedTopBarStyle = BookshelfTopBarStyle.resolveForLayout(
        configuredStyle = configuredTopBarStyle,
        groupGridMode = groupGridMode,
    )
    val navigationGroupIndices = remember(dockGroups) {
        dockGroups.indices.filter { index ->
            dockGroups[index].groupId != BookGroup.IdNoGroup
        }
    }
    val navigationGroups = navigationGroupIndices.map(dockGroups::get)
    val navigationSelectedIndex = navigationGroupIndices
        .indexOf(selectedGroupIndex)
        .coerceAtLeast(0)

    LaunchedEffect(Unit) {
        dockProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220),
        )
    }

    val dockModifier = Modifier
        .graphicsLayer {
            alpha = dockProgress.value
            translationY = dockTranslation * (1f - dockProgress.value)
        }
        .onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInRoot()
            val hostLocation = IntArray(2)
            hostView.getLocationOnScreen(hostLocation)
            onFloatingDockBoundsChanged(
                Rect(
                    hostLocation[0] + bounds.left.roundToInt(),
                    hostLocation[1] + bounds.top.roundToInt(),
                    hostLocation[0] + bounds.right.roundToInt(),
                    hostLocation[1] + bounds.bottom.roundToInt(),
                )
            )
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset),
    ) {
        if (resolvedTopBarStyle == BookshelfTopBarStyle.COMPACT_TOOLBAR) {
            BookshelfCompactToolbar(
                groups = dockGroups,
                selectedIndex = selectedGroupIndex,
                groupGridMode = groupGridMode,
                onSearchClick = onSearchClick,
                onGroupClick = onGroupClick,
                onGroupLongClick = onGroupLongClick,
                onManageClick = onManageClick,
                onOpenSortMenu = onOpenSortMenu,
                onSortMenuItemClick = onSortMenuItemClick,
                onMenuItemClick = onMenuItemClick,
                topDistancePx = dockTopDistancePx,
                contentTopInsetPx = dockContentTopInsetPx,
                transparencyPercent = dockTransparency,
                searchPosition = dockSearchPosition,
                backdropSource = backdropSource,
                modifier = dockModifier,
            )
        } else {
            BookshelfFloatingDock(
                groups = navigationGroups,
                selectedIndex = navigationSelectedIndex,
                onSearchClick = onSearchClick,
                onGroupClick = { index ->
                    navigationGroupIndices.getOrNull(index)?.let(onGroupClick)
                },
                onGroupLongClick = { index ->
                    navigationGroupIndices.getOrNull(index)?.let(onGroupLongClick)
                },
                topDistancePx = dockTopDistancePx,
                contentTopInsetPx = dockContentTopInsetPx,
                transparencyPercent = dockTransparency,
                searchPosition = dockSearchPosition,
                backdropSource = backdropSource,
                modifier = dockModifier,
            )
            BookshelfContentToolbar(
                onManageClick = onManageClick,
                onOpenSortMenu = onOpenSortMenu,
                onSortMenuItemClick = onSortMenuItemClick,
                onMenuItemClick = onMenuItemClick,
            )
        }
    }
}

@Composable
private fun BookshelfContentToolbar(
    onManageClick: () -> Unit,
    onOpenSortMenu: () -> List<NgExpandableActionMenuItem>,
    onSortMenuItemClick: (Int) -> Unit,
    onMenuItemClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 16.dp),
    ) {
        BookshelfContentToolbarActionButton(
            iconRes = R.drawable.ic_settings,
            labelRes = R.string.manage,
            onClick = onManageClick,
        )
        Spacer(modifier = Modifier.weight(1f))
        BookshelfSortMenuHost(
            onOpen = onOpenSortMenu,
            onItemClick = onSortMenuItemClick,
        ) { openMenu ->
            BookshelfContentToolbarActionButton(
                iconRes = R.drawable.ic_swap_vert,
                labelRes = R.string.sort,
                onClick = openMenu,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        BookshelfContentToolbarMenuButton(
            onMenuItemClick = onMenuItemClick,
            modifier = Modifier
                .width(58.dp)
                .fillMaxHeight(),
        )
    }
}
