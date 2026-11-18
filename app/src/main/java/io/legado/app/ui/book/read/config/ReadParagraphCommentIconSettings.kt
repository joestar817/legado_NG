package io.legado.app.ui.book.read.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.*
import io.legado.app.model.ParagraphCommentIconRenderer
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.getClipText
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal fun paragraphCommentIconEditorIntent(context: Context, config: ParagraphCommentIconConfig): Intent =
    Intent(context, CodeEditActivity::class.java).apply {
        putExtra("text", config.template())
        putExtra("title", context.getString(R.string.paragraph_icon_edit))
        putExtra("languageName", "text.html.basic")
        putExtra("returnUnchangedText", true)
        putExtra(CodeEditActivity.EXTRA_CONFIRM_SAVE_ON_EXIT, true)
    }

@Composable
internal fun ReadParagraphCommentIconSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(ParagraphCommentBubbleSettings.load()) }
    val initialIndex = remember { library.bubbles.indexOfFirst { it.id == library.selectedId }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var colorPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var inputMode by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var restore by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    val appearance = library.appearance

    fun update(value: ParagraphCommentBubbleLibrary) {
        if (value == library) return
        val refresh = value.changesReadingFrom(library)
        ParagraphCommentBubbleSettings.save(library, value)
        library = value
        if (refresh) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                delay(250)
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                refreshJob = null
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (refreshJob != null) {
                refreshJob?.cancel()
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }
        }
    }
    BackHandler(enabled = colorPage != null) { colorPage = null }

    fun startImport(load: suspend () -> Pair<String, String>, targetId: String? = null, fromUrlDialog: Boolean = false) {
        if (busy) return
        busy = true
        importJob = scope.launch {
            try {
                val (text, name) = load()
                withContext(Dispatchers.Default) { ParagraphCommentIconSettings.validate(text) }
                val current = library
                if (targetId != null && current.bubbles.none { it.id == targetId }) return@launch
                val bubble = if (targetId == null) ParagraphCommentBubbleSettings.newBubble(name, text)
                    else ParagraphCommentBubble(targetId, name, text)
                update(current.put(bubble, select = targetId == null))
                if (targetId == null) listState.requestScrollToItem(current.bubbles.size)
                if (fromUrlDialog) inputMode = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = error.localizedMessage ?: context.getString(R.string.paragraph_icon_invalid)
                if (fromUrlDialog) inputError = message else context.toastOnUi(message)
            } finally { busy = false }
        }
    }
    val editor = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = editingId
        val name = editingName
        editingId = null
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra("text")?.let { text ->
            startImport({ text to name }, target)
        }
    }
    fun edit(bubble: ParagraphCommentBubble?) {
        editingId = bubble?.id
        editingName = bubble?.name ?: input.trim().let { if (it.endsWith(".svg", true)) it else "$it.svg" }
        val config = appearance.copy(source = ParagraphCommentIconSource.CUSTOM,
            customSvg = bubble?.svg ?: ParagraphCommentIconSettings.builtin(ParagraphCommentIconShape.ROUNDED))
        editor.launch(paragraphCommentIconEditorIntent(context, config))
    }
    val filePicker = rememberLauncherForActivityResult(SelectFileContract()) { uri ->
        if (uri != null) startImport({
            withContext(Dispatchers.IO) {
                val text = uri.inputStream(context).getOrThrow().use(ParagraphCommentBubbleImport::read)
                val name = DocumentFile.fromSingleUri(context, uri)?.name?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.paragraph_icon_default_name)
                text to name
            }
        })
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maximumHeight = when {
        colorPage != null -> screenHeight * .86f
        screenHeight < 480.dp -> screenHeight * .84f
        else -> (screenHeight * .72f).coerceAtMost(600.dp)
    }
    NgGlassSurface(Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = maximumHeight),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), style = readFloatingGlassStyle()) {
        if (colorPage != null) {
            val night = colorPage == 1
            AdvancedColorPage(
                title = stringResource(if (night) R.string.paragraph_icon_night_color else R.string.paragraph_icon_day_color),
                initialColor = if (night) appearance.nightColor else appearance.dayColor,
                onBack = { colorPage = null },
                onColorChanged = { color -> update(library.copy(appearance = if (night) appearance.copy(nightColor = color) else appearance.copy(dayColor = color))) },
                onReset = { update(library.copy(appearance = if (night) appearance.copy(nightColor = ParagraphCommentIconConfig().nightColor)
                    else appearance.copy(dayColor = ParagraphCommentIconConfig().dayColor))) },
            )
        } else Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)) {
            NgLongDrawerHeader(title = stringResource(R.string.paragraph_icon_title), centerTitle = true,
                titleFontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 16.dp),
                navigationContent = { ParagraphIconHeaderButton(R.drawable.ic_arrow_back, stringResource(R.string.back), onBack) },
                actionContent = {
                    Box {
                        ParagraphIconHeaderButton(R.drawable.ic_more_vert, stringResource(R.string.more)) { menu = true }
                        NgExpandableActionMenu(menu, { menu = false }, listOf(
                            NgExpandableActionMenuItem(1, R.string.paragraph_icon_new, R.drawable.ic_add, enabled = !busy),
                            NgExpandableActionMenuItem(2, R.string.paragraph_icon_local, R.drawable.ic_folder_open, enabled = !busy),
                            NgExpandableActionMenuItem(3, R.string.paragraph_icon_network, R.drawable.ic_web_outline, enabled = !busy),
                            NgExpandableActionMenuItem(4, R.string.paragraph_icon_clipboard, R.drawable.ic_paste, enabled = !busy),
                            NgExpandableActionMenuItem(5, R.string.restore_default, R.drawable.ic_restore, dividerBefore = true, enabled = !busy),
                        ), onItemClick = { item ->
                            menu = false
                            when (item.itemId) {
                                1 -> { input = ""; inputError = null; inputMode = "new" }
                                2 -> filePicker.launch(arrayOf("image/svg+xml", "application/xml", "text/xml", "text/plain", "application/octet-stream"))
                                3 -> { input = ""; inputError = null; inputMode = "network" }
                                4 -> {
                                    val text = context.getClipText()?.trim()?.removePrefix("\uFEFF")?.trimStart()
                                    if (text.isNullOrBlank()) context.toastOnUi(R.string.paragraph_icon_clipboard_empty)
                                    else startImport({
                                        if (text.startsWith("https://", true) || text.startsWith("http://", true)) ParagraphCommentBubbleImport.download(text)
                                        else text to context.getString(R.string.paragraph_icon_default_name)
                                    })
                                }
                                5 -> restore = true
                            }
                        }, menuContainerColor = ngDrawerContentCardColor(),
                            variant = NgExpandableActionMenuVariant.END_ANCHORED_DROPDOWN)
                    }
                })
            if (busy && inputMode != "network") Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.paragraph_icon_importing), Modifier.weight(1f), fontSize = 13.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                NgDialogTextActionButton(stringResource(R.string.cancel), { importJob?.cancel() })
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                // On short windows keep even a long library to two rows before the appearance controls.
                val shortWindow = maxHeight < 320.dp
                Column(
                    modifier = if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NgFormPanel {
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            SwitchSettingRow(stringResource(R.string.paragraph_icon_enabled), library.enabled,
                                { enabled -> update(library.copy(enabled = enabled)) }, enabled = library.bubbles.isNotEmpty())
                        }
                    }
                    if (library.bubbles.isEmpty()) {
                        Text(stringResource(R.string.paragraph_icon_empty), Modifier.padding(horizontal = 2.dp),
                            fontSize = 12.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                    } else {
                        val listModifier = if (shortWindow) Modifier.heightIn(max = 110.dp) else Modifier.weight(1f, fill = false)
                        LazyColumn(
                            modifier = listModifier.fillMaxWidth().selectableGroup(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(library.bubbles, key = { it.id }) { bubble ->
                                ParagraphBubbleCard(bubble, appearance, library.selectedId == bubble.id, !busy,
                                    { update(library.select(bubble.id)) }, { edit(bubble) }, { deleteId = bubble.id })
                            }
                        }
                    }
                    ParagraphBubbleAppearance(appearance, { update(library.copy(appearance = it)) }, { colorPage = it })
                }
            }
        }
    }
    CompositionLocalProvider(LocalNgDialogColorMode provides NgDialogColorMode.INHERIT_HOST) {
        if (inputMode != null) {
            val new = inputMode == "new"
            val dismissInput = {
                if (!new && busy) importJob?.cancel()
                inputMode = null
            }
            Dialog(onDismissRequest = dismissInput) {
                val hint = stringResource(if (new) R.string.paragraph_icon_name else R.string.paragraph_icon_url)
                NgDialog(stringResource(if (new) R.string.paragraph_icon_new else R.string.paragraph_icon_network),
                    variant = NgDialogVariant.FORM_EDITOR, titleFontSize = 18.sp, titleLineHeight = 24.sp,
                    titleFontWeight = FontWeight.Medium, actions = {
                        NgDialogTextActionButton(stringResource(R.string.cancel), dismissInput, secondary = true)
                        val action = when {
                            new -> R.string.confirm
                            busy -> R.string.paragraph_icon_importing
                            else -> R.string.paragraph_icon_import_action
                        }
                        NgDialogTextActionButton(stringResource(action), {
                            if (new) {
                                inputMode = null
                                edit(null)
                            } else {
                                inputError = null
                                val address = input
                                startImport({ ParagraphCommentBubbleImport.download(address) }, fromUrlDialog = true)
                            }
                        }, enabled = !busy && input.isNotBlank())
                    }) {
                    NgFormField(hint, input, { input = it; inputError = null },
                        placeholder = hint, enabled = !busy, isError = inputError != null, supportingText = inputError,
                        keyboardOptions = if (new) KeyboardOptions.Default else KeyboardOptions(keyboardType = KeyboardType.Uri),
                        variant = NgFormFieldVariant.DIALOG_UNDERLINE, autoFocus = true)
                }
            }
        }
        val target = library.bubbles.find { it.id == deleteId }
        if (target != null) Dialog(onDismissRequest = { deleteId = null }) {
            NgDialog(stringResource(R.string.delete), variant = NgDialogVariant.SIMPLE_CONFIRMATION, actions = {
                NgDialogTextActionButton(stringResource(R.string.cancel), { deleteId = null }, secondary = true, modifier = Modifier.weight(1f))
                NgDialogTextActionButton(stringResource(R.string.delete), {
                    update(library.remove(target.id)); deleteId = null
                }, danger = true, modifier = Modifier.weight(1f))
            }) { Text(stringResource(R.string.paragraph_icon_delete_confirm, target.name), color = Color(NgTheme.colors.onSurface), fontSize = 14.sp) }
        }
        if (restore) Dialog(onDismissRequest = { restore = false }) {
            NgDialog(stringResource(R.string.restore_default), variant = NgDialogVariant.SIMPLE_CONFIRMATION, actions = {
                NgDialogTextActionButton(stringResource(R.string.cancel), { restore = false }, secondary = true, modifier = Modifier.weight(1f))
                NgDialogTextActionButton(stringResource(R.string.restore_default), {
                    update(library.restore(ParagraphCommentBubbleSettings.defaults())); restore = false
                }, danger = true, modifier = Modifier.weight(1f))
            }) { Text(stringResource(R.string.paragraph_icon_restore_confirm), color = Color(NgTheme.colors.onSurface), fontSize = 14.sp) }
        }
    }
}

