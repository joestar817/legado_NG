package io.legado.app.ui.book.read.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal const val PAGE_TOUCH_SLOP_MAX = 9999
internal const val PAGE_TOUCH_CLICK_MAX = 399

internal object ReadMoreConfigKeys {
    const val CLICK_REGIONAL_CONFIG = "clickRegionalConfig"
    const val BOOK_IMAGE_STYLE = "bookImageStyle"
}

internal data class ReadMoreConfigOption(
    val value: String,
    val label: String,
)

internal data class ReadMoreConfigUiState(
    val booleans: Map<String, Boolean>,
    val values: Map<String, String>,
    val options: Map<String, List<ReadMoreConfigOption>>,
    val thresholdValues: Map<String, Int>,
    val pageAnim: Int,
) {
    fun boolean(key: String): Boolean = booleans[key] == true

    fun value(key: String): String = values[key].orEmpty()

    fun options(key: String): List<ReadMoreConfigOption> = options[key].orEmpty()

    fun thresholdValue(key: String): Int = thresholdValues[key] ?: 0
}

internal data class ReadMoreConfigActions(
    val onBooleanChanged: (String, Boolean) -> Unit,
    val onValueChanged: (String, String) -> Unit,
    val onAction: (String) -> Unit,
    val onPageAnimChanged: (Int) -> Unit,
    val onThresholdChanged: (String, Int) -> Unit,
    val onPageKeysChanged: (String, String) -> Unit,
    val pageKeyCapture: PageKeyCaptureController,
)

@Composable
internal fun ReadMoreConfigScreen(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp

    NgGlassSurface(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.read_more_settings_title),
                centerTitle = true,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 8.dp),
            ) {
                InterfaceSettingsGroup(state, actions)
                Spacer(Modifier.height(12.dp))
                ContentSettingsGroups(state, actions)
            }
        }
    }
}

@Composable
private fun InterfaceSettingsGroup(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
) {
    NgFormGroup(stringResource(R.string.read_settings_tab_interface)) {
        MoreConfigRow {
            ReadMoreChoiceSettingRow(
                title = stringResource(R.string.screen_direction),
                selectedValue = state.value(PreferKey.screenOrientation),
                options = state.options(PreferKey.screenOrientation),
                onSelected = { actions.onValueChanged(PreferKey.screenOrientation, it) },
            )
        }
        NgFormGroupDivider()
        MoreConfigRow {
            ReadMoreChoiceSettingRow(
                title = stringResource(R.string.keep_light),
                selectedValue = state.value(PreferKey.keepLight),
                options = state.options(PreferKey.keepLight),
                onSelected = { actions.onValueChanged(PreferKey.keepLight, it) },
            )
        }
        NgFormGroupDivider()
        MoreConfigRow {
            SwitchSettingRow(
                title = stringResource(R.string.pt_hide_status_bar),
                checked = state.boolean(PreferKey.hideStatusBar),
                onCheckedChange = { actions.onBooleanChanged(PreferKey.hideStatusBar, it) },
            )
        }
        NgFormGroupDivider()
        MoreConfigRow {
            SwitchSettingRow(
                title = stringResource(R.string.read_body_to_lh),
                checked = state.boolean(PreferKey.readBodyToLh),
                onCheckedChange = { actions.onBooleanChanged(PreferKey.readBodyToLh, it) },
            )
        }
        NgFormGroupDivider()
        MoreConfigRow {
            SwitchSettingRow(
                title = stringResource(R.string.read_content_avoid_cutout),
                checked = state.boolean(PreferKey.paddingDisplayCutouts),
                onCheckedChange = { actions.onBooleanChanged(PreferKey.paddingDisplayCutouts, it) },
            )
        }
    }
}

