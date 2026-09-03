package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.help.config.NgManagedTheme
import io.legado.app.help.config.isBuiltIn
import io.legado.app.help.config.md3.Md3ThemeImportDraft
import io.legado.app.help.config.md3.Md3ThemePackageFormat
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgActionBarButton
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDialogValueRow
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSettingsCardSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import kotlin.math.roundToInt

@Composable
internal fun ThemeManagerScreen(
    builtInThemes: List<NgManagedTheme>,
    savedThemes: List<NgManagedTheme>,
    activeThemeId: String?,
    currentThemeName: String,
    onBack: () -> Unit,
    onSaveCurrent: (String) -> Unit,
    onImportPackage: () -> Unit,
    onThemeSelected: (NgManagedTheme) -> Unit,
    onThemeEdit: (NgManagedTheme) -> Unit,
    editingTheme: NgManagedTheme?,
    draftTheme: NgManagedTheme?,
    onDismissThemeEditor: () -> Unit,
    onDraftThemeChanged: (NgManagedTheme) -> Unit,
    onSelectBackground: (Boolean) -> Unit,
    onBackgroundBlurChanged: (Boolean, Int) -> Unit,
    onClearBackground: (Boolean) -> Unit,
    onSaveTheme: () -> Unit,
    onThemeExport: (NgManagedTheme) -> Unit,
    onThemeDelete: (NgManagedTheme) -> Unit,
    md3ImportDraft: Md3ThemeImportDraft?,
    md3ImportInstalling: Boolean,
    onDismissMd3Import: () -> Unit,
    onConfirmMd3Import: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var themeNameInitial by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundActionsDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var backgroundBlurDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var pendingDeleteTheme by remember { mutableStateOf<NgManagedTheme?>(null) }
    val normalizedQuery = query.trim()
    val visibleBuiltIns = remember(builtInThemes, normalizedQuery) {
        builtInThemes.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }
    val visibleSaved = remember(savedThemes, normalizedQuery) {
        savedThemes.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ThemeManagerTopBar(
            query = query,
            onQueryChange = { query = it },
            onBack = onBack,
            onSaveCurrent = { themeNameInitial = currentThemeName },
            onImportPackage = onImportPackage,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visibleBuiltIns.isNotEmpty()) {
                item(key = "built-in-title") {
                    ThemeSectionTitle(stringResource(R.string.ng_theme_built_in))
                }
                items(visibleBuiltIns, key = { it.id }) { theme ->
                    NgThemeManagementCard(
                        theme = theme,
                        selected = activeThemeId == theme.id,
                        onClick = { onThemeSelected(theme) },
                        onMoreClick = { onThemeEdit(theme) },
                    )
                }
            }
            if (visibleSaved.isNotEmpty()) {
                item(key = "saved-title") {
                    ThemeSectionTitle(stringResource(R.string.ng_theme_saved))
                }
                items(visibleSaved, key = { it.id }) { theme ->
                    NgSwipeToDelete(
                        deletable = true,
                        reordering = false,
                        onDeleteRequested = { pendingDeleteTheme = theme },
                    ) {
                        NgThemeManagementCard(
                            theme = theme,
                            selected = activeThemeId == theme.id,
                            onClick = { onThemeSelected(theme) },
                            onMoreClick = { onThemeEdit(theme) },
                        )
                    }
                }
            }
            if (visibleBuiltIns.isEmpty() && visibleSaved.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.ng_theme_no_results),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }

    if (editingTheme != null && draftTheme != null) {
        NgThemeEditorSheet(
            draftTheme = draftTheme,
            copyOnSave = editingTheme.isBuiltIn,
            onDismissRequest = onDismissThemeEditor,
            onThemeChanged = onDraftThemeChanged,
            onEditBackground = { backgroundActionsDark = it },
            onSave = onSaveTheme,
            onExport = { onThemeExport(draftTheme) }
        )
    }
    if (md3ImportDraft != null) {
        NgMd3ThemeImportPreviewSheet(
            draft = md3ImportDraft,
            installing = md3ImportInstalling,
            onDismissRequest = onDismissMd3Import,
            onSaveOnly = { onConfirmMd3Import(false) },
            onSaveAndApply = { onConfirmMd3Import(true) },
        )
    }
    themeNameInitial?.let { initialName ->
        NgThemeNameDialog(
            initialName = initialName,
            onDismissRequest = { themeNameInitial = null },
            onConfirm = { name ->
                themeNameInitial = null
                onSaveCurrent(name)
            },
        )
    }
    backgroundActionsDark?.let { dark ->
        val background = if (dark) draftTheme?.darkBackground else draftTheme?.lightBackground
        NgThemeBackgroundActionsDialog(
            hasImage = !background?.path.isNullOrBlank(),
            onDismissRequest = { backgroundActionsDark = null },
            onSelectImage = {
                backgroundActionsDark = null
                onSelectBackground(dark)
            },
            onEditBlur = {
                backgroundActionsDark = null
                backgroundBlurDark = dark
            },
            onClear = {
                backgroundActionsDark = null
                onClearBackground(dark)
            },
        )
    }
    backgroundBlurDark?.let { dark ->
        val background = if (dark) draftTheme?.darkBackground else draftTheme?.lightBackground
        NgThemeBackgroundBlurDialog(
            initialValue = background?.blur ?: 0,
            onDismissRequest = { backgroundBlurDark = null },
            onConfirm = { blur ->
                backgroundBlurDark = null
                onBackgroundBlurChanged(dark, blur)
            },
        )
    }
    pendingDeleteTheme?.let { theme ->
        NgThemeDeleteDialog(
            themeName = theme.name,
            onDismissRequest = { pendingDeleteTheme = null },
            onConfirm = {
                pendingDeleteTheme = null
                onThemeDelete(theme)
            },
        )
    }
}

