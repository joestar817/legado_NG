package io.legado.app.ui.book.read

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.config.ReadThemeMode
import io.legado.app.help.config.ReadFloatingTool
import io.legado.app.ui.book.read.config.popupVisibleBoundsInWindow
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal enum class ReadFloatingToolExpansion {
    BRIGHTNESS,
    THEME,
    AI,
    PAGE_ANIM,
    CHINESE,
}

internal enum class ReadFloatingToolDock {
    LEFT,
    RIGHT;

    val isRight: Boolean
        get() = this == RIGHT

    fun toggled(): ReadFloatingToolDock = if (this == LEFT) RIGHT else LEFT

    companion object {
        fun fromStoredRight(storedRight: Boolean): ReadFloatingToolDock {
            return if (storedRight) RIGHT else LEFT
        }
    }
}

@Composable
internal fun ReadFloatingToolRail(
    dockSide: ReadFloatingToolDock,
    tools: List<ReadFloatingTool> = ReadFloatingTool.entries.filter { it.enabledByDefault },
    expansion: ReadFloatingToolExpansion?,
    brightness: Int,
    brightnessAutomatic: Boolean,
    nightMode: Boolean,
    themeMode: ReadThemeMode,
    autoPage: Boolean,
    pageAnim: Int,
    chineseMode: Int,
    onExpansionChange: (ReadFloatingToolExpansion?) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onToggleBrightnessAutomatic: () -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onThemeModeSelected: (ReadThemeMode) -> Unit,
    onAiPurify: () -> Unit,
    onAiSettings: () -> Unit,
    onReadAloud: () -> Unit,
    onAutoPage: () -> Unit,
    onPageAnimSelected: (Int) -> Unit,
    onChineseModeSelected: (Int) -> Unit,
    onToggleDockSide: () -> Unit
) {
    ToolRail(
        dockRight = dockSide.isRight,
        tools = tools,
        expansion = expansion,
        nightMode = nightMode,
        autoPage = autoPage,
        onExpansionChange = onExpansionChange,
        onSearch = onSearch,
        onReplace = onReplace,
        onReadAloud = onReadAloud,
        onAutoPage = onAutoPage,
        onToggleDockSide = onToggleDockSide,
        panelContent = { panel ->
            when (panel) {
                ReadFloatingToolExpansion.BRIGHTNESS -> BrightnessPanel(
                    brightness = brightness,
                    automatic = brightnessAutomatic,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessChangeFinished = onBrightnessChangeFinished,
                    onToggleAutomatic = onToggleBrightnessAutomatic,
                )
                ReadFloatingToolExpansion.THEME -> ThemeModePanel(
                    selectedMode = themeMode,
                    onModeSelected = onThemeModeSelected,
                )
                ReadFloatingToolExpansion.AI -> AiPanel(
                    onAiPurify = onAiPurify,
                    onAiSettings = onAiSettings,
                )
                ReadFloatingToolExpansion.PAGE_ANIM -> ChoicePanel(
                    labels = listOf(
                        stringResource(R.string.page_anim_cover),
                        stringResource(R.string.page_anim_slide),
                        stringResource(R.string.page_anim_simulation),
                        stringResource(R.string.page_anim_scroll),
                        stringResource(R.string.page_anim_none),
                    ),
                    selectedIndex = pageAnim,
                    onSelected = onPageAnimSelected,
                )
                ReadFloatingToolExpansion.CHINESE -> ChoicePanel(
                    labels = stringArrayResource(R.array.chinese_mode_short).toList(),
                    selectedIndex = chineseMode,
                    onSelected = onChineseModeSelected,
                )
            }
        },
    )
}

