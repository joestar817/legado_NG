package io.legado.app.ui.book.read.config

import androidx.lifecycle.ViewModel
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig

/** UI targets retain the original configuration objects across picker results and rotation. */
internal class ReadStyleEditorSession : ViewModel() {
    var presetMode: ReadAppearanceMode? = null
    var highlightMode: Int? = null
    var backgroundTarget: ReadStyleEditTarget? = null
    var colorTarget: ReadStyleEditTarget? = null
    var allowEInk: Boolean = false

    fun selectPresetMode(index: Int): ReadAppearanceMode = ReadAppearanceMode.fromIndex(index).also {
        presetMode = it
    }

    fun selectHighlightMode(index: Int): Int = index.coerceIn(0, 1).also {
        highlightMode = it
    }
}

internal class ReadStyleEditTarget(
    val appearance: ReadBookConfig.Config,
    val layout: ReadBookConfig.Config,
    val mode: ReadAppearanceMode,
    val bookUrl: String?,
) {
    fun renderBackground(width: Int, height: Int) = renderEditorReadBackground(this, width, height)
}
