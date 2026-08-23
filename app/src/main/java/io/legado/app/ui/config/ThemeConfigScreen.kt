package io.legado.app.ui.config

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonochromePhotos
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal data class ThemeConfigScreenState(
    val themeMode: String = "0",
    val showLauncherIcon: Boolean = true,
    @param:DrawableRes val launcherIconRes: Int = R.mipmap.ic_launcher,
    val floatingBottomBar: Boolean = false,
    val floatingBottomBarBottomDistancePx: Int = 0,
    val floatingBottomBarTransparency: Int =
        FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT,
    val drawerTransparency: Int =
        NgDrawerAppearanceConfig.DEFAULT_TRANSPARENCY_PERCENT,
    val drawerPrimaryStrength: Int =
        NgDrawerAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT,
    val drawerHorizontalMarginDp: Int =
        NgDrawerAppearanceConfig.DEFAULT_HORIZONTAL_MARGIN_DP,
    val drawerCornerRadiusDp: Int =
        NgDrawerAppearanceConfig.DEFAULT_CORNER_RADIUS_DP,
    val bookshelfTopBarStyle: BookshelfTopBarStyle = BookshelfTopBarStyle.COMPACT_TOOLBAR,
    val bookshelfFloatingDockMinTopDistancePx: Int = 0,
    val bookshelfFloatingDockTopDistancePx: Int = 0,
    val bookshelfFloatingDockTransparency: Int =
        BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT,
    val bookshelfFloatingDockSearchPosition: BookshelfFloatingDockSearchPosition =
        BookshelfFloatingDockSearchPosition.LEFT,
    val transparentAppBars: Boolean = false,
    val autoRefresh: Boolean = false,
    val onlyUpdateRead: Boolean = false,
    val defaultToRead: Boolean = false,
    val showDiscovery: Boolean = true,
    val showRss: Boolean = true,
    val defaultHomePage: String = "bookshelf",
    val fontScaleSummary: String = "",
    val dayBackgroundSummary: String = "",
    val nightBackgroundSummary: String = ""
)

internal enum class ThemeConfigSection {
    ALL,
    APPEARANCE,
    INTERFACE
}