@Composable
private fun ParagraphBubbleAppearance(
    appearance: ParagraphCommentIconConfig,
    onChange: (ParagraphCommentIconConfig) -> Unit,
    onColorPage: (Int) -> Unit,
) {
    NgFormPanel {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.paragraph_icon_size), Modifier.weight(1f), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
                Text("${appearance.size}%", color = Color(NgTheme.colors.primary), fontSize = 14.sp)
            }
            NgSlider(
                value = appearance.size.toFloat(),
                onValueChange = { onChange(appearance.copy(size = it.roundToInt())) },
                valueRange = 70f..150f,
                steps = 15,
                variant = NgSliderVariant.CONTINUOUS,
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }
        NgFormGroupDivider()
        Box(Modifier.padding(horizontal = 14.dp)) {
            SwitchSettingRow(stringResource(R.string.paragraph_icon_follow_text), appearance.followTextColor,
                { onChange(appearance.copy(followTextColor = it)) })
        }
        NgFormGroupDivider()
        val manualColor = !appearance.followTextColor
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (manualColor) 1f else .45f)
            .clickable(enabled = manualColor) { onColorPage(0) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.paragraph_icon_color), Modifier.weight(1f), fontSize = ReadSettingTextSize.navigationLabel, color = Color(NgTheme.colors.onSurface))
            ParagraphIconColorSwatch(stringResource(R.string.paragraph_icon_day_short), appearance.dayColor, manualColor) { onColorPage(0) }
            ParagraphIconColorSwatch(stringResource(R.string.paragraph_icon_night_short), appearance.nightColor, manualColor) { onColorPage(1) }
            Icon(painterResource(R.drawable.ic_arrow_right), null, Modifier.size(16.dp), Color(NgTheme.colors.onSurfaceVariant))
        }
    }
}

