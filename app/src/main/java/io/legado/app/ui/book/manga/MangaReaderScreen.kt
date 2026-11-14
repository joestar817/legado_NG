package io.legado.app.ui.book.manga

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.manga.config.MangaReadingStyle
import io.legado.app.ui.book.manga.config.MangaFooterContent
import io.legado.app.ui.book.manga.entities.BaseMangaPage
import io.legado.app.ui.book.read.readSourceActionItems
import io.legado.app.ui.book.read.config.ReadChoicePopup
import io.legado.app.ui.book.read.config.ReadConfigDock
import io.legado.app.ui.book.read.config.ReadMoreConfigOption
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgMangaPanelTheme
import kotlin.math.roundToInt

@Composable
internal fun MangaReaderScreen(
    state: MangaReaderState,
    onAction: (MangaAction) -> Unit,
    onSetting: (MangaSetting, Int) -> Unit,
    onPanel: (MangaPanel) -> Unit,
    onPage: (BaseMangaPage) -> Unit,
    onProgress: (Int) -> Unit,
    onMenuVisible: (Boolean) -> Unit,
) {
    val footerStatus = rememberMangaFooterStatus()
    val menu = updateTransition(state.panel == MangaPanel.MENU, label = "manga-menu")
    val menuVisible = menu.currentState || menu.targetState
    SideEffect {
        if (menuVisible != state.menuVisible) onMenuVisible(menuVisible)
    }
    BackHandler(state.panel != MangaPanel.NONE) {
        onPanel(when (state.panel) {
            MangaPanel.FILTER -> MangaPanel.DISPLAY
            MangaPanel.FOOTER -> MangaPanel.DISPLAY
            else -> MangaPanel.NONE
        })
    }
    Box(Modifier.fillMaxSize().background(colorResource(R.color.book_ant_10))) {
        Box(Modifier.fillMaxSize()) {
            key(state.bookUrl) {
                MangaImages(state, onPage, { onPanel(MangaPanel.MENU) }, { onAction(MangaAction.TAIL_RETRY) })
            }
            if (!state.loading && !state.footer.hideFooter) {
                MangaInfoBar(state, Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .semantics { contentDescription = "漫画页脚" }, footerStatus)
            }
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize().background(Color(NgTheme.colors.background)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.error == null) {
                        if (!AppConfig.isEInkMode) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                        Text("加载中", Modifier.padding(top = 10.dp), color = Color(NgTheme.colors.onSurface))
                    } else {
                        Text(state.error.orEmpty(), color = Color(NgTheme.colors.onSurface), textAlign = TextAlign.Center)
                        if (state.retryAllowed) Text("重新加载", Modifier.padding(16.dp)
                            .clickable { onAction(MangaAction.RETRY) }, color = Color(NgTheme.colors.primary), fontSize = 18.sp)
                    }
                }
            }
        }
        NgMangaPanelTheme {
            if (state.panel != MangaPanel.NONE) {
                Box(Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onPanel(MangaPanel.NONE) })
            }
            menu.AnimatedVisibility(visible = { it }, modifier = Modifier.align(Alignment.TopCenter),
                enter = if (AppConfig.isEInkMode) EnterTransition.None else slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
                exit = if (AppConfig.isEInkMode || state.panel != MangaPanel.NONE) ExitTransition.None else slideOutVertically(tween(200)) { -it } + fadeOut(tween(200))) {
                MangaTopMenu(state, onAction)
            }
            menu.AnimatedVisibility(visible = { it }, modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (AppConfig.isEInkMode) EnterTransition.None else slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit = if (AppConfig.isEInkMode || state.panel != MangaPanel.NONE) ExitTransition.None else slideOutVertically(tween(200)) { it } + fadeOut(tween(200))) {
                MangaBottomMenu(state, onAction, onPanel, onProgress)
            }
            if (state.panel != MangaPanel.NONE && state.panel != MangaPanel.MENU) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                    key(state.panel) {
                        NgDismissibleDrawer(onDismiss = { onPanel(MangaPanel.NONE) }) {
                            MangaModule(state, onSetting, onPanel, onAction, footerStatus)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MangaTopMenu(state: MangaReaderState, onAction: (MangaAction) -> Unit) {
    var sourceMenuExpanded by remember(state.sourceOrigin) { mutableStateOf(false) }
    val sourceDisabledDescription = stringResource(R.string.disabled)
    LaunchedEffect(state.panel, state.sourceAvailable) {
        if (state.panel != MangaPanel.MENU || !state.sourceAvailable) sourceMenuExpanded = false
    }
    NgGlassSurface(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)
        .fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        style = NgGlassDefaults.mangaPanelStyle(), role = NgMaterialRole.MANGA_PANEL) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            MangaIcon(R.drawable.ic_manga_menu_back, "返回") { onAction(MangaAction.BACK) }
            Text(state.bookName, Modifier.weight(1f).padding(start = 6.dp).clickable { onAction(MangaAction.INFO) },
                fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Color(NgTheme.colors.onSurface))
            MangaIcon(R.drawable.ic_manga_menu_source, "换源", glyphSize = 20.dp) { onAction(MangaAction.CHANGE_SOURCE) }
            MangaIcon(R.drawable.ic_manga_menu_refresh, "刷新", glyphSize = 20.dp) { onAction(MangaAction.REFRESH) }
        }
        if (AppConfig.showReadTitleBarAddition) {
            MangaDivider(Modifier.padding(horizontal = 10.dp))
            Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(state.chapterName, Modifier.weight(1f).combinedClickable(
                    onClick = { onAction(MangaAction.OPEN_WEB) }, onLongClick = { onAction(MangaAction.WEB_MODE) }),
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(NgTheme.colors.onSurface))
                Box(Modifier.widthIn(max = 144.dp).fillMaxHeight()
                    .semantics { if (!state.sourceEnabled) stateDescription = sourceDisabledDescription }
                    .clickable(enabled = state.sourceAvailable, role = Role.Button) { sourceMenuExpanded = true },
                    contentAlignment = Alignment.CenterEnd) {
                    Text(state.sourceName,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color(if (state.sourceEnabled) NgTheme.colors.secondary else NgTheme.colors.onSurfaceVariant),
                        textDecoration = if (state.sourceEnabled) TextDecoration.None else TextDecoration.LineThrough,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    NgIconActionMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                        items = readSourceActionItems(state.sourceHasLogin, state.sourceEnabled, state.chapterCanPay),
                        style = NgGlassDefaults.mangaPanelStyle(),
                        role = NgMaterialRole.MANGA_PANEL,
                        onItemClick = {
                            when (it.itemId) {
                                R.id.menu_login -> onAction(MangaAction.SOURCE_LOGIN)
                                R.id.menu_edit_source -> onAction(MangaAction.SOURCE_EDIT)
                                R.id.menu_disable_source -> onAction(MangaAction.SOURCE_DISABLE)
                                R.id.menu_enable -> onAction(MangaAction.SOURCE_ENABLE)
                                R.id.menu_chapter_pay -> onAction(MangaAction.CHAPTER_PAY)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaBottomMenu(state: MangaReaderState, onAction: (MangaAction) -> Unit,
    onPanel: (MangaPanel) -> Unit, onProgress: (Int) -> Unit) {
    NgGlassSurface(Modifier.navigationBarsPadding().padding(8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        style = NgGlassDefaults.mangaPanelStyle(), role = NgMaterialRole.MANGA_PANEL) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val reversed = state.horizontal && state.rtl
            MangaChapterButton(if (reversed) "下一章" else "上一章",
                if (reversed) state.chapterIndex + 1 < state.chapterCount else state.chapterIndex > 0) {
                onAction(if (reversed) MangaAction.NEXT_CHAPTER else MangaAction.PREVIOUS_CHAPTER)
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp)) {
                // NgSlider is explicitly LTR; mirror just its physical track, not the saved image index.
                NgSlider(value = state.imageIndex.toFloat(), onValueChange = { onProgress(it.roundToInt()) },
                    valueRange = 0f..maxOf(1, state.imageCount - 1).toFloat(), enabled = state.imageCount > 1,
                    variant = NgSliderVariant.MANGA_PROGRESS,
                    modifier = Modifier.fillMaxHeight().then(if (reversed) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier))
                Text(if (state.imageCount == 0) "0 / 0" else "${state.imageIndex + 1} / ${state.imageCount}",
                    Modifier.align(Alignment.TopCenter).padding(top = 4.dp), fontSize = 11.sp, lineHeight = 14.sp,
                    color = Color(NgTheme.colors.onSurface))
            }
            MangaChapterButton(if (reversed) "上一章" else "下一章",
                if (reversed) state.chapterIndex > 0 else state.chapterIndex + 1 < state.chapterCount) {
                onAction(if (reversed) MangaAction.PREVIOUS_CHAPTER else MangaAction.NEXT_CHAPTER)
            }
        }
        MangaDivider(Modifier.padding(horizontal = 10.dp))
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            MangaTool(R.drawable.ic_manga_menu_catalog, "目录", Modifier.weight(1f)) { onAction(MangaAction.CATALOG) }
            MangaTool(R.drawable.ic_manga_menu_reading, "阅读", Modifier.weight(1f)) { onPanel(MangaPanel.READING) }
            MangaTool(R.drawable.ic_manga_menu_display, "显示", Modifier.weight(1f)) { onPanel(MangaPanel.DISPLAY) }
            MangaTool(R.drawable.ic_manga_menu_settings, "设置", Modifier.weight(1f)) { onPanel(MangaPanel.SETTINGS) }
        }
    }
}

@Composable
private fun MangaModule(state: MangaReaderState, onSetting: (MangaSetting, Int) -> Unit,
    onPanel: (MangaPanel) -> Unit, onAction: (MangaAction) -> Unit, footerStatus: MangaFooterStatus) {
    val panel = state.panel
    val title = when (panel) {
        MangaPanel.READING -> "阅读"
        MangaPanel.DISPLAY -> "显示"
        MangaPanel.FILTER -> "高级滤镜"
        MangaPanel.FOOTER -> "页脚"
        else -> "设置"
    }
    val parent = when (panel) {
        MangaPanel.FILTER -> MangaPanel.DISPLAY
        MangaPanel.FOOTER -> MangaPanel.DISPLAY
        else -> MangaPanel.NONE
    }
    NgGlassSurface(Modifier.fillMaxWidth().heightIn(max = 540.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = NgGlassDefaults.mangaPanelStyle(), role = NgMaterialRole.MANGA_PANEL) {
        Box(Modifier.fillMaxWidth().height(8.dp), Alignment.Center) {
            Box(Modifier.size(44.dp, 4.dp).background(Color(NgTheme.colors.onSurface).copy(alpha = 0.22f), CircleShape))
        }
        Row(Modifier.fillMaxWidth().height(48.dp)
            .padding(start = if (parent != MangaPanel.NONE) 0.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (parent != MangaPanel.NONE) MangaIcon(R.drawable.ic_arrow_back, "返回") { onPanel(parent) }
            Text(title, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                color = Color(NgTheme.colors.onSurface))
            if (panel == MangaPanel.READING) {
                Box(Modifier.height(48.dp).clickable { onAction(MangaAction.RESET_READING) }
                    .padding(horizontal = 12.dp), Alignment.Center) {
                    Text("恢复默认", fontSize = 14.sp, color = Color(NgTheme.colors.primary))
                }
            }
        }
        MangaDivider()
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            when (panel) {
                MangaPanel.READING -> {
                    MangaSwitch("本书独立设置", state.bookStyle,
                        summary = if (state.bookStyle) "方式、方向和效果仅用于本书" else "修改漫画默认的方式、方向和效果") {
                        onSetting(MangaSetting.BOOK_STYLE, it)
                    }
                    MangaDivider()
                    MangaSection("阅读方式")
                    MangaChoice(listOf("逐页", "连续"), state.reading.mode) {
                        onSetting(MangaSetting.MODE, it)
                    }
                    MangaDirectionSelection(state, onSetting)
                    if (state.paged) {
                        MangaSection("翻页效果")
                        ReadConfigDock(
                            labels = listOf("无动画", "滑动", "覆盖", "仿真"),
                            selectedIndex = state.reading.effect,
                            onSelected = { effect ->
                                if (state.reading.effect != effect) onSetting(MangaSetting.EFFECT, effect)
                            },
                        )
                    }
                    MangaDivider()
                    MangaSwitch(if (state.paged) "自动翻页" else "自动滚动", state.autoMode != MangaAutoMode.OFF) {
                        onSetting(MangaSetting.AUTO, it)
                    }
                    if (state.autoMode == MangaAutoMode.PAGE) MangaNumber("间隔", state.autoInterval,
                        "秒", 1, 9999) { onSetting(MangaSetting.INTERVAL, it) }
                    if (state.autoMode == MangaAutoMode.SCROLL) {
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("慢", fontSize = 13.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                            NgSlider(state.autoScrollLevel.toFloat(), { onSetting(MangaSetting.SPEED, it.roundToInt()) },
                                0f..8f, modifier = Modifier.weight(1f), steps = 7, variant = NgSliderVariant.COMPACT)
                            Text("快", fontSize = 13.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                        }
                        Text("${state.autoScrollInterval} 秒/屏", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                            fontSize = 12.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                    }
                }
                MangaPanel.DISPLAY -> {
                    MangaValueSlider("亮度", state.filter.l, if (state.filter.l == 0) "系统" else "${(state.filter.l / 255f * 100).roundToInt()}%") {
                        onSetting(MangaSetting.BRIGHTNESS, it)
                    }
                    MangaDivider()
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("图片色彩", Modifier.width(80.dp), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
                        Box(Modifier.weight(1f)) {
                            MangaChoice(listOf("原彩", "灰度", "墨水"), state.colorMode.ordinal) { onSetting(MangaSetting.COLOR, it) }
                        }
                    }
                    if (state.colorMode == MangaColorMode.INK) {
                        MangaValueSlider("黑白阈值", state.threshold) { onSetting(MangaSetting.THRESHOLD, it) }
                    }
                    MangaDivider()
                    MangaLink("高级滤镜") { onPanel(MangaPanel.FILTER) }
                    MangaLink("页脚") { onPanel(MangaPanel.FOOTER) }
                }
                MangaPanel.FILTER -> {
                    MangaValueSlider("红", state.filter.r) { onSetting(MangaSetting.RED, it) }
                    MangaValueSlider("绿", state.filter.g) { onSetting(MangaSetting.GREEN, it) }
                    MangaValueSlider("蓝", state.filter.b) { onSetting(MangaSetting.BLUE, it) }
                    MangaValueSlider("透明", state.filter.a) { onSetting(MangaSetting.ALPHA, it) }
                }
                MangaPanel.SETTINGS -> {
                    MangaSwitch("点击翻页", state.clickTurn) { onSetting(MangaSetting.CLICK, it) }
                    MangaSwitch("图片缩放", state.scaleEnabled) { onSetting(MangaSetting.SCALE, it) }
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.read_catalog_style), Modifier.width(104.dp), fontSize = 14.sp,
                            maxLines = 1, color = Color(NgTheme.colors.onSurface))
                        Box(Modifier.weight(1f)) {
                            MangaChoice(listOf(stringResource(R.string.read_catalog_style_card),
                                stringResource(R.string.read_catalog_style_compact_side),
                                stringResource(R.string.read_catalog_style_dense_list)), state.catalogStyle.ordinal) {
                                onSetting(MangaSetting.CATALOG_STYLE, it)
                            }
                        }
                    }
                    MangaDivider()
                    MangaSwitch("章节提示", state.chapterHints) { onSetting(MangaSetting.HINTS, it) }
                    MangaDivider()
                    MangaNumber("预加载图片", state.preload, "张", 0, 9999) { onSetting(MangaSetting.PRELOAD, it) }
                    MangaLink("原网页") { onAction(MangaAction.OPEN_WEB) }
                    MangaLink("网页打开方式") { onAction(MangaAction.WEB_MODE) }
                }
                MangaPanel.FOOTER -> {
                    MangaFooterSettings(state, onSetting, footerStatus)
                }
                else -> Unit
            }
        }
    }
}

private val mangaFooterOptions = listOf(
    ReadMoreConfigOption(MangaFooterContent.NONE.toString(), "无"),
    ReadMoreConfigOption(MangaFooterContent.BOOK_NAME.toString(), "书名"),
    ReadMoreConfigOption(MangaFooterContent.CHAPTER_NAME.toString(), "章节名"),
    ReadMoreConfigOption(MangaFooterContent.IMAGE_NUMBER.toString(), "图片序号"),
    ReadMoreConfigOption(MangaFooterContent.CHAPTER_NUMBER.toString(), "章节序号"),
    ReadMoreConfigOption(MangaFooterContent.PROGRESS.toString(), "阅读进度"),
    ReadMoreConfigOption(MangaFooterContent.TIME.toString(), "时间"),
    ReadMoreConfigOption(MangaFooterContent.BATTERY.toString(), "电量"),
    ReadMoreConfigOption(MangaFooterContent.TIME_BATTERY.toString(), "时间及电量"),
)

@Composable
private fun MangaFooterSettings(state: MangaReaderState, onSetting: (MangaSetting, Int) -> Unit,
    footerStatus: MangaFooterStatus) {
    var expandedPosition by remember { mutableStateOf<Int?>(null) }
    val positions = listOf("左侧", "中间", "右侧")
    val values = listOf(state.footer.left, state.footer.middle, state.footer.right)
    val settings = listOf(MangaSetting.FOOTER_LEFT, MangaSetting.FOOTER_MIDDLE, MangaSetting.FOOTER_RIGHT)
    MangaSwitch("显示页脚", !state.footer.hideFooter) { onSetting(MangaSetting.FOOTER, it) }
    if (state.footer.hideFooter) return
    MangaDivider()
    values.forEachIndexed { index, value ->
        Row(Modifier.fillMaxWidth().height(48.dp)
            .clickable { expandedPosition = index }, verticalAlignment = Alignment.CenterVertically) {
            Text(positions[index], Modifier.weight(1f), fontSize = 15.sp,
                color = Color(NgTheme.colors.onSurface))
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mangaFooterOptions.firstOrNull { it.value == value.toString() }?.label ?: "无",
                        fontSize = 13.sp, color = Color(NgTheme.colors.onSurfaceVariant))
                    Icon(painterResource(R.drawable.ic_manga_menu_chevron), null,
                        Modifier.padding(start = 6.dp).size(20.dp),
                        tint = Color(NgTheme.colors.onSurfaceVariant))
                }
                ReadChoicePopup(
                    expanded = expandedPosition == index,
                    options = mangaFooterOptions,
                    selectedValue = value.toString(),
                    onDismissRequest = { expandedPosition = null },
                    onSelected = { onSetting(settings[index], it.toInt()) },
                    scrollable = true,
                    style = NgGlassDefaults.mangaPanelStyle(),
                    role = NgMaterialRole.MANGA_PANEL,
                )
            }
        }
    }
    MangaDivider()
    Text("预览", Modifier.padding(top = 12.dp, bottom = 6.dp), fontSize = 12.sp,
        color = Color(NgTheme.colors.onSurfaceVariant))
    MangaInfoBar(state, Modifier.padding(bottom = 4.dp)
        .semantics { contentDescription = "页脚预览" }, footerStatus)
}

