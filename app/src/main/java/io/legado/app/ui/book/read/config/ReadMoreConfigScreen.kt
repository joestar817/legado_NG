package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import io.legado.app.constant.PreferKey
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal const val READ_MORE_CONFIG_WINDOW_HEIGHT_DP = 540

internal object ReadMoreConfigKeys {
    const val CLICK_REGIONAL_CONFIG = "clickRegionalConfig"
    const val CUSTOM_PAGE_KEY = "customPageKey"
    const val DISABLE_RETURN_KEY = "disableReturnKey"
    const val BOOK_IMAGE_STYLE = "bookImageStyle"
    const val TEXT_TOOLBAR = "textToolbar"
}

internal enum class ReadMoreConfigTab {
    INTERFACE,
    PAGE,
    CONTENT,
}

internal data class ReadMoreConfigOption(
    val value: String,
    val label: String,
)

internal data class ReadMoreConfigUiState(
    val booleans: Map<String, Boolean>,
    val values: Map<String, String>,
    val options: Map<String, List<ReadMoreConfigOption>>,
    val actionValues: Map<String, String>,
    val optimizeRenderSupported: Boolean,
) {
    fun boolean(key: String): Boolean = booleans[key] == true

    fun value(key: String): String = values[key].orEmpty()

    fun options(key: String): List<ReadMoreConfigOption> = options[key].orEmpty()

    fun actionValue(key: String): String? = actionValues[key]
}

internal data class ReadMoreConfigActions(
    val onTabSelected: (ReadMoreConfigTab) -> Unit,
    val onBooleanChanged: (String, Boolean) -> Unit,
    val onValueChanged: (String, String) -> Unit,
    val onAction: (String) -> Unit,
)

@Composable
internal fun ReadMoreConfigScreen(
    tab: ReadMoreConfigTab,
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val accentColor = Color(NgTheme.colors.primary)
    val selectedContentColor = if (accentColor.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }

    NgGlassSurface(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxSize(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.read_more_settings_title),
                modifier = Modifier
                    .height(42.dp)
                    .padding(horizontal = 16.dp),
                color = contentColor,
                fontSize = 20.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
            )
            ReadMoreConfigDock(
                selectedTab = tab,
                contentColor = contentColor,
                selectedContainerColor = accentColor,
                selectedContentColor = selectedContentColor,
                onSelected = actions.onTabSelected,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (tab) {
                    ReadMoreConfigTab.INTERFACE -> InterfaceSettingsPage(
                        state = state,
                        actions = actions,
                        contentColor = contentColor,
                    )

                    ReadMoreConfigTab.PAGE -> PageSettingsPage(
                        state = state,
                        actions = actions,
                        contentColor = contentColor,
                    )

                    ReadMoreConfigTab.CONTENT -> ContentSettingsPage(
                        state = state,
                        actions = actions,
                        contentColor = contentColor,
                        accentColor = accentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceSettingsPage(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
    contentColor: Color,
) {
    SettingsColumn {
        ChoiceSettingRow(
            title = stringResource(R.string.read_catalog_style),
            selectedValue = state.value(PreferKey.readCatalogStyle),
            options = state.options(PreferKey.readCatalogStyle),
            onSelected = { actions.onValueChanged(PreferKey.readCatalogStyle, it) },
        )
        ReadMoreDivider(contentColor)
        ChoiceSettingRow(
            title = stringResource(R.string.screen_direction),
            selectedValue = state.value(PreferKey.screenOrientation),
            options = state.options(PreferKey.screenOrientation),
            onSelected = { actions.onValueChanged(PreferKey.screenOrientation, it) },
        )
        ReadMoreDivider(contentColor)
        ChoiceSettingRow(
            title = stringResource(R.string.keep_light),
            selectedValue = state.value(PreferKey.keepLight),
            options = state.options(PreferKey.keepLight),
            onSelected = { actions.onValueChanged(PreferKey.keepLight, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.pt_hide_status_bar),
            checked = state.boolean(PreferKey.hideStatusBar),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.hideStatusBar, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.read_body_to_lh),
            checked = state.boolean(PreferKey.readBodyToLh),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.readBodyToLh, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.read_content_avoid_cutout),
            checked = state.boolean(PreferKey.paddingDisplayCutouts),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.paddingDisplayCutouts, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.show_floating_toolbar),
            checked = state.boolean(PreferKey.showBrightnessView),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.showBrightnessView, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.show_read_title_addition),
            checked = state.boolean(PreferKey.showReadTitleAddition),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.showReadTitleAddition, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.read_bar_style_follow_page),
            checked = state.boolean(PreferKey.readBarStyleFollowPage),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.readBarStyleFollowPage, it) },
        )
        ReadMoreDivider(contentColor)
        ActionSettingRow(
            title = stringResource(R.string.text_selection_action_order),
            onClick = { actions.onAction(ReadMoreConfigKeys.TEXT_TOOLBAR) },
        )
        ReadMoreDivider(contentColor)
        ChoiceSettingRow(
            title = stringResource(R.string.progress_bar_behavior),
            selectedValue = state.value(PreferKey.progressBarBehavior),
            options = state.options(PreferKey.progressBarBehavior),
            onSelected = { actions.onValueChanged(PreferKey.progressBarBehavior, it) },
        )
    }
}

