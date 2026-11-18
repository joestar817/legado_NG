package io.legado.app.help.config

import io.legado.app.R
import io.legado.app.utils.defaultSharedPreferences
import splitties.init.appCtx
import java.util.UUID

/** UI models; persistence uses explicit primitive preference keys, without reflection. */
internal data class ParagraphCommentBubble(val id: String, val name: String, val svg: String)

internal data class ParagraphCommentBubbleLibrary(
    val bubbles: List<ParagraphCommentBubble>,
    val selectedId: String?,
    val enabled: Boolean = false,
    val appearance: ParagraphCommentIconConfig = ParagraphCommentIconConfig(),
) {
    fun select(id: String): ParagraphCommentBubbleLibrary =
        if (bubbles.any { it.id == id }) copy(selectedId = id) else this

    fun put(bubble: ParagraphCommentBubble, select: Boolean): ParagraphCommentBubbleLibrary {
        val exists = bubbles.any { it.id == bubble.id }
        val updated = if (exists) bubbles.map { if (it.id == bubble.id) bubble else it } else bubbles + bubble
        return copy(bubbles = updated, selectedId = if (select) bubble.id else selectedId)
    }

    fun remove(id: String): ParagraphCommentBubbleLibrary {
        val index = bubbles.indexOfFirst { it.id == id }
        if (index < 0) return this
        val remaining = bubbles.filterNot { it.id == id }
        val selected = if (selectedId == id) remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id else selectedId
        return copy(bubbles = remaining, selectedId = selected, enabled = enabled && remaining.isNotEmpty())
    }

    fun restore(defaults: List<ParagraphCommentBubble>): ParagraphCommentBubbleLibrary = copy(
        bubbles = defaults,
        selectedId = selectedId?.takeIf { id -> defaults.any { it.id == id } } ?: defaults.firstOrNull()?.id,
        enabled = enabled && defaults.isNotEmpty(),
    )

    fun changesReadingFrom(previous: ParagraphCommentBubbleLibrary): Boolean =
        enabled != previous.enabled ||
            (enabled && (appearance != previous.appearance || selectedSvg() != previous.selectedSvg()))

    private fun selectedSvg(): String? = bubbles.firstOrNull { it.id == selectedId }?.svg
}

internal object ParagraphCommentBubbleSettings {
    private const val PREFIX = "paragraphCommentBubbles."
    private const val ORDER = PREFIX + "order"
    private const val SELECTED = PREFIX + "selected"
    private const val ENABLED = PREFIX + "enabled"

    fun defaults(): List<ParagraphCommentBubble> {
        val names = listOf(R.string.paragraph_icon_rounded, R.string.paragraph_icon_dialog, R.string.paragraph_icon_minimal)
        return ParagraphCommentIconShape.entries.mapIndexed { index, shape ->
            ParagraphCommentBubble("default-${shape.name.lowercase()}", appCtx.getString(names[index]) + ".svg",
                ParagraphCommentIconSettings.builtin(shape))
        }
    }

    fun newBubble(name: String, svg: String): ParagraphCommentBubble =
        ParagraphCommentBubble(UUID.randomUUID().toString(), name, svg)

    fun load(): ParagraphCommentBubbleLibrary {
        val preferences = appCtx.defaultSharedPreferences
        val order = preferences.getString(ORDER, null)
        val bubbles = if (order == null) defaults() else order.split('\n').filter { it.isNotBlank() }.mapNotNull { id ->
            val name = preferences.getString(PREFIX + id + ".name", null) ?: return@mapNotNull null
            val svg = preferences.getString(PREFIX + id + ".svg", null) ?: return@mapNotNull null
            ParagraphCommentBubble(id, name, svg)
        }
        val selected = preferences.getString(SELECTED, bubbles.firstOrNull()?.id)
            ?.takeIf { id -> bubbles.any { it.id == id } }
        return ParagraphCommentBubbleLibrary(bubbles, selected,
            preferences.getBoolean(ENABLED, false) && selected != null, ParagraphCommentIconSettings.loadAppearance())
    }

    /** Chapter layout reads only the active SVG, regardless of the library size. */
    fun loadConfig(): ParagraphCommentIconConfig {
        val preferences = appCtx.defaultSharedPreferences
        val appearance = ParagraphCommentIconSettings.loadAppearance()
        if (!preferences.getBoolean(ENABLED, false)) return appearance
        val id = preferences.getString(SELECTED, null) ?: return appearance
        val bubble = if (!preferences.contains(ORDER)) defaults().find { it.id == id } else {
            if (id !in preferences.getString(ORDER, "").orEmpty().split('\n')) return appearance
            val svg = preferences.getString(PREFIX + id + ".svg", null) ?: return appearance
            val name = preferences.getString(PREFIX + id + ".name", "").orEmpty()
            ParagraphCommentBubble(id, name, svg)
        } ?: return appearance
        return appearance.copy(source = ParagraphCommentIconSource.CUSTOM, customName = bubble.name, customSvg = bubble.svg)
    }

    fun save(previous: ParagraphCommentBubbleLibrary, library: ParagraphCommentBubbleLibrary) {
        val preferences = appCtx.defaultSharedPreferences
        val editor = preferences.edit()
        if (previous.bubbles !== library.bubbles) {
            val savedOrder = preferences.getString(ORDER, null)
            val oldBubbles = if (savedOrder == null) emptyMap() else previous.bubbles.associateBy { it.id }
            val ids = library.bubbles.mapTo(linkedSetOf()) { it.id }
            savedOrder?.lineSequence()?.filter { it.isNotBlank() && it !in ids }?.forEach { id ->
                editor.remove(PREFIX + id + ".name").remove(PREFIX + id + ".svg")
            }
            for (bubble in library.bubbles) {
                if (oldBubbles[bubble.id] != bubble) {
                    editor.putString(PREFIX + bubble.id + ".name", bubble.name)
                        .putString(PREFIX + bubble.id + ".svg", bubble.svg)
                }
            }
            editor.putString(ORDER, ids.joinToString("\n"))
        }
        editor.putString(SELECTED, library.selectedId)
            .putBoolean(ENABLED, library.enabled)
        ParagraphCommentIconSettings.saveAppearance(editor, library.appearance)
        editor.apply()
    }
}
