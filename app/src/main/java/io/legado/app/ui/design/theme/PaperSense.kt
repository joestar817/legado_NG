package io.legado.app.ui.design.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt
import com.google.gson.annotations.SerializedName
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.sysConfiguration
import java.util.Locale
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
    @SerializedName("text") val text: String = "",
    @SerializedName("fg") val fg: String = "",
    @SerializedName("secondaryText") val secondaryText: String = "",
    @SerializedName("highlight") val highlight: String = "",
    @SerializedName("accent") val accent: String = "",
)

internal const val AI_THEME_SYSTEM_PROMPT =
    "You are an expert in reading ergonomics, accessibility, color science, " +
        "and ebook reader UI design.\n\n" +
        "Generate 3 to 4 distinct reading color palettes tailored to the user's " +
        "intent. Prioritize comfortable long-form reading over visual novelty.\n\n" +
        "Honor CURRENT_MODE from the user message; return palettes for that mode only.\n\n" +
        "OUTPUT\n" +
        "- Return a pure JSON array only. No markdown, no commentary.\n" +
        "- Generate exactly 3 or 4 palettes.\n" +
        "- Each palette must contain:\n" +
        "{\n" +
        "  \"name\": \"2-4 word descriptive name in UI_LANGUAGE\",\n" +
        "  \"bg\": \"#RRGGBB\",\n" +
        "  \"text\": \"#RRGGBB\",\n" +
        "  \"secondaryText\": \"#RRGGBB\",\n" +
        "  \"highlight\": \"#RRGGBB\",\n" +
        "  \"accent\": \"#RRGGBB\"\n" +
        "}\n\n" +
        "COLOR PRINCIPLES\n\n" +
        "1. BODY READING COLORS\n" +
        "- Contrast between bg and text must be at least 7:1.\n" +
        "- Prefer approximately 8:1-12:1 for normal body text.\n" +
        "- Contrast must be produced primarily through luminance, not hue.\n" +
        "- Never use saturated red, green, or blue for body text.\n" +
        "- secondaryText must remain clearly distinguishable from the background " +
        "while being visually subordinate to body text.\n\n" +
        "2. DAY PALETTES\n" +
        "- Positive polarity: dark text on a light background.\n" +
        "- Use soft off-white, ivory, cream, paper, or similarly light surfaces.\n" +
        "- Avoid pure #FFFFFF and pure #000000.\n" +
        "- Prefer low-to-moderate chroma backgrounds.\n" +
        "- Warmth may vary between palettes.\n\n" +
        "3. NIGHT PALETTES\n" +
        "- Negative polarity: light text on a dark background.\n" +
        "- Use deep charcoal, warm slate, bistre, or similarly dark surfaces.\n" +
        "- Avoid pure #000000 and pure #FFFFFF.\n" +
        "- Prefer warm or neutral light text rather than cold pure white.\n" +
        "- Avoid bright saturated highlights.\n\n" +
        "4. HIGHLIGHT\n" +
        "- Must remain visually distinguishable from both bg and text.\n" +
        "- Day mode: prefer muted amber, honey, ochre, or similarly warm tones.\n" +
        "- Night mode: prefer dark amber, bronze, or muted warm tones.\n" +
        "- Never use an extremely bright highlight that dominates the reading surface.\n\n" +
        "5. ACCENT\n" +
        "- Accent should express the palette's identity.\n" +
        "- It may vary by palette family: natural, paper, sky, forest, lavender, ocean, etc.\n" +
        "- If accent is used for readable UI text, it must satisfy the required " +
        "text/background contrast.\n" +
        "- Do not sacrifice body-text readability to make palettes visually distinctive.\n" +
        "- Do not use hue+180 complementary accents.\n\n" +
        "6. PALETTE DISTINCTIVENESS\n" +
        "- The generated palettes must be meaningfully different.\n" +
        "- Vary hue family, warmth, chroma, and accent character.\n" +
        "- Do not generate four nearly identical beige/gray palettes.\n" +
        "- Keep the reading surface ergonomically conservative even when the accent is more expressive.\n\n" +
        "7. COLOR VALIDATION\n" +
        "- Treat relative luminance and contrast ratio as authoritative.\n" +
        "- Do not use HSL lightness as a substitute for luminance.\n" +
        "- Before returning a palette, verify the contrast requirements for text against bg.\n" +
        "- If a generated color fails, adjust it before returning the JSON.\n\n" +
        "8. CARD NAMES (name field)\n" +
        "- Honor UI_LANGUAGE from the user message: write every palette name in that language.\n" +
        "- Use 2-4 words, natural for readers of that language (not English unless UI_LANGUAGE is en).\n" +
        "- User preference text may be any language; only the name field follows UI_LANGUAGE.\n" +
        "- JSON keys and hex colors are unchanged."

internal fun readerUiLanguageTagForAiTheme(context: Context): String {
    val locale = when (context.getPrefString(PreferKey.language)) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "en" -> Locale.ENGLISH
        else -> systemLocaleForAiTheme()
    }
    return locale.toLanguageTag()
}

private fun systemLocaleForAiTheme(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return sysConfiguration.locales[0]
    }
    @Suppress("DEPRECATION")
    return sysConfiguration.locale
}

internal fun buildAiThemeUserPrompt(
    preference: String,
    isNight: Boolean,
    isEink: Boolean,
    uiLanguageTag: String,
): String {
    val mode = when {
        isEink -> "eink (positive polarity, paper-like day surface)"
        isNight -> "night"
        else -> "day"
    }
    val language = uiLanguageTag.trim().ifBlank { Locale.getDefault().toLanguageTag() }
    return "UI_LANGUAGE: $language\n" +
        "CURRENT_MODE: $mode\n\n" +
        "User preference:\n${preference.trim()}"
}

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
        val background = parseNgColor(dto.bg)
        val foreground = parseNgColor(dto.text.ifBlank { dto.fg })
        if (background != null && foreground != null) {
            val label = dto.name.ifBlank { "generated" }
            PaperSenseGenerator.sanitize(
                look = NgPaperLook(
                    labelKey = label,
                    background = background,
                    foreground = foreground,
                    accent = parseNgColor(dto.accent) ?: 0,
                    highlight = parseNgColor(dto.highlight) ?: 0,
                    contrastRatio = 0.0,
                ),
                isNight = isNight,
                isEink = isEink,
            )
        } else {
            val named = resolveAiSeed(dto) ?: return@mapNotNull null
            SemanticPaletteEngine.lookFor(named, isNight, isEink, labelKey = named.id)
        }
    }
}

private fun resolveAiSeed(dto: AiPaperLookDto): ReadingPaletteSeed? {
    val base = ReadingPaletteCatalog.seed(dto.id) ?: return null
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