@Composable
private fun PageSettingsPage(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
    contentColor: Color,
) {
    SettingsColumn {
        ChoiceSettingRow(
            title = stringResource(R.string.double_page_horizontal),
            selectedValue = state.value(PreferKey.doublePageHorizontal),
            options = state.options(PreferKey.doublePageHorizontal),
            onSelected = { actions.onValueChanged(PreferKey.doublePageHorizontal, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.mouse_wheel_page),
            checked = state.boolean(PreferKey.mouseWheelPage),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.mouseWheelPage, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.volume_key_page),
            checked = state.boolean(PreferKey.volumeKeyPage),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.volumeKeyPage, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.volume_key_page_on_play),
            checked = state.boolean(PreferKey.volumeKeyPageOnPlay),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.volumeKeyPageOnPlay, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.key_page_on_long_press),
            checked = state.boolean(PreferKey.keyPageOnLongPress),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.keyPageOnLongPress, it) },
        )
        ReadMoreDivider(contentColor)
        ActionSettingRow(
            title = stringResource(R.string.page_touch_slop_title),
            value = state.actionValue(PreferKey.pageTouchSlop),
            onClick = { actions.onAction(PreferKey.pageTouchSlop) },
        )
        ReadMoreDivider(contentColor)
        ActionSettingRow(
            title = stringResource(R.string.page_touch_click_title),
            value = state.actionValue(PreferKey.pageTouchClick),
            onClick = { actions.onAction(PreferKey.pageTouchClick) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.no_anim_scroll_page),
            checked = state.boolean(PreferKey.noAnimScrollPage),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.noAnimScrollPage, it) },
        )
        ReadMoreDivider(contentColor)
        ActionSettingRow(
            title = stringResource(R.string.click_regional_config),
            onClick = { actions.onAction(ReadMoreConfigKeys.CLICK_REGIONAL_CONFIG) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.disable_return_key),
            checked = state.boolean(ReadMoreConfigKeys.DISABLE_RETURN_KEY),
            onCheckedChange = {
                actions.onBooleanChanged(ReadMoreConfigKeys.DISABLE_RETURN_KEY, it)
            },
        )
        ReadMoreDivider(contentColor)
        ActionSettingRow(
            title = stringResource(R.string.custom_page_key),
            onClick = { actions.onAction(ReadMoreConfigKeys.CUSTOM_PAGE_KEY) },
        )
    }
}

@Composable
private fun ContentSettingsPage(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
    contentColor: Color,
    accentColor: Color,
) {
    SettingsColumn {
        ReadMoreSectionLabel(
            text = stringResource(R.string.read_settings_current_book),
            color = Color(NgTheme.colors.secondary),
        )
        ChoiceSettingRow(
            title = stringResource(R.string.image_style),
            selectedValue = state.value(ReadMoreConfigKeys.BOOK_IMAGE_STYLE),
            options = state.options(ReadMoreConfigKeys.BOOK_IMAGE_STYLE),
            preferPopupBelow = true,
            onSelected = {
                actions.onValueChanged(ReadMoreConfigKeys.BOOK_IMAGE_STYLE, it)
            },
        )
        ReadMoreDivider(contentColor)
        ReadMoreSectionLabel(
            text = stringResource(R.string.read_settings_all_books),
            color = Color(NgTheme.colors.secondary),
        )
        SwitchSettingRow(
            title = stringResource(R.string.use_zh_layout),
            checked = state.boolean(PreferKey.useZhLayout),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.useZhLayout, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.text_full_justify),
            checked = state.boolean(PreferKey.textFullJustify),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.textFullJustify, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.text_bottom_justify),
            checked = state.boolean(PreferKey.textBottomJustify),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.textBottomJustify, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.adapt_special_style),
            checked = state.boolean(PreferKey.adaptSpecialStyle),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.adaptSpecialStyle, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.auto_change_source),
            checked = state.boolean(PreferKey.autoChangeSource),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.autoChangeSource, it) },
        )
        ReadMoreDivider(contentColor)
        SwitchSettingRow(
            title = stringResource(R.string.selectText),
            checked = state.boolean(PreferKey.textSelectAble),
            onCheckedChange = { actions.onBooleanChanged(PreferKey.textSelectAble, it) },
        )
        ReadMoreDivider(contentColor)
        ChoiceSettingRow(
            title = stringResource(R.string.click_image_way),
            selectedValue = state.value(PreferKey.clickImgWay),
            options = state.options(PreferKey.clickImgWay),
            onSelected = { actions.onValueChanged(PreferKey.clickImgWay, it) },
        )
        if (state.optimizeRenderSupported) {
            ReadMoreDivider(contentColor)
            SwitchSettingRow(
                title = stringResource(R.string.enable_optimize_render),
                checked = state.boolean(PreferKey.optimizeRender),
                onCheckedChange = { actions.onBooleanChanged(PreferKey.optimizeRender, it) },
            )
        }
    }
}

