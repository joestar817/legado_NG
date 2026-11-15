package io.legado.app.ui.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.home.InstalledHomeWidgetSkin
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

/** Select a skin directly, or manage imported skins without changing the current layout. */
@Composable
internal fun HomeWidgetSuiteDrawer(
    usedWidgets: List<HomeWidgetInstance>,
    appliedWidgets: List<HomeWidgetInstance>,
    preferredStyle: String?,
    onOpenLibrary: () -> Unit,
    onImport: () -> Unit,
    onDelete: (InstalledHomeWidgetSkin) -> Unit,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean,
) {
    val skins = LocalHomeWidgetSkins.current
    val styles = remember(skins) {
        skins.choices("reading").filter { it.size == HomeWidgetSize.SMALL }
            .sortedBy { if (isPlainHomeWidgetStyle(it.styleId)) 0 else 1 }
    }
    val currentStyle = remember(usedWidgets, skins) {
        val rendered = usedWidgets.map { it.copy(variantId = skins.render(it).id) }
        homeWidgetCommonStyle(rendered)
    }
    val appliedStyle = remember(appliedWidgets, skins) {
        homeWidgetCommonStyle(appliedWidgets.map { it.copy(variantId = skins.render(it).id) })
    }
    val selectedId = preferredStyle?.takeIf(skins::allows) ?: currentStyle
    var submitted by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<InstalledHomeWidgetSkin?>(null) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(preferredStyle, styles) {
        val index = styles.indexOfFirst { it.styleId == preferredStyle }
        if (index >= 0) gridState.scrollToItem(index)
    }
    HomeWidgetDrawerShell(
        title = stringResource(R.string.home_suite_title),
        confirmTitle = if (managing) stringResource(R.string.home_skin_done) else null,
        onDismiss = onDismiss,
        onConfirm = { managing = false },
        confirmEnabled = !busy,
        headerAction = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeWidgetSuiteHeaderButton(
                    icon = R.drawable.ic_import,
                    iconSize = 24.dp,
                    contentDescription = stringResource(R.string.home_skin_import),
                    enabled = !busy,
                    onClick = { managing = false; onImport() },
                )
                Box {
                    HomeWidgetSuiteHeaderButton(
                        icon = R.drawable.ic_grid_menu,
                        iconSize = 21.dp,
                        contentDescription = stringResource(R.string.more),
                        enabled = !busy,
                        onClick = { menu = !menu },
                    )
                    NgExpandableActionMenu(
                        expanded = menu, onDismissRequest = { menu = false },
                        items = listOf(
                            NgExpandableActionMenuItem(1, R.string.home_skin_manage, R.drawable.ic_edit,
                                enabled = skins.installed.isNotEmpty() && !managing && !busy),
                            NgExpandableActionMenuItem(2, R.string.home_suite_get_more, R.drawable.ic_about_cloud_download),
                        ),
                        variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                        menuContainerColor = ngDrawerContentCardColor(),
                        properties = PopupProperties(focusable = true, clippingEnabled = false),
                        onItemClick = {
                            menu = false
                            if (it.itemId == 1) managing = true else {
                                managing = false
                                onOpenLibrary()
                            }
                        },
                    )
                }
            }
        },
    ) {
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            val columns = when {
                maxWidth >= 148.dp * fontScale * 4 + 36.dp -> 4
                maxWidth >= 92.dp * fontScale * 2 + 12.dp -> 2
                else -> 1
            }
            val rows = (styles.size + columns - 1) / columns
            val choicesHeight = (88.dp + 32.dp * fontScale) * rows + 10.dp * (rows - 1).coerceAtLeast(0)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(minOf(choicesHeight, maxHeight)).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(styles, key = { it.styleId }) { style ->
                    HomeWidgetSuiteChoice(
                        style = style,
                        selected = selectedId == style.styleId,
                        inUse = appliedStyle == style.styleId,
                        onSelect = { if (!submitted) submitted = onSave(style.styleId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && (managing || usedWidgets.isNotEmpty()),
                        managing = managing,
                        onDelete = if (managing) skins.installedStyle(style.styleId)?.let { skin ->
                            { deleting = skin }
                        } else null,
                    )
                }
            }
        }
    }
    deleting?.let { skin ->
        Dialog(onDismissRequest = { deleting = null }) {
            NgDialog(title = stringResource(R.string.home_skin_delete), actions = {
                NgDialogTextActionButton(stringResource(R.string.cancel), { deleting = null })
                NgDialogTextActionButton(stringResource(R.string.delete), {
                    deleting = null
                    onDelete(skin)
                })
            }) {
                Text(skin.name, color = Color(NgTheme.colors.onSurface))
            }
        }
    }
}