@Composable
private fun MangaIcon(icon: Int, title: String, glyphSize: Dp = 24.dp, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClick), Alignment.Center) {
        Icon(painterResource(icon), title, Modifier.size(glyphSize), tint = Color(NgTheme.colors.onSurface))
    }
}

@Composable
private fun MangaTool(icon: Int, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().clickable(onClick = onClick).padding(bottom = 6.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), title, Modifier.size(20.dp), tint = Color(NgTheme.colors.onSurface))
        Text(title, Modifier.padding(top = 3.dp), fontSize = 13.sp, lineHeight = 18.sp, color = Color(NgTheme.colors.onSurface))
    }
}

@Composable
private fun MangaChapterButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(48.dp).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Text(title, fontSize = 14.sp, color = Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else 0.4f))
    }
}

@Composable
private fun MangaSection(title: String) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 8.dp), fontSize = 15.sp,
        color = Color(NgTheme.colors.onSurface), fontWeight = FontWeight.Medium)
}

@Composable
private fun MangaDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.35f), thickness = 0.5.dp)
}

@Composable
private fun MangaChoice(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    NgFloatingTabBar(
        items = labels.map { NgFloatingTabSpec(text = it) },
        selectedIndex = selected,
        onTabSelected = onSelected,
        variant = NgFloatingTabBarVariant.MANGA_SETTINGS,
        size = NgFloatingTabBarSize.COMPACT,
    )
}