@Composable
internal fun ThemeConfigScreen(
    state: ThemeConfigScreenState,
    section: ThemeConfigSection,
    onThemeModeSelected: (String) -> Unit,
    onLauncherIconClick: () -> Unit,
    onFloatingBottomBarChanged: (Boolean) -> Unit,
    onFloatingBottomBarBottomDistanceChanged: (Int) -> Unit,
    onFloatingBottomBarBottomDistanceChangeFinished: () -> Unit,
    onFloatingBottomBarTransparencyChanged: (Int) -> Unit,
    onFloatingBottomBarTransparencyChangeFinished: () -> Unit,
    onDrawerTransparencyChanged: (Int) -> Unit,
    onDrawerTransparencyChangeFinished: () -> Unit,
    onDrawerPrimaryStrengthChanged: (Int) -> Unit,
    onDrawerPrimaryStrengthChangeFinished: () -> Unit,
    onDrawerHorizontalMarginChanged: (Int) -> Unit,
    onDrawerHorizontalMarginChangeFinished: () -> Unit,
    onDrawerCornerRadiusChanged: (Int) -> Unit,
    onDrawerCornerRadiusChangeFinished: () -> Unit,
    onBookshelfTopBarStyleSelected: (BookshelfTopBarStyle) -> Unit,
    onBookshelfFloatingDockTopDistanceChanged: (Int) -> Unit,
    onBookshelfFloatingDockTopDistanceChangeFinished: () -> Unit,
    onBookshelfFloatingDockTransparencyChanged: (Int) -> Unit,
    onBookshelfFloatingDockTransparencyChangeFinished: () -> Unit,
    onBookshelfFloatingDockSearchPositionSelected:
        (BookshelfFloatingDockSearchPosition) -> Unit,
    onTransparentAppBarsChanged: (Boolean) -> Unit,
    onAutoRefreshChanged: (Boolean) -> Unit,
    onOnlyUpdateReadChanged: (Boolean) -> Unit,
    onDefaultToReadChanged: (Boolean) -> Unit,
    onShowDiscoveryChanged: (Boolean) -> Unit,
    onShowRssChanged: (Boolean) -> Unit,
    onDefaultHomePageSelected: (String) -> Unit,
    onOpenCustomColors: () -> Unit,
    onOpenFontScale: () -> Unit,
    onOpenCoverConfig: () -> Unit,
    onOpenThemeManager: () -> Unit,
    onOpenDayBackground: () -> Unit,
    onOpenNightBackground: () -> Unit
) {
    val showAppearance = section != ThemeConfigSection.INTERFACE
    val showInterface = section != ThemeConfigSection.APPEARANCE
    val selectedMode = THEME_MODES.indexOf(state.themeMode).coerceAtLeast(0)
    var bottomBarExpanded by rememberSaveable { mutableStateOf(false) }
    var drawerAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var bookshelfTopBarExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        if (showAppearance) {
            NgFloatingTabBar(
                items = listOf(
                    NgFloatingTabSpec(
                        text = stringResource(R.string.theme_mode_follow_short),
                        iconVector = Icons.Rounded.BrightnessAuto
                    ),
                    NgFloatingTabSpec(
                        text = stringResource(R.string.theme_mode_day_short),
                        iconVector = Icons.Rounded.LightMode
                    ),
                    NgFloatingTabSpec(
                        text = stringResource(R.string.theme_mode_night_short),
                        iconVector = Icons.Rounded.DarkMode
                    ),
                    NgFloatingTabSpec(
                        text = stringResource(R.string.theme_mode_eink_short),
                        iconVector = Icons.Rounded.MonochromePhotos
                    )
                ),
                selectedIndex = selectedMode,
                onTabSelected = { index -> onThemeModeSelected(THEME_MODES[index]) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        NgSettingsGroup {
            if (showAppearance && state.showLauncherIcon) {
                NgSettingsItem(
                    title = stringResource(R.string.change_icon),
                    summary = stringResource(R.string.change_icon_summary),
                    trailing = NgSettingsTrailing.CUSTOM,
                    onClick = onLauncherIconClick,
                    customTrailing = {
                        LauncherIconPreview(
                            iconRes = state.launcherIconRes,
                            contentDescription = stringResource(R.string.change_icon)
                        )
                    }
                )
            }
            if (showInterface) {
                NgExpandableSettingsItem(
                title = stringResource(R.string.main_bottom_bar_style),
                summary = stringResource(
                    if (state.floatingBottomBar) {
                        R.string.floating_bottom_bar
                    } else {
                        R.string.traditional_bottom_bar
                    }
                ),
                expanded = bottomBarExpanded,
                onExpandedChange = { bottomBarExpanded = it }
            ) {
                NgFloatingTabBar(
                    items = listOf(
                        NgFloatingTabSpec(
                            text = stringResource(R.string.traditional_bottom_bar),
                            iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                        ),
                        NgFloatingTabSpec(
                            text = stringResource(R.string.floating_bottom_bar),
                            iconRes = R.drawable.ic_bookshelf_top_bar_floating
                        )
                    ),
                    selectedIndex = if (state.floatingBottomBar) 1 else 0,
                    onTabSelected = { index -> onFloatingBottomBarChanged(index == 1) },
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(visible = state.floatingBottomBar) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NgDockSlider(
                            title = stringResource(
                                R.string.floating_bottom_bar_bottom_distance
                            ),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.floatingBottomBarBottomDistancePx
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX
                            ),
                            value = state.floatingBottomBarBottomDistancePx.toFloat(),
                            valueRange = FloatingBottomBarConfig.MIN_BOTTOM_DISTANCE_PX.toFloat()..
                                FloatingBottomBarConfig.MAX_BOTTOM_DISTANCE_PX.toFloat(),
                            steps = FloatingBottomBarConfig.BOTTOM_DISTANCE_SLIDER_STEPS,
                            onValueChange = { value ->
                                onFloatingBottomBarBottomDistanceChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onFloatingBottomBarBottomDistanceChangeFinished
                        )
                        NgDockSlider(
                            title = stringResource(R.string.floating_bottom_bar_transparency),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                state.floatingBottomBarTransparency
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT
                            ),
                            value = state.floatingBottomBarTransparency.toFloat(),
                            valueRange = FloatingBottomBarConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                                FloatingBottomBarConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                            onValueChange = { value ->
                                onFloatingBottomBarTransparencyChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onFloatingBottomBarTransparencyChangeFinished
                        )
                    }
                }
            }
                NgExpandableSettingsItem(
                title = stringResource(R.string.ng_drawer_appearance),
                summary = stringResource(
                    R.string.ng_drawer_appearance_summary,
                    state.drawerTransparency,
                    state.drawerPrimaryStrength,
                    state.drawerHorizontalMarginDp,
                    state.drawerCornerRadiusDp,
                ),
                expanded = drawerAppearanceExpanded,
                onExpandedChange = { drawerAppearanceExpanded = it },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NgDockSlider(
                        title = stringResource(R.string.ng_drawer_transparency),
                        valueText = stringResource(
                            R.string.ng_drawer_percent_value,
                            state.drawerTransparency,
                        ),
                        minimumText = stringResource(R.string.ng_drawer_percent_value, 0),
                        maximumText = stringResource(R.string.ng_drawer_percent_value, 100),
                        value = state.drawerTransparency.toFloat(),
                        valueRange = NgDrawerAppearanceConfig.MIN_PERCENT.toFloat()..
                            NgDrawerAppearanceConfig.MAX_PERCENT.toFloat(),
                        onValueChange = { value ->
                            onDrawerTransparencyChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onDrawerTransparencyChangeFinished,
                    )
                    NgDockSlider(
                        title = stringResource(R.string.ng_drawer_primary_strength),
                        valueText = stringResource(
                            R.string.ng_drawer_percent_value,
                            state.drawerPrimaryStrength,
                        ),
                        minimumText = stringResource(R.string.ng_drawer_percent_value, 0),
                        maximumText = stringResource(R.string.ng_drawer_percent_value, 100),
                        value = state.drawerPrimaryStrength.toFloat(),
                        valueRange = NgDrawerAppearanceConfig.MIN_PERCENT.toFloat()..
                            NgDrawerAppearanceConfig.MAX_PERCENT.toFloat(),
                        onValueChange = { value ->
                            onDrawerPrimaryStrengthChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onDrawerPrimaryStrengthChangeFinished,
                    )
                    NgDockSlider(
                        title = stringResource(R.string.ng_drawer_horizontal_margin),
                        valueText = stringResource(
                            R.string.ng_drawer_dp_value,
                            state.drawerHorizontalMarginDp,
                        ),
                        minimumText = stringResource(
                            R.string.ng_drawer_dp_value,
                            NgDrawerAppearanceConfig.MIN_HORIZONTAL_MARGIN_DP,
                        ),
                        maximumText = stringResource(
                            R.string.ng_drawer_dp_value,
                            NgDrawerAppearanceConfig.MAX_HORIZONTAL_MARGIN_DP,
                        ),
                        value = state.drawerHorizontalMarginDp.toFloat(),
                        valueRange = NgDrawerAppearanceConfig.MIN_HORIZONTAL_MARGIN_DP.toFloat()..
                            NgDrawerAppearanceConfig.MAX_HORIZONTAL_MARGIN_DP.toFloat(),
                        steps = NgDrawerAppearanceConfig.HORIZONTAL_MARGIN_SLIDER_STEPS,
                        onValueChange = { value ->
                            onDrawerHorizontalMarginChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onDrawerHorizontalMarginChangeFinished,
                    )
                    NgDockSlider(
                        title = stringResource(R.string.ng_drawer_corner_radius),
                        valueText = stringResource(
                            R.string.ng_drawer_dp_value,
                            state.drawerCornerRadiusDp,
                        ),
                        minimumText = stringResource(
                            R.string.ng_drawer_dp_value,
                            NgDrawerAppearanceConfig.MIN_CORNER_RADIUS_DP,
                        ),
                        maximumText = stringResource(
                            R.string.ng_drawer_dp_value,
                            NgDrawerAppearanceConfig.MAX_CORNER_RADIUS_DP,
                        ),
                        value = state.drawerCornerRadiusDp.toFloat(),
                        valueRange = NgDrawerAppearanceConfig.MIN_CORNER_RADIUS_DP.toFloat()..
                            NgDrawerAppearanceConfig.MAX_CORNER_RADIUS_DP.toFloat(),
                        steps = NgDrawerAppearanceConfig.CORNER_RADIUS_SLIDER_STEPS,
                        onValueChange = { value ->
                            onDrawerCornerRadiusChanged(value.roundToInt())
                        },
                        onValueChangeFinished = onDrawerCornerRadiusChangeFinished,
                    )
                }
            }
                NgExpandableSettingsItem(
                title = stringResource(R.string.bookshelf_top_bar_style),
                summary = stringResource(
                    when (state.bookshelfTopBarStyle) {
                        BookshelfTopBarStyle.COMPACT_TOOLBAR ->
                            R.string.bookshelf_top_bar_compact_toolbar

                        BookshelfTopBarStyle.GROUP_NAVIGATION ->
                            R.string.bookshelf_top_bar_group_navigation
                    }
                ),
                expanded = bookshelfTopBarExpanded,
                onExpandedChange = { bookshelfTopBarExpanded = it }
            ) {
                NgFloatingTabBar(
                    items = listOf(
                        NgFloatingTabSpec(
                            text = stringResource(R.string.bookshelf_top_bar_compact_toolbar),
                            iconRes = R.drawable.ic_bookshelf_top_bar_traditional
                        ),
                        NgFloatingTabSpec(
                            text = stringResource(R.string.bookshelf_top_bar_group_navigation),
                            iconRes = R.drawable.ic_bookshelf_top_bar_floating
                        )
                    ),
                    selectedIndex = BookshelfTopBarStyle.entries.indexOf(
                        state.bookshelfTopBarStyle
                    ),
                    onTabSelected = { index ->
                        onBookshelfTopBarStyleSelected(BookshelfTopBarStyle.entries[index])
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        NgSettingsItem(
                            title = stringResource(
                                R.string.bookshelf_floating_dock_search_position
                            ),
                            trailing = NgSettingsTrailing.CUSTOM,
                            customTrailing = {
                                NgFloatingTabBar(
                                    items = listOf(
                                        NgFloatingTabSpec(
                                            text = stringResource(R.string.left)
                                        ),
                                        NgFloatingTabSpec(
                                            text = stringResource(R.string.right)
                                        )
                                    ),
                                    selectedIndex =
                                        BookshelfFloatingDockSearchPosition.entries.indexOf(
                                            state.bookshelfFloatingDockSearchPosition
                                        ),
                                    onTabSelected = { index ->
                                        onBookshelfFloatingDockSearchPositionSelected(
                                            BookshelfFloatingDockSearchPosition.entries[index]
                                        )
                                    },
                                    modifier = Modifier.width(132.dp)
                                )
                            }
                        )
                        NgDockSlider(
                            title = stringResource(R.string.bookshelf_floating_dock_top_distance),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.bookshelfFloatingDockTopDistancePx
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                state.bookshelfFloatingDockMinTopDistancePx
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_top_distance_value,
                                BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX
                            ),
                            value = state.bookshelfFloatingDockTopDistancePx.toFloat(),
                            valueRange = state.bookshelfFloatingDockMinTopDistancePx.toFloat()..
                                BookshelfFloatingDockConfig.MAX_TOP_DISTANCE_PX.toFloat(),
                            steps = BookshelfFloatingDockConfig.TOP_DISTANCE_SLIDER_STEPS,
                            onValueChange = { value ->
                                onBookshelfFloatingDockTopDistanceChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onBookshelfFloatingDockTopDistanceChangeFinished
                        )
                        NgDockSlider(
                            title = stringResource(R.string.bookshelf_floating_dock_transparency),
                            valueText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                state.bookshelfFloatingDockTransparency
                            ),
                            minimumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT
                            ),
                            maximumText = stringResource(
                                R.string.bookshelf_floating_dock_transparency_value,
                                BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT
                            ),
                            value = state.bookshelfFloatingDockTransparency.toFloat(),
                            valueRange = BookshelfFloatingDockConfig.MIN_TRANSPARENCY_PERCENT.toFloat()..
                                BookshelfFloatingDockConfig.MAX_TRANSPARENCY_PERCENT.toFloat(),
                            onValueChange = { value ->
                                onBookshelfFloatingDockTransparencyChanged(value.roundToInt())
                            },
                            onValueChangeFinished =
                                onBookshelfFloatingDockTransparencyChangeFinished
                        )
                }
            }
                NgSettingsItem(
                title = stringResource(R.string.transparent_app_bars),
                summary = stringResource(R.string.transparent_app_bars_summary),
                trailing = NgSettingsTrailing.SWITCH,
                checked = state.transparentAppBars,
                onCheckedChange = onTransparentAppBarsChanged,
                onClick = { onTransparentAppBarsChanged(!state.transparentAppBars) }
            )
            }
            if (showAppearance) {
                NgSettingsItem(
                    title = stringResource(R.string.ng_custom_colors),
                    summary = stringResource(R.string.ng_custom_colors_summary),
                    onClick = onOpenCustomColors
                )
                NgSettingsItem(
                    title = stringResource(R.string.font_scale),
                    summary = state.fontScaleSummary,
                    onClick = onOpenFontScale
                )
                NgSettingsItem(
                    title = stringResource(R.string.theme_list),
                    summary = stringResource(R.string.theme_list_summary),
                    onClick = onOpenThemeManager
                )
            }
            if (section == ThemeConfigSection.ALL) {
                NgSettingsItem(
                    title = stringResource(R.string.cover_config),
                    summary = stringResource(R.string.cover_config_summary),
                    onClick = onOpenCoverConfig
                )
            }
        }

        if (showAppearance) {
            Spacer(Modifier.height(4.dp))
            NgSettingsSectionLabel(stringResource(R.string.day))
            NgSettingsGroup {
                NgSettingsItem(
                    title = stringResource(R.string.background_image),
                    summary = state.dayBackgroundSummary,
                    onClick = onOpenDayBackground
                )
            }

            NgSettingsSectionLabel(stringResource(R.string.night))
            NgSettingsGroup {
                NgSettingsItem(
                    title = stringResource(R.string.background_image),
                    summary = state.nightBackgroundSummary,
                    onClick = onOpenNightBackground
                )
            }
        }

        if (section == ThemeConfigSection.INTERFACE) {
            Spacer(Modifier.height(4.dp))
            NgSettingsSectionLabel(stringResource(R.string.main_activity))
            NgSettingsGroup {
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.pt_auto_refresh),
                    summary = stringResource(R.string.ps_auto_refresh),
                    checked = state.autoRefresh,
                    onCheckedChange = onAutoRefreshChanged
                )
                AnimatedVisibility(visible = state.autoRefresh) {
                    InterfaceSwitchSettingItem(
                        title = stringResource(R.string.only_update_read),
                        summary = stringResource(R.string.ps_only_update_read),
                        checked = state.onlyUpdateRead,
                        onCheckedChange = onOnlyUpdateReadChanged
                    )
                }
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.pt_default_read),
                    summary = stringResource(R.string.ps_default_read),
                    checked = state.defaultToRead,
                    onCheckedChange = onDefaultToReadChanged
                )
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.show_discovery),
                    checked = state.showDiscovery,
                    onCheckedChange = onShowDiscoveryChanged
                )
                InterfaceSwitchSettingItem(
                    title = stringResource(R.string.show_rss),
                    checked = state.showRss,
                    onCheckedChange = onShowRssChanged
                )
                DefaultHomePageSettingItem(
                    selectedValue = state.defaultHomePage,
                    onValueSelected = onDefaultHomePageSelected
                )
            }
        }
    }
}

@Composable
private fun InterfaceSwitchSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    NgSettingsItem(
        title = title,
        summary = summary,
        trailing = NgSettingsTrailing.SWITCH,
        checked = checked,
        onCheckedChange = onCheckedChange,
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun DefaultHomePageSettingItem(
    selectedValue: String,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "bookshelf" to stringResource(R.string.bookshelf),
        "explore" to stringResource(R.string.discovery),
        "rss" to stringResource(R.string.rss),
        "my" to stringResource(R.string.my)
    )
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
        ?: stringResource(R.string.bookshelf)
    Box {
        NgSettingsItem(
            title = stringResource(R.string.default_home_page),
            value = selectedLabel,
            trailing = NgSettingsTrailing.VALUE,
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(NgTheme.colors.dialogContainer)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color(NgTheme.colors.onSurface)) },
                    onClick = {
                        expanded = false
                        onValueSelected(value)
                    }
                )
            }
        }
    }
}

@Composable
private fun LauncherIconPreview(
    @DrawableRes iconRes: Int,
    contentDescription: String
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            imageView.setImageResource(iconRes)
            imageView.contentDescription = contentDescription
        },
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

private val THEME_MODES = listOf("0", "1", "2", "3")
