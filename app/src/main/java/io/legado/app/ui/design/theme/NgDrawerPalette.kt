package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import com.materialkolor.hct.Hct
import io.legado.app.help.config.NgDrawerAppearanceConfig
import kotlin.math.abs
import kotlin.math.min

internal data class NgDrawerSurfaceColors(
    @param:ColorInt val top: Int,
    @param:ColorInt val bottom: Int,
)

internal data class NgDrawerSemanticColors(
    @param:ColorInt val content: Int,
    @param:ColorInt val secondaryContent: Int,
    @param:ColorInt val indicator: Int,
    @param:ColorInt val onIndicator: Int,
    @param:ColorInt val action: Int,
    @param:ColorInt val outline: Int,
)

/**
 * 全局 NG 抽屉自己的大面积材质色板。
 *
 * 抽屉继续只消费当前 NG 主题的承载色与 [NgColorScheme.surfaceTint]，但不复用阅读
 * 浮窗偏轻薄的 RGB 混色曲线。这里在 HCT 中保留主题色相，以受控的色度和明度变化
 * 提高暖色、冷色在大面积抽屉上的一致表现；100% 仍是安全的浓郁材质，不会退化成
 * 整面原始主色。
 */
internal object NgDrawerPalette {

    private const val OPAQUE_WHITE = -0x1
    private const val DARK_CONTENT_CARD_LIFT = 0.12f
    private const val TEXT_MIN_CONTRAST = 4.5
    private const val CONTROL_MIN_CONTRAST = 3.0
    private const val CONTROL_MAX_CONTRAST = 4.2

    private const val LIGHT_TOP_CHROMA_CAP = 32.0
    private const val LIGHT_BOTTOM_CHROMA_CAP = 42.0
    private const val DARK_TOP_CHROMA_CAP = 24.0
    private const val DARK_BOTTOM_CHROMA_CAP = 32.0

    private const val TOP_CHROMA_REACH = 0.68
    private const val BOTTOM_CHROMA_REACH = 0.86
    private const val TOP_TONE_REACH = 0.28
    private const val BOTTOM_TONE_REACH = 0.44
    private const val TOP_HUE_SHIFT_REACH = 0.68
    private const val BOTTOM_HUE_SHIFT_REACH = 1.0

    /**
     * 红橙主题在高明度大面积表面上会比同色的小面积控件更容易呈现粉感。
     * 这里只对暖色高明度表面做很小的顺时针色相补偿，让材质保持橙色观感；
     * 冷色、低明度和主题本身的语义色均不受影响。
     */
    private const val WARM_SURFACE_HUE_CENTER = 48.0
    private const val WARM_SURFACE_HUE_RADIUS = 42.0
    private const val WARM_SURFACE_MAX_HUE_SHIFT = 18.0
    private const val WARM_SURFACE_TONE_START = 58.0
    private const val WARM_SURFACE_TONE_RANGE = 34.0

