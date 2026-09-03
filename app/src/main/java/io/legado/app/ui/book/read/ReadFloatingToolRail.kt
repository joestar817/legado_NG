package io.legado.app.ui.book.read

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ReadThemeMode
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

internal enum class ReadFloatingToolExpansion {
    BRIGHTNESS,
    THEME,
    AI
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
    expansion: ReadFloatingToolExpansion?,
    brightness: Int,
    brightnessAutomatic: Boolean,
    autoPage: Boolean,
    nightMode: Boolean,
    themeMode: ReadThemeMode,
    onExpansionChange: (ReadFloatingToolExpansion?) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onToggleBrightnessAutomatic: () -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onAutoPage: () -> Unit,
    onThemeModeSelected: (ReadThemeMode) -> Unit,
    onAiPurify: () -> Unit,
    onAiSettings: () -> Unit,
    onToggleDockSide: () -> Unit
) {
    val dockRight = dockSide.isRight
    val panelVisible = expansion != null
    Row(verticalAlignment = Alignment.Top) {
        if (dockRight) {
            ToolExpansion(
                visible = panelVisible,
                dockRight = true,
                expansion = expansion,
                brightness = brightness,
                brightnessAutomatic = brightnessAutomatic,
                onBrightnessChange = onBrightnessChange,
                onBrightnessChangeFinished = onBrightnessChangeFinished,
                onToggleBrightnessAutomatic = onToggleBrightnessAutomatic,
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                onAiPurify = onAiPurify,
                onAiSettings = onAiSettings
            )
            if (panelVisible) Spacer(Modifier.width(8.dp))
        }

        ToolRail(
            expansion = expansion,
            autoPage = autoPage,
            nightMode = nightMode,
            onExpansionChange = onExpansionChange,
            onSearch = onSearch,
            onReplace = onReplace,
            onAutoPage = onAutoPage,
            onToggleDockSide = onToggleDockSide
        )

        if (!dockRight) {
            if (panelVisible) Spacer(Modifier.width(8.dp))
            ToolExpansion(
                visible = panelVisible,
                dockRight = false,
                expansion = expansion,
                brightness = brightness,
                brightnessAutomatic = brightnessAutomatic,
                onBrightnessChange = onBrightnessChange,
                onBrightnessChangeFinished = onBrightnessChangeFinished,
                onToggleBrightnessAutomatic = onToggleBrightnessAutomatic,
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                onAiPurify = onAiPurify,
                onAiSettings = onAiSettings
            )
        }
    }
}

@Composable
private fun ToolRail(
    expansion: ReadFloatingToolExpansion?,
    autoPage: Boolean,
    nightMode: Boolean,
    onExpansionChange: (ReadFloatingToolExpansion?) -> Unit,
    onSearch: () -> Unit,
    onReplace: () -> Unit,
    onAutoPage: () -> Unit,
    onToggleDockSide: () -> Unit
) {
    NgGlassSurface(
        modifier = Modifier.width(44.dp),
        shape = RoundedCornerShape(12.dp),
        style = readFloatingGlassStyle(),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        ToolButton(
            iconRes = R.drawable.ic_daytime,
            labelRes = R.string.brightness,
            selected = expansion == ReadFloatingToolExpansion.BRIGHTNESS,
            onClick = {
                onExpansionChange(
                    expansion.toggle(ReadFloatingToolExpansion.BRIGHTNESS)
                )
            }
        )
        ToolButton(
            iconRes = R.drawable.ic_search,
            labelRes = R.string.search_content,
            iconSize = 20.dp,
            onClick = onSearch
        )
        ToolButton(
            iconRes = R.drawable.ic_cfg_replace,
            labelRes = R.string.replace_rule_title,
            onClick = onReplace
        )
        ToolButton(
            iconRes = if (autoPage) {
                R.drawable.ic_auto_page_stop
            } else {
                R.drawable.ic_auto_page
            },
            labelRes = if (autoPage) R.string.auto_next_page_stop else R.string.auto_next_page,
            selected = autoPage,
            onClick = onAutoPage
        )
        ToolButton(
            iconRes = R.drawable.ic_brightness,
            labelRes = R.string.dark_theme,
            selected = nightMode || expansion == ReadFloatingToolExpansion.THEME,
            onClick = {
                onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.THEME))
            }
        )
        ToolButton(
            iconRes = R.drawable.ic_ai,
            labelRes = R.string.ai_actions,
            iconSize = 18.dp,
            selected = expansion == ReadFloatingToolExpansion.AI,
            onClick = {
                onExpansionChange(expansion.toggle(ReadFloatingToolExpansion.AI))
            }
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.45f)
        )
        ToolButton(
            iconRes = R.drawable.ic_swap_horiz,
            labelRes = R.string.adjust_pos,
            onClick = onToggleDockSide
        )
    }
}

@Composable
private fun ToolButton(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    iconSize: Dp = 22.dp,
    selected: Boolean = false
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
            .height(48.dp)
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
    }
}

@Composable
private fun ToolExpansion(
    visible: Boolean,
    dockRight: Boolean,
    expansion: ReadFloatingToolExpansion?,
    brightness: Int,
    brightnessAutomatic: Boolean,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: () -> Unit,
    onToggleBrightnessAutomatic: () -> Unit,
    themeMode: ReadThemeMode,
    onThemeModeSelected: (ReadThemeMode) -> Unit,
    onAiPurify: () -> Unit,
    onAiSettings: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            expandFrom = if (dockRight) Alignment.End else Alignment.Start
        ),
        exit = shrinkHorizontally(
            shrinkTowards = if (dockRight) Alignment.End else Alignment.Start
        )
    ) {
        Box(
            modifier = Modifier.height(357.dp),
            contentAlignment = when (expansion) {
                ReadFloatingToolExpansion.AI -> Alignment.BottomCenter
                else -> Alignment.TopCenter
            }
        ) {
            when (expansion) {
                ReadFloatingToolExpansion.BRIGHTNESS -> BrightnessPanel(
                    brightness = brightness,
                    automatic = brightnessAutomatic,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessChangeFinished = onBrightnessChangeFinished,
                    onToggleAutomatic = onToggleBrightnessAutomatic
                )

                ReadFloatingToolExpansion.THEME -> ThemeModePanel(
                    selectedMode = themeMode,
                    onModeSelected = onThemeModeSelected,
                    modifier = Modifier.padding(top = 200.dp),
                )

                ReadFloatingToolExpansion.AI -> AiPanel(
                    onAiPurify = onAiPurify,
                    onAiSettings = onAiSettings,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                null -> Unit
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