@Composable
private fun ThemeSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 2.dp, top = 8.dp, bottom = 2.dp),
        color = Color(NgTheme.colors.primary),
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun NgThemeManagementCard(
    theme: NgManagedTheme,
    selected: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val light = remember(context, theme.colors) {
        NgThemeResolver.resolveColorScheme(context, theme.colors, false)
    }
    val dark = remember(context, theme.colors) {
        NgThemeResolver.resolveColorScheme(context, theme.colors, true)
    }
    val themePrimary = if (NgTheme.snapshot.isDark) dark.primary else light.primary
    val shape = RoundedCornerShape(18.dp)
    NgSettingsCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        cornerRadius = 18.dp,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    if (selected) {
                        drawRect(
                            color = Color(themePrimary),
                            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), size.height)
                        )
                    }
                }
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorResource(R.color.ng_settings_icon_bg))
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cfg_theme),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color(themePrimary)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = theme.name,
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ThemeColorPreview(
                    lightColors = intArrayOf(light.primary, light.secondary, light.background),
                    darkColors = intArrayOf(dark.primary, dark.secondary, dark.background)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "›",
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ThemeManagerTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSaveCurrent: () -> Unit,
    onImportPackage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                itemId = THEME_ACTION_SAVE_CURRENT,
                titleRes = R.string.ng_theme_save_current,
                iconRes = R.drawable.ic_save,
            ),
            NgExpandableActionMenuItem(
                itemId = THEME_ACTION_IMPORT_PACKAGE,
                titleRes = R.string.ng_theme_import_package,
                iconRes = R.drawable.ic_import,
            ),
        )
    }
    NgFloatingSearchToolbar(
        query = query,
        onQueryChange = onQueryChange,
        hint = stringResource(R.string.search_theme),
        onBack = onBack,
        modifier = modifier,
    ) {
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                onClick = menuState::onAnchorClick,
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                menuContainerColor = ngDrawerContentCardColor(),
                properties = PopupProperties(focusable = true, clippingEnabled = false),
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        THEME_ACTION_SAVE_CURRENT -> onSaveCurrent()
                        THEME_ACTION_IMPORT_PACKAGE -> onImportPackage()
                    }
                },
            )
        }
    }
}

private const val THEME_ACTION_SAVE_CURRENT = 0x6E7401
private const val THEME_ACTION_IMPORT_PACKAGE = 0x6E7402

@Composable
private fun ThemeColorPreview(lightColors: IntArray, darkColors: IntArray) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lightColors.forEach { color -> ThemeColorDot(color) }
        Spacer(Modifier.width(6.dp))
        darkColors.forEach { color -> ThemeColorDot(color) }
    }
}