@Composable
private fun MangaSwitch(title: String, value: Boolean, summary: String? = null, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(if (summary == null) 48.dp else 64.dp)
        .clickable { onChange(if (value) 0 else 1) }, verticalAlignment = Alignment.CenterVertically) {
        if (summary == null) {
            Text(title, Modifier.weight(1f), fontSize = 15.sp, color = Color(NgTheme.colors.onSurface))
        } else {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, fontSize = 15.sp, color = Color(NgTheme.colors.onSurface))
                Text(summary, Modifier.padding(top = 3.dp), fontSize = 12.sp,
                    lineHeight = 17.sp, color = Color(NgTheme.colors.onSurfaceVariant))
            }
        }
        NgSwitchControl(value, { onChange(if (it) 1 else 0) })
    }
}

@Composable
private fun MangaDirectionSelection(state: MangaReaderState, onSetting: (MangaSetting, Int) -> Unit) {
    var expanded by remember(state.bookUrl, state.loading) { mutableStateOf(false) }
    val options = listOf(
        ReadMoreConfigOption("ltr", "横向 · 左→右"),
        ReadMoreConfigOption("rtl", "横向 · 右→左"),
        ReadMoreConfigOption("vertical", "纵向"),
    )
    val selected = when {
        !state.horizontal -> "vertical"
        state.rtl -> "rtl"
        else -> "ltr"
    }
    Row(Modifier.fillMaxWidth().height(48.dp).clickable(enabled = !state.loading) { expanded = true },
        verticalAlignment = Alignment.CenterVertically) {
        Text("阅读方向", Modifier.weight(1f), fontSize = 15.sp, color = Color(NgTheme.colors.onSurface))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(options.first { it.value == selected }.label, fontSize = 13.sp,
                    color = Color(NgTheme.colors.onSurfaceVariant))
                Icon(painterResource(R.drawable.ic_manga_menu_chevron), null, Modifier.padding(start = 6.dp).size(20.dp),
                    tint = Color(NgTheme.colors.onSurfaceVariant))
            }
            ReadChoicePopup(
                expanded = expanded,
                options = options,
                selectedValue = selected,
                onDismissRequest = { expanded = false },
                onSelected = { value ->
                    val layout = if (value == "vertical") MangaReadingStyle.VERTICAL else MangaReadingStyle.HORIZONTAL
                    if (state.reading.layout != layout) onSetting(MangaSetting.LAYOUT, layout)
                    if (value != "vertical" && state.rtl != (value == "rtl")) {
                        onSetting(MangaSetting.DIRECTION, if (value == "rtl") 1 else 0)
                    }
                },
                style = NgGlassDefaults.mangaPanelStyle(),
                role = NgMaterialRole.MANGA_PANEL,
            )
        }
    }
}

