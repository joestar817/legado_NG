package io.legado.app.ui.config

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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.ListeningCartoonType
import io.legado.app.help.config.NgDynamicSceneTheme
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.components.NgSettingsTrailing
import io.legado.app.ui.design.components.compose.NgDockSlider
import io.legado.app.ui.design.components.compose.NgExpandableSettingsItem
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgLauncherIcon
import io.legado.app.ui.design.components.compose.NgSettingsGroup
import io.legado.app.ui.design.components.compose.NgSettingsItem
import io.legado.app.ui.design.components.compose.NgSettingsSectionLabel
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal data class ThemeConfigScreenState(
    val themeModeGroup: NgThemeModeGroup = NgThemeModeGroup.STANDARD,
    val presentationMode: NgThemePresentationMode = NgThemePresentationMode.STANDARD,
    val standardThemeMode: String = "0",
    val internalThemeMode: NgThemePresentationMode =
        NgThemePresentationMode.SOFT_GRADIENT,
    val softGradientColor: NgSoftGradientColorPreset = NgSoftGradientColorPreset.CLEAR_BLUE,
    val softGradientLightField: NgSoftGradientLightFieldPreset =
        NgSoftGradientLightFieldPreset.BALANCED,
    val dynamicScenePreset: ListeningCartoonType = ListeningCartoonType.SAKURA,
    val visualSystem: NgVisualSystem = NgVisualSystem.DEFAULT,
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
    onThemeModeGroupSelected: (NgThemeModeGroup) -> Unit,
    onStandardThemeModeSelected: (String) -> Unit,
    onInternalThemeModeSelected: (NgThemePresentationMode) -> Unit,
    onSoftGradientColorSelected: (NgSoftGradientColorPreset) -> Unit,
    onSoftGradientLightFieldSelected: (NgSoftGradientLightFieldPreset) -> Unit,
    onDynamicScenePresetSelected: (ListeningCartoonType) -> Unit,
    onVisualSystemSelected: (NgVisualSystem) -> Unit,
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
    val selectedStandardMode = STANDARD_THEME_MODES
        .indexOf(state.standardThemeMode)
        .coerceAtLeast(0)
    val selectedInternalMode = INTERNAL_THEME_MODES
        .indexOf(state.internalThemeMode)
        .coerceAtLeast(0)
    val selectedDynamicScene = DYNAMIC_SCENE_PRESETS
        .indexOf(state.dynamicScenePreset)
        .coerceAtLeast(0)
    var themeModeExpanded by rememberSaveable { mutableStateOf(false) }
    var visualSystemExpanded by rememberSaveable { mutableStateOf(false) }
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
            val modeName = when (state.presentationMode) {
                NgThemePresentationMode.STANDARD -> standardThemeModeName(
                    state.standardThemeMode,
                )
                NgThemePresentationMode.SOFT_GRADIENT ->
                    stringResource(R.string.ng_theme_mode_soft_gradient)
                NgThemePresentationMode.DYNAMIC_SCENE ->
                    stringResource(state.dynamicScenePreset.themeSceneLabelRes())
                NgThemePresentationMode.EINK ->
                    stringResource(R.string.theme_mode_eink_short)
            }
            NgExpandableSettingsItem(
                title = stringResource(R.string.theme_mode),
                summary = stringResource(
                    R.string.ng_theme_mode_summary,
                    stringResource(
                        if (state.themeModeGroup == NgThemeModeGroup.STANDARD) {
                            R.string.ng_theme_mode_standard
                        } else {
                            R.string.ng_theme_mode_internal
                        }
                    ),
                    modeName,
                ),
                expanded = themeModeExpanded,
                onExpandedChange = { themeModeExpanded = it },
            ) {
                NgFloatingTabBar(
                    items = listOf(
                        NgFloatingTabSpec(
                            text = stringResource(R.string.ng_theme_mode_standard),
                        ),
                        NgFloatingTabSpec(
                            text = stringResource(R.string.ng_theme_mode_internal),
                        ),
                    ),
                    selectedIndex = NgThemeModeGroup.entries.indexOf(state.themeModeGroup),
                    onTabSelected = { index ->
                        onThemeModeGroupSelected(NgThemeModeGroup.entries[index])
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.themeModeGroup == NgThemeModeGroup.STANDARD) {
                    ThemeModeFieldLabel(stringResource(R.string.ng_theme_mode_standard))
                    NgFloatingTabBar(
                        items = listOf(
                            NgFloatingTabSpec(
                                text = stringResource(R.string.theme_mode_follow_short),
                                iconVector = Icons.Rounded.BrightnessAuto,
                            ),
                            NgFloatingTabSpec(
                                text = stringResource(R.string.theme_mode_day_short),
                                iconVector = Icons.Rounded.LightMode,
                            ),
                            NgFloatingTabSpec(
                                text = stringResource(R.string.theme_mode_night_short),
                                iconVector = Icons.Rounded.DarkMode,
                            ),
                        ),
                        selectedIndex = selectedStandardMode,
                        onTabSelected = { index ->
                            onStandardThemeModeSelected(STANDARD_THEME_MODES[index])
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ThemeModeFieldLabel(stringResource(R.string.ng_theme_mode_internal))
                    NgFloatingTabBar(
                        items = listOf(
                            NgFloatingTabSpec(
                                text = stringResource(R.string.ng_theme_mode_soft_gradient),
                            ),
                            NgFloatingTabSpec(
                                text = stringResource(R.string.ng_theme_mode_dynamic_scene),
                            ),
                            NgFloatingTabSpec(
                                text = stringResource(R.string.theme_mode_eink_short),
                                iconVector = Icons.Rounded.MonochromePhotos,
                            ),
                        ),
                        selectedIndex = selectedInternalMode,
                        onTabSelected = { index ->
                            onInternalThemeModeSelected(INTERNAL_THEME_MODES[index])
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.presentationMode == NgThemePresentationMode.SOFT_GRADIENT) {
                        ThemeModeFieldLabel(stringResource(R.string.ng_soft_gradient_color))
                        NgFloatingTabBar(
                            items = NgSoftGradientColorPreset.entries.map { preset ->
                                NgFloatingTabSpec(
                                    text = stringResource(preset.labelRes()),
                                )
                            },
                            selectedIndex = NgSoftGradientColorPreset.entries.indexOf(
                                state.softGradientColor,
                            ),
                            onTabSelected = { index ->
                                onSoftGradientColorSelected(
                                    NgSoftGradientColorPreset.entries[index],
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ThemeModeFieldLabel(stringResource(R.string.ng_soft_gradient_light_field))
                        NgFloatingTabBar(
                            items = NgSoftGradientLightFieldPreset.entries.map { preset ->
                                NgFloatingTabSpec(
                                    text = stringResource(preset.labelRes()),
                                )
                            },
                            selectedIndex = NgSoftGradientLightFieldPreset.entries.indexOf(
                                state.softGradientLightField,
                            ),
                            onTabSelected = { index ->
                                onSoftGradientLightFieldSelected(
                                    NgSoftGradientLightFieldPreset.entries[index],
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.presentationMode == NgThemePresentationMode.DYNAMIC_SCENE) {
                        ThemeModeFieldLabel(
                            stringResource(R.string.ng_theme_mode_dynamic_scene),
                        )
                        NgFloatingTabBar(
                            items = DYNAMIC_SCENE_PRESETS.map { preset ->
                                NgFloatingTabSpec(
                                    text = stringResource(preset.themeSceneLabelRes()),
                                )
                            },
                            selectedIndex = selectedDynamicScene,
                            onTabSelected = { index ->
                                onDynamicScenePresetSelected(DYNAMIC_SCENE_PRESETS[index])
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        NgSettingsGroup {
            if (
                showAppearance &&
                state.presentationMode != NgThemePresentationMode.EINK
            ) {
                NgExpandableSettingsItem(
                    title = stringResource(R.string.ng_visual_system),
                    summary = stringResource(state.visualSystem.labelRes()),
                    expanded = visualSystemExpanded,
                    onExpandedChange = { visualSystemExpanded = it },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NgFloatingTabBar(
                            items = listOf(
                                NgFloatingTabSpec(
                                    text = stringResource(
                                        R.string.ng_visual_system_transparent_glass
                                    )
                                ),
                                NgFloatingTabSpec(
                                    text = stringResource(
                                        R.string.ng_visual_system_liquid_glass
                                    )
                                ),
                            ),
                            selectedIndex = NgVisualSystem.entries.indexOf(state.visualSystem),
                            onTabSelected = { index ->
                                onVisualSystemSelected(NgVisualSystem.entries[index])
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
            if (
                showAppearance &&
                (
                    state.presentationMode == NgThemePresentationMode.STANDARD ||
                        state.presentationMode == NgThemePresentationMode.DYNAMIC_SCENE
                )
            ) {
                NgSettingsItem(
                    title = stringResource(R.string.ng_custom_colors),
                    summary = stringResource(R.string.ng_custom_colors_summary),
                    onClick = onOpenCustomColors
                )
            }
            if (showAppearance) {
                NgSettingsItem(
                    title = stringResource(R.string.font_scale),
                    summary = state.fontScaleSummary,
                    onClick = onOpenFontScale
                )
            }
            if (
                showAppearance &&
                state.presentationMode == NgThemePresentationMode.STANDARD
            ) {
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

        if (
            showAppearance &&
            state.presentationMode == NgThemePresentationMode.STANDARD
        ) {
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
private fun standardThemeModeName(mode: String): String = stringResource(
    when (mode) {
        "1" -> R.string.theme_mode_day_short
        "2" -> R.string.theme_mode_night_short
        else -> R.string.theme_mode_follow_short
    }
)

@Composable
private fun ThemeModeFieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        color = Color(NgTheme.colors.primary),
        fontSize = 13.sp,
    )
}

private fun NgSoftGradientLightFieldPreset.labelRes(): Int = when (this) {
    NgSoftGradientLightFieldPreset.BALANCED -> R.string.ng_soft_gradient_light_balanced
    NgSoftGradientLightFieldPreset.CLEAR -> R.string.ng_soft_gradient_light_clear
    NgSoftGradientLightFieldPreset.STILL_SEA -> R.string.ng_soft_gradient_light_still_sea
    NgSoftGradientLightFieldPreset.AQUA -> R.string.ng_soft_gradient_light_aqua
    NgSoftGradientLightFieldPreset.FLOW_SHADOW -> R.string.ng_soft_gradient_light_flow_shadow
}

private fun NgSoftGradientColorPreset.labelRes(): Int = when (this) {
    NgSoftGradientColorPreset.CLEAR_BLUE -> R.string.ng_soft_gradient_clear_blue
    NgSoftGradientColorPreset.DUSK_VIOLET -> R.string.ng_soft_gradient_dusk_violet
    NgSoftGradientColorPreset.YOUNG_BAMBOO -> R.string.ng_soft_gradient_young_bamboo
    NgSoftGradientColorPreset.FOREST_AFTER_RAIN -> R.string.ng_soft_gradient_forest_after_rain
    NgSoftGradientColorPreset.CHERRY_GLOW -> R.string.ng_soft_gradient_cherry_glow
}

private fun ListeningCartoonType.themeSceneLabelRes(): Int = when (this) {
    ListeningCartoonType.SAKURA -> R.string.listening_motion_cartoon_sakura
    ListeningCartoonType.CATS -> R.string.listening_motion_cartoon_cats
    ListeningCartoonType.RAIN_NIGHT -> R.string.listening_motion_cartoon_rain_night
}

private fun NgVisualSystem.labelRes(): Int = when (this) {
    NgVisualSystem.TRANSPARENT_GLASS -> R.string.ng_visual_system_transparent_glass
    NgVisualSystem.LIQUID_GLASS -> R.string.ng_visual_system_liquid_glass
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
    NgSettingsItem(
        title = stringResource(R.string.default_home_page),
        value = selectedLabel,
        trailing = NgSettingsTrailing.VALUE,
        onClick = { expanded = true },
        valueOverlay = {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(x = 0.dp, y = (-20).dp),
                containerColor = colorResource(R.color.ng_surface_card),
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
        },
    )
}

@Composable
private fun LauncherIconPreview(
    @DrawableRes iconRes: Int,
    contentDescription: String
) {
    NgLauncherIcon(
        iconRes = iconRes,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

private val STANDARD_THEME_MODES = listOf("0", "1", "2")

private val INTERNAL_THEME_MODES = listOf(
    NgThemePresentationMode.SOFT_GRADIENT,
    NgThemePresentationMode.DYNAMIC_SCENE,
    NgThemePresentationMode.EINK,
)

private val DYNAMIC_SCENE_PRESETS = NgDynamicSceneTheme.presets