@Composable
private fun ThemeColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgThemeEditorSheet(
    draftTheme: NgManagedTheme,
    copyOnSave: Boolean,
    onDismissRequest: () -> Unit,
    onThemeChanged: (NgManagedTheme) -> Unit,
    onEditBackground: (Boolean) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                NgLongDrawerHeader(
                    title = stringResource(R.string.ng_theme_edit),
                    secondaryActionIconRes = R.drawable.ic_share,
                    secondaryActionContentDescription = stringResource(R.string.share),
                    onSecondaryActionClick = onExport,
                    actionIconRes = R.drawable.ic_save,
                    actionContentDescription = stringResource(R.string.save),
                    onActionClick = onSave,
                    centerTitle = true,
                )
                Box(modifier = Modifier.weight(1f)) {
                    ThemeEditScreen(
                        theme = draftTheme,
                        copyOnSave = copyOnSave,
                        onThemeChanged = onThemeChanged,
                        onEditBackground = onEditBackground
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgMd3ThemeImportPreviewSheet(
    draft: Md3ThemeImportDraft,
    installing: Boolean,
    onDismissRequest: () -> Unit,
    onSaveOnly: () -> Unit,
    onSaveAndApply: () -> Unit,
) {
    val context = LocalContext.current
    val baseSnapshot = NgTheme.snapshot
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val light = remember(context, draft.theme.colors) {
        NgThemeResolver.resolveColorScheme(context, draft.theme.colors, false)
    }
    val dark = remember(context, draft.theme.colors) {
        NgThemeResolver.resolveColorScheme(context, draft.theme.colors, true)
    }
    val backgroundCount = listOf(
        draft.preview.spec.backgroundProfile.light.archivePath,
        draft.preview.spec.backgroundProfile.dark.archivePath,
    ).count { path -> path != null && path in draft.preview.spec.resources.values }
    val formatLabel = when (draft.preview.spec.sourceFormat) {
        Md3ThemePackageFormat.PORTABLE_V1 -> stringResource(R.string.ng_theme_import_md3_format)
        Md3ThemePackageFormat.LEGACY_APPLICATION_THEME_V1 ->
            stringResource(R.string.ng_theme_import_legacy_format)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!installing) onDismissRequest() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        contentColor = Color(baseSnapshot.colors.onSurface),
        shape = RectangleShape,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth(),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val snapshot = NgTheme.snapshot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.ng_theme_import_preview),
                    color = Color(snapshot.colors.onSurface),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(snapshot.colors.surfaceContainer))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = draft.theme.name,
                        color = Color(snapshot.colors.onSurface),
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatLabel,
                        color = Color(snapshot.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                    )
                    ThemeColorPreview(
                        lightColors = intArrayOf(light.primary, light.secondary, light.background),
                        darkColors = intArrayOf(dark.primary, dark.secondary, dark.background),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.ng_theme_import_summary,
                        backgroundCount,
                        draft.preview.spec.coverAlbums.size,
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color(snapshot.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NgActionBarButton(
                        text = stringResource(R.string.ng_theme_import_save_only),
                        icon = Icons.Rounded.FileDownload,
                        onClick = onSaveOnly,
                        modifier = Modifier.weight(1f),
                        enabled = !installing,
                        variant = NgButtonVariant.OUTLINE,
                    )
                    NgActionBarButton(
                        text = stringResource(R.string.ng_theme_import_save_apply),
                        icon = Icons.Rounded.Check,
                        onClick = onSaveAndApply,
                        modifier = Modifier.weight(1f),
                        enabled = !installing,
                        variant = NgButtonVariant.PRIMARY,
                    )
                }
            }
        }
    }
}

@Composable
private fun NgThemeNameDialog(
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val confirm = {
        val normalized = name.trim()
        if (normalized.isNotEmpty()) {
            onConfirm(normalized)
        } else {
            onDismissRequest()
        }
        Unit
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.theme_name),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = confirm,
                )
            },
        ) {
            NgFormField(
                label = stringResource(R.string.theme_name),
                value = name,
                onValueChange = { name = it },
                variant = NgFormFieldVariant.DIALOG_UNDERLINE,
                autoFocus = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
        }
    }
}

@Composable
private fun NgThemeBackgroundActionsDialog(
    hasImage: Boolean,
    onDismissRequest: () -> Unit,
    onSelectImage: () -> Unit,
    onEditBlur: () -> Unit,
    onClear: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.background_image),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {},
        ) {
            NgDialogValueRow(
                title = stringResource(R.string.select_image),
                value = "",
                onClick = onSelectImage,
            )
            NgDialogDivider()
            NgDialogValueRow(
                title = stringResource(R.string.background_image_blurring),
                value = "",
                onClick = onEditBlur,
            )
            if (hasImage) {
                NgDialogDivider()
                NgDialogValueRow(
                    title = stringResource(R.string.clear),
                    value = "",
                    onClick = onClear,
                )
            }
        }
    }
}

@Composable
private fun NgThemeBackgroundBlurDialog(
    initialValue: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue.coerceIn(0, 25)) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.background_image_blurring),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.STANDARD,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onConfirm(value) },
                )
            },
        ) {
            Text(
                text = value.toString(),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            NgSlider(
                value = value.toFloat(),
                onValueChange = { value = it.roundToInt().coerceIn(0, 25) },
                valueRange = 0f..25f,
                steps = 24,
                variant = NgSliderVariant.DISCRETE,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = stringResource(R.string.background_image_hint),
                modifier = Modifier.padding(top = 6.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun NgThemeDeleteDialog(
    themeName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.delete),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .widthIn(max = 520.dp),
            variant = NgDialogVariant.CLASSIC_CONFIRMATION,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.no),
                    onClick = onDismissRequest,
                    secondary = true,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.yes),
                    onClick = onConfirm,
                    danger = true,
                )
            },
        ) {
            Text(
                text = stringResource(R.string.ng_theme_delete_message, themeName),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}
