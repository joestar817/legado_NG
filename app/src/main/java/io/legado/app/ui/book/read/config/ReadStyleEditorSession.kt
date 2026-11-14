package io.legado.app.ui.book.read.config

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig

/** UI targets retain the original configuration objects across picker results and rotation. */
internal class ReadStyleEditorSession : ViewModel() {
    var presetMode: ReadAppearanceMode? by mutableStateOf(null)
    var highlightMode: Int? = null
    var backgroundTarget: ReadStyleEditTarget? = null
    var backgroundLibraryTarget: ReadStyleEditTarget? = null
    var importingBackground: Boolean by mutableStateOf(false)
    var colorTarget: ReadStyleEditTarget? = null
    var floatingColorSnapshot: ReadFloatingColorSnapshot? = null
    var allowEInk: Boolean = false

    fun selectPresetMode(index: Int): ReadAppearanceMode = ReadAppearanceMode.fromIndex(index).also {
        presetMode = it
    }

    fun selectHighlightMode(index: Int): Int = index.coerceIn(0, 1).also {
        highlightMode = it
    }
}

/** Only the edited mode is restored; a null night-follow flag is meaningful. */
internal data class ReadFloatingColorSnapshot(
    val mode: ReadAppearanceMode,
    val seed: Int,
    val nightFollowApp: Boolean?,
) {
    fun restore(config: ReadBookConfig.Config) {
        when (mode) {
            ReadAppearanceMode.DAY -> config.readFloatingSeed = seed
            ReadAppearanceMode.NIGHT -> {
                config.readFloatingSeedNight = seed
                config.readFloatingFollowAppNight = nightFollowApp
            }
            ReadAppearanceMode.EINK -> Unit
        }
    }

    companion object {
        fun capture(config: ReadBookConfig.Config, mode: ReadAppearanceMode) = ReadFloatingColorSnapshot(
            mode = mode,
            seed = config.readFloatingSeedForMode(mode),
            nightFollowApp = config.readFloatingFollowAppNight,
        )
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
