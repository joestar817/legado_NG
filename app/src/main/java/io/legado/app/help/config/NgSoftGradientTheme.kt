package io.legado.app.help.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgColorSpec
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.ui.design.theme.NgContrastLevel
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgPaletteStyle
import io.legado.app.ui.design.theme.NgTopBarTextMode
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlin.math.abs

internal enum class NgSoftGradientColorPreset(val storageValue: String) {
    CLEAR_BLUE("clear_blue"),
    DUSK_VIOLET("dusk_violet");

    companion object {
        fun fromStorage(value: String?): NgSoftGradientColorPreset =
            entries.firstOrNull { it.storageValue == value } ?: CLEAR_BLUE
    }
}

internal enum class NgSoftGradientLightFieldPreset(val storageValue: String) {
    BALANCED("balanced"),
    CLEAR("clear"),
    STILL_SEA("still_sea"),
    AQUA("aqua"),
    FLOW_SHADOW("flow_shadow");

    companion object {
        fun fromStorage(value: String?): NgSoftGradientLightFieldPreset =
            entries.firstOrNull { it.storageValue == value } ?: BALANCED
    }
}

internal enum class NgThemeGradientMotion {
    NONE,
    FLOW_SHADOW,
}

internal data class NgThemeGradientRadialLayer(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    /** 相对于光心到画布最远角距离的半径倍率。 */
    val radius: Float = 1f,
    val colors: List<Int> = emptyList(),
    val stops: List<Float> = emptyList(),
) {
    fun normalized(): NgThemeGradientRadialLayer? {
        val normalizedColors = colors.take(MAX_COLORS)
        if (normalizedColors.size < MIN_COLORS || !radius.isFinite()) return null
        return copy(
            centerX = centerX.finiteOrDefault(0.5f).coerceIn(MIN_CENTER, MAX_CENTER),
            centerY = centerY.finiteOrDefault(0.5f).coerceIn(MIN_CENTER, MAX_CENTER),
            radius = radius.coerceIn(MIN_RADIUS, MAX_RADIUS),
            colors = normalizedColors,
            stops = normalizedGradientStops(stops, normalizedColors.size),
        )
    }

    private companion object {
        const val MIN_COLORS = 2
        const val MAX_COLORS = 6
        const val MIN_CENTER = -1f
        const val MAX_CENTER = 2f
        const val MIN_RADIUS = 0.05f
        const val MAX_RADIUS = 2f
    }
}

