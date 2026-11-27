package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadFloatingAppearanceConfig
import io.legado.app.help.config.ReadFloatingColorStyle
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassStyle

internal object ReadFloatingAppearanceState {

    private val transparencyState = mutableIntStateOf(
        ReadBookConfig.durConfig.curReadFloatingTransparency()
    )
    private val primaryStrengthState = mutableIntStateOf(
        ReadBookConfig.durConfig.curReadFloatingPrimaryStrength()
    )
    private val colorStyleState = mutableStateOf(
        ReadBookConfig.effectiveReadFloatingColor().colorStyle
    )
    private val liquidGlassState = mutableStateOf(ReadBookConfig.readFloatingLiquidGlassEnabled)

    val transparencyPercent: Int
        get() = transparencyState.intValue

    val primaryStrengthPercent: Int
        get() = primaryStrengthState.intValue

    val colorStyle: ReadFloatingColorStyle
        get() = colorStyleState.value

    val liquidGlassEnabled: Boolean
        get() = liquidGlassState.value

    val visualSystem: NgVisualSystem
        get() = if (liquidGlassEnabled) NgVisualSystem.LIQUID_GLASS else NgVisualSystem.TRANSPARENT_GLASS

    fun setLiquidGlassEnabled(enabled: Boolean) {
        ReadBookConfig.readFloatingLiquidGlassEnabled = enabled
        liquidGlassState.value = enabled
    }

    fun update(
        transparencyPercent: Int,
        primaryStrengthPercent: Int,
        colorStyle: ReadFloatingColorStyle,
    ) {
        transparencyState.intValue = ReadFloatingAppearanceConfig.normalizePercent(
            transparencyPercent
        )
        primaryStrengthState.intValue = ReadFloatingAppearanceConfig.normalizePercent(
            primaryStrengthPercent
        )
        colorStyleState.value = colorStyle
    }

    fun refreshFromConfig() {
        liquidGlassState.value = ReadBookConfig.readFloatingLiquidGlassEnabled
        update(
            transparencyPercent = ReadBookConfig.durConfig.curReadFloatingTransparency(),
            primaryStrengthPercent = ReadBookConfig.durConfig.curReadFloatingPrimaryStrength(),
            colorStyle = ReadBookConfig.effectiveReadFloatingColor().colorStyle,
        )
    }
}

@Composable
internal fun readFloatingGlassStyle(
    transparencyPercent: Int = ReadFloatingAppearanceState.transparencyPercent,
    primaryStrengthPercent: Int = ReadFloatingAppearanceState.primaryStrengthPercent,
    colorStyle: ReadFloatingColorStyle = ReadFloatingAppearanceState.colorStyle,
): NgGlassStyle = NgGlassDefaults.floatingStyle(
    transparencyPercent = transparencyPercent,
    primaryStrengthPercent = primaryStrengthPercent,
    colorStyle = colorStyle,
)