@Composable
private fun ToolRail(
    dockRight: Boolean,
    tools: List<ReadFloatingTool>,
    expansion: ReadFloatingToolExpansion?,
    nightMode: Boolean,
    autoPage: Boolean,
    onExpansionChange: (ReadFloatingToolExpansion?) -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onReadAloud: () -> Unit,
    onAutoPage: () -> Unit,
    onToggleDockSide: () -> Unit,
    panelContent: @Composable (ReadFloatingToolExpansion) -> Unit,
) {
    NgGlassSurface(
        modifier = Modifier.width(44.dp),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(vertical = FloatingToolRailVerticalPadding)
    ) {
      Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * FloatingToolMaxScreenFraction)
          .verticalScroll(rememberScrollState())) {
        tools.forEach { tool ->
            key(tool.key) {
                when (tool) {
                    ReadFloatingTool.BRIGHTNESS -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        selected = expansion == ReadFloatingToolExpansion.BRIGHTNESS,
                        expanded = expansion == ReadFloatingToolExpansion.BRIGHTNESS,
                        dockRight = dockRight,
                        onDismiss = { onExpansionChange(null) },
                        onClick = { onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.BRIGHTNESS)) },
                        popupContent = { panelContent(ReadFloatingToolExpansion.BRIGHTNESS) },
                    )
                    ReadFloatingTool.SEARCH -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        iconSize = 20.dp,
                        onClick = onSearch,
                    )
                    ReadFloatingTool.REPLACE -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        onClick = onReplace,
                    )
                    ReadFloatingTool.THEME -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        selected = nightMode || expansion == ReadFloatingToolExpansion.THEME,
                        expanded = expansion == ReadFloatingToolExpansion.THEME,
                        dockRight = dockRight,
                        onDismiss = { onExpansionChange(null) },
                        onClick = { onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.THEME)) },
                        popupContent = { panelContent(ReadFloatingToolExpansion.THEME) },
                    )
                    ReadFloatingTool.AI -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        iconSize = tool.iconSizeDp.dp,
                        selected = expansion == ReadFloatingToolExpansion.AI,
                        expanded = expansion == ReadFloatingToolExpansion.AI,
                        dockRight = dockRight,
                        onDismiss = { onExpansionChange(null) },
                        onClick = { onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.AI)) },
                        popupContent = { panelContent(ReadFloatingToolExpansion.AI) },
                    )
                    ReadFloatingTool.READ_ALOUD -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        iconSize = tool.iconSizeDp.dp,
                        onClick = onReadAloud,
                    )
                    ReadFloatingTool.AUTO_SCROLL -> ToolButton(
                        iconRes = if (autoPage) R.drawable.ic_auto_page_stop else tool.iconRes,
                        labelRes = if (autoPage) R.string.auto_next_page_stop else tool.railDescriptionRes,
                        iconSize = tool.iconSizeDp.dp,
                        selected = autoPage,
                        onClick = onAutoPage,
                    )
                    ReadFloatingTool.PAGE_ANIM -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        iconSize = tool.iconSizeDp.dp,
                        selected = expansion == ReadFloatingToolExpansion.PAGE_ANIM,
                        expanded = expansion == ReadFloatingToolExpansion.PAGE_ANIM,
                        dockRight = dockRight,
                        onDismiss = { onExpansionChange(null) },
                        onClick = { onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.PAGE_ANIM)) },
                        popupContent = { panelContent(ReadFloatingToolExpansion.PAGE_ANIM) },
                    )
                    ReadFloatingTool.CHINESE -> ToolButton(
                        iconRes = tool.iconRes,
                        labelRes = tool.railDescriptionRes,
                        selected = expansion == ReadFloatingToolExpansion.CHINESE,
                        expanded = expansion == ReadFloatingToolExpansion.CHINESE,
                        dockRight = dockRight,
                        onDismiss = { onExpansionChange(null) },
                        onClick = { onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.CHINESE)) },
                        popupContent = { panelContent(ReadFloatingToolExpansion.CHINESE) },
                    )
                }
            }
        }
        if (tools.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = FloatingToolDividerVerticalPadding),
                thickness = FloatingToolDividerThickness,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.45f),
            )
        }
        ToolButton(
            iconRes = R.drawable.ic_read_side_switch,
            labelRes = R.string.adjust_pos,
            onClick = onToggleDockSide
        )
      }
    }
}

@Composable
private fun ToolButton(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    iconSize: Dp = 22.dp,
    selected: Boolean = false,
    expanded: Boolean = false,
    dockRight: Boolean = false,
    onDismiss: () -> Unit = {},
    popupContent: (@Composable () -> Unit)? = null,
) {
    val contentColor = if (selected) {
        Color(NgTheme.colors.primary)
    } else {
        Color(NgTheme.colors.onSurface)
    }
    val containerColor = if (selected) {
        Color(NgTheme.colors.selectedContainer).copy(alpha = 0.78f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(FloatingToolButtonHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(labelRes),
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
        }
        if (popupContent != null) {
            ToolExpansionPopup(
                expanded = expanded,
                dockRight = dockRight,
                onDismiss = onDismiss,
                content = popupContent,
            )
        }
    }
}

@Composable
private fun ToolExpansionPopup(
    expanded: Boolean,
    dockRight: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val anchorView = LocalView.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(dockRight, gapPx, marginPx, anchorView) {
        ReadFloatingToolPopupPositionProvider(dockRight, gapPx, marginPx) {
            popupVisibleBoundsInWindow(anchorView)
        }
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (visibility.currentState || visibility.targetState || !visibility.isIdle) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = expandHorizontally(
                    expandFrom = if (dockRight) Alignment.End else Alignment.Start,
                ),
                exit = shrinkHorizontally(
                    shrinkTowards = if (dockRight) Alignment.End else Alignment.Start,
                ),
                content = { content() },
            )
        }
    }
}