@Composable
private fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
private fun ReadMoreSectionLabel(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Switch) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}

@Composable
private fun ChoiceSettingRow(
    title: String,
    selectedValue: String,
    options: List<ReadMoreConfigOption>,
    onSelected: (String) -> Unit,
    preferPopupBelow: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    val contentColor = Color(NgTheme.colors.onSurface)
    val density = LocalDensity.current
    val gapPx = with(density) { 6.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val popupPositionProvider = remember(gapPx, marginPx, preferPopupBelow) {
        EndAnchoredPopupPositionProvider(
            gapPx = gapPx,
            marginPx = marginPx,
            preferBelow = preferPopupBelow,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = true },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    color = contentColor.copy(alpha = 0.68f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
            if (expanded) {
                Popup(
                    popupPositionProvider = popupPositionProvider,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    NgGlassSurface(
                        modifier = Modifier.width(156.dp),
                        shape = RoundedCornerShape(12.dp),
                        style = readFloatingGlassStyle(),
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                modifier = Modifier
                                    .height(42.dp)
                                    .semantics {
                                        selected = option.value == selectedValue
                                    },
                                text = {
                                    Text(
                                        text = option.label,
                                        color = if (option.value == selectedValue) {
                                            Color(NgTheme.colors.secondary)
                                        } else {
                                            contentColor
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = if (option.value == selectedValue) {
                                            FontWeight.Medium
                                        } else {
                                            FontWeight.Normal
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onSelected(option.value)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private class EndAnchoredPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val preferBelow: Boolean,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val desiredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val maxX = (windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val x = desiredX.coerceIn(marginPx, maxX)
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx
        val belowY = anchorBounds.bottom + gapPx
        val maxY = (windowSize.height - popupContentSize.height - marginPx)
            .coerceAtLeast(marginPx)
        val y = if (preferBelow) {
            if (belowY <= maxY) belowY else aboveY.coerceAtLeast(marginPx)
        } else {
            if (aboveY >= marginPx) aboveY else belowY.coerceAtMost(maxY)
        }
        return IntOffset(x, y)
    }
}

@Composable
internal fun ReadThresholdSliderDialog(
    title: String,
    initialValue: Int,
    maxValue: Int,
    valueLabel: (Int) -> String,
    onDismiss: () -> Unit,
    onValueChanged: (Int) -> Unit,
) {
    var sliderValue by remember(initialValue, maxValue) {
        mutableFloatStateOf(initialValue.coerceIn(0, maxValue).toFloat())
    }
    val value = sliderValue.roundToInt().coerceIn(0, maxValue)
    val contentColor = Color(NgTheme.colors.onSurface)
    NgGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = contentColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_close),
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(22.dp),
                        tint = contentColor.copy(alpha = 0.72f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valueLabel(value),
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.secondary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            NgSlider(
                value = value.toFloat(),
                onValueChange = {
                    sliderValue = it
                    onValueChanged(it.roundToInt().coerceIn(0, maxValue))
                },
                valueRange = 0f..maxValue.toFloat(),
                steps = (maxValue - 1).coerceAtLeast(0),
                variant = NgSliderVariant.CONTINUOUS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ActionSettingRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    ValueSettingRow(
        title = title,
        value = value,
        onClick = onClick,
    )
}

@Composable
private fun ValueSettingRow(
    title: String,
    value: String?,
    onClick: () -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = contentColor.copy(alpha = 0.68f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = contentColor.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ReadMoreConfigDock(
    selectedTab: ReadMoreConfigTab,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    onSelected: (ReadMoreConfigTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        ReadMoreConfigTab.INTERFACE to stringResource(R.string.read_settings_tab_interface),
        ReadMoreConfigTab.PAGE to stringResource(R.string.read_settings_tab_page),
        ReadMoreConfigTab.CONTENT to stringResource(R.string.read_settings_tab_content),
    )
    val shape = RoundedCornerShape(12.dp)
    val dockSurfaceColor = ReadDrawerStyle.dockSurfaceColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(dockSurfaceColor)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (tab, label) ->
            val selected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(selectedContainerColor) else Modifier
                    )
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable(role = Role.Tab) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReadMoreDivider(contentColor: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(contentColor.copy(alpha = 0.12f)),
    )
}
