package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.materialkolor.hct.Hct
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import splitties.init.appCtx

enum class PaletteFamily {
    WARM_PAPER,
    COOL_DAY,
    EYE_CARE_GREEN,
    NIGHT_DIM,
    OLED_DARK,
}

enum class ReadingDisplayMode {
    LIGHT,
    DARK,
    EINK,
}

/**
 * Content-centric paper seed. Oklch L/C/h drive tokens; HCT repairs contrast.
 * [warmth] is -1 cool cyan to +1 warm orange. [contrastTarget] 0 soft (~7:1) to 1 hard (~13:1).
 * [chromaScale] is paper chroma; reading stays at or below ~0.04.
 */
data class ReadingPaletteSeed(
    val id: String,
    val family: PaletteFamily,
    val targetHue: Float,
    val warmth: Float = 0f,
    val contrastTarget: Float = 0.5f,
    val chromaScale: Float = 0.02f,
)

data class ReadingSemanticTokens(
    @ColorInt val background: Int,
    @ColorInt val surface: Int,
    @ColorInt val text: Int,
    @ColorInt val secondaryText: Int,
    @ColorInt val accent: Int,
    @ColorInt val highlightBg: Int,
    @ColorInt val divider: Int,
)

internal object ReadingPaletteCatalog {

    const val DEFAULT_ID = "natural"

    val DEFAULT_ENABLED_IDS = listOf("natural", "paper", "ink")

    val seeds: List<ReadingPaletteSeed> = listOf(
        ReadingPaletteSeed(
            id = "natural",
            family = PaletteFamily.WARM_PAPER,
            targetHue = 75f,
            warmth = 0.4f,
            contrastTarget = 0.5f,
            chromaScale = 0.015f,
        ),
        ReadingPaletteSeed(
            id = "paper",
            family = PaletteFamily.WARM_PAPER,
            targetHue = 65f,
            warmth = 0.8f,
            contrastTarget = 0.35f,
            chromaScale = 0.03f,
        ),
        ReadingPaletteSeed(
            id = "ink",
            family = PaletteFamily.NIGHT_DIM,
            targetHue = 60f,
            warmth = 0.1f,
            contrastTarget = 0.7f,
            chromaScale = 0.005f,
        ),
        ReadingPaletteSeed(
            id = "mist",
            family = PaletteFamily.COOL_DAY,
            targetHue = 220f,
            warmth = -0.6f,
            contrastTarget = 0.35f,
            chromaScale = 0.01f,
        ),
        ReadingPaletteSeed(
            id = "forest",
            family = PaletteFamily.EYE_CARE_GREEN,
            targetHue = 135f,
            warmth = 0.2f,
            contrastTarget = 0.45f,
            chromaScale = 0.022f,
        ),
        ReadingPaletteSeed(
            id = "midnight",
            family = PaletteFamily.OLED_DARK,
            targetHue = 240f,
            warmth = -0.5f,
            contrastTarget = 0.55f,
            chromaScale = 0.018f,
        ),
    )

    private val byId = seeds.associateBy { it.id }

    fun seed(id: String): ReadingPaletteSeed? = byId[id]

    fun baseId(labelKey: String): String? {
        val id = labelKey.substringBefore('-')
        return if (id in byId) id else null
    }

    @StringRes
    fun labelRes(id: String): Int? = when (baseId(id) ?: id) {
        "natural" -> R.string.read_palette_natural
        "paper" -> R.string.read_palette_paper
        "ink" -> R.string.read_palette_ink
        "mist" -> R.string.read_palette_mist
        "forest" -> R.string.read_palette_forest
        "midnight" -> R.string.read_palette_midnight
        else -> null
    }

    fun seedsFor(ids: Collection<String>): List<ReadingPaletteSeed> {
        val wanted = ids.mapNotNull { byId[it] }
        return wanted.ifEmpty { DEFAULT_ENABLED_IDS.mapNotNull { byId[it] } }
    }

    fun enabledIds(): List<String> {
        val raw = runCatching {
            appCtx.getPrefString(PreferKey.ngReadPaletteEnabledIds)
        }.getOrNull().orEmpty()
        val parsed = raw.split(',').map { it.trim() }.filter { it in byId }
        return parsed.ifEmpty { DEFAULT_ENABLED_IDS }
    }

    fun setEnabledIds(ids: Collection<String>) {
        val next = ids.filter { it in byId }.ifEmpty { DEFAULT_ENABLED_IDS }
        runCatching {
            appCtx.putPrefString(PreferKey.ngReadPaletteEnabledIds, next.joinToString(","))
        }
    }