@Composable
private fun MangaLink(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp, color = Color(NgTheme.colors.onSurface))
        Text("›", fontSize = 26.sp, color = Color(NgTheme.colors.onSurface))
    }
}

@Composable
private fun MangaValueSlider(title: String, value: Int, label: String = value.toString(), onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.width(80.dp), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
        NgSlider(value.toFloat(), { onChange(it.roundToInt()) }, 0f..255f,
            modifier = Modifier.weight(1f), variant = NgSliderVariant.COMPACT)
        Text(label, Modifier.width(46.dp), textAlign = TextAlign.End, fontSize = 13.sp, color = Color(NgTheme.colors.onSurface))
    }
}

@Composable
private fun MangaNumber(title: String, value: Int, unit: String, min: Int, max: Int, onChange: (Int) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf(value.toString()) }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
        listOf(-1, 0, 1).forEach { step ->
            if (step == 0) Text("$value $unit".trim(), Modifier.widthIn(min = 56.dp).height(48.dp)
                .wrapContentHeight().clickable { input = value.toString(); editing = true },
                textAlign = TextAlign.Center, fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
            else Box(Modifier.size(48.dp)
                .clickable(enabled = if (step < 0) value > min else value < max) { onChange((value + step).coerceIn(min, max)) },
                Alignment.Center) {
                Box(Modifier.size(28.dp).border(1.dp, Color(NgTheme.colors.outlineVariant), CircleShape))
                Text(if (step < 0) "−" else "+", fontSize = 22.sp, color = Color(NgTheme.colors.onSurface))
            }
        }
    }
    if (editing) Dialog(onDismissRequest = { editing = false }) {
        val number = input.toIntOrNull()?.takeIf { it in min..max }
        NgDialog(title, variant = NgDialogVariant.FORM_EDITOR, actions = {
            NgDialogTextActionButton("取消", { editing = false }, secondary = true)
            NgDialogTextActionButton("确定", { number?.let(onChange); editing = false }, enabled = number != null)
        }) {
            NgFormField(label = "", value = input, onValueChange = { input = it.filter(Char::isDigit).take(4) },
                variant = NgFormFieldVariant.DIALOG_UNDERLINE, autoFocus = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
}
