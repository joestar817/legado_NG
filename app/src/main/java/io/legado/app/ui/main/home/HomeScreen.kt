package io.legado.app.ui.main.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.help.home.InstalledHomeWidgetSkin
import io.legado.app.BuildConfig
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgIconButton
import io.legado.app.ui.design.theme.NgThemeContextProvider
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.rememberNgThemeContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    bottomInsetPx: Int,
    scrollToTopToken: Int,
    onEdit: () -> Unit,
    onEditWidget: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onAdd: (String, String) -> Boolean,
    onVariantSave: (String, String, Boolean?) -> Boolean,
    onSuiteSave: (String) -> Boolean,
    skinRequest: String?,
    onSkinRequestConsumed: () -> Unit,
    onOpenSkinLibrary: () -> Unit,
    onImportSkin: () -> Unit,
    onDeleteSkin: (InstalledHomeWidgetSkin) -> Unit,
    skinBusy: Boolean,
    onRemove: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onReset: () -> Unit,
    listeningState: HomeListeningState,
    onListeningAction: (HomeListeningState, HomeListeningAction) -> Unit,
    readingState: HomeReadingState,
    onOpenReading: () -> Unit,
    calendarState: HomeCalendarState,
    calendarSelections: Map<String, HomeCalendarSelection>,
    onCalendarAction: (String, HomeCalendarAction) -> Unit,
    updatesState: HomeUpdatesState,
    autoRefreshUpdates: Boolean,
    onOpenUpdatesBook: (HomeUpdateBook) -> Unit,
    onRetryUpdates: () -> Unit,
    onPreviewTypesChanged: (Set<String>) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showSuitePicker by rememberSaveable { mutableStateOf(false) }
    var preferredSkin by rememberSaveable { mutableStateOf<String?>(null) }
    var editingWidgetId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    val skins = LocalHomeWidgetSkins.current
    LaunchedEffect(skinRequest, skins, state.layoutReadFailed, state.pendingEdit) {
        val style = skins.installed.firstOrNull { it.id == skinRequest }?.renderer?.styleId
        if (style != null) {
            if (!state.layoutReadFailed) {
                if (!state.pendingEdit) {
                    onEdit()
                    return@LaunchedEffect
                }
                preferredSkin = style
                showSuitePicker = true
            }
            onSkinRequestConsumed()
        }
    }
    val gridState = rememberLazyGridState()
    // Drag offsets and pending moves belong to this edit session, never to the restored layout.
    val reorderState = key(state.pendingEdit) {
        rememberReorderableLazyGridState(gridState) { from, to ->
            val fromId = from.key as? String
            val toId = to.key as? String
            if (state.pendingEdit && fromId != null && toId != null) onMove(fromId, toId)
        }
    }
    val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }
    val widgets = state.displayedWidgets
    BackHandler(enabled = state.pendingEdit && editingWidgetId == null && !showPicker && !showSuitePicker) {
        onCancel()
    }
    LaunchedEffect(state.pendingEdit, state.layoutReadFailed, widgets) {
        if (widgets.none { it.id == editingWidgetId }) editingWidgetId = null
        if (!state.pendingEdit) {
            editingWidgetId = null
            showPicker = false
            showSuitePicker = false
            preferredSkin = null
        }
        if (state.layoutReadFailed) {
            editingWidgetId = null
            showPicker = false
            showSuitePicker = false
        }
    }
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0 && gridState.layoutInfo.totalItemsCount > 0) {
            gridState.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().onSizeChanged { viewportWidthPx = it.width }) {
        if (state.pendingEdit) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                HomeEditToolbar(
                    modifier = Modifier.width(minOf(264.dp, maxWidth)),
                    onCancel = onCancel,
                    onSave = onSave,
                    onAdd = { showSuitePicker = false; editingWidgetId = null; showPicker = true },
                    onStyle = { showPicker = false; editingWidgetId = null; showSuitePicker = true },
                    styleEnabled = widgets.isNotEmpty(),
                )
            }
        }

        if (state.layoutReadFailed) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.home_layout_read_failed),
                    color = Color(NgTheme.colors.onSurface),
                )
                Spacer(Modifier.height(16.dp))
                NgButton(onClick = { showReset = true }) {
                    Text(stringResource(R.string.home_reset_layout))
                }
            }
        } else {
            HomeWidgetMeasuredLayout(widgets, listeningState, readingState, Modifier.fillMaxSize(),
                calendarState = calendarState, calendarSelections = calendarSelections,
                updatesState = updatesState) { dimensions ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(dimensions.columns),
                state = gridState,
                modifier = Modifier.fillMaxSize().combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onEdit,
                    onLongClickLabel = stringResource(R.string.home_manage_home),
                ),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomInset + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = widgets,
                    key = { it.id },
                    span = { GridItemSpan(skins.render(it).size.columns.coerceAtMost(dimensions.columns)) },
                    contentType = { it.typeId },
                ) { widget ->
                    val variant = skins.render(widget)
                    val widgetEditing = state.editing
                    ReorderableItem(state = reorderState, key = widget.id, enabled = state.pendingEdit) { _ ->
                        HomeEditableWidget(
                            widget = widget,
                            variant = variant,
                            editing = widgetEditing,
                            selected = state.selectedWidgetId == widget.id,
                            dragHandle = if (widgetEditing) Modifier.draggableHandle() else Modifier,
                            onLongClick = { onEditWidget(widget.id) },
                            onSelect = { onEditWidget(widget.id) },
                            onOptions = { editingWidgetId = widget.id },
                            onRemove = {
                                onRemove(widget.id)
                                if (editingWidgetId == widget.id) editingWidgetId = null
                            },
                            onMoveUp = widgets.indexOf(widget).takeIf { it > 0 }?.let { index ->
                                { onMove(widget.id, widgets[index - 1].id); true }
                            },
                            onMoveDown = widgets.indexOf(widget).takeIf { it < widgets.lastIndex }?.let { index ->
                                { onMove(widget.id, widgets[index + 1].id); true }
                            },
                            listeningState = listeningState,
                            onListeningAction = onListeningAction,
                            readingState = readingState,
                            onOpenReading = onOpenReading,
                            dimensions = dimensions,
                            calendarState = calendarState,
                            calendarSelection = calendarState.selectionFor(widget.id, calendarSelections),
                            onCalendarAction = { onCalendarAction(widget.id, it) },
                            updatesState = updatesState,
                            onOpenUpdatesBook = onOpenUpdatesBook,
                            onRetryUpdates = onRetryUpdates,
                        )
                    }
                }
                if (widgets.isEmpty()) {
                    item(key = "home_empty", span = { GridItemSpan(dimensions.columns) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.home_empty),
                                color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            NgButton(onClick = {
                                if (!state.pendingEdit) onEdit()
                                showPicker = true
                            }) { Text(stringResource(R.string.home_add_widget)) }
                        }
                    }
                }
            }
            }
        }
    }

    val editingWidget = widgets.firstOrNull { it.id == editingWidgetId }
    if (showPicker || editingWidget != null || showSuitePicker) {
        val previewTheme = rememberNgThemeContext()
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val layoutDirection = LocalLayoutDirection.current
        val textStyle = LocalTextStyle.current
        val locale = Locale.getDefault()
        val timeZone = ZoneId.systemDefault()
        val viewportWidth = if (viewportWidthPx > 0) with(LocalDensity.current) { viewportWidthPx.toDp() }
            else LocalConfiguration.current.screenWidthDp.dp
        val widgetWidth = viewportWidth - 32.dp
        // Add and single edit share one preview generation with the same complete invalidation inputs.
        val probeCache = if (showPicker || editingWidget != null) remember(showPicker, skins, widgets, listeningState, readingState, calendarState, calendarSelections,
            updatesState, updatesState.books.map { it.book.name }, widgetWidth,
            density.density, density.fontScale, layoutDirection, configuration, previewTheme,
            textStyle, locale, timeZone) {
            HomeWidgetProbeCache()
        } else null
        DisposableEffect(probeCache) {
            onDispose {
                if (BuildConfig.DEBUG && probeCache != null) android.util.Log.d("HomeWidgetPreview",
                    "measurement cache: probes=${probeCache.missCount}, reused=${probeCache.hitCount}, keys=${probeCache.size}, " +
                        "dimensions=${probeCache.dimensionsMissCount}, dimensionsReused=${probeCache.dimensionsHitCount}")
            }
        }
        if (showSuitePicker) {
            NgThemeContextProvider(previewTheme) {
                HomeWidgetSuiteDrawer(
                    usedWidgets = widgets,
                    appliedWidgets = state.widgets,
                    preferredStyle = preferredSkin,
                    onOpenLibrary = onOpenSkinLibrary,
                    onImport = onImportSkin,
                    onDelete = onDeleteSkin,
                    busy = skinBusy,
                    onDismiss = { showSuitePicker = false; preferredSkin = null },
                    onSave = { styleId ->
                        val applied = onSuiteSave(styleId)
                        if (applied) {
                            showSuitePicker = false
                            preferredSkin = null
                            editingWidgetId = null
                            showPicker = false
                        }
                        applied
                    },
                )
            }
        } else if (showPicker) {
            HomeWidgetAddDrawer(
                usedWidgets = widgets,
                onDismiss = { showPicker = false },
                onAdd = { type, variant ->
                    val added = onAdd(type, variant)
                    if (added) showPicker = false
                    added
                },
                onPreviewTypesChanged = onPreviewTypesChanged,
                preview = { candidate, variant ->
                    val previewWidgets = remember(widgets, candidate) { homeWidgetChoiceLayout(widgets, candidate) }
                    NgThemeContextProvider(previewTheme) {
                        HomeWidgetPreview(candidate, variant, listeningState, widgetWidth, readingState, previewWidgets,
                            calendarState, calendarSelections, updatesState,
                            maximumHeight = HomeWidgetAddPreviewHeight, probeCache = probeCache)
                    }
                },
            )
        } else if (editingWidget != null) {
            HomeWidgetEditorDrawer(
                widget = editingWidget,
                usedWidgets = widgets,
                confirmTitle = stringResource(R.string.home_apply),
                onDismiss = { editingWidgetId = null },
                autoRefreshUpdates = (state.draftAutoRefreshUpdates ?: autoRefreshUpdates)
                    .takeIf { editingWidget.typeId == "updates" },
                onSave = { variant, autoRefresh ->
                    val applied = onVariantSave(editingWidget.id, variant, autoRefresh)
                    if (applied) editingWidgetId = null
                    applied
                },
                onPreviewTypesChanged = onPreviewTypesChanged,
                preview = { variant ->
                    NgThemeContextProvider(previewTheme) {
                        HomeWidgetPreview(editingWidget, variant, listeningState, widgetWidth, readingState, widgets,
                            calendarState, calendarSelections, updatesState,
                            maximumHeight = HomeWidgetAddPreviewHeight, probeCache = probeCache)
                    }
                },
            )
        }
    }
    if (showReset) {
        Dialog(onDismissRequest = { showReset = false }) {
            NgDialog(
                title = stringResource(R.string.home_reset_layout),
                actions = {
                    NgDialogTextActionButton(stringResource(R.string.home_cancel), { showReset = false })
                    NgDialogTextActionButton(stringResource(R.string.home_reset), {
                        onReset()
                        showReset = false
                    })
                },
            ) {
                Text(
                    stringResource(R.string.home_reset_message),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun HomeEditToolbar(
    onCancel: () -> Unit,
    onAdd: () -> Unit,
    onStyle: () -> Unit,
    onSave: () -> Unit,
    styleEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val radius = 24.dp
    val neutral = if (NgTheme.snapshot.isDark) Color(0xFF242321) else Color.White
    NgGlassSurface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(radius),
        role = NgMaterialRole.CONTROL,
        liquidCornerRadius = radius,
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = neutral,
        ).copy(
            containerTop = neutral.copy(alpha = 0.94f),
            containerBottom = neutral.copy(alpha = 0.88f),
            accentGlow = Color.Transparent,
            borderColor = Color.White.copy(alpha = if (NgTheme.snapshot.isDark) 0.22f else 0.92f),
            shadowElevation = 3.dp,
        ),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val actions = listOf(
                Triple(R.string.home_cancel, onCancel, true),
                Triple(R.string.home_add, onAdd, true),
                Triple(R.string.home_style, onStyle, styleEnabled),
                Triple(R.string.home_done, onSave, true),
            )
            actions.forEachIndexed { index, (label, action, enabled) ->
                if (index > 0) Spacer(Modifier.width(1.dp).height(18.dp)
                    .background(Color(NgTheme.colors.primary).copy(alpha = 0.18f)))
                NgIconButton(onClick = action, enabled = enabled,
                    modifier = Modifier.weight(1f).height(48.dp)) {
                    val icon = when (label) {
                        R.string.home_cancel -> R.drawable.ic_baseline_close
                        R.string.home_add -> R.drawable.ic_add
                        R.string.home_style -> R.drawable.ic_cfg_theme
                        else -> R.drawable.ic_check
                    }
                    Icon(painterResource(icon), contentDescription = stringResource(label),
                        modifier = Modifier.size(24.dp),
                        tint = Color(if (label == R.string.home_done) NgTheme.colors.primary
                            else NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else 0.4f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeEditableWidget(
    widget: HomeWidgetInstance,
    variant: HomeWidgetVariant,
    editing: Boolean,
    selected: Boolean,
    dragHandle: Modifier,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onOptions: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Boolean)?,
    onMoveDown: (() -> Boolean)?,
    listeningState: HomeListeningState,
    onListeningAction: (HomeListeningState, HomeListeningAction) -> Unit,
    readingState: HomeReadingState,
    onOpenReading: () -> Unit,
    dimensions: HomeWidgetDimensions,
    calendarState: HomeCalendarState,
    calendarSelection: HomeCalendarSelection,
    onCalendarAction: (HomeCalendarAction) -> Unit,
    updatesState: HomeUpdatesState,
    onOpenUpdatesBook: (HomeUpdateBook) -> Unit,
    onRetryUpdates: () -> Unit,
) {
    val palette = if (widget.typeId == "listening") homeListeningSkinPalette(variant.styleId)
        else homeWidgetSkinPalette(variant.styleId)
    val toolsHeight = if (editing) dimensions.tools(variant.size) else 0.dp
    HomeWidgetCard(
        widget, variant, editing, onLongClick, onSelect, listeningState, onListeningAction,
        modifier = if (editing && selected) Modifier.border(
            1.2.dp, palette?.primary ?: Color(NgTheme.colors.primary),
            RoundedCornerShape(HomeWidgetSkinCornerRadius),
        ) else Modifier,
        headerMinHeight = dimensions.header(variant.size, widget.typeId, variant.styleId),
        bodyMinHeight = dimensions.body(variant.size, widget.typeId, variant.styleId, widget.id),
        toolHeight = toolsHeight,
        editorHeader = if (editing) {
            {
                Box(Modifier.fillMaxWidth().height(toolsHeight)) {
                    HomeWidgetTools(
                        dragHandle, onOptions, onRemove, onMoveUp, onMoveDown,
                        variant.size,
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp), palette,
                    )
                }
            }
        } else null,
        readingState = readingState, onOpenReading = onOpenReading,
        minimumHeight = dimensions.height(variant.size, editing, widget.typeId, variant.styleId, widget.id),
        calendarState = calendarState, calendarSelection = calendarSelection, onCalendarAction = onCalendarAction,
        updatesState = updatesState, onOpenUpdatesBook = onOpenUpdatesBook, onRetryUpdates = onRetryUpdates,
    )
}

@Composable
private fun HomeWidgetTools(
    dragHandle: Modifier,
    onOptions: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Boolean)?,
    onMoveDown: (() -> Boolean)?,
    size: HomeWidgetSize,
    modifier: Modifier = Modifier,
    palette: HomeListeningPalette? = null,
) {
    val dragLabel = stringResource(R.string.home_drag_widget)
    val moveUpLabel = stringResource(R.string.home_move_widget_up)
    val moveDownLabel = stringResource(R.string.home_move_widget_down)
    val foreground = palette?.foreground ?: Color(NgTheme.colors.onSurface)
    val buttonSurface = palette?.secondaryContainer
        ?: colorResource(R.color.ng_surface_panel).copy(alpha = 0.62f)
    BoxWithConstraints(modifier) {
        val columns = when {
            maxWidth >= 144.dp -> 3
            maxWidth >= 96.dp -> 2
            else -> 1
        }
        val tools: List<@Composable () -> Unit> = listOf({
            Box(
                modifier = dragHandle.size(48.dp).semantics {
                    contentDescription = dragLabel
                    role = Role.Button
                    customActions = buildList {
                        onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel, it)) }
                        onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel, it)) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).background(buttonSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DragIndicator, null, tint = foreground, modifier = Modifier.size(18.dp))
                }
            }
        }, {
            NgIconButton(onClick = onOptions, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(28.dp).background(buttonSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, stringResource(R.string.home_edit), tint = foreground, modifier = Modifier.size(18.dp))
                }
            }
        }, {
            NgIconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(28.dp).background(buttonSurface, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.home_remove),
                        tint = palette?.error ?: Color(NgTheme.colors.error), modifier = Modifier.size(18.dp))
                }
            }
        })
        Column {
            tools.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    row.forEachIndexed { index, tool ->
                        tool()
                        if (size == HomeWidgetSize.LARGE && rowIndex == 0 && index == 0) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeWidgetCard(
    widget: HomeWidgetInstance,
    variant: HomeWidgetVariant,
    editing: Boolean,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    listeningState: HomeListeningState,
    onListeningAction: (HomeListeningState, HomeListeningAction) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    headerMinHeight: Dp = HomeWidgetSkinHeaderHeight,
    bodyMinHeight: Dp = if (isCompactHomeWidgetSmall(variant.size, widget.typeId, variant.styleId)) 0.dp
        else variant.size.standardHeight() - HomeWidgetSkinHeaderHeight,
    toolHeight: Dp = 0.dp,
    editorHeader: (@Composable () -> Unit)? = null,
    readingState: HomeReadingState = HomeReadingState(),
    onOpenReading: () -> Unit = {},
    minimumHeight: Dp = if (isCompactHomeWidgetSmall(variant.size, widget.typeId, variant.styleId)) HomeWidgetSkinHeaderHeight
        else variant.size.standardHeight(),
    calendarState: HomeCalendarState = HomeCalendarState(),
    calendarSelection: HomeCalendarSelection = HomeCalendarSelection(java.time.YearMonth.from(calendarState.today)),
    onCalendarAction: (HomeCalendarAction) -> Unit = {},
    updatesState: HomeUpdatesState = HomeUpdatesState(),
    onOpenUpdatesBook: (HomeUpdateBook) -> Unit = {},
    onRetryUpdates: () -> Unit = {},
) {
    HomeWidgetSurface(
        variant = variant,
        modifier = modifier.fillMaxWidth().then(if (interactive) Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { if (editing) onSelect() },
            onClickLabel = if (editing) stringResource(R.string.home_edit) else null,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.home_manage_widgets),
            role = if (editing) Role.Button else null,
        ) else Modifier).then(if (variant.styleId == "basic") Modifier.border(
            0.6.dp, colorResource(R.color.ng_surface_panel).copy(alpha = 0.55f),
            RoundedCornerShape(HomeWidgetSkinCornerRadius),
        ) else Modifier),
        minimumHeight = minimumHeight,
        headerHeight = headerMinHeight,
        headerOffset = toolHeight,
        listening = widget.typeId == "listening",
        backdropVariant = when (widget.typeId) {
            "calendar" -> HomeWidgetBackdropVariant.CALENDAR
            "updates" -> HomeWidgetBackdropVariant.BOOK_NEWS
            else -> HomeWidgetBackdropVariant.STANDARD
        },
    ) {
        editorHeader?.invoke()
        HomeWidgetHeader(widget.typeId, variant, editing, interactive, headerMinHeight, onOpenReading, onLongClick,
            updatesState = updatesState, onRefreshUpdates = onRetryUpdates)
        Column(Modifier.fillMaxWidth().heightIn(min = bodyMinHeight)) {
            HomeWidgetContents.find(widget.typeId).render(variant, HomeWidgetContentContext(
                editing = editing, listening = listeningState,
                onListeningAction = onListeningAction, onEdit = onLongClick,
                interactive = interactive && !editing, reading = readingState,
                bodyMinimumHeight = bodyMinHeight,
                calendar = calendarState, calendarSelection = calendarSelection, onCalendarAction = onCalendarAction,
                updates = updatesState, onOpenUpdatesBook = onOpenUpdatesBook, onRetryUpdates = onRetryUpdates,
            ))
        }
    }
}