/** Positions each panel from its own tool button, independent of the rail's ordering. */
internal class ReadFloatingToolPopupPositionProvider(
    private val dockRight: Boolean,
    private val gapPx: Int,
    private val marginPx: Int,
    private val visibleBoundsProvider: (() -> IntRect?)? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val bounds = visibleBoundsProvider?.invoke()?.takeIf { it.width > 0 && it.height > 0 }
            ?: IntRect(0, 0, windowSize.width, windowSize.height)
        val desiredX = if (dockRight) {
            anchorBounds.left - gapPx - popupContentSize.width
        } else {
            anchorBounds.right + gapPx
        }
        val minX = bounds.left + marginPx
        val maxX = (bounds.right - marginPx - popupContentSize.width).coerceAtLeast(minX)
        val minY = bounds.top + marginPx
        val maxY = (bounds.bottom - marginPx - popupContentSize.height).coerceAtLeast(minY)
        val desiredY = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        return IntOffset(desiredX.coerceIn(minX, maxX), desiredY.coerceIn(minY, maxY))
    }
}

@Composable
private fun ChoicePanel(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    val config = LocalConfiguration.current
    val width = minOf(272.dp, (config.screenWidthDp.dp - 76.dp).coerceAtLeast(120.dp))
    val columns = if (labels.size > 3 &&
        (config.screenWidthDp < 360 || config.fontScale > 1.1f || labels.any { it.length > 7 })) 3 else labels.size
    NgGlassSurface(
        modifier = Modifier.width(width),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        labels.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * columns + columnIndex
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color(NgTheme.colors.selectedContainer).copy(alpha = 0.88f) else Color.Transparent)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelected(index) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeModePanel(
    selectedMode: ReadThemeMode,
    onModeSelected: (ReadThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        Triple(
            ReadThemeMode.FOLLOW_SYSTEM,
            R.string.theme_mode_follow_short,
            R.drawable.ic_brightness_auto,
        ),
        Triple(
            ReadThemeMode.DAY,
            R.string.theme_mode_day_short,
            R.drawable.ic_daytime,
        ),
        Triple(
            ReadThemeMode.NIGHT,
            R.string.theme_mode_night_short,
            R.drawable.ic_brightness,
        ),
    )
    NgGlassSurface(
        modifier = modifier.width(272.dp),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEach { (mode, labelRes, iconRes) ->
                val selected = mode == selectedMode
                val contentColor = Color(
                    if (selected) {
                        NgTheme.colors.primary
                    } else {
                        NgTheme.colors.onSurface
                    }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) {
                                Color(NgTheme.colors.selectedContainer).copy(alpha = 0.88f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable(
                            role = Role.RadioButton,
                            onClick = { onModeSelected(mode) },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = contentColor,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(labelRes),
                            color = contentColor,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (selected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrightnessPanel(
    brightness: Int,
    automatic: Boolean,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onToggleAutomatic: () -> Unit
) {
    val value = brightness.coerceIn(0, 255)
    NgGlassSurface(
        modifier = Modifier.width(272.dp),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_daytime),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(NgTheme.colors.onSurface)
            )
            NgSlider(
                value = value.toFloat(),
                onValueChange = { onBrightnessChange(it.roundToInt()) },
                valueRange = 0f..255f,
                variant = NgSliderVariant.COMPACT,
                enabled = !automatic,
                onValueChangeFinished = onBrightnessChangeFinished,
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            )
            Text(
                text = "${(value / 255f * 100).roundToInt()}%",
                modifier = Modifier.width(34.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (automatic) {
                            Color(NgTheme.colors.selectedContainer).copy(alpha = 0.88f)
                        } else {
                            Color(NgTheme.colors.surfaceVariant).copy(alpha = 0.52f)
                        }
                    )
                    .clickable(onClick = onToggleAutomatic),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_brightness_auto),
                    contentDescription = stringResource(R.string.brightness_auto),
                    modifier = Modifier.size(20.dp),
                    tint = Color(
                        if (automatic) {
                            NgTheme.colors.primary
                        } else {
                            NgTheme.colors.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun AiPanel(
    onAiPurify: () -> Unit,
    onAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NgGlassSurface(
        modifier = modifier.width(144.dp),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        AiActionRow(
            iconRes = R.drawable.ic_ai_purify,
            labelRes = R.string.ai_purify_chapter,
            onClick = onAiPurify
        )
        AiActionRow(
            iconRes = R.drawable.ic_settings,
            labelRes = R.string.ai_purify_settings,
            onClick = onAiSettings
        )
    }
}

@Composable
private fun AiActionRow(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(144.dp)
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(labelRes),
            color = Color(NgTheme.colors.onSurface),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun ReadFloatingToolExpansion?.toggle(
    requested: ReadFloatingToolExpansion
): ReadFloatingToolExpansion? = if (this == requested) null else requested