    fun toggleEnabled(id: String): List<String> {
        if (id !in byId) return enabledIds()
        val current = enabledIds().toMutableList()
        if (current.contains(id)) {
            if (current.size <= 1) return current
            current.remove(id)
        } else {
            current.add(id)
        }
        setEnabledIds(current)
        return enabledIds()
    }

    fun enable(id: String): List<String> {
        if (id !in byId) return enabledIds()
        val current = enabledIds()
        if (id in current) return current
        setEnabledIds(current + id)
        return enabledIds()
    }
}

internal object SemanticPaletteEngine {

    fun modeFor(isNight: Boolean, isEink: Boolean): ReadingDisplayMode = when {
        isEink -> ReadingDisplayMode.EINK
        isNight -> ReadingDisplayMode.DARK
        else -> ReadingDisplayMode.LIGHT
    }

    fun deriveTokens(
        seed: ReadingPaletteSeed,
        mode: ReadingDisplayMode,
    ): ReadingSemanticTokens {
        val raw = when (mode) {
            ReadingDisplayMode.LIGHT -> deriveLight(seed)
            ReadingDisplayMode.DARK -> deriveDark(seed)
            ReadingDisplayMode.EINK -> deriveEInk(seed)
        }
        return gate(raw, mode)
    }

    fun lookFor(
        seed: ReadingPaletteSeed,
        isNight: Boolean,
        isEink: Boolean,
        labelKey: String = seed.id,
    ): NgPaperLook? {
        val tokens = deriveTokens(seed, modeFor(isNight, isEink))
        return PaperSenseGenerator.sanitize(
            look = NgPaperLook(
                labelKey = labelKey,
                background = tokens.background,
                foreground = tokens.text,
                accent = tokens.accent,
                highlight = tokens.highlightBg,
                contrastRatio = 0.0,
                seed = seed,
            ),
            isNight = isNight,
            isEink = isEink,
        )
    }

    fun jitter(seed: ReadingPaletteSeed, random: Random): ReadingPaletteSeed {
        fun delta(span: Float) = (random.nextFloat() * 2f - 1f) * span
        return seed.copy(
            targetHue = (seed.targetHue + delta(8f) + 360f) % 360f,
            warmth = (seed.warmth + delta(0.15f)).coerceIn(-1f, 1f),
            contrastTarget = (seed.contrastTarget + delta(0.12f)).coerceIn(0f, 1f),
            chromaScale = (seed.chromaScale * (1f + delta(0.15f))).coerceIn(0f, 0.04f),
        )
    }

    private fun deriveLight(seed: ReadingPaletteSeed): ReadingSemanticTokens {
        val bgL = 0.965f - (seed.warmth * 0.015f)
        val bgC = seed.chromaScale.coerceAtMost(0.025f)
        val bg = oklch(bgL, bgC, seed.targetHue)
        val textL = 0.26f - (seed.contrastTarget * 0.06f)
        val textC = (seed.chromaScale * 0.3f).coerceAtMost(0.008f)
        val text = oklch(textL, textC, seed.targetHue)
        val secondary = oklch(0.52f, textC * 0.5f, seed.targetHue)
        val surface = oklch(bgL - 0.035f, bgC * 1.2f, seed.targetHue)
        val accent = oklch(0.45f, 0.07f, accentHue(seed))
        val highlight = oklch(0.90f, 0.06f, 85f)
        val divider = NgColorMath.withAlpha(text, 0.12f)
        return ReadingSemanticTokens(bg, surface, text, secondary, accent, highlight, divider)
    }

    private fun deriveDark(seed: ReadingPaletteSeed): ReadingSemanticTokens {
        val bgL = 0.16f + (seed.warmth * 0.012f)
        val bgC = (seed.chromaScale * 0.4f).coerceAtMost(0.012f)
        val bg = oklch(bgL.coerceIn(0.12f, 0.20f), bgC, seed.targetHue)
        val textL = 0.74f + (seed.contrastTarget * 0.05f)
        val text = oklch(textL.coerceIn(0.68f, 0.80f), 0.005f, seed.targetHue)
        val secondary = oklch(0.48f, 0.003f, seed.targetHue)
        val surface = oklch((bgL + 0.05f).coerceIn(0.14f, 0.26f), bgC, seed.targetHue)
        val accent = oklch(0.62f, 0.05f, accentHue(seed))
        val highlight = oklch(0.30f, 0.04f, 65f)
        val divider = NgColorMath.withAlpha(text, 0.15f)
        return ReadingSemanticTokens(bg, surface, text, secondary, accent, highlight, divider)
    }