/** Use the same circular surface as the color picker, with a 48dp touch target. */
@Composable
private fun HomeWidgetSuiteHeaderButton(
    @DrawableRes icon: Int,
    iconSize: Dp,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurfaceVariant)
    NgThemeSheetActionButton(
        onClick = onClick,
        contentDescription = contentDescription,
        enabled = enabled,
        touchSize = 48.dp,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

@Composable
private fun HomeWidgetSuiteChoice(
    style: HomeWidgetVariant,
    selected: Boolean,
    inUse: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    managing: Boolean,
    onDelete: (() -> Unit)? = null,
) {
    val primary = Color(NgTheme.colors.primary)
    val shape = RoundedCornerShape(16.dp)
    val fontScale = LocalDensity.current.fontScale
    val name = LocalHomeWidgetSkins.current.installedStyle(style.styleId)?.name ?: stringResource(style.styleTitleRes)
    Column(modifier.height(88.dp + 32.dp * fontScale).clip(shape).background(ngDrawerContentCardColor())
        .border(1.5.dp, if (selected) primary else Color.Transparent, shape)
        .then(if (managing) Modifier else Modifier.selectable(
            selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect))
        .padding(start = 6.dp, top = 6.dp, end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(11.dp))) {
            Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
                HomeWidgetSuiteSwatch(style.styleId)
            }
            if (onDelete != null) {
                NgIconButton(onClick = onDelete, enabled = enabled,
                    modifier = Modifier.align(Alignment.TopEnd).size(44.dp)) {
                    Box(Modifier.size(26.dp).background(ngDrawerContentCardColor(), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_bookshelf_manage_delete),
                            "${stringResource(R.string.delete)} $name", tint = Color(NgTheme.colors.error),
                            modifier = Modifier.size(17.dp))
                    }
                }
            } else if (inUse) {
                Box(Modifier.align(Alignment.TopEnd).size(44.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(26.dp).background(ngDrawerContentCardColor(), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_check), stringResource(R.string.home_widget_in_use),
                            tint = primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(32.dp * fontScale).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center) {
            Text(name, color = Color(NgTheme.colors.onSurface), fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Swatches share skin colors/artwork without creating additional live widget trees. */
@Composable
private fun HomeWidgetSuiteSwatch(styleId: String) {
    if (styleId == "white") {
        NgVisualSurface(
            modifier = Modifier.fillMaxSize(), role = NgMaterialRole.CONTENT,
            cornerRadius = 11.dp,
            style = homeWhiteWidgetSurfaceStyle().copy(shadowElevation = 1.dp),
            visualSystemOverride = NgVisualSystem.TRANSPARENT_GLASS,
            surfaceDecoration = { HomeWhiteWidgetEdge(Modifier.matchParentSize(), cornerRadius = 11.dp) },
        ) {}
    } else if (styleId == "basic") {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
            Color(0xFFD9F8F5), Color(0xFFF7FCFA), Color(0xFFC4E8D7),
        ))))
    } else Box(Modifier.fillMaxSize()) {
        HomeWidgetSkinBackdrop(styleId, modifier = Modifier.fillMaxSize(), small = styleId == "storybook")
        val illustration = homeWidgetArtworkPainter(styleId, when (styleId) {
            "storybook" -> "calendar"
            "blue_fish" -> "reading"
            else -> "lamp"
        })
        if (illustration != null) Image(illustration,
            contentDescription = null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 34.dp)
                .size(if (styleId == "storybook") 70.dp else 74.dp))
    }
}
