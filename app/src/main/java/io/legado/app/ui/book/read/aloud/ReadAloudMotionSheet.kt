package io.legado.app.ui.book.read.aloud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ListeningFireStyle
import io.legado.app.help.config.ListeningFluidType
import io.legado.app.help.config.ListeningMotionColorMode
import io.legado.app.help.config.ListeningMotionConfig
import io.legado.app.help.config.ListeningMotionEffect
import io.legado.app.help.config.ListeningMotionSettings
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt

@Composable
internal fun ReadAloudMotionSheetContent(
    state: ListeningMotionSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEffectChange: (ListeningMotionEffect) -> Unit,
    onFireStyleChange: (ListeningFireStyle) -> Unit,
    onFluidTypeChange: (ListeningFluidType) -> Unit,
    onColorModeChange: (ListeningMotionColorMode) -> Unit,
    onCustomColorChange: (Int) -> Unit,
    onIntensityPreview: (Int) -> Unit,
    onIntensityCommitted: () -> Unit,
) {
    var editingCustomColor by remember { mutableStateOf(false) }
    NgBottomDrawerSurface(modifier = Modifier.fillMaxWidth()) {
        if (editingCustomColor) {
            NgInlineColorPicker(
                title = stringResource(R.string.listening_motion_custom_color),
                initialColor = state.customColor,
                onBack = { editingCustomColor = false },
                onColorChanged = onCustomColorChange,
                onReset = {
                    onCustomColorChange(ListeningMotionConfig.DEFAULT_CUSTOM_COLOR)
                },
            )
            return@NgBottomDrawerSurface
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.listening_motion_title),
                navigationIconRes = R.drawable.ic_arrow_back,
                navigationContentDescription = stringResource(R.string.back),
                onNavigationClick = onBack,
                centerTitle = true,
            )
            NgFormSwitchSettingRow(
                title = stringResource(R.string.listening_motion_enable),
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.padding(top = 2.dp),
            )
            MotionEffectRail(
                selected = state.effect,
                enabled = state.enabled,
                onEffectChange = onEffectChange,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (state.effect == ListeningMotionEffect.FLUID) {
                Text(
                    text = stringResource(R.string.listening_motion_fluid_type),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                FluidTypeRail(
                    selected = state.fluidType,
                    enabled = state.enabled,
                    onTypeChange = onFluidTypeChange,
                )
            }
            if (state.effect == ListeningMotionEffect.FLAME) {
                Text(
                    text = stringResource(R.string.listening_motion_fire_style),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                FireStyleRail(
                    selected = state.fireStyle,
                    enabled = state.enabled,
                    onStyleChange = onFireStyleChange,
                )
                Text(
                    text = stringResource(R.string.listening_motion_color),
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                MotionColorModeRail(
                    selected = state.colorMode,
                    enabled = state.enabled,
                    onModeChange = onColorModeChange,
                )
                if (state.colorMode == ListeningMotionColorMode.CUSTOM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = state.enabled) { editingCustomColor = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(state.customColor))
                                .border(
                                    width = .6.dp,
                                    color = Color(NgTheme.colors.outlineVariant),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                        )
                        Text(
                            text = stringResource(R.string.listening_motion_custom_color),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            text = stringResource(R.string.edit),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.30f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.listening_motion_intensity),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = stringResource(
                        R.string.listening_motion_intensity_value,
                        state.intensity,
                    ),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            }
            NgSlider(
                value = state.intensity.toFloat(),
                onValueChange = {
                    onIntensityPreview(it.roundToInt().coerceIn(0, 100))
                },
                valueRange = 0f..100f,
                enabled = state.enabled,
                variant = NgSliderVariant.CONTINUOUS,
                onValueChangeFinished = onIntensityCommitted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MotionColorModeRail(
    selected: ListeningMotionColorMode,
    enabled: Boolean,
    onModeChange: (ListeningMotionColorMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else .42f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ListeningMotionColorMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Color(NgTheme.colors.selectedContainer).copy(alpha = .76f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = if (isSelected) 1.dp else .6.dp,
                        color = if (isSelected) {
                            Color(NgTheme.colors.primary).copy(alpha = .78f)
                        } else {
                            Color(NgTheme.colors.outlineVariant).copy(alpha = .28f)
                        },
                        shape = shape,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onModeChange(mode) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label(),
                    color = if (isSelected) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurface)
                    },
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FireStyleRail(
    selected: ListeningFireStyle,
    enabled: Boolean,
    onStyleChange: (ListeningFireStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.42f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ListeningFireStyle.entries.forEach { style ->
            val isSelected = style == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Color(NgTheme.colors.selectedContainer).copy(alpha = 0.76f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.6.dp,
                        color = if (isSelected) {
                            Color(NgTheme.colors.primary).copy(alpha = 0.78f)
                        } else {
                            Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f)
                        },
                        shape = shape,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onStyleChange(style) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = style.label(),
                    color = if (isSelected) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurface)
                    },
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FluidTypeRail(
    selected: ListeningFluidType,
    enabled: Boolean,
    onTypeChange: (ListeningFluidType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.42f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ListeningFluidType.entries.forEach { type ->
            val isSelected = type == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Color(NgTheme.colors.selectedContainer).copy(alpha = 0.76f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.6.dp,
                        color = if (isSelected) {
                            Color(NgTheme.colors.primary).copy(alpha = 0.78f)
                        } else {
                            Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f)
                        },
                        shape = shape,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onTypeChange(type) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = type.label(),
                    color = if (isSelected) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurface)
                    },
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MotionEffectRail(
    selected: ListeningMotionEffect,
    enabled: Boolean,
    onEffectChange: (ListeningMotionEffect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.42f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ListeningMotionEffect.entries.forEach { effect ->
            val isSelected = effect == selected
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(82.dp)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            Color(NgTheme.colors.selectedContainer).copy(alpha = 0.72f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) {
                            Color(NgTheme.colors.primary).copy(alpha = 0.74f)
                        } else {
                            Color.Transparent
                        },
                        shape = shape,
                    )
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onEffectChange(effect) },
                    )
                    .padding(horizontal = 2.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = effect.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSelected) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurface)
                    },
                )
                Text(
                    text = effect.label(),
                    modifier = Modifier.padding(top = 7.dp),
                    color = if (isSelected) {
                        Color(NgTheme.colors.primary)
                    } else {
                        Color(NgTheme.colors.onSurface)
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun ListeningMotionEffect.icon(): ImageVector = when (this) {
    ListeningMotionEffect.FLAME -> Icons.Rounded.LocalFireDepartment
    ListeningMotionEffect.FLUID -> Icons.Rounded.WaterDrop
}

@Composable
internal fun ListeningMotionEffect.label(): String = stringResource(
    when (this) {
        ListeningMotionEffect.FLAME -> R.string.listening_motion_flame
        ListeningMotionEffect.FLUID -> R.string.listening_motion_fluid
    }
)

@Composable
internal fun ListeningFireStyle.label(): String = stringResource(
    when (this) {
        ListeningFireStyle.GODFIRE -> R.string.listening_motion_fire_godfire
        ListeningFireStyle.INFERNO -> R.string.listening_motion_fire_inferno
        ListeningFireStyle.RIFT -> R.string.listening_motion_fire_rift
    }
)

@Composable
internal fun ListeningFluidType.label(): String = stringResource(
    when (this) {
        ListeningFluidType.SMOKE -> R.string.listening_motion_fluid_smoke
        ListeningFluidType.WATER -> R.string.listening_motion_fluid_water
        ListeningFluidType.EDGE -> R.string.listening_motion_fluid_edge
    }
)

@Composable
private fun ListeningMotionColorMode.label(): String = stringResource(
    when (this) {
        ListeningMotionColorMode.ORIGINAL -> R.string.listening_motion_color_original
        ListeningMotionColorMode.COVER -> R.string.listening_motion_color_cover
        ListeningMotionColorMode.CUSTOM -> R.string.listening_motion_color_custom
    }
)