    fun resolveSurfaceColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
    ): NgDrawerSurfaceColors {
        val colors = snapshot.colors
        val neutralTop = NgColorMath.blend(
            colors.drawerContainer,
            colors.surface,
            if (snapshot.isDark) 0.16f else 0.24f,
        )
        val strength = NgDrawerAppearanceConfig.strengthFraction(primaryStrengthPercent)
        if (snapshot.isEInk || strength == 0.0) {
            return NgDrawerSurfaceColors(
                top = neutralTop,
                bottom = colors.drawerContainer,
            )
        }
        val seed = Hct.fromInt(NgColorMath.opaque(colors.surfaceTint))
        return NgDrawerSurfaceColors(
            top = resolveSurfaceTone(
                neutral = neutralTop,
                seed = seed,
                strength = strength,
                chromaReach = TOP_CHROMA_REACH,
                chromaCap = if (snapshot.isDark) {
                    DARK_TOP_CHROMA_CAP
                } else {
                    LIGHT_TOP_CHROMA_CAP
                },
                toneReach = TOP_TONE_REACH,
                hueShiftReach = TOP_HUE_SHIFT_REACH,
            ),
            bottom = resolveSurfaceTone(
                neutral = colors.drawerContainer,
                seed = seed,
                strength = strength,
                chromaReach = BOTTOM_CHROMA_REACH,
                chromaCap = if (snapshot.isDark) {
                    DARK_BOTTOM_CHROMA_CAP
                } else {
                    LIGHT_BOTTOM_CHROMA_CAP
                },
                toneReach = BOTTOM_TONE_REACH,
                hueShiftReach = BOTTOM_HUE_SHIFT_REACH,
            ),
        )
    }

    fun resolveSemanticColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
    ): NgDrawerSemanticColors {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        return resolveSemanticColors(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
            backgrounds = intArrayOf(surfaces.top, surfaces.bottom),
        )
    }

    private fun resolveSemanticColors(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        backgrounds: IntArray,
    ): NgDrawerSemanticColors {
        val colors = snapshot.colors
        val strength = NgDrawerAppearanceConfig.strengthFraction(primaryStrengthPercent)
        val content = findContrastingTone(
            preferred = colors.onSurface,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = false,
        )
        val secondaryContent = findContrastingTone(
            preferred = colors.onSurfaceVariant,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = false,
        )
        val indicatorContrast = CONTROL_MIN_CONTRAST +
            (CONTROL_MAX_CONTRAST - CONTROL_MIN_CONTRAST) * strength
        val indicator = findContrastingTone(
            preferred = colors.primary,
            backgrounds = backgrounds,
            contrastThreshold = indicatorContrast,
            preserveChroma = true,
        )
        val action = findContrastingTone(
            preferred = colors.secondary,
            backgrounds = backgrounds,
            contrastThreshold = TEXT_MIN_CONTRAST,
            preserveChroma = true,
        )
        val outline = findContrastingTone(
            preferred = colors.outline,
            backgrounds = backgrounds,
            contrastThreshold = CONTROL_MIN_CONTRAST,
            preserveChroma = false,
        )
        return NgDrawerSemanticColors(
            content = content,
            secondaryContent = secondaryContent,
            indicator = indicator,
            onIndicator = NgColorMath.contentColorFor(indicator),
            action = action,
            outline = outline,
        )
    }

    fun applySemanticRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
    ): NgThemeSnapshot = applySemanticRoles(
        snapshot = snapshot,
        primaryStrengthPercent = primaryStrengthPercent,
        contentCardContainer = null,
    )

    /** 日间白卡在夜间抽屉中的轻量、同色相承载面。 */
    fun applyAdaptiveContentCardRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
    ): NgThemeSnapshot = applySemanticRoles(
        snapshot = snapshot,
        primaryStrengthPercent = primaryStrengthPercent,
        contentCardContainer = resolveAdaptiveContentCardColor(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
        ),
    )

    @ColorInt
    fun resolveAdaptiveContentCardColor(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
    ): Int {
        if (!snapshot.isDark || snapshot.isEInk) return OPAQUE_WHITE
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        return NgColorMath.blend(
            surfaces.bottom,
            OPAQUE_WHITE,
            DARK_CONTENT_CARD_LIFT,
        )
    }

    private fun applySemanticRoles(
        snapshot: NgThemeSnapshot,
        primaryStrengthPercent: Int,
        @ColorInt contentCardContainer: Int?,
    ): NgThemeSnapshot {
        val surfaces = resolveSurfaceColors(snapshot, primaryStrengthPercent)
        val backgrounds = if (contentCardContainer == null) {
            intArrayOf(surfaces.top, surfaces.bottom)
        } else {
            intArrayOf(surfaces.top, surfaces.bottom, contentCardContainer)
        }
        val semantic = resolveSemanticColors(
            snapshot = snapshot,
            primaryStrengthPercent = primaryStrengthPercent,
            backgrounds = backgrounds,
        )
        val colors = snapshot.colors
        val indicatorContainer = NgColorMath.blend(
            surfaces.bottom,
            semantic.indicator,
            if (snapshot.isDark) 0.28f else 0.14f,
        )
        return snapshot.copy(
            colors = colors.copy(
                primary = semantic.indicator,
                onPrimary = semantic.onIndicator,
                primaryContainer = indicatorContainer,
                onPrimaryContainer = NgColorMath.contentColorFor(indicatorContainer),
                secondary = semantic.action,
                onSurface = semantic.content,
                onSurfaceVariant = semantic.secondaryContent,
                outline = semantic.outline,
                outlineVariant = NgColorMath.blend(
                    surfaces.bottom,
                    semantic.outline,
                    0.48f,
                ),
                onTopBar = semantic.content,
                cardContainer = contentCardContainer ?: colors.cardContainer,
                selectedContainer = indicatorContainer,
            )
        )
    }

    @ColorInt
    private fun resolveSurfaceTone(
        @ColorInt neutral: Int,
        seed: Hct,
        strength: Double,
        chromaReach: Double,
        chromaCap: Double,
        toneReach: Double,
        hueShiftReach: Double,
    ): Int {
        val base = Hct.fromInt(NgColorMath.opaque(neutral))
        val targetChroma = min(seed.chroma * chromaReach, chromaCap)
        val chroma = base.chroma + (targetChroma - base.chroma) * strength
        val tone = base.tone + (seed.tone - base.tone) * toneReach * strength
        val hue = resolveSurfaceHue(
            seedHue = seed.hue,
            baseTone = base.tone,
            strength = strength,
            hueShiftReach = hueShiftReach,
        )
        return Hct.from(hue, chroma, tone).toInt()
    }

    private fun resolveSurfaceHue(
        seedHue: Double,
        baseTone: Double,
        strength: Double,
        hueShiftReach: Double,
    ): Double {
        val warmWeight = (
            1.0 - hueDistance(seedHue, WARM_SURFACE_HUE_CENTER) /
                WARM_SURFACE_HUE_RADIUS
            ).coerceIn(0.0, 1.0)
        val highToneWeight = (
            (baseTone - WARM_SURFACE_TONE_START) / WARM_SURFACE_TONE_RANGE
            ).coerceIn(0.0, 1.0)
        return (
            seedHue + WARM_SURFACE_MAX_HUE_SHIFT * warmWeight * highToneWeight *
                strength * hueShiftReach
            ) % 360.0
    }

    private fun hueDistance(first: Double, second: Double): Double {
        val direct = abs(first - second)
        return min(direct, 360.0 - direct)
    }

    @ColorInt
    private fun findContrastingTone(
        @ColorInt preferred: Int,
        backgrounds: IntArray,
        contrastThreshold: Double,
        preserveChroma: Boolean,
    ): Int {
        val opaquePreferred = NgColorMath.opaque(preferred)
        if (minimumContrast(opaquePreferred, backgrounds) >= contrastThreshold) {
            return opaquePreferred
        }
        val source = Hct.fromInt(opaquePreferred)
        val chromaSteps = if (preserveChroma) {
            doubleArrayOf(1.0, 0.75, 0.5, 0.25, 0.0)
        } else {
            doubleArrayOf(0.0)
        }
        chromaSteps.forEach { chromaFraction ->
            val candidate = (0..100)
                .asSequence()
                .map { tone ->
                    val color = Hct.from(
                        source.hue,
                        source.chroma * chromaFraction,
                        tone.toDouble(),
                    ).toInt()
                    Triple(color, tone, minimumContrast(color, backgrounds))
                }
                .filter { it.third >= contrastThreshold }
                .minWithOrNull(
                    compareBy<Triple<Int, Int, Double>>(
                        { abs(it.second - source.tone) },
                        { -it.third },
                    )
                )
            if (candidate != null) return candidate.first
        }
        return if (
            minimumContrast(0xFF000000.toInt(), backgrounds) >=
            minimumContrast(0xFFFFFFFF.toInt(), backgrounds)
        ) {
            0xFF000000.toInt()
        } else {
            0xFFFFFFFF.toInt()
        }
    }

    private fun minimumContrast(
        @ColorInt foreground: Int,
        backgrounds: IntArray,
    ): Double = backgrounds.minOf { background ->
        NgColorMath.contrastRatio(foreground, background)
    }
}
