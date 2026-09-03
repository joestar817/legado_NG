package io.legado.app.help.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

internal enum class NgThemeModeGroup {
    STANDARD,
    INTERNAL,
}

internal enum class NgThemePresentationMode {
    STANDARD,
    SOFT_GRADIENT,
    DYNAMIC_SCENE,
    EINK,
}

/**
 * Keeps the regular day/night choice independent from NG-owned presentation modes.
 *
 * E-Ink continues to use the legacy themeMode value so existing runtime gates remain valid.
 * Soft gradient uses a separate flag and temporarily renders through the light theme branch;
 * leaving the internal mode restores the untouched regular mode.
 */
internal object NgThemeModeStore {

    private const val PRESENTATION_STANDARD = "standard"
    private const val PRESENTATION_SOFT_GRADIENT = "soft_gradient"
    private const val PRESENTATION_DYNAMIC_SCENE = "dynamic_scene"
    private const val STANDARD_MODE_FOLLOW = "0"
    private const val STANDARD_MODE_DAY = "1"
    private val standardModes = setOf("0", "1", "2")

    fun current(context: Context): NgThemePresentationMode = when {
        AppConfig.isEInkMode -> NgThemePresentationMode.EINK
        context.getPrefString(
            PreferKey.ngThemePresentationMode,
            PRESENTATION_STANDARD,
        ) == PRESENTATION_SOFT_GRADIENT -> NgThemePresentationMode.SOFT_GRADIENT
        context.getPrefString(
            PreferKey.ngThemePresentationMode,
            PRESENTATION_STANDARD,
        ) == PRESENTATION_DYNAMIC_SCENE -> NgThemePresentationMode.DYNAMIC_SCENE
        else -> NgThemePresentationMode.STANDARD
    }

    fun currentGroup(context: Context): NgThemeModeGroup = when (current(context)) {
        NgThemePresentationMode.STANDARD -> NgThemeModeGroup.STANDARD
        NgThemePresentationMode.SOFT_GRADIENT,
        NgThemePresentationMode.DYNAMIC_SCENE,
        NgThemePresentationMode.EINK -> NgThemeModeGroup.INTERNAL
    }

    fun standardThemeMode(context: Context): String {
        val stored = context.getPrefString(PreferKey.ngStandardThemeMode)
        if (stored != null && stored in standardModes) return stored
        return AppConfig.themeMode.takeIf { it in standardModes } ?: STANDARD_MODE_FOLLOW
    }

    fun lastInternalMode(context: Context): NgThemePresentationMode {
        return when (context.getPrefString(PreferKey.ngInternalThemeMode)) {
            INTERNAL_MODE_EINK -> NgThemePresentationMode.EINK
            INTERNAL_MODE_SOFT_GRADIENT -> NgThemePresentationMode.SOFT_GRADIENT
            INTERNAL_MODE_DYNAMIC_SCENE -> NgThemePresentationMode.DYNAMIC_SCENE
            else -> if (AppConfig.isEInkMode) {
                NgThemePresentationMode.EINK
            } else {
                NgThemePresentationMode.SOFT_GRADIENT
            }
        }
    }

    fun activateGroup(context: Context, group: NgThemeModeGroup) {
        when (group) {
            NgThemeModeGroup.STANDARD -> activateStandard(context, standardThemeMode(context))
            NgThemeModeGroup.INTERNAL -> activateInternal(context, lastInternalMode(context))
        }
    }

    fun activateStandard(context: Context, themeMode: String) {
        val normalized = themeMode.takeIf { it in standardModes } ?: STANDARD_MODE_FOLLOW
        context.putPrefString(PreferKey.ngStandardThemeMode, normalized)
        context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_STANDARD)
        ThemeConfig.applyThemeMode(context, normalized)
    }

    fun activateInternal(context: Context, mode: NgThemePresentationMode) {
        captureStandardMode(context)
        when (mode) {
            NgThemePresentationMode.SOFT_GRADIENT -> {
                context.putPrefString(
                    PreferKey.ngThemePresentationMode,
                    PRESENTATION_SOFT_GRADIENT,
                )
                context.putPrefString(
                    PreferKey.ngInternalThemeMode,
                    INTERNAL_MODE_SOFT_GRADIENT,
                )
                ThemeConfig.applyThemeMode(context, STANDARD_MODE_DAY)
            }

            NgThemePresentationMode.DYNAMIC_SCENE -> {
                context.putPrefString(
                    PreferKey.ngThemePresentationMode,
                    PRESENTATION_DYNAMIC_SCENE,
                )
                context.putPrefString(
                    PreferKey.ngInternalThemeMode,
                    INTERNAL_MODE_DYNAMIC_SCENE,
                )
                ThemeConfig.applyThemeMode(context, standardThemeMode(context))
            }

            NgThemePresentationMode.EINK -> {
                context.putPrefString(PreferKey.ngThemePresentationMode, PRESENTATION_STANDARD)
                context.putPrefString(PreferKey.ngInternalThemeMode, INTERNAL_MODE_EINK)
                ThemeConfig.applyThemeMode(context, EINK_THEME_MODE)
            }

            NgThemePresentationMode.STANDARD -> activateStandard(
                context,
                standardThemeMode(context),
            )
        }
    }

    private fun captureStandardMode(context: Context) {
        if (current(context) != NgThemePresentationMode.STANDARD) return
        AppConfig.themeMode.takeIf { it in standardModes }?.let {
            context.putPrefString(PreferKey.ngStandardThemeMode, it)
        }
    }

    private const val INTERNAL_MODE_SOFT_GRADIENT = "soft_gradient"
    private const val INTERNAL_MODE_DYNAMIC_SCENE = "dynamic_scene"
    private const val INTERNAL_MODE_EINK = "eink"
    private const val EINK_THEME_MODE = "3"
}
