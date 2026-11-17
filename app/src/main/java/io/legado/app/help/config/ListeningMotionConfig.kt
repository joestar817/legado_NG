package io.legado.app.help.config

import android.content.SharedPreferences
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx
import kotlin.math.roundToInt

enum class ListeningMotionEffect(val storageValue: String) {
    FLAME("flame"),
    FLUID("fluid"),
    PACKAGE("package");

    companion object {
        fun fromStorage(value: String?): ListeningMotionEffect =
            entries.firstOrNull { it.storageValue == value } ?: FLAME
    }
}

enum class ListeningFluidType(val storageValue: String) {
    SMOKE("smoke"),
    WATER("water"),
    EDGE("edge");

    companion object {
        fun fromStorage(value: String?): ListeningFluidType =
            entries.firstOrNull { it.storageValue == value } ?: SMOKE
    }
}

enum class ListeningFireStyle(val storageValue: String) {
    GODFIRE("godfire"),
    INFERNO("inferno"),
    RIFT("rift");

    companion object {
        fun fromStorage(value: String?): ListeningFireStyle =
            entries.firstOrNull { it.storageValue == value } ?: GODFIRE
    }
}

enum class ListeningMotionColorMode(val storageValue: String) {
    ORIGINAL("original"),
    COVER("cover"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String?): ListeningMotionColorMode =
            entries.firstOrNull { it.storageValue == value } ?: COVER
    }
}

data class ListeningMotionSettings(
    val enabled: Boolean = false,
    val effect: ListeningMotionEffect = ListeningMotionEffect.FLAME,
    val fireStyle: ListeningFireStyle = ListeningFireStyle.GODFIRE,
    val fluidType: ListeningFluidType = ListeningFluidType.SMOKE,
    val packageId: String? = null,
    val colorMode: ListeningMotionColorMode = ListeningMotionColorMode.COVER,
    val customColor: Int = ListeningMotionConfig.DEFAULT_CUSTOM_COLOR,
    val intensity: Int = ListeningMotionConfig.DEFAULT_INTENSITY,
)

internal fun normalizeListeningMotionIntensity(value: Int): Int = value.coerceIn(0, 100)

internal fun NgManagedTheme.listeningIntensity(): NgEffectThemeParameter? =
    effect?.parameterSchema?.firstOrNull {
        it.key == "intensity" && it.type == "range" && it.min != null && it.max != null && it.max > it.min
    }

internal fun NgEffectThemeParameter.toListeningPercent(value: String): Int {
    val start = requireNotNull(min).toDouble()
    val span = requireNotNull(max).toDouble() - start
    return normalizeListeningMotionIntensity(((value.toDouble() - start) / span * 100).roundToInt())
}

internal fun NgEffectThemeParameter.fromListeningPercent(percent: Int): String {
    val start = requireNotNull(min).toDouble()
    val end = requireNotNull(max).toDouble()
    return (start + (end - start) * (normalizeListeningMotionIntensity(percent) / 100.0)).toFloat().toString()
}

object ListeningMotionConfig {

    const val DEFAULT_INTENSITY = 40
    const val DEFAULT_FLUID_INTENSITY = 100
    const val DEFAULT_CUSTOM_COLOR = -0x002EC5D9

    var enabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.listeningMotionEnabled, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.listeningMotionEnabled, value)

    var effect: ListeningMotionEffect
        get() = ListeningMotionEffect.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionEffect)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionEffect, value.storageValue)

    var fireStyle: ListeningFireStyle
        get() = ListeningFireStyle.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionFireStyle)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionFireStyle, value.storageValue)

    var fluidType: ListeningFluidType
        get() = ListeningFluidType.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionFluidType)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionFluidType, value.storageValue)

    private val packageId: String?
        get() = appCtx.getPrefString(PreferKey.listeningMotionPackageId)

    internal fun selectPackage(id: String) {
        NgListeningEffectLibraryStore.withInstalledEffect(appCtx, id) {
            appCtx.defaultSharedPreferences.edit()
                .putString(PreferKey.listeningMotionPackageId, id)
                .putString(PreferKey.listeningMotionEffect, ListeningMotionEffect.PACKAGE.storageValue)
                .apply()
        }
    }

    internal fun clearRemovedPackage(prefs: SharedPreferences, edit: SharedPreferences.Editor, id: String) {
        edit.remove("${PreferKey.listeningMotionPackageIntensity}.$id")
        if (prefs.getString(PreferKey.listeningMotionPackageId, null) == id) {
            edit.remove(PreferKey.listeningMotionPackageId)
        }
    }

    var colorMode: ListeningMotionColorMode
        get() = ListeningMotionColorMode.fromStorage(
            appCtx.getPrefString(PreferKey.listeningMotionColorMode)
        )
        set(value) = appCtx.putPrefString(PreferKey.listeningMotionColorMode, value.storageValue)

    var customColor: Int
        get() = appCtx.getPrefInt(PreferKey.listeningMotionCustomColor, DEFAULT_CUSTOM_COLOR)
        set(value) = appCtx.putPrefInt(PreferKey.listeningMotionCustomColor, value)

    var intensity: Int
        get() = normalizeListeningMotionIntensity(
            appCtx.getPrefInt(PreferKey.listeningMotionIntensity, DEFAULT_INTENSITY)
        )
        set(value) = appCtx.putPrefInt(
            PreferKey.listeningMotionIntensity,
            normalizeListeningMotionIntensity(value),
        )

    var fluidIntensity: Int
        get() = normalizeListeningMotionIntensity(
            appCtx.getPrefInt(
                PreferKey.listeningMotionFluidIntensity,
                DEFAULT_FLUID_INTENSITY,
            )
        )
        set(value) = appCtx.putPrefInt(
            PreferKey.listeningMotionFluidIntensity,
            normalizeListeningMotionIntensity(value),
        )

    internal fun setPackageIntensity(id: String, value: Int) {
        appCtx.putPrefInt("${PreferKey.listeningMotionPackageIntensity}.$id", normalizeListeningMotionIntensity(value))
    }

    private fun packageIntensity(id: String?): Int {
        val theme = NgListeningEffectLibraryStore.allEffects(appCtx).firstOrNull { it.id == id }
        val parameter = theme?.listeningIntensity() ?: return 100
        val value = theme.effectValues[parameter.key] ?: parameter.defaultValue
        return normalizeListeningMotionIntensity(appCtx.getPrefInt(
            "${PreferKey.listeningMotionPackageIntensity}.$id", parameter.toListeningPercent(value),
        ))
    }

    fun intensityFor(effect: ListeningMotionEffect): Int = when (effect) {
        ListeningMotionEffect.FLAME -> intensity
        ListeningMotionEffect.FLUID -> fluidIntensity
        ListeningMotionEffect.PACKAGE -> packageIntensity(packageId)
    }

    fun current(): ListeningMotionSettings {
        val selectedId = packageId
        val currentEffect = effect.takeUnless { it == ListeningMotionEffect.PACKAGE && selectedId == null }
            ?: ListeningMotionEffect.FLAME
        return ListeningMotionSettings(
            enabled = enabled,
            effect = currentEffect,
            fireStyle = fireStyle,
            fluidType = fluidType,
            packageId = selectedId,
            colorMode = colorMode,
            customColor = customColor,
            intensity = intensityFor(currentEffect),
        )
    }
}
