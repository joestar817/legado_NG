package io.legado.app.help.storage

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import java.lang.reflect.Type
import java.time.LocalDate

internal object Md3BackupCompatibility {

    private val signaturePreferenceKeys = setOf(
        "composeEngine",
        "mainNavigationOrder",
        "materialVersion"
    )

    private val exclusiveGroupIds = setOf(
        -4L,
        -5L,
        -7L,
        -8L,
        -11L,
        -20L,
        -21L,
        -22L,
        -23L,
        -24L
    )

    private val integerPreferenceKeys = setOf(
        PreferKey.autoReadSpeed,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR,
        PreferKey.brightness,
        PreferKey.nightBrightness
    )

    private val booleanPreferenceKeys = setOf(
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.hideSystemNavigationBar,
        PreferKey.readBodyToLh,
        PreferKey.textFullJustify,
        PreferKey.textBottomJustify,
        PreferKey.adaptSpecialStyle,
        "brightnessAuto"
    )

    val bookGson: Gson by lazy {
        GSON.newBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter)
            .create()
    }

    private object LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        override fun serialize(
            src: LocalDate?,
            typeOfSrc: Type,
            context: JsonSerializationContext,
        ): JsonElement? = src?.let { JsonPrimitive(it.toString()) }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext,
        ): LocalDate? = json.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    fun isBackup(preferences: Map<String, *>?, groupIds: Iterable<Long>): Boolean {
        val hasTypedPreferenceSignature = preferences?.let {
            it[PreferKey.showBrightnessView] is String ||
                    it[PreferKey.brightnessVwPos] is String ||
                    it[PreferKey.saveTabPosition] is Long
        } == true
        val hasKeySignature = preferences
            ?.keys
            ?.count(signaturePreferenceKeys::contains)
            ?.let { it >= 2 } == true
        return hasTypedPreferenceSignature || hasKeySignature ||
                groupIds.any(exclusiveGroupIds::contains)
    }

    fun shouldRestoreGroup(groupId: Long): Boolean = groupId >= 0

    fun normalizePreference(key: String, value: Any?): Any? {
        return when (key) {
            in integerPreferenceKeys -> value.toCompatibleInt()
            in booleanPreferenceKeys -> value as? Boolean
            else -> null
        }
    }

    private fun Any?.toCompatibleInt(): Int? {
        return when (this) {
            is Int -> this
            is Long -> takeIf {
                it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
            }?.toInt()
            else -> null
        }
    }
}