/** Align the title row and keep action slots stable while editing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeWidgetHeader(
    typeId: String,
    variant: HomeWidgetVariant,
    editing: Boolean,
    interactive: Boolean,
    minimumHeight: Dp,
    onOpenReading: () -> Unit,
    onEdit: () -> Unit,
    updatesState: HomeUpdatesState = HomeUpdatesState(),
    onRefreshUpdates: () -> Unit = {},
) {
    val small = variant.size == HomeWidgetSize.SMALL
    val isUpdates = typeId == "updates"
    val rowMinimumHeight = if (isUpdates) {
        if (small) 40.dp else HomeWidgetSkinHeaderHeight
    } else minimumHeight
    val palette = if (typeId == "listening") homeListeningSkinPalette(variant.styleId)
        else homeWidgetSkinPalette(variant.styleId)
    val content = HomeWidgetContents.find(typeId)
    val title = stringResource(content.titleRes)
    val updatesCheck = if (typeId == "updates" && !small) homeUpdatesHeaderCheck(updatesState) else null
    val updatesCheckWidth = updatesCheck?.let { homeUpdatesHeaderCheckWidth(it) } ?: 0.dp
    val headerContent: @Composable (Dp) -> Unit = { checkWidth ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (small) 12.dp else 14.dp)
                .heightIn(min = rowMinimumHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(homeWidgetHeaderIconRes(typeId, content.iconRes)), null,
                tint = palette?.primary ?: Color(NgTheme.colors.primary),
                modifier = Modifier.size(homeWidgetHeaderIconSize(small)),
            )
            Spacer(Modifier.width(homeWidgetHeaderIconGap(small)))
            Text(
                title, Modifier.weight(1f),
                color = palette?.foreground ?: Color(NgTheme.colors.onSurface),
                style = homeWidgetHeaderTitleStyle(small),
            )
            if (typeId == "reading") {
                Box(
                    Modifier.size(48.dp).then(if (!editing && interactive) Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.home_reading_view_all),
                        onClick = onOpenReading,
                        onLongClickLabel = stringResource(R.string.home_manage_widgets),
                        onLongClick = onEdit,
                    ) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!editing && (variant.styleId == "storybook" || variant.styleId == "blue_fish")) HomeWidgetControlDisc(
                        variant.styleId, primary = false, editing = false, modifier = Modifier.size(32.dp))
                    if (!editing) Icon(
                        painterResource(R.drawable.ic_arrow_right),
                        contentDescription = stringResource(R.string.home_reading_view_all),
                        tint = palette?.foreground ?: Color(NgTheme.colors.onSurfaceVariant),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (typeId == "updates" && !small) {
                HomeUpdatesHeaderCheckStatus(requireNotNull(updatesCheck),
                    if (updatesState.checking) palette?.primary ?: Color(NgTheme.colors.primary)
                    else palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant),
                    Modifier.width(checkWidth))
                Spacer(Modifier.width(4.dp))
                if (editing) Spacer(Modifier.size(48.dp))
                else HomeUpdatesRefreshButton(updatesState,
                    palette?.primary ?: Color(NgTheme.colors.primary), interactive, onRefreshUpdates,
                    headerDiscStyleId = variant.styleId.takeIf { it == "storybook" || it == "night" || it == "blue_fish" })
            }
        }
    }
    if (isUpdates) {
        Column(Modifier.fillMaxWidth().heightIn(min = minimumHeight)) {
            HomeUpdatesDecoratedHeader(variant, title, updatesState,
                trailingActionWidth = if (small) 0.dp else 48.dp,
                checkWidth = updatesCheckWidth, content = headerContent)
            HomeUpdatesCountBelowHeader(updatesState, small,
                palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant))
        }
    } else {
        headerContent(0.dp)
    }
}
