package io.legado.app.help.config

import com.google.gson.annotations.SerializedName
import io.legado.app.ui.design.theme.NgPaperLook
import io.legado.app.ui.design.theme.PaperSenseGenerator
import io.legado.app.ui.design.theme.ReadingPaletteCatalog
import io.legado.app.ui.design.theme.SemanticPaletteEngine
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * Persisted AI Theme card deck per reading surface (day / night / e-ink).
 * New generations append; deck is capped at [MAX_DECK_SIZE] with oldest entries dropped first.
 */
object AiThemeDeckStore {

    const val PREF_KEY = "aiThemeDeck.v1"
    const val MAX_DECK_SIZE = 16

    fun loadDeck(isNight: Boolean, isEink: Boolean): List<NgPaperLook> {
        val snapshot = readSnapshot()
        val stored = snapshot.deckFor(isNight, isEink)
        return stored.mapNotNull { it.toLook(isNight, isEink) }
    }

    fun saveDeck(isNight: Boolean, isEink: Boolean, looks: List<NgPaperLook>) {
        val trimmed = looks.takeLast(MAX_DECK_SIZE)
        val snapshot = readSnapshot()
        val updated = snapshot.withDeck(isNight, isEink, trimmed.map { it.toStored() })
        writeSnapshot(updated)
    }

    fun appendGenerated(
        isNight: Boolean,
        isEink: Boolean,
        current: List<NgPaperLook>,
        incoming: List<NgPaperLook>,
    ): List<NgPaperLook> {
        if (incoming.isEmpty()) return current
        val merged = mergeDeck(current, incoming)
        saveDeck(isNight, isEink, merged)
        return merged
    }

    fun defaultSeedDeck(isNight: Boolean, isEink: Boolean): List<NgPaperLook> {
        return PaperSenseGenerator.generate(
            isNight = isNight,
            isEink = isEink,
            seedIds = ReadingPaletteCatalog.enabledIds(),
        )
    }

    internal fun mergeDeck(
        existing: List<NgPaperLook>,
        incoming: List<NgPaperLook>,
        maxSize: Int = MAX_DECK_SIZE,
    ): List<NgPaperLook> {
        if (incoming.isEmpty()) return existing.takeLast(maxSize)
        val merged = existing + incoming
        return if (merged.size <= maxSize) merged else merged.takeLast(maxSize)
    }

    private fun readSnapshot(): AiThemeDeckSnapshot {
        return runCatching {
            appCtx.getPrefString(PREF_KEY)?.let { json ->
                GSON.fromJson(json, AiThemeDeckSnapshot::class.java)
            }
        }.getOrNull() ?: AiThemeDeckSnapshot()
    }

    private fun writeSnapshot(snapshot: AiThemeDeckSnapshot) {
        val empty = snapshot.day.isEmpty() && snapshot.night.isEmpty() && snapshot.eink.isEmpty()
        appCtx.putPrefString(PREF_KEY, if (empty) null else GSON.toJson(snapshot))
    }
}

internal data class AiThemeDeckSnapshot(
    @SerializedName("day") val day: List<StoredPaperLookDto> = emptyList(),
    @SerializedName("night") val night: List<StoredPaperLookDto> = emptyList(),
    @SerializedName("eink") val eink: List<StoredPaperLookDto> = emptyList(),
) {
    fun deckFor(isNight: Boolean, isEink: Boolean): List<StoredPaperLookDto> = when {
        isEink -> eink
        isNight -> night
        else -> day
    }

    fun withDeck(
        isNight: Boolean,
        isEink: Boolean,
        deck: List<StoredPaperLookDto>,
    ): AiThemeDeckSnapshot = when {
        isEink -> copy(eink = deck)
        isNight -> copy(night = deck)
        else -> copy(day = deck)
    }
}

internal data class StoredPaperLookDto(
    @SerializedName("labelKey") val labelKey: String = "",
    @SerializedName("background") val background: Int = 0,
    @SerializedName("foreground") val foreground: Int = 0,
    @SerializedName("accent") val accent: Int = 0,
    @SerializedName("highlight") val highlight: Int = 0,
    @SerializedName("contrastRatio") val contrastRatio: Double = 0.0,
    @SerializedName("seedId") val seedId: String? = null,
)

internal fun NgPaperLook.toStored(): StoredPaperLookDto = StoredPaperLookDto(
    labelKey = labelKey,
    background = background,
    foreground = foreground,
    accent = accent,
    highlight = highlight,
    contrastRatio = contrastRatio,
    seedId = seed?.id,
)

internal fun StoredPaperLookDto.toLook(isNight: Boolean, isEink: Boolean): NgPaperLook? {
    val seed = seedId?.let { ReadingPaletteCatalog.seed(it) }
    if (seed != null) {
        return SemanticPaletteEngine.lookFor(seed, isNight, isEink, labelKey = labelKey.ifBlank { seed.id })
    }
    if (background == 0 && foreground == 0) return null
    return NgPaperLook(
        labelKey = labelKey.ifBlank { "generated" },
        background = background,
        foreground = foreground,
        accent = accent,
        highlight = highlight,
        contrastRatio = contrastRatio,
        seed = null,
    )
}
