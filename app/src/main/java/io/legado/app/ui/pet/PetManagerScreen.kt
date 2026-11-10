package io.legado.app.ui.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.ai.AiChatEntryStyle
import io.legado.app.help.pet.*
import io.legado.app.help.resource.PetManagementActions
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.main.AiChatButtonView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val PET_MENU_IMPORT = 1
private const val PET_MENU_GET = 2

internal fun PetLocalRecord.displayName() = manifest?.name ?: id
internal fun PetLocalRecord.versionLabel() = info?.versionName ?: manifest?.versionCode?.toString() ?: "未知版本"
internal fun PetLocalRecord.originLabel() = when (origin?.channel) {
    "resource" -> "资源库"
    "local" -> "本地导入"
    else -> "本地安装"
}
private fun PetLocalRecord.statusLabel(): String? = when (status) {
    PetLocalStatus.READY -> null
    PetLocalStatus.MISSING -> "文件缺失"
    PetLocalStatus.INCOMPATIBLE -> "接口不兼容"
    PetLocalStatus.CORRUPT -> "文件损坏"
    PetLocalStatus.LOAD_ERROR -> "运行失败"
}

@Composable
internal fun PetManagerScreen(
    state: PetManagerState, initialId: String?, onBack: () -> Unit,
    onImport: (String?) -> Unit, onMore: () -> Unit,
    onUse: (petId: String) -> Unit, onUseFromDetail: (petId: String, requestId: String) -> Unit,
    onUseButton: () -> Unit, onEntryEnabledChanged: (Boolean) -> Unit, onReload: (String) -> Unit, onUninstall: (String) -> Unit,
    onExport: (String) -> Unit, onDiagnostic: (String) -> Unit,
    onConfirmImport: () -> Unit, onDismissImport: () -> Unit, onRetry: () -> Unit,
) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var uninstallId by rememberSaveable { mutableStateOf<String?>(null) }
    var positioned by rememberSaveable(initialId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.records, initialId) {
        if (!positioned && !state.loading && initialId != null) {
            val index = state.records.indexOfFirst { it.id == initialId }
            if (index >= 0) { listState.scrollToItem(index + 1); detailId = initialId }
            positioned = true
        }
    }
    LaunchedEffect(state.importedId) {
        state.importedId?.let { id ->
            val index = state.records.indexOfFirst { it.id == id }
            if (index >= 0) listState.animateScrollToItem(index + 1)
        }
    }
    Column(Modifier.fillMaxSize()) {
        NgFloatingTitleToolbar(
            title = stringResource(R.string.ai_chat_entry_menu),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 10.dp),
            variant = NgFloatingToolbarVariant.LARGE_TOUCH_TARGETS,
            trailingContent = {
                Box {
                    val menuState = remember { NgPopupToggleState() }
                    val menuItems = remember(state.busy) {
                        listOf(
                            NgExpandableActionMenuItem(
                                itemId = PET_MENU_IMPORT,
                                titleRes = R.string.pet_import_package,
                                iconRes = R.drawable.ic_theme_menu_import,
                                enabled = !state.busy,
                            ),
                            NgExpandableActionMenuItem(
                                itemId = PET_MENU_GET,
                                titleRes = R.string.pet_get_pets,
                                iconRes = R.drawable.ic_theme_menu_download,
                            ),
                        )
                    }
                    NgFloatingToolbarActionButton(
                        iconRes = R.drawable.ic_grid_menu,
                        contentDescription = stringResource(R.string.more),
                        onClick = menuState::onAnchorClick,
                        variant = NgFloatingToolbarVariant.LARGE_TOUCH_TARGETS,
                    )
                    NgExpandableActionMenu(
                        expanded = menuState.expanded,
                        onDismissRequest = menuState::onDismissRequest,
                        items = menuItems,
                        variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                        sideSlideEndMargin = 16.dp,
                        menuContainerColor = ngDrawerContentCardColor(),
                        properties = PopupProperties(focusable = true, clippingEnabled = false),
                        onItemClick = { item ->
                            if (menuState.expanded) {
                                menuState.close()
                                when (item.itemId) {
                                    PET_MENU_IMPORT -> onImport(null)
                                    PET_MENU_GET -> onMore()
                                }
                            }
                        },
                    )
                }
            },
        )
        NgGlassSurface(
            modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            role = NgMaterialRole.CONTENT,
            liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(containerColor = colorResource(R.color.ng_surface_card)),
        ) {
            NgFormSwitchRow(title = stringResource(R.string.ai_chat_entry_enabled), checked = state.enabled,
                onCheckedChange = onEntryEnabledChanged, enabled = !state.loading && !state.busy,
                modifier = Modifier.padding(horizontal = 14.dp).padding(top = 6.dp))
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Color(NgTheme.colors.outline).copy(alpha = .25f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("已安装 ${state.records.size} 项", Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal)
                NgFormActionButton("${stringResource(R.string.pet_get_pets)} ›", onMore, appearance = NgFormActionButtonAppearance.TEXT,
                    buttonHeight = 48.dp, textSize = 13.sp, textLineHeight = 18.sp)
            }
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(NgTheme.colors.primary))
            state.error?.let {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, Modifier.weight(1f), color = Color(NgTheme.colors.error))
                    NgFormActionButton("重试", onRetry, appearance = NgFormActionButtonAppearance.TEXT)
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "builtin:button") {
                    Surface(shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp), color = colorResource(R.color.ng_surface_card)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp), verticalAlignment = Alignment.CenterVertically) {
                            NgManagementListCard(title = "悬浮按钮", summary = "内置", modifier = Modifier.weight(1f),
                                titleFontWeight = FontWeight.Normal, leading = { FloatingButtonPreview() })
                            PetUseAction(active = state.enabled && state.selectedId == AiChatEntryStyle.BUTTON.id,
                                enabled = state.enabled && !state.loading && !state.busy, onUse = onUseButton,
                                selectedWhileDisabled = state.selectedId == AiChatEntryStyle.BUTTON.id && !state.enabled)
                            Spacer(Modifier.width(48.dp))
                        }
                    }
                }
                items(state.records, key = { it.id }) { pet ->
                    PetRow(pet, state, onDetail = { detailId = pet.id }, onUse = { onUse(pet.id) },
                        onAction = { action -> when (action) {
                            1 -> onImport(pet.id)
                            2 -> onExport(pet.id)
                            3 -> onReload(pet.id)
                            4 -> onDiagnostic(pet.id)
                            5 -> uninstallId = pet.id
                        } })
                }
            }
        }
    }
    state.candidate?.let { candidate ->
        ImportDialog(candidate, state, onDismissImport, onConfirmImport)
    }
    state.records.firstOrNull { it.id == detailId }?.let { pet ->
        PetDetailDialog(
            pet = pet,
            state = state,
            dismiss = { detailId = null },
            use = { requestId -> onUseFromDetail(pet.id, requestId) },
            uninstall = { uninstallId = pet.id; detailId = null },
            diagnostic = onDiagnostic,
        )
    }
    state.records.firstOrNull { it.id == uninstallId }?.let { pet ->
        Dialog(onDismissRequest = { if (!state.busy) uninstallId = null }) {
            NgDialog(title = "卸载宠物", actions = {
                PetButton("取消", { uninstallId = null }, Modifier.weight(1f), primary = false, enabled = !state.busy)
                PetButton("卸载", { onUninstall(pet.id); uninstallId = null }, Modifier.weight(1f), enabled = !state.busy)
            }) {
                Text("卸载${pet.displayName()}？", color = Color(NgTheme.colors.onSurface))
                if (state.selectedId == pet.id) Text("将切回悬浮按钮，保留入口开关状态。", color = Color(NgTheme.colors.onSurfaceVariant), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun PetRow(pet: PetLocalRecord, state: PetManagerState, onDetail: () -> Unit, onUse: () -> Unit, onAction: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val active = state.enabled && state.selectedId == pet.id && pet.status == PetLocalStatus.READY
    Surface(shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp), color = colorResource(R.color.ng_surface_card)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp), verticalAlignment = Alignment.CenterVertically) {
            NgManagementListCard(title = pet.displayName(),
                summary = pet.statusLabel() ?: "${pet.versionLabel()} · ${pet.originLabel()}",
                modifier = Modifier.weight(1f), onClick = onDetail, titleFontWeight = FontWeight.Normal,
                leading = { PetPreview(pet.id, state.revision, Modifier.size(56.dp)) { PetManagementActions.readPreview(pet.id) } })
            PetUseAction(active = active,
                enabled = state.canUseFromCard(pet.status),
                selectedWhileDisabled = state.selectedId == pet.id && !state.enabled, onUse = onUse)
            Box {
                IconButton(onClick = { menu = true }, enabled = !state.busy, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreVert, "${pet.displayName()}更多操作", tint = Color(NgTheme.colors.onSurface))
                }
                NgExpandableActionMenu(expanded = menu, onDismissRequest = { menu = false },
                    items = listOf(
                        NgExpandableActionMenuItem(1, 0, 0, title = "从文件更新"),
                        NgExpandableActionMenuItem(2, 0, 0, title = "导出 ZIP", enabled = pet.status != PetLocalStatus.MISSING),
                        NgExpandableActionMenuItem(3, 0, 0, title = "重新加载", enabled = active),
                        NgExpandableActionMenuItem(4, 0, 0, title = "复制诊断"),
                        NgExpandableActionMenuItem(5, 0, 0, title = "卸载", danger = true),
                    ), onItemClick = { menu = false; onAction(it.itemId) })
            }
        }
    }
}

