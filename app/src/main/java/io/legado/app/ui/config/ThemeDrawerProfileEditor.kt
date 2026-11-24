package io.legado.app.ui.config

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.NgThemeDrawerProfile
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabBarVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgDockSelectionColors
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.formatNgColor

private enum class DrawerBackgroundKind(
    @param:StringRes val title: Int,
    val sources: List<String>,
) {
    COLOR(R.string.ng_drawer_background_color, listOf(NgThemeDrawerProfile.THEME_COLOR, "custom_color")),
    IMAGE(R.string.ng_drawer_background_image, listOf("theme_image", "custom_image")),
    LIQUID_GLASS(R.string.ng_visual_system_liquid_glass, listOf(NgThemeDrawerProfile.LIQUID_GLASS)),
}

/** The owner decides whether changes persist now or remain in a theme draft. */
@Composable
internal fun NgDrawerBackgroundSettingsContent(
    profile: NgThemeDrawerProfile?,
    colors: NgColorSystem,
    onProfileChanged: (NgThemeDrawerProfile) -> Unit,
    onProfileChangeFinished: () -> Unit,
    onSelectImage: (Boolean) -> Unit,
    selectionColors: NgDockSelectionColors? = null,
) {
    val value = profile ?: NgThemeDrawerProfile()
    val kind = DrawerBackgroundKind.entries.firstOrNull { value.source in it.sources }
        ?: DrawerBackgroundKind.LIQUID_GLASS
    var colorNight by remember { mutableStateOf<Boolean?>(null) }

    fun selectSource(source: String) {
        if (source == value.source) return
        colorNight = null
        onProfileChanged(value.copy(source = source))
        onProfileChangeFinished()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NgFloatingTabBar(
            items = DrawerBackgroundKind.entries.map {
                NgFloatingTabSpec(text = stringResource(it.title))
            },
            selectedIndex = kind.ordinal,
            onTabSelected = { index ->
                val selected = DrawerBackgroundKind.entries[index]
                if (selected != kind) selectSource(selected.sources.first())
            },
            variant = NgFloatingTabBarVariant.APPEARANCE_SELECTION,
            selectionColors = selectionColors,
        )
        if (kind.sources.size > 1) {
            NgFloatingTabBar(
                items = kind.sources.map { NgFloatingTabSpec(text = ngDrawerBackgroundSourceName(it)) },
                selectedIndex = kind.sources.indexOf(value.source),
                onTabSelected = { selectSource(kind.sources[it]) },
                variant = NgFloatingTabBarVariant.APPEARANCE_SELECTION,
                selectionColors = selectionColors,
            )
        }
        when (value.source) {
            "custom_color" -> {
                val dayColor = drawerBackgroundColor(value, colors, night = false)
                val nightColor = drawerBackgroundColor(value, colors, night = true)
                ThemeDayNightSettingsRow(
                    title = { night ->
                        stringResource(
                            if (night) R.string.ng_drawer_background_night_color
                            else R.string.ng_drawer_background_day_color,
                        )
                    },
                    summary = { night -> formatNgColor(if (night) nightColor else dayColor) },
                    onOpen = { colorNight = it },
                    showDayNightIcons = false,
                    trailing = { night ->
                        Box(
                            Modifier.size(26.dp)
                                .background(Color(if (night) nightColor else dayColor), CircleShape)
                                .border(1.dp, Color(NgTheme.colors.outlineVariant), CircleShape),
                        )
                    },
                )
                colorNight?.let { night ->
                    NgColorPickerSheet(
                        show = true,
                        initialColor = if (night) nightColor else dayColor,
                        resetColor = null,
                        showAlphaSlider = false,
                        onDismissRequest = { colorNight = null },
                        onSelectionConfirmed = { color, _ ->
                            colorNight = null
                            onProfileChanged(
                                value.updated(night, value.forNight(night).copy(backgroundColor = color)),
                            )
                            onProfileChangeFinished()
                        },
                    )
                }
            }
            "custom_image" -> ThemeDayNightSettingsRow(
                title = { night ->
                    stringResource(
                        if (night) R.string.ng_drawer_background_night_image
                        else R.string.ng_drawer_background_day_image,
                    )
                },
                summary = { night ->
                    val hasImage = !value.forNight(night).imagePath.isNullOrBlank()
                    stringResource(
                        if (hasImage) R.string.ng_drawer_background_change
                        else R.string.ng_drawer_background_choose,
                    )
                },
                onOpen = onSelectImage,
            )
        }
    }
}

@Composable
internal fun ngDrawerBackgroundSourceName(source: String): String = stringResource(
    when (source) {
        NgThemeDrawerProfile.THEME_COLOR -> R.string.ng_drawer_background_theme_color
        "custom_color" -> R.string.ng_drawer_background_custom_color
        "theme_image" -> R.string.ng_drawer_background_theme_image
        "custom_image" -> R.string.ng_drawer_background_custom_image
        else -> R.string.ng_visual_system_liquid_glass
    }
)

@Composable
private fun drawerBackgroundColor(profile: NgThemeDrawerProfile, colors: NgColorSystem, night: Boolean): Int {
    profile.forNight(night).backgroundColor?.let { return it }
    val context = LocalContext.current
    return remember(context, colors, night) {
        NgThemeResolver.resolve(context, colors, night).colors.background
    }
}