@Composable
private fun ParagraphBubbleCard(bubble: ParagraphCommentBubble, appearance: ParagraphCommentIconConfig, selected: Boolean,
                                enabled: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = Color(NgTheme.colors.primary)
    val border = if (selected) Modifier.border(1.5.dp, accent, RoundedCornerShape(NgTheme.shapes.mediumDp.dp)) else Modifier
    val config = remember(bubble.svg) { ParagraphCommentIconConfig(source = ParagraphCommentIconSource.CUSTOM, customSvg = bubble.svg) }
    val previewColor = appearance.color(ReadBookConfig.isNightTheme, ReadBookConfig.textColor)
    val scale = appearance.size / 100f
    NgFormPanel(border) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 52.dp)
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
                .padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp, 44.dp), contentAlignment = Alignment.Center) {
                    ParagraphIconPicture(config, previewColor, Modifier.size(40.dp * scale, 29.dp * scale))
                }
                Text(bubble.name, Modifier.padding(start = 6.dp).weight(1f, fill = false), fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = Color(NgTheme.colors.onSurface))
                if (selected) {
                    Box(Modifier.padding(start = 5.dp).size(14.dp).background(accent, CircleShape)) {
                        Icon(painterResource(R.drawable.ic_check), null, Modifier.padding(2.dp), Color(NgTheme.colors.onPrimary))
                    }
                }
            }
            ParagraphIconItemButton(R.drawable.ic_edit, stringResource(R.string.paragraph_icon_edit_named, bubble.name), enabled, onEdit)
            ParagraphIconItemButton(R.drawable.ic_book_info_delete, stringResource(R.string.paragraph_icon_delete_named, bubble.name), enabled, onDelete)
        }
    }
}

@Composable
private fun ParagraphIconItemButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), description, Modifier.size(21.dp), Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else .45f))
    }
}

@Composable
private fun ParagraphIconHeaderButton(icon: Int, description: String, onClick: () -> Unit) {
    NgThemeSheetActionButton(onClick = onClick, contentDescription = description, touchSize = 40.dp) {
        Icon(painterResource(icon), null, Modifier.size(21.dp), Color(NgTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun ParagraphIconColorSwatch(label: String, color: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        Text(label, fontSize = 12.sp, color = Color(NgTheme.colors.onSurfaceVariant))
        Box(Modifier.size(16.dp).background(Color(color), CircleShape).border(.5.dp, Color.Black.copy(alpha = .1f), CircleShape))
    }
}

@Composable
internal fun ParagraphIconPicture(config: ParagraphCommentIconConfig, color: Int, modifier: Modifier) {
    val density = LocalDensity.current
    val textColor = ReadBookConfig.textColor
    val width = with(density) { 54.dp.roundToPx() }
    val height = with(density) { 39.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, config, color, textColor, width, height) {
        value = withContext(Dispatchers.Default) { ParagraphCommentIconRenderer.bitmap(config, "99", color, width, height, textColor) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize()) }
    }
}