@Composable
private fun PetUseAction(active: Boolean, enabled: Boolean, onUse: () -> Unit, selectedWhileDisabled: Boolean = false) {
    val primary = Color(NgTheme.colors.primary)
    // Reserve the same action width in every row, including with larger system fonts.
    val actionWidth = (with(LocalDensity.current) { 12.sp.toDp() } * 3 + 36.dp).coerceAtLeast(72.dp)
    Column(Modifier.width(actionWidth).heightIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (active || selectedWhileDisabled) {
            val statusColor = if (active) primary else Color(NgTheme.colors.onSurfaceVariant)
            Surface(shape = CircleShape, color = statusColor.copy(alpha = .10f)) {
                Row(Modifier.heightIn(min = 28.dp).padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                    Text(if (active) "使用中" else "已关闭", color = statusColor, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        } else {
            PetButton("使用", onUse, enabled = enabled, small = true)
        }
    }
}

@Composable
private fun FloatingButtonPreview() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val primary = Color(NgTheme.colors.primary).toArgb()
    val bitmap = remember(context, configuration, density, primary) {
        val size = with(density) { 72.dp.roundToPx() }.coerceAtLeast(1)
        val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Draw the actual entry once without attaching its View or starting its animation ticker.
        AiChatButtonView(context).apply {
            updateAccentColor(primary)
            measure(android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY))
            layout(0, 0, size, size)
            draw(AndroidCanvas(image))
        }
        image.asImageBitmap()
    }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Image(bitmap, null, Modifier.size(56.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun PetButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, small: Boolean = false) {
    NgFormActionButton(text, onClick, modifier, enabled = enabled,
        variant = if (primary) NgButtonVariant.PRIMARY_LIGHT_CONTENT else NgButtonVariant.OUTLINE,
        appearance = NgFormActionButtonAppearance.SURFACE_CARD_BORDERLESS,
        buttonHeight = if (small) 36.dp else 44.dp, minimumWidth = if (small) 56.dp else 76.dp,
        textSize = if (small) 14.sp else 16.sp)
}

@Composable
internal fun PetPreview(
    key: Any, revision: Long, modifier: Modifier,
    catalogFallback: (suspend () -> Bitmap?)? = null,
    load: suspend () -> ByteArray?,
) {
    val bitmap by produceState<ImageBitmap?>(null, key, revision) {
        value = withContext(Dispatchers.IO) {
            try {
                val localBitmap = load()?.let { bytes ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= 512 && bounds.outHeight / it <= 512 }
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
                (localBitmap ?: catalogFallback?.invoke())?.asImageBitmap()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Icon(Icons.Rounded.Pets, null, Modifier.size(32.dp), tint = Color(NgTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun ImportDialog(candidate: PetImportCandidate, state: PetManagerState, dismiss: () -> Unit, confirm: () -> Unit) {
    val action = when (candidate.action) {
        PetImportAction.INSTALL -> "导入"
        PetImportAction.UPDATE -> "更新"
        PetImportAction.DOWNGRADE -> "降级安装"
        PetImportAction.REPLACE -> "替换"
        PetImportAction.NO_OP -> "已安装"
    }
    // Display-only enrichment must match the inspected installation snapshot exactly.
    // Import policy and commit permissions continue to use candidate.action / candidate.previous.
    val previous = candidate.previous?.let { snapshot ->
        state.records.firstOrNull { it.id == snapshot.id && it.sha256 == snapshot.sha256 } ?: snapshot
    }
    val version = candidate.info?.versionName ?: candidate.manifest.versionCode.toString()
    val current = state.records.firstOrNull { it.id == state.selectedId }
    Dialog(onDismissRequest = { if (!state.busy) dismiss() }) {
        NgDialog(title = "${action}宠物", actions = {
            PetButton("取消", dismiss, Modifier.weight(1f), primary = false, enabled = !state.busy)
            PetButton(action, confirm, Modifier.weight(1f), enabled = !state.busy)
        }) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (previous == null) {
                    PetPreview(candidate, 0, Modifier.fillMaxWidth().height(150.dp), load = candidate::readPreview)
                    Text(candidate.manifest.name, Modifier.align(Alignment.CenterHorizontally), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(NgTheme.colors.onSurface))
                    Text(listOfNotNull(version, candidate.info?.author).joinToString(" · "), Modifier.align(Alignment.CenterHorizontally), color = Color(NgTheme.colors.onSurfaceVariant))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PetPreview(candidate, 0, Modifier.size(76.dp), load = candidate::readPreview)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(candidate.manifest.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(NgTheme.colors.onSurface))
                            Text("本地导入", color = Color(NgTheme.colors.onSurfaceVariant))
                        }
                    }
                    Surface(color = colorResource(R.color.ng_surface_card), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("已安装"); Text(previous.versionLabel(), fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                                Text("→", color = Color(NgTheme.colors.primary), fontSize = 24.sp)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("导入版本"); Text(version, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                            }
                            Text(when (candidate.action) {
                                PetImportAction.REPLACE -> "版本相同，内容已变化"
                                PetImportAction.DOWNGRADE -> "导入版本低于已安装版本"
                                else -> "安装新版本"
                            }, Modifier.padding(top = 10.dp), color = Color(NgTheme.colors.onSurfaceVariant))
                        }
                    }
                    Text("将${action}已安装的${candidate.manifest.name}", color = Color(NgTheme.colors.onSurface))
                    Text("来源：${previous.originLabel()} → 本地导入", color = Color(NgTheme.colors.onSurfaceVariant))
                }
                state.importFileName?.let { Text("文件  $it", color = Color(NgTheme.colors.onSurfaceVariant)) }
                Text("大小  ${Formatter.formatFileSize(LocalContext.current, candidate.sizeBytes)}", color = Color(NgTheme.colors.onSurfaceVariant))
                HorizontalDivider(color = Color(NgTheme.colors.outline).copy(alpha = .25f))
                Text(when {
                    previous == null -> "导入后不会自动启用"
                    state.enabled && state.selectedId == candidate.manifest.id -> "当前宠物将重新加载"
                    state.enabled && current != null -> "${current.displayName()}继续使用"
                    else -> "保留当前入口选择"
                }, color = Color(NgTheme.colors.onSurface))
                Text("插件包含可执行代码，请确认来源可信", color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PetDetailDialog(
    pet: PetLocalRecord,
    state: PetManagerState,
    dismiss: () -> Unit,
    use: (requestId: String) -> Unit,
    uninstall: () -> Unit,
    diagnostic: (String) -> Unit,
) {
    val useRequestId = rememberSaveable(pet.id) { UUID.randomUUID().toString() }
    LaunchedEffect(useRequestId, state.successfulUseRequestId) {
        if (state.successfulUseRequestId == useRequestId) dismiss()
    }
    var technical by rememberSaveable(pet.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = dismiss) {
        NgDialog(title = pet.displayName(), actions = {
            PetButton("卸载", uninstall, Modifier.weight(1f), primary = false, enabled = !state.busy)
            PetButton("使用", { use(useRequestId) }, Modifier.weight(1f),
                enabled = state.canUseFromDetail(pet.status))
        }) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                key(pet.id, pet.sha256) {
                    PetPreview(
                        pet.id, state.revision, Modifier.fillMaxWidth().height(160.dp),
                        catalogFallback = { PetManagementActions.fetchCatalogPreview(pet) },
                    ) { PetManagementActions.readPreview(pet.id) }
                }
                Text("${pet.versionLabel()} · ${pet.originLabel()}", color = Color(NgTheme.colors.onSurfaceVariant))
                pet.info?.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(NgTheme.colors.onSurface)) }
                pet.info?.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(NgTheme.colors.onSurface)) }
                Text(Formatter.formatFileSize(LocalContext.current, pet.sizeBytes), color = Color(NgTheme.colors.onSurfaceVariant))
                pet.statusLabel()?.let { Text(it, color = Color(NgTheme.colors.error)) }
                Text(if (technical) "技术信息 ▴" else "技术信息 ▾", Modifier.fillMaxWidth().clickable { technical = !technical }.padding(vertical = 10.dp), color = Color(NgTheme.colors.primary))
                if (technical) {
                    Text("ID：${pet.id}\n内部版本：${pet.manifest?.versionCode ?: "未知"}\nAPI：${pet.manifest?.apiVersion ?: "未知"}\nSHA-256：${pet.sha256 ?: "未知"}", color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp)
                    pet.error?.let { Text("${it.stage}：${it.message}", color = Color(NgTheme.colors.error), fontSize = 12.sp) }
                    NgFormActionButton("复制诊断", { diagnostic(pet.id) }, appearance = NgFormActionButtonAppearance.TEXT)
                }
            }
        }
    }
}