@Composable
internal fun ReadPageTurningScreen(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val accentColor = Color(NgTheme.colors.primary)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp
    NgGlassSurface(
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp)) {
            NgLongDrawerHeader(
                title = stringResource(R.string.read_settings_tab_page),
                centerTitle = true,
            )
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp),
            ) {
                NgFormGroup(stringResource(R.string.page_anim)) {
                    ReadStyleDock(
                        labels = listOf(
                            stringResource(R.string.page_anim_cover),
                            stringResource(R.string.page_anim_slide),
                            stringResource(R.string.page_anim_simulation),
                            stringResource(R.string.page_anim_scroll),
                            stringResource(R.string.page_anim_none),
                        ),
                        selectedIndex = state.pageAnim,
                        contentColor = contentColor,
                        selectedContainerColor = accentColor,
                        selectedContentColor = Color(NgTheme.colors.onPrimary),
                        onSelected = actions.onPageAnimChanged,
                        height = 42.dp,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(1.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                NgFormGroup(stringResource(R.string.read_page_section_layout)) {
                    PageTurningRow {
                        val options = state.options(PreferKey.doublePageHorizontal)
                        ChoiceSettingRow(
                            title = stringResource(R.string.double_page_horizontal),
                            selectedValue = state.value(PreferKey.doublePageHorizontal),
                            options = options,
                            onSelected = { actions.onValueChanged(PreferKey.doublePageHorizontal, it) },
                            preferPopupBelow = true,
                            menuWidth = pageLayoutMenuWidth(options),
                            scrollablePopup = true,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                NgFormGroup(stringResource(R.string.read_page_section_keys)) {
                    PageTurningRow {
                        SwitchSettingRow(
                            title = stringResource(R.string.volume_key_page),
                            checked = state.boolean(PreferKey.volumeKeyPage),
                            onCheckedChange = { actions.onBooleanChanged(PreferKey.volumeKeyPage, it) },
                        )
                    }
                    NgFormGroupDivider()
                    PageTurningRow {
                        SwitchSettingRow(
                            title = stringResource(R.string.key_page_on_long_press),
                            summary = stringResource(R.string.key_page_on_long_press_summary),
                            checked = state.boolean(PreferKey.keyPageOnLongPress),
                            onCheckedChange = { actions.onBooleanChanged(PreferKey.keyPageOnLongPress, it) },
                        )
                    }
                    NgFormGroupDivider()
                    ReadPageKeyExpandableSetting(
                        bindings = PageKeyBindings(
                            previous = state.value(PreferKey.prevKeys),
                            next = state.value(PreferKey.nextKeys),
                        ),
                        capture = actions.pageKeyCapture,
                        onBindingsChanged = { actions.onPageKeysChanged(it.previous, it.next) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                NgFormGroup(stringResource(R.string.read_page_section_touch)) {
                    ReadThresholdExpandableSetting(
                        title = stringResource(R.string.page_touch_slop_title),
                        value = state.thresholdValue(PreferKey.pageTouchSlop),
                        maxValue = PAGE_TOUCH_SLOP_MAX,
                        systemDefaultAtZero = true,
                        onValueChanged = { actions.onThresholdChanged(PreferKey.pageTouchSlop, it) },
                    )
                    NgFormGroupDivider()
                    ReadThresholdExpandableSetting(
                        title = stringResource(R.string.page_touch_click_title),
                        value = state.thresholdValue(PreferKey.pageTouchClick),
                        maxValue = PAGE_TOUCH_CLICK_MAX,
                        systemDefaultAtZero = false,
                        onValueChanged = { actions.onThresholdChanged(PreferKey.pageTouchClick, it) },
                    )
                    NgFormGroupDivider()
                    PageTurningRow {
                        ActionSettingRow(
                            title = stringResource(R.string.click_regional_config),
                            onClick = { actions.onAction(ReadMoreConfigKeys.CLICK_REGIONAL_CONFIG) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadThresholdExpandableSetting(
    title: String,
    value: Int,
    maxValue: Int,
    systemDefaultAtZero: Boolean,
    onValueChanged: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val draft = rememberSaveable(maxValue) { mutableIntStateOf(value.coerceIn(0, maxValue)) }
    val committed = remember(maxValue) { mutableIntStateOf(value.coerceIn(0, maxValue)) }
    val currentOnValueChanged = rememberUpdatedState(onValueChanged)
    val bringIntoView = remember { BringIntoViewRequester() }
    val contentColor = Color(NgTheme.colors.onSurface)

    LaunchedEffect(value, maxValue) {
        val updated = value.coerceIn(0, maxValue)
        if (updated != committed.intValue) {
            committed.intValue = updated
            draft.intValue = updated
        }
    }

    fun commit() {
        val selected = draft.intValue.coerceIn(0, maxValue)
        if (selected != committed.intValue) {
            committed.intValue = selected
            currentOnValueChanged.value(selected)
        }
    }

    DisposableEffect(maxValue) {
        onDispose { commit() }
    }

    val currentLabel = if (systemDefaultAtZero && draft.intValue == 0) {
        stringResource(R.string.read_settings_system_default)
    } else {
        stringResource(R.string.read_settings_pixels, draft.intValue)
    }
    val minimumLabel = if (systemDefaultAtZero) {
        stringResource(R.string.read_settings_system_default)
    } else {
        stringResource(R.string.read_settings_pixels, 0)
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        PageTurningRow {
            EditorNavigationRow(
                title = title,
                summary = currentLabel,
                color = contentColor,
                expanded = expanded,
                rowHeight = 48.dp,
                arrowVariant = EditorNavigationArrowVariant.RIGHT_TO_DOWN,
                onClick = {
                    if (expanded) commit()
                    expanded = !expanded
                },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                NgSlider(
                    value = draft.intValue.toFloat(),
                    onValueChange = { draft.intValue = it.roundToInt().coerceIn(0, maxValue) },
                    valueRange = 0f..maxValue.toFloat(),
                    steps = maxValue - 1,
                    visualSteps = 0,
                    variant = NgSliderVariant.CONTINUOUS,
                    onValueChangeFinished = { commit() },
                    modifier = Modifier.semantics { contentDescription = title },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(minimumLabel, color = contentColor.copy(alpha = 0.65f), fontSize = 12.sp)
                    Text(
                        stringResource(R.string.read_settings_pixels, maxValue),
                        color = contentColor.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(250)
            bringIntoView.bringIntoView()
        }
    }
}

@Composable
private fun PageTurningRow(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 14.dp)) { content() }
}

@Composable
private fun ContentSettingsGroups(
    state: ReadMoreConfigUiState,
    actions: ReadMoreConfigActions,
) {
    NgFormGroup(stringResource(R.string.read_settings_current_book)) {
        MoreConfigRow {
            ReadMoreChoiceSettingRow(
                title = stringResource(R.string.image_style),
                selectedValue = state.value(ReadMoreConfigKeys.BOOK_IMAGE_STYLE),
                options = state.options(ReadMoreConfigKeys.BOOK_IMAGE_STYLE),
                onSelected = {
                    actions.onValueChanged(ReadMoreConfigKeys.BOOK_IMAGE_STYLE, it)
                },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    NgFormGroup(stringResource(R.string.read_settings_all_books)) {
        MoreConfigRow {
            SwitchSettingRow(
                title = stringResource(R.string.auto_change_source),
                checked = state.boolean(PreferKey.autoChangeSource),
                onCheckedChange = { actions.onBooleanChanged(PreferKey.autoChangeSource, it) },
            )
        }
        NgFormGroupDivider()
        MoreConfigRow {
            ReadMoreChoiceSettingRow(
                title = stringResource(R.string.click_image_way),
                selectedValue = state.value(PreferKey.clickImgWay),
                options = state.options(PreferKey.clickImgWay),
                onSelected = { actions.onValueChanged(PreferKey.clickImgWay, it) },
            )
        }
    }
}

@Composable
private fun MoreConfigRow(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 14.dp)) { content() }
}

@Composable
internal fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (summary.isNullOrBlank()) 48.dp else 64.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Switch) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (summary.isNullOrBlank()) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Column(
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NgSwitchControl(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(width = 52.dp, height = 36.dp),
        )
    }
}

@Composable
private fun ReadMoreChoiceSettingRow(
    title: String,
    selectedValue: String,
    options: List<ReadMoreConfigOption>,
    onSelected: (String) -> Unit,
) {
    ChoiceSettingRow(
        title = title,
        selectedValue = selectedValue,
        options = options,
        onSelected = onSelected,
        preferPopupBelow = true,
        menuWidth = readSettingsMenuWidth(options),
        scrollablePopup = true,
        useVisibleWindowBounds = true,
    )
}

internal fun readSettingsPopupWidth(longestLabel: Dp, screenWidth: Dp): Dp =
    (longestLabel + 40.dp).coerceAtLeast(120.dp)
        .coerceAtMost((screenWidth - 16.dp).coerceAtLeast(0.dp))

@Composable
private fun readSettingsMenuWidth(options: List<ReadMoreConfigOption>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return remember(options, measurer, density, textStyle, screenWidth) {
        val longest = options.maxOfOrNull { option ->
            with(density) { measurer.measure(option.label, textStyle, maxLines = 1).size.width.toDp() }
        } ?: 0.dp
        readSettingsPopupWidth(longest, screenWidth)
    }
}

@Composable
private fun pageLayoutMenuWidth(options: List<ReadMoreConfigOption>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return remember(options, measurer, density, textStyle, screenWidth) {
        val longest = options.maxOfOrNull { option ->
            with(density) { measurer.measure(option.label, textStyle, maxLines = 1).size.width.toDp() }
        } ?: 0.dp
        (longest + 40.dp).coerceIn(120.dp, (screenWidth - 16.dp).coerceAtLeast(120.dp))
    }
}

@Composable
private fun ChoiceSettingRow(
    title: String,
    selectedValue: String,
    options: List<ReadMoreConfigOption>,
    onSelected: (String) -> Unit,
    preferPopupBelow: Boolean = false,
    menuWidth: Dp = 156.dp,
    scrollablePopup: Boolean = false,
    useVisibleWindowBounds: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    val contentColor = Color(NgTheme.colors.onSurface)
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
            ReadChoicePopup(
                expanded = expanded,
                options = options,
                selectedValue = selectedValue,
                onDismissRequest = { expanded = false },
                onSelected = onSelected,
                preferPopupBelow = preferPopupBelow,
                menuWidth = menuWidth,
                scrollable = scrollablePopup,
                variant = if (scrollablePopup) ReadChoicePopupVariant.ADAPTIVE else ReadChoicePopupVariant.STANDARD,
                useVisibleWindowBounds = useVisibleWindowBounds,
            )
        }
    }
}

@Composable
internal fun ActionSettingRow(
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