    private fun deriveEInk(seed: ReadingPaletteSeed): ReadingSemanticTokens {
        val contrastLift = (seed.contrastTarget * 10f).roundToInt()
        val bg = gray(0xFA - (seed.warmth * 4f).roundToInt().coerceIn(0, 8))
        val text = gray(0x18 + (8 - contrastLift).coerceIn(0, 12))
        val secondary = gray(0x66)
        val highlight = gray(0xD6)
        val divider = gray(0xCC)
        return ReadingSemanticTokens(bg, bg, text, secondary, text, highlight, divider)
    }

    private fun gate(
        tokens: ReadingSemanticTokens,
        mode: ReadingDisplayMode,
    ): ReadingSemanticTokens {
        if (mode == ReadingDisplayMode.EINK) {
            return tokens.copy(
                background = NgColorMath.opaque(tokens.background),
                surface = NgColorMath.opaque(tokens.surface),
                text = NgColorMath.opaque(tokens.text),
                secondaryText = NgColorMath.opaque(tokens.secondaryText),
                accent = NgColorMath.opaque(tokens.accent),
                highlightBg = NgColorMath.opaque(tokens.highlightBg),
            )
        }
        val isNight = mode == ReadingDisplayMode.DARK
        val min = if (isNight) PaperSenseGenerator.NIGHT_MIN_CONTRAST else PaperSenseGenerator.DAY_MIN_CONTRAST
        val max = if (isNight) PaperSenseGenerator.NIGHT_MAX_CONTRAST else PaperSenseGenerator.DAY_MAX_CONTRAST
        val bg = NgColorMath.opaque(tokens.background)
        val text = NgColorMath.fixForegroundContrast(
            foreground = tokens.text,
            background = bg,
            minContrast = min,
            maxContrast = max,
            isNight = isNight,
        )
        val secondary = NgColorMath.fixForegroundContrast(
            foreground = tokens.secondaryText,
            background = bg,
            minContrast = 4.5,
            maxContrast = if (isNight) 8.0 else 7.0,
            isNight = isNight,
        )
        return tokens.copy(
            background = bg,
            surface = NgColorMath.opaque(tokens.surface),
            text = NgColorMath.opaque(text),
            secondaryText = NgColorMath.opaque(secondary),
            accent = NgColorMath.opaque(tokens.accent),
            highlightBg = NgColorMath.opaque(tokens.highlightBg),
        )
    }

    /**
     * Restrained same-family or cool-blue links. Not hue+180 — that saturates
     * complementary reds/greens and fails long-form reading.
     */
    internal fun accentHue(seed: ReadingPaletteSeed): Float = when (seed.family) {
        PaletteFamily.WARM_PAPER, PaletteFamily.NIGHT_DIM -> 232f
        PaletteFamily.COOL_DAY, PaletteFamily.OLED_DARK -> seed.targetHue
        PaletteFamily.EYE_CARE_GREEN -> 148f
    }

    private fun gray(channel: Int): Int {
        val c = channel.coerceIn(0, 255)
        return packArgb(255, c, c, c)
    }
}

internal fun oklch(l: Float, c: Float, hDeg: Float): Int {
    val h = Math.toRadians(hDeg.toDouble())
    val a = c.toDouble() * cos(h)
    val b = c.toDouble() * sin(h)
    val l_ = l + 0.3963377774 * a + 0.2158037573 * b
    val m_ = l - 0.1055613458 * a - 0.0638541728 * b
    val s_ = l - 0.0894841775 * a - 1.2914855480 * b
    val l3 = l_ * l_ * l_
    val m3 = m_ * m_ * m_
    val s3 = s_ * s_ * s_
    val rLin = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val gLin = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bLin = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3
    return packArgb(
        255,
        srgbChannel(rLin),
        srgbChannel(gLin),
        srgbChannel(bLin),
    )
}

/** JVM unit tests stub `android.graphics.Color`; pack ARGB ourselves. */
internal fun packArgb(alpha: Int, red: Int, green: Int, blue: Int): Int {
    return (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)
}

private fun srgbChannel(linear: Double): Int {
    val clamped = linear.coerceIn(0.0, 1.0)
    val encoded = if (clamped <= 0.0031308) {
        12.92 * clamped
    } else {
        1.055 * clamped.pow(1.0 / 2.4) - 0.055
    }
    return (encoded * 255.0).roundToInt().coerceIn(0, 255)
}

internal fun accentHueOf(@ColorInt color: Int): Double = Hct.fromInt(NgColorMath.opaque(color)).hue
