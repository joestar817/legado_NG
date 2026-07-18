package io.legado.app.help.tts

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.BaseSource

enum class TtsEngineType {
    @SerializedName("system")
    SYSTEM,

    @SerializedName("script")
    SCRIPT
}

const val DEFAULT_TTS_PREVIEW_TEXT = "前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。"

data class TtsVoice(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("language")
    val language: String? = null,
    @SerializedName("gender")
    val gender: String? = null,
    @SerializedName("style")
    val style: String? = null,
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    @SerializedName("sample_text")
    val sampleText: String? = null,
    @SerializedName("extra")
    val extra: JsonObject? = null
)

data class TtsVoiceStyle(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: String? = null,
    @SerializedName("tag")
    val tag: String? = null
) {
    val displayName: String
        get() = name.ifBlank { value?.takeIf { it.isNotBlank() } ?: id }

    val scriptValue: String
        get() = value ?: id
}

fun TtsVoice.previewText(): String {
    sampleText?.takeIf { it.isNotBlank() }?.let { return it }
    return DEFAULT_TTS_PREVIEW_TEXT
}

fun TtsVoice.styleOptions(): List<TtsVoiceStyle> {
    val styles = extra?.get("styles") ?: return emptyList()
    val parsed = when {
        styles.isJsonArray -> styles.asJsonArray.mapIndexedNotNull { index, element ->
            element.toStyleOption(index.toString())
        }
        styles.isJsonObject -> styles.asJsonObject.entrySet().mapNotNull { entry ->
            entry.value.toStyleOption(entry.key)
        }
        styles.isJsonPrimitive -> styles.asString
            .split(",", "，", "|", "/")
            .mapNotNull { value ->
                value.trim().takeIf { it.isNotBlank() }?.let {
                    TtsVoiceStyle(id = it, name = it, value = it)
                }
            }
        else -> emptyList()
    }
    return parsed
        .filter { it.id.isNotBlank() && it.displayName.isNotBlank() }
        .distinctBy { it.id }
}

fun TtsVoice.styleById(styleId: String?): TtsVoiceStyle? {
    val target = styleId?.takeIf { it.isNotBlank() } ?: return null
    return styleOptions().firstOrNull { it.id == target || it.value == target }
}

private fun JsonElement.toStyleOption(defaultId: String): TtsVoiceStyle? {
    return when {
        isJsonPrimitive -> asString.trim().takeIf { it.isNotBlank() }?.let {
            TtsVoiceStyle(id = defaultId.ifBlank { it }, name = it, value = it)
        }
        isJsonObject -> {
            val obj = asJsonObject
            val id = obj.stringValue("id")
                ?: obj.stringValue("value")
                ?: obj.stringValue("name")
                ?: defaultId
            val name = obj.stringValue("name")
                ?: obj.stringValue("label")
                ?: id
            TtsVoiceStyle(
                id = id,
                name = name,
                value = obj.stringValue("value"),
                tag = obj.stringValue("tag")
            )
        }
        else -> null
    }
}

private fun JsonObject.stringValue(name: String): String? {
    return get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
}

