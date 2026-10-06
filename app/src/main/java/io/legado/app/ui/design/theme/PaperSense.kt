package io.legado.app.ui.design.theme

import androidx.annotation.ColorInt
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import kotlin.random.Random

enum class NgColorPickerTab {
    PALETTE,
    WHEEL,
    CONTINUOUS,
}

enum class NgColorPickerSlot {
    TEXT,
    BACKGROUND,
    ACCENT,
    HIGHLIGHT,
    GENERIC,
}

data class NgPaperLook(
    val labelKey: String,
    @ColorInt val background: Int,
    @ColorInt val foreground: Int,
    @ColorInt val accent: Int,
    @ColorInt val highlight: Int,
    val contrastRatio: Double,
    val seed: ReadingPaletteSeed? = null,
) {
    fun colorFor(slot: NgColorPickerSlot): Int = when (slot) {
        NgColorPickerSlot.BACKGROUND -> background
        NgColorPickerSlot.ACCENT -> accent
        NgColorPickerSlot.HIGHLIGHT -> highlight
        NgColorPickerSlot.TEXT, NgColorPickerSlot.GENERIC -> foreground
    }
}

internal object PaperSenseGenerator {

    const val DAY_MIN_CONTRAST = 8.5
    const val DAY_MAX_CONTRAST = 13.5
    const val NIGHT_MIN_CONTRAST = 7.0
    const val NIGHT_MAX_CONTRAST = 11.0
    const val AA_FLOOR = 4.5

    private const val DAY_ACCENT = 0xFF2B579A.toInt()
    private const val NIGHT_ACCENT = 0xFF6B8FB5.toInt()
    private const val NIGHT_HIGHLIGHT = 0xFF4A3F1E.toInt()

    fun generate(
        isNight: Boolean,
        isEink: Boolean,
        count: Int = 4,
        random: Random = Random.Default,
        seedIds: Collection<String> = ReadingPaletteCatalog.DEFAULT_ENABLED_IDS,
    ): List<NgPaperLook> {
        val seeds = ReadingPaletteCatalog.seedsFor(seedIds)
        val accepted = LinkedHashMap<String, NgPaperLook>()
        seeds.forEach { seed ->
            val look = SemanticPaletteEngine.lookFor(seed, isNight, isEink) ?: return@forEach
            admit(accepted, look, isNight, isEink)
        }
        var attempts = 0
        while (accepted.size < count && attempts < 40) {
            attempts++
            val seed = seeds[random.nextInt(seeds.size)]
            val variant = SemanticPaletteEngine.jitter(seed, random)
            val look = SemanticPaletteEngine.lookFor(
                seed = variant,
                isNight = isNight,
                isEink = isEink,
                labelKey = "${seed.id}-var",
            ) ?: continue
            admit(accepted, look, isNight, isEink)
        }
        return accepted.values.take(count.coerceAtLeast(1))
    }

    fun sanitize(
        look: NgPaperLook,
        isNight: Boolean,
        isEink: Boolean,
    ): NgPaperLook? {
        if (isEink && NgColorMath.relativeLuminance(look.background) < 0.4) return null
        val nightMode = isNight && !isEink
        val min = if (nightMode) NIGHT_MIN_CONTRAST else DAY_MIN_CONTRAST
        val max = if (nightMode) NIGHT_MAX_CONTRAST else DAY_MAX_CONTRAST
        val fixedFg = NgColorMath.fixForegroundContrast(
            foreground = look.foreground,
            background = look.background,
            minContrast = min,
            maxContrast = max,
            isNight = nightMode,
        )
        val ratio = NgColorMath.displayedContrast(fixedFg, look.background)
        if (ratio < AA_FLOOR) return null
        if (ratio > 21.0) return null
        val accent = NgColorMath.opaque(if (look.accent == 0) accentFor(nightMode) else look.accent)
        val highlight = NgColorMath.opaque(
            if (look.highlight == 0) highlightFor(nightMode) else look.highlight
        )
        return look.copy(
            background = NgColorMath.opaque(look.background),
            foreground = NgColorMath.opaque(fixedFg),
            accent = accent,
            highlight = highlight,
            contrastRatio = ratio,
        )
    }

    private fun admit(
        accepted: MutableMap<String, NgPaperLook>,
        candidate: NgPaperLook,
        isNight: Boolean,
        isEink: Boolean,
    ) {
        val sanitized = sanitize(candidate, isNight, isEink) ?: return
        val key = "${sanitized.labelKey}:${sanitized.background}:${sanitized.foreground}"
        accepted.putIfAbsent(key, sanitized)
    }

    private fun accentFor(isNight: Boolean): Int = if (isNight) NIGHT_ACCENT else DAY_ACCENT

    private fun highlightFor(isNight: Boolean): Int =
        if (isNight) NIGHT_HIGHLIGHT else 0xFFFDF3B8.toInt()
}

internal data class AiPaperLookDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("hue") val hue: Float? = null,
    @SerializedName("warmth") val warmth: Float? = null,
    @SerializedName("contrast") val contrast: Float? = null,
    @SerializedName("chroma") val chroma: Float? = null,
    @SerializedName("bg") val bg: String = "",
    @SerializedName("fg") val fg: String = "",
    @SerializedName("accent") val accent: String = "",
)

internal fun parseAiPaperLooks(
    raw: String,
    isNight: Boolean,
    isEink: Boolean,
): List<NgPaperLook> {
    val json = extractJsonArray(raw) ?: return emptyList()
    val dtos = runCatching {
        GSON.fromJson(json, Array<AiPaperLookDto>::class.java)
    }.getOrNull() ?: return emptyList()
    return dtos.mapNotNull { dto ->
        val named = resolveAiSeed(dto)
        if (named != null) {
            SemanticPaletteEngine.lookFor(named, isNight, isEink, labelKey = named.id)
        } else {
            val background = parseNgColor(dto.bg) ?: return@mapNotNull null
            val foreground = parseNgColor(dto.fg) ?: return@mapNotNull null
            val accent = parseNgColor(dto.accent) ?: 0
            PaperSenseGenerator.sanitize(
                look = NgPaperLook(
                    labelKey = dto.name.ifBlank { "generated" },
                    background = background,
                    foreground = foreground,
                    accent = accent,
                    highlight = 0,
                    contrastRatio = 0.0,
                ),
                isNight = isNight,
                isEink = isEink,
            )
        }
    }
}

private fun resolveAiSeed(dto: AiPaperLookDto): ReadingPaletteSeed? {
    val base = ReadingPaletteCatalog.seed(dto.id)
        ?: ReadingPaletteCatalog.seeds.firstOrNull { seed ->
            dto.name.contains(seed.id, ignoreCase = true)
        }
        ?: return null
    return base.copy(
        targetHue = dto.hue ?: base.targetHue,
        warmth = dto.warmth ?: base.warmth,
        contrastTarget = dto.contrast ?: base.contrastTarget,
        chromaScale = dto.chroma ?: base.chromaScale,
    )
}

private fun extractJsonArray(raw: String): String? {
    val start = raw.indexOf('[')
    val end = raw.lastIndexOf(']')
    if (start < 0 || end <= start) return null
    return raw.substring(start, end + 1)
}
