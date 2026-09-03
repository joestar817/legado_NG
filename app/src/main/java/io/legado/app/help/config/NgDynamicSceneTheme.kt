package io.legado.app.help.config

import android.content.Context
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgColorSystem
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/** APK 内置 GLES 场景；与常规主题、背景图和主题包状态完全隔离。 */
internal object NgDynamicSceneTheme {

    val presets = listOf(
        ListeningCartoonType.SAKURA,
        ListeningCartoonType.CATS,
    )

    fun current(context: Context): ListeningCartoonType {
        return ListeningCartoonType.fromStorageOrNull(
            context.getPrefString(PreferKey.ngDynamicScenePreset),
        )?.takeIf { it in presets } ?: ListeningCartoonType.SAKURA
    }

    fun select(context: Context, preset: ListeningCartoonType) {
        require(preset in presets) { "Unsupported theme scene: ${preset.storageValue}" }
        context.putPrefString(PreferKey.ngDynamicScenePreset, preset.storageValue)
        if (NgThemeModeStore.current(context) == NgThemePresentationMode.DYNAMIC_SCENE) {
            ThemeConfig.applyThemeMode(context, NgThemeModeStore.standardThemeMode(context))
        }
    }

    fun theme(context: Context): NgManagedTheme = theme(current(context))

    fun theme(preset: ListeningCartoonType): NgManagedTheme = when (preset) {
        ListeningCartoonType.SAKURA -> NgBuiltInThemes.sakura
        ListeningCartoonType.CATS -> NgBuiltInThemes.cats
        ListeningCartoonType.RAIN_NIGHT -> error("Rain night is not a theme scene")
    }

    fun sceneProfile(context: Context): NgThemeSceneProfile =
        requireNotNull(theme(context).sceneProfile)

    fun colors(context: Context): NgColorSystem =
        readColors(context, current(context)) ?: theme(context).colors

    fun updateColors(context: Context, colors: NgColorSystem) {
        persistColors(context, current(context), colors.normalized())
        ThemeConfig.applyTheme(context)
    }

    fun migrateLegacyColorsIfCustomized(
        context: Context,
        preset: ListeningCartoonType,
        colors: NgColorSystem,
    ) {
        val normalized = colors.normalized()
        if (normalized != legacyDefaultColors(preset)) {
            persistColors(context, preset, normalized)
        }
    }

    fun fromLegacyThemeId(themeId: String?): ListeningCartoonType? = when (themeId) {
        NgBuiltInThemes.sakura.id -> ListeningCartoonType.SAKURA
        NgBuiltInThemes.cats.id -> ListeningCartoonType.CATS
        else -> null
    }

    private fun readColors(
        context: Context,
        preset: ListeningCartoonType,
    ): NgColorSystem? {
        val raw = context.getPrefString(colorPreferenceKey(preset)) ?: return null
        return runCatching {
            GSON.fromJson(raw, NgColorSystem::class.java).normalized()
        }.getOrNull()
    }

    private fun persistColors(
        context: Context,
        preset: ListeningCartoonType,
        colors: NgColorSystem,
    ) {
        context.putPrefString(colorPreferenceKey(preset), GSON.toJson(colors))
    }

    private fun colorPreferenceKey(preset: ListeningCartoonType): String = when (preset) {
        ListeningCartoonType.SAKURA -> PreferKey.ngDynamicSceneSakuraColors
        ListeningCartoonType.CATS -> PreferKey.ngDynamicSceneCatsColors
        ListeningCartoonType.RAIN_NIGHT -> error("Rain night is not a theme scene")
    }

    private fun legacyDefaultColors(preset: ListeningCartoonType): NgColorSystem {
        val primary = when (preset) {
            ListeningCartoonType.SAKURA -> LEGACY_SAKURA_PRIMARY
            ListeningCartoonType.CATS -> LEGACY_CATS_PRIMARY
            ListeningCartoonType.RAIN_NIGHT -> error("Rain night is not a theme scene")
        }
        val currentDefault = theme(preset).colors
        return currentDefault.copy(
            lightSeed = primary,
            darkSeed = primary,
            manualLight = currentDefault.manualLight.copy(primary = primary),
            manualDark = currentDefault.manualDark.copy(primary = primary),
        ).normalized()
    }

    private val LEGACY_SAKURA_PRIMARY = 0xFFFFA3D1.toInt()
    private val LEGACY_CATS_PRIMARY = 0xFF98B848.toInt()
}