data class TtsEngineSetting(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("type")
    val type: TtsEngineType,
    @SerializedName("enabled")
    val enabled: Boolean = true,
    @SerializedName("built_in")
    val builtIn: Boolean = false,
    @SerializedName("engine_package")
    val enginePackage: String? = null,
    @SerializedName("url")
    val url: String = "",
    @SerializedName("script")
    val script: String = "",
    @SerializedName("option_values")
    val optionValues: Map<String, String> = emptyMap(),
    @SerializedName(value = "contentType", alternate = ["content_type"])
    var contentType: String? = "audio/x-wav",
    @SerializedName(value = "concurrentRate", alternate = ["concurrent_rate"])
    override var concurrentRate: String? = "0",
    @SerializedName(value = "maxConcurrency", alternate = ["max_concurrency"])
    val maxConcurrency: Int = 0,
    @SerializedName(value = "loginUrl", alternate = ["login_url"])
    override var loginUrl: String? = null,
    @SerializedName(value = "loginUi", alternate = ["login_ui"])
    override var loginUi: String? = null,
    @SerializedName(value = "loginCheckJs", alternate = ["login_check_js"])
    val loginCheckJs: String? = null,
    @SerializedName("header")
    override var header: String? = null,
    @SerializedName(value = "jsLib", alternate = ["js_lib"])
    override var jsLib: String? = null,
    @SerializedName(value = "enabledCookieJar", alternate = ["enabled_cookie_jar"])
    override var enabledCookieJar: Boolean? = false,
    @SerializedName(value = "sampleText", alternate = ["sample_text"])
    val sampleText: String? = null,
    @SerializedName(value = "voicesUrl", alternate = ["voices_url"])
    val voicesUrl: String? = null,
    @SerializedName(value = "voicesParser", alternate = ["voices_parser"])
    val voicesParser: String = "auto",
    @SerializedName("base_url")
    val baseUrl: String = "",
    @SerializedName("active_voice_id")
    val activeVoiceId: String? = null,
    @SerializedName("default_speed")
    val defaultSpeed: Int = 50,
    @SerializedName("default_volume")
    val defaultVolume: Int = 50,
    @SerializedName("default_pitch")
    val defaultPitch: Int = 50,
    @SerializedName("voices_path")
    val voicesPath: String = "/voices",
    @SerializedName("synthesis_path")
    val synthesisPath: String = "/forward",
    @SerializedName("text_param")
    val textParam: String = "text",
    @SerializedName("voice_param")
    val voiceParam: String = "voice",
    @SerializedName("speed_param")
    val speedParam: String = "speed",
    @SerializedName("volume_param")
    val volumeParam: String = "volume",
    @SerializedName("pitch_param")
    val pitchParam: String = "pitch",
    @SerializedName("voices")
    val voices: List<TtsVoice> = emptyList(),
    @SerializedName("disabled_voice_ids")
    val disabledVoiceIds: List<String> = emptyList(),
    @Transient
    val runtimeSpeed: Int? = null,
    @Transient
    val runtimeVolume: Int? = null,
    @Transient
    val runtimePitch: Int? = null,
    @Transient
    val runtimeVoices: List<TtsVoice>? = null,
    @Transient
    val lastVoiceUpdateTime: Long = 0L
) : BaseSource {
    val isScriptEngine: Boolean get() = type == TtsEngineType.SCRIPT

    fun effectiveVoices(): List<TtsVoice> {
        return runtimeVoices?.takeIf { it.isNotEmpty() } ?: voices
    }

    fun enabledVoices(): List<TtsVoice> {
        val disabledIds = disabledVoiceIds.toSet()
        return effectiveVoices().filterNot { it.id in disabledIds }
    }

    fun isVoiceEnabled(voice: TtsVoice): Boolean {
        return voice.id !in disabledVoiceIds.toSet()
    }

    fun supportsVoiceFetch(): Boolean {
        return isScriptEngine && script.contains(Regex("""function\s+voices\s*\("""))
    }

    fun effectiveSpeed(): Int {
        return (runtimeSpeed ?: defaultSpeed).coerceIn(0, 100)
    }

    fun effectiveVolume(): Int {
        return (runtimeVolume ?: defaultVolume).coerceIn(0, 100)
    }

    fun effectivePitch(): Int {
        return (runtimePitch ?: defaultPitch).coerceIn(0, 100)
    }

    fun effectiveMaxConcurrency(globalLimit: Int): Int {
        val safeGlobalLimit = globalLimit.coerceAtLeast(1)
        return maxConcurrency.takeIf { it > 0 }
            ?.coerceIn(1, safeGlobalLimit)
            ?: safeGlobalLimit
    }

    fun effectiveSynthesisUrl(): String {
        if (url.isNotBlank()) {
            return url
        }
        val base = baseUrl.trim().trimEnd('/').ifBlank { "http://localhost:8774" }
        val path = synthesisPath.trim().ifBlank { "/forward" }.trimStart('/')
        val query = buildList {
            add("${volumeParam.ifBlank { "volume" }}={{speakVolume}}")
            add("${speedParam.ifBlank { "speed" }}={{speakSpeed}}")
            if (voiceParam.isNotBlank()) {
                add("${voiceParam}={{voiceId}}")
            }
            if (pitchParam.isNotBlank()) {
                add("${pitchParam}={{speakPitch}}")
            }
            add("${textParam.ifBlank { "text" }}={{java.encodeURI(speakText)}}")
        }.joinToString("&")
        return "$base/$path?$query"
    }

    fun effectiveVoicesUrl(): String {
        return voicesUrl.orEmpty()
    }

    fun effectiveOptionValues(options: List<TtsScriptOption> = emptyList()): Map<String, String> {
        if (options.isEmpty()) {
            return optionValues
        }
        return options.associate { option ->
            option.safeKey to (optionValues[option.safeKey] ?: option.defaultValue.orEmpty())
        }
    }

    fun activeVoice(): TtsVoice? {
        return enabledVoices().firstOrNull { it.id == activeVoiceId }
    }

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "ttsEngineV2:$id"
    }
}

data class TtsScriptOption(
    @SerializedName("key")
    val key: String? = null,
    @SerializedName(value = "label", alternate = ["name"])
    val label: String? = null,
    @SerializedName("type")
    val type: String? = "text",
    @SerializedName(value = "defaultValue", alternate = ["default_value", "default"])
    val defaultValue: String? = null,
    @SerializedName(value = "values", alternate = ["items", "chars"])
    val values: List<JsonElement>? = emptyList()
) {
    val safeKey: String get() = key.orEmpty()

    val safeValues: List<TtsScriptOptionValue>
        get() = values.orEmpty().mapNotNull { element ->
            when {
                element.isJsonPrimitive -> {
                    val value = element.asString.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    TtsScriptOptionValue(label = value, value = value)
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val value = obj.get("value")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.takeIf { it.isNotBlank() }
                        ?: obj.get("id")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val label = obj.get("label")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.takeIf { it.isNotBlank() }
                        ?: obj.get("name")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.takeIf { it.isNotBlank() }
                        ?: value
                    TtsScriptOptionValue(label = label, value = value)
                }
                else -> null
            }
        }.distinctBy { it.value }

    val displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: safeKey

    val normalizedType: String
        get() = when (type.orEmpty().trim().lowercase()) {
            "bool", "boolean", "switch", "toggle" -> "boolean"
            "select", "choice", "list", "enum" -> "select"
            "password" -> "password"
            "number", "int", "integer", "float", "decimal" -> "number"
            else -> "text"
        }
}

data class TtsScriptOptionValue(
    val label: String,
    val value: String
)