internal data class NgThemeGradientProfile(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 1f,
    val endY: Float = 1f,
    val colors: List<Int> = emptyList(),
    val stops: List<Float> = emptyList(),
    val radialLayers: List<NgThemeGradientRadialLayer> = emptyList(),
    val motion: NgThemeGradientMotion = NgThemeGradientMotion.NONE,
) {
    fun normalized(): NgThemeGradientProfile? {
        val normalizedColors = colors.take(MAX_COLORS)
        if (normalizedColors.size < MIN_COLORS) return null
        val normalizedStartX = startX.finiteOrDefault(0f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        val normalizedStartY = startY.finiteOrDefault(0f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        var normalizedEndX = endX.finiteOrDefault(1f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        var normalizedEndY = endY.finiteOrDefault(1f)
            .coerceIn(MIN_COORDINATE, MAX_COORDINATE)
        if (
            abs(normalizedEndX - normalizedStartX) < MIN_VECTOR_LENGTH &&
            abs(normalizedEndY - normalizedStartY) < MIN_VECTOR_LENGTH
        ) {
            normalizedEndX = normalizedStartX
            normalizedEndY = if (normalizedStartY <= 1f) {
                normalizedStartY + 1f
            } else {
                normalizedStartY - 1f
            }
        }
        return copy(
            startX = normalizedStartX,
            startY = normalizedStartY,
            endX = normalizedEndX,
            endY = normalizedEndY,
            colors = normalizedColors,
            stops = normalizedGradientStops(stops, normalizedColors.size),
            radialLayers = radialLayers.take(MAX_RADIAL_LAYERS)
                .mapNotNull(NgThemeGradientRadialLayer::normalized),
        )
    }

    private companion object {
        const val MIN_COLORS = 2
        const val MAX_COLORS = 8
        const val MAX_RADIAL_LAYERS = 8
        const val MIN_COORDINATE = -1f
        const val MAX_COORDINATE = 2f
        const val MIN_VECTOR_LENGTH = 0.001f
    }
}

internal object NgSoftGradientTheme {

    /** Neutral selected surface shared by every soft-gradient color preset. */
    internal val selectedContainer = 0xF2FFFFFF.toInt()

    fun colorPreset(context: Context): NgSoftGradientColorPreset =
        NgSoftGradientColorPreset.fromStorage(
            context.getPrefString(PreferKey.ngSoftGradientColor),
        )

    fun lightFieldPreset(context: Context): NgSoftGradientLightFieldPreset =
        NgSoftGradientLightFieldPreset.fromStorage(
            context.getPrefString(PreferKey.ngSoftGradientLightField),
        )

    fun selectColor(context: Context, preset: NgSoftGradientColorPreset) {
        context.putPrefString(PreferKey.ngSoftGradientColor, preset.storageValue)
        reapplyIfActive(context)
    }

    fun selectLightField(context: Context, preset: NgSoftGradientLightFieldPreset) {
        context.putPrefString(PreferKey.ngSoftGradientLightField, preset.storageValue)
        reapplyIfActive(context)
    }

    fun colors(context: Context): NgColorSystem = when (colorPreset(context)) {
        NgSoftGradientColorPreset.CLEAR_BLUE -> clearBlueColors
        NgSoftGradientColorPreset.DUSK_VIOLET -> duskVioletColors
    }

    fun gradient(context: Context): NgThemeGradientProfile = when (colorPreset(context)) {
        NgSoftGradientColorPreset.CLEAR_BLUE -> clearBlueGradients.getValue(
            lightFieldPreset(context),
        )
        NgSoftGradientColorPreset.DUSK_VIOLET -> duskVioletGradients.getValue(
            lightFieldPreset(context),
        )
    }

    private fun reapplyIfActive(context: Context) {
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.SOFT_GRADIENT) {
            ThemeConfig.applyThemeMode(context, DAY_THEME_MODE)
        }
    }

    private val clearBlueManualColors = NgManualColorSet(
        primary = 0xFF0A74A8.toInt(),
        secondary = 0xFFFFFFFF.toInt(),
        primaryText = 0xFF11181C.toInt(),
        secondaryText = 0xFF48616C.toInt(),
        background = 0xFFF4F8FA.toInt(),
        labelContainer = 0xFFE5F0F5.toInt(),
    )

    private val clearBlueColors = NgColorSystem(
        mode = NgColorGenerationMode.MANUAL,
        lightSeed = 0xFF0A74A8.toInt(),
        darkSeed = 0xFF0A74A8.toInt(),
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        manualLight = clearBlueManualColors,
        manualDark = clearBlueManualColors,
        lightTopBarTextMode = NgTopBarTextMode.LIGHT,
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
    )

    private val duskVioletManualColors = NgManualColorSet(
        primary = 0xFF7352B5.toInt(),
        secondary = 0xFFFFFFFF.toInt(),
        primaryText = 0xFF1D1722.toInt(),
        secondaryText = 0xFF66566F.toInt(),
        background = 0xFFFAF6FA.toInt(),
        labelContainer = 0xFFF0E7F3.toInt(),
    )

    private val duskVioletColors = NgColorSystem(
        mode = NgColorGenerationMode.MANUAL,
        lightSeed = 0xFF7352B5.toInt(),
        darkSeed = 0xFF7352B5.toInt(),
        paletteStyle = NgPaletteStyle.TONAL_SPOT,
        contrast = NgContrastLevel.DEFAULT,
        colorSpec = NgColorSpec.MATERIAL_3_2021,
        manualLight = duskVioletManualColors,
        manualDark = duskVioletManualColors,
        lightTopBarTextMode = NgTopBarTextMode.LIGHT,
        darkTopBarTextMode = NgTopBarTextMode.LIGHT,
    )

    private val clearBlueGradients = mapOf(
        NgSoftGradientLightFieldPreset.BALANCED to gradientProfile(
            baseColors = listOf(
                0xFF468FC3,
                0xFF5CB3D6,
                0xFF88D7E5,
                0xFFB8EDF1,
                0xFF50A7BC,
            ),
            stops = listOf(0f, 0.25f, 0.52f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.96f, 0.08f, 0.62f, 0x79316DA4, 0x00316DA4),
                radial(-0.12f, 0.30f, 0.70f, 0x735FCBE4, 0x005FCBE4),
                radial(0.46f, 0.63f, 0.58f, 0xA6D8F7F4, 0x00D8F7F4),
                radial(1.04f, 0.72f, 0.38f, 0x5D8DE6E7, 0x008DE6E7),
                radial(0.16f, 0.22f, 0.18f, 0x52E4FAF7, 0x00E4FAF7),
                radial(0.84f, 0.43f, 0.16f, 0x463C8CB7, 0x003C8CB7),
                radial(0.22f, 0.77f, 0.20f, 0x48C7F1EE, 0x00C7F1EE),
                vignette(0.50f, 0.52f, 0.78f, 0x3D145A72),
            ),
        ),
        NgSoftGradientLightFieldPreset.CLEAR to gradientProfile(
            baseColors = listOf(
                0xFF5DA5D2,
                0xFF72C2DE,
                0xFFA2E3EB,
                0xFFD0F5F3,
                0xFF70C2D0,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.78f, 1f),
            radialLayers = listOf(
                radial(1.02f, 0.06f, 0.60f, 0x57377FB3, 0x00377FB3),
                radial(-0.08f, 0.28f, 0.72f, 0x7A83DCEB, 0x0083DCEB),
                radial(0.44f, 0.62f, 0.60f, 0xB5E5FBF7, 0x00E5FBF7),
                radial(0.98f, 0.74f, 0.40f, 0x6CA8ECE8, 0x00A8ECE8),
                radial(0.18f, 0.21f, 0.20f, 0x5BF3FEFA, 0x00F3FEFA),
                radial(0.82f, 0.42f, 0.17f, 0x3D559FC3, 0x00559FC3),
                radial(0.24f, 0.78f, 0.21f, 0x55DCF8F2, 0x00DCF8F2),
                vignette(0.50f, 0.54f, 0.82f, 0x25176880),
            ),
        ),
        NgSoftGradientLightFieldPreset.STILL_SEA to gradientProfile(
            baseColors = listOf(
                0xFF347CB3,
                0xFF4598C2,
                0xFF6CBED2,
                0xFF9AD9DE,
                0xFF28798F,
            ),
            stops = listOf(0f, 0.26f, 0.53f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.60f, 0x9E1A4C89, 0x001A4C89),
                radial(-0.10f, 0.34f, 0.66f, 0x594EB8D8, 0x004EB8D8),
                radial(0.44f, 0.62f, 0.55f, 0x8ABCE9EA, 0x00BCE9EA),
                radial(1.02f, 0.72f, 0.36f, 0x526CC7D0, 0x006CC7D0),
                radial(0.16f, 0.23f, 0.17f, 0x49D5F2EE, 0x00D5F2EE),
                radial(0.84f, 0.43f, 0.15f, 0x55246192, 0x00246192),
                radial(0.22f, 0.77f, 0.19f, 0x42AEE2E3, 0x00AEE2E3),
                vignette(0.50f, 0.52f, 0.72f, 0x5C073F58),
            ),
        ),
        NgSoftGradientLightFieldPreset.AQUA to gradientProfile(
            baseColors = listOf(
                0xFF3D8DB5,
                0xFF52B4C7,
                0xFF7ED6D7,
                0xFFB2ECE2,
                0xFF2A8992,
            ),
            stops = listOf(0f, 0.25f, 0.52f, 0.79f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.60f, 0x6B2D6EA5, 0x002D6EA5),
                radial(-0.12f, 0.32f, 0.70f, 0x715AD2D9, 0x005AD2D9),
                radial(0.46f, 0.64f, 0.58f, 0xA7C9F6EA, 0x00C9F6EA),
                radial(1.04f, 0.70f, 0.38f, 0x6B8CE8D7, 0x008CE8D7),
                radial(0.16f, 0.22f, 0.18f, 0x50E5FAEE, 0x00E5FAEE),
                radial(0.84f, 0.42f, 0.16f, 0x473B809F, 0x003B809F),
                radial(0.22f, 0.78f, 0.20f, 0x4CC1F3DE, 0x00C1F3DE),
                vignette(0.50f, 0.53f, 0.78f, 0x3D065762),
            ),
        ),
    ).withFlowShadow()

    private val duskVioletGradients = mapOf(
        NgSoftGradientLightFieldPreset.BALANCED to gradientProfile(
            baseColors = listOf(
                0xFF4A478F,
                0xFF6D55A8,
                0xFF9C67B4,
                0xFFD38AB9,
                0xFF704E93,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.77f, 1f),
            radialLayers = listOf(
                radial(0.96f, 0.08f, 0.58f, 0xA02B285F, 0x002B285F),
                radial(-0.10f, 0.30f, 0.62f, 0x784E62C2, 0x004E62C2),
                radial(0.46f, 0.62f, 0.52f, 0xA6F2C4DE, 0x00F2C4DE),
                radial(1.02f, 0.72f, 0.34f, 0x756F2E76, 0x006F2E76),
                radial(0.16f, 0.22f, 0.17f, 0x62F8D9EB, 0x00F8D9EB),
                radial(0.84f, 0.43f, 0.15f, 0x5A3D215F, 0x003D215F),
                radial(0.22f, 0.77f, 0.18f, 0x59E395C1, 0x00E395C1),
                vignette(0.50f, 0.52f, 0.75f, 0x571F163D),
            ),
        ),
        NgSoftGradientLightFieldPreset.CLEAR to gradientProfile(
            baseColors = listOf(
                0xFF6860AA,
                0xFF8E6BB7,
                0xFFBC83C5,
                0xFFE9B2D0,
                0xFF8964A6,
            ),
            stops = listOf(0f, 0.24f, 0.50f, 0.78f, 1f),
            radialLayers = listOf(
                radial(1.00f, 0.06f, 0.58f, 0x6A40397E, 0x0040397E),
                radial(-0.08f, 0.28f, 0.66f, 0x716E75D2, 0x006E75D2),
                radial(0.44f, 0.62f, 0.56f, 0xB8F8DAE9, 0x00F8DAE9),
                radial(0.98f, 0.74f, 0.36f, 0x6B9A4B91, 0x009A4B91),
                radial(0.18f, 0.21f, 0.18f, 0x66FCE7F2, 0x00FCE7F2),
                radial(0.82f, 0.42f, 0.16f, 0x493F2D6B, 0x003F2D6B),
                radial(0.24f, 0.78f, 0.19f, 0x60F0B7D6, 0x00F0B7D6),
                vignette(0.50f, 0.54f, 0.80f, 0x3D2C1F4D),
            ),
        ),
        NgSoftGradientLightFieldPreset.STILL_SEA to gradientProfile(
            baseColors = listOf(
                0xFF35306F,
                0xFF504083,
                0xFF754893,
                0xFFA35D9B,
                0xFF46336D,
            ),
            stops = listOf(0f, 0.25f, 0.51f, 0.77f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.56f, 0xB01D194D, 0x001D194D),
                radial(-0.10f, 0.34f, 0.60f, 0x66504FA9, 0x00504FA9),
                radial(0.44f, 0.62f, 0.48f, 0x8BCE87BD, 0x00CE87BD),
                radial(1.02f, 0.72f, 0.32f, 0x763F1F61, 0x003F1F61),
                radial(0.16f, 0.23f, 0.15f, 0x55D7A8D1, 0x00D7A8D1),
                radial(0.84f, 0.43f, 0.14f, 0x66231148, 0x00231148),
                radial(0.22f, 0.77f, 0.17f, 0x55B968A8, 0x00B968A8),
                vignette(0.50f, 0.52f, 0.70f, 0x71130D35),
            ),
        ),
        NgSoftGradientLightFieldPreset.AQUA to gradientProfile(
            baseColors = listOf(
                0xFF464A98,
                0xFF665BB2,
                0xFF8A78C6,
                0xFFC5A2D9,
                0xFF55508A,
            ),
            stops = listOf(0f, 0.24f, 0.51f, 0.78f, 1f),
            radialLayers = listOf(
                radial(0.98f, 0.08f, 0.58f, 0x8A27275F, 0x0027275F),
                radial(-0.10f, 0.32f, 0.64f, 0x70526EC5, 0x00526EC5),
                radial(0.46f, 0.64f, 0.52f, 0x9DD5C7EA, 0x00D5C7EA),
                radial(1.02f, 0.70f, 0.34f, 0x68473582, 0x00473582),
                radial(0.16f, 0.22f, 0.16f, 0x5AD7E0F2, 0x00D7E0F2),
                radial(0.84f, 0.42f, 0.15f, 0x57322163, 0x00322163),
                radial(0.22f, 0.78f, 0.18f, 0x56D59BCB, 0x00D59BCB),
                vignette(0.50f, 0.53f, 0.75f, 0x55201842),
            ),
        ),
    ).withFlowShadow()

    private fun Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile>
        .withFlowShadow(): Map<NgSoftGradientLightFieldPreset, NgThemeGradientProfile> =
        this + (
            NgSoftGradientLightFieldPreset.FLOW_SHADOW to
                getValue(NgSoftGradientLightFieldPreset.BALANCED).copy(
                    motion = NgThemeGradientMotion.FLOW_SHADOW,
                )
        )

    private fun gradientProfile(
        baseColors: List<Long>,
        stops: List<Float>,
        radialLayers: List<NgThemeGradientRadialLayer>,
    ): NgThemeGradientProfile = requireNotNull(
        NgThemeGradientProfile(
            startX = 0.08f,
            startY = 0f,
            endX = 0.78f,
            endY = 1f,
            colors = baseColors.map(Long::toInt),
            stops = stops,
            radialLayers = radialLayers,
        ).normalized(),
    )

    private fun radial(
        centerX: Float,
        centerY: Float,
        radius: Float,
        innerColor: Long,
        outerColor: Long,
    ) = NgThemeGradientRadialLayer(
        centerX = centerX,
        centerY = centerY,
        radius = radius,
        colors = listOf(innerColor.toInt(), outerColor.toInt()),
    )

    private fun vignette(
        centerX: Float,
        centerY: Float,
        clearUntil: Float,
        edgeColor: Long,
    ) = NgThemeGradientRadialLayer(
        centerX = centerX,
        centerY = centerY,
        radius = 1f,
        colors = listOf(0x00000000, 0x00000000, edgeColor.toInt()),
        stops = listOf(0f, clearUntil, 1f),
    )

    private const val DAY_THEME_MODE = "1"
}

private fun Float.finiteOrDefault(default: Float): Float = if (isFinite()) this else default

private fun normalizedGradientStops(stops: List<Float>, colorCount: Int): List<Float> {
    if (
        stops.size == colorCount &&
        stops.all { it.isFinite() } &&
        stops.zipWithNext().all { (first, second) -> first <= second }
    ) {
        return stops.map { it.coerceIn(0f, 1f) }
    }
    if (colorCount <= 1) return listOf(0f)
    return List(colorCount) { index -> index.toFloat() / (colorCount - 1) }
}
