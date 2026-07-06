package io.legado.app.help.tts

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.TtsEngineRuntimeEntity
import io.legado.app.data.entities.TtsVoiceEntity
import io.legado.app.model.ReadAloud
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

object TtsEngineStore {

    const val SYSTEM_DEFAULT_ID = "system_default"
    const val MULTITTS_FORWARDER_ID = "multitts_forwarder"
    const val BAIDU_TTS_ID = "baidu_tts"
    const val ALIYUN_TTS_ID = "aliyun_tts"
    const val NEXT_FORWARDER_ID = "next_forwarder"
    const val NEXT_EDGE_PROXY_ID = "next_edge_proxy"
    const val OPTIONS_EXAMPLE_ID = "script_options_example"
    const val STATIC_VOICES_EXAMPLE_ID = "script_static_voices_example"
    const val CUSTOM_HTTP_ID = "custom_http_forwarder"
    private const val SYSTEM_ENGINE_PREFIX = "system_engine_"
    private const val DEFAULT_TTS_ASSET_DIR = "defaultData/tts"
    private const val OLD_MULTITTS_FORWARDER_URL =
        "http://localhost:8774/forward?volume={{speakVolume}}&speed={{speakSpeed*2}}&voice={{voiceId}}&text={{java.encodeURI(speakText)}}"
    private const val OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME =
        "http://localhost:8774/forward?volume=50&speed={{speakSpeed*2}}&voice={{voiceId}}&text={{java.encodeURI(speakText)}}"
    private const val OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME_NO_VOICE =
        "http://localhost:8774/forward?volume=50&speed={{speakSpeed*2}}&text={{java.encodeURI(speakText)}}"
    private const val DEFAULT_MULTITTS_FORWARDER_URL =
        "http://localhost:8774/forward?volume={{speakVolume}}&speed={{speakSpeed}}&pitch={{speakPitch}}&voice={{voiceId}}&text={{java.encodeURI(speakText)}}"
    private val DEFAULT_SCRIPT_ASSETS = listOf(
        "multitts_forwarder.js",
        "next_edge_proxy.js",
        "script_options_example.js",
        "static_voices_example.js"
    )

    fun engines(): List<TtsEngineSetting> {
        val savedEngines = savedEnginesWithSystemDefaultDisabled()
        val retiredIds = retiredTemplateIds()
        val visibleSavedEngines = savedEngines.filterNot { it.id in retiredIds }
        val saved = visibleSavedEngines.associateBy { it.id }
        val deletedIds = deletedEngineIds()
        val builtInEngines = builtInEngines().filterNot { it.id in deletedIds }
        val builtInIds = builtInEngines.mapTo(hashSetOf()) { it.id }
        val merged = builtInEngines.map { builtIn ->
            saved[builtIn.id]?.let { savedEngine ->
                savedEngine.copy(
                    type = builtIn.type,
                    builtIn = builtIn.builtIn,
                    enginePackage = builtIn.enginePackage,
                    defaultSpeed = if (builtIn.type == TtsEngineType.SYSTEM) {
                        builtIn.defaultSpeed
                    } else {
                        savedEngine.defaultSpeed
                    },
                    defaultVolume = if (builtIn.type == TtsEngineType.SYSTEM) {
                        builtIn.defaultVolume
                    } else {
                        savedEngine.defaultVolume
                    },
                    defaultPitch = if (builtIn.type == TtsEngineType.SYSTEM) {
                        builtIn.defaultPitch
                    } else {
                        savedEngine.defaultPitch
                    }
                ).withUpdatedDefaultScript(builtIn)
            } ?: builtIn
        }
        val custom = saved.values
            .filterNot { savedEngine ->
                savedEngine.id in builtInIds ||
                        savedEngine.id in deletedIds ||
                        savedEngine.id in retiredIds ||
                        savedEngine.id.startsWith(SYSTEM_ENGINE_PREFIX)
            }
        val allById = (merged + custom).associateBy { it.id }
        val savedOrder = visibleSavedEngines.map { it.id }.filter { it in allById }
        val remainingOrder = allById.keys.filterNot { it in savedOrder }
        return (savedOrder + remainingOrder).mapNotNull { allById[it] }
            .map { it.withRuntimeState() }
    }

    fun activeEngineId(): String {
        val saved = appCtx.getPrefString(PreferKey.ttsEngineV2ActiveId)
        return saved?.takeIf { id -> engines().any { it.id == id && it.enabled } }
            ?: engines().firstOrNull { it.enabled }?.id.orEmpty()
    }

    fun activeEngine(): TtsEngineSetting {
        return engine(activeEngineId())
            ?: engines().firstOrNull { it.enabled }
            ?: builtInEngines().first()
    }

    fun hasEnabledEngine(): Boolean {
        return engines().any { it.enabled }
    }

    fun isDeletableEngine(engine: TtsEngineSetting): Boolean {
        return engine.type != TtsEngineType.SYSTEM
    }

    fun engine(id: String?): TtsEngineSetting? {
        if (id.isNullOrBlank()) return null
        return engines().firstOrNull { it.id == id }
    }

    fun saveEngine(engine: TtsEngineSetting) {
        val wasActive = activeEngineId() == engine.id
        val previous = engine(engine.id)
        val currentEngines = engines()
        val exists = currentEngines.any { it.id == engine.id }
        val updatedEngines = if (exists) {
            currentEngines.map { if (it.id == engine.id) engine else it }
        } else {
            currentEngines + engine
        }
        saveEngines(updatedEngines)
        if (previous?.shouldClearVoiceCacheFor(engine) == true) {
            appDb.ttsVoiceDao.deleteByEngine(engine.id)
        }
        val effectiveEngine = TtsEngineStore.engine(engine.id) ?: engine
        if (wasActive) {
            if (effectiveEngine.enabled) {
                ReadAloud.httpTtsEngineV2 = effectiveEngine.takeIf {
                    it.type == TtsEngineType.SCRIPT
                }
                ReadAloud.upReadAloudClass()
            } else {
                selectFirstEnabledEngine()
            }
        }
    }

    fun createCustomScriptEngine(): TtsEngineSetting {
        val now = System.currentTimeMillis()
        val script = """
            // @name 新建朗读引擎
            // @schema 1
            // @version 1.0.0
            // @uuid custom_tts_$now
            // @author User
            // @enabled true
            // @cookieJar false
            // @audioType audio/x-wav
            // @description 自定义 JS 朗读引擎。

            function options() {
                return [];
            }

            function voices(options, ctx) {
                return [];
            }

            function synthesize(text, voice, params, options, ctx) {
                return {};
            }
        """.trimIndent()
        return scriptEngineFromScript(script)
            ?: error("自定义朗读引擎模板解析失败")
    }

    fun importEngineText(text: String): Result<List<TtsEngineSetting>> {
        val source = text.trim()
        if (source.isBlank()) {
            return Result.failure(IllegalArgumentException("导入内容为空"))
        }
        return runCatching {
            val engines = parseImportEngineText(source)
            val savedEngines = engines.map { importEngine(it) }
            savedEngines
        }
    }

    fun importLegacyHttpTts(source: HttpTTS): TtsEngineSetting? {
        if (!isLegacyMultiTtsForwarder(source)) {
            return null
        }
        val engine = scriptEngineFromAsset("multitts_forwarder.js") ?: return null
        return importEngine(engine.copy(enabled = true))
    }

    fun isLegacyMultiTtsForwarder(source: HttpTTS): Boolean {
        val normalizedUrl = source.url.trim()
        if (normalizedUrl in setOf(
                OLD_MULTITTS_FORWARDER_URL,
                OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME,
                OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME_NO_VOICE,
                DEFAULT_MULTITTS_FORWARDER_URL
            )
        ) {
            return true
        }
        return source.name.contains("MultiTTS", ignoreCase = true) &&
                normalizedUrl.startsWith("http://localhost:8774/forward") &&
                normalizedUrl.contains("{{java.encodeURI(speakText)}}")
    }

    fun saveEngines(engines: List<TtsEngineSetting>) {
        appCtx.putPrefString(
            PreferKey.ttsEngineV2SettingsJson,
            GSON.toJson(engines.map { it.forConfigSave() })
        )
    }

    fun deleteEngine(id: String): Boolean {
        val engine = engine(id) ?: return false
        if (!isDeletableEngine(engine)) {
            return false
        }
        val wasActive = activeEngineId() == id
        if (wasActive) {
            appCtx.putPrefString(PreferKey.ttsEngineV2ActiveId, "")
        }
        if (engine.builtIn || id in defaultTemplateIds()) {
            saveDeletedEngineIds(deletedEngineIds() + id)
        }
        saveEngines(engines().filterNot { it.id == id })
        appDb.ttsVoiceDao.deleteByEngine(id)
        appDb.ttsEngineRuntimeDao.deleteByEngine(id)
        if (wasActive) {
            selectFirstEnabledEngine()
        }
        return true
    }

    fun selectEngine(id: String) {
        val engine = engine(id)
        if (engine?.enabled != true) {
            return
        }
        appCtx.putPrefString(PreferKey.ttsEngineV2ActiveId, id)
        ReadAloud.upReadAloudClass()
    }

    fun selectVoice(engineId: String, voiceId: String?): TtsEngineSetting? {
        val engine = engine(engineId)?.takeIf { it.enabled } ?: return null
        val selectedVoiceId = voiceId?.takeIf { id ->
            engine.enabledVoices().any { it.id == id }
        }
        if (voiceId != null && selectedVoiceId == null) {
            return null
        }
        appCtx.putPrefString(PreferKey.ttsEngineV2ActiveId, engineId)
        val updated = engine.copy(activeVoiceId = selectedVoiceId)
        saveEngines(engines().map { if (it.id == engineId) updated else it })
        ReadAloud.refreshReadAloudClass()
        return engine(engineId)
    }

    fun upsertVoiceList(engineId: String, voices: List<TtsVoice>): TtsEngineSetting? {
        val engine = engine(engineId) ?: return null
        val now = System.currentTimeMillis()
        appDb.ttsVoiceDao.replaceForEngine(
            engineId = engineId,
            voices = voices.map { it.toEntity(engineId, now) }
        )
        val activeVoiceId = engine.activeVoiceId
            ?.takeIf { voiceId -> voices.any { it.id == voiceId } }
            ?: voices.firstOrNull()?.id
        val updated = engine.copy(activeVoiceId = activeVoiceId)
        saveEngine(updated)
        return updated.copy(runtimeVoices = voices, lastVoiceUpdateTime = now)
    }

    fun setVoiceEnabled(
        engineId: String,
        voiceId: String,
        enabled: Boolean
    ): TtsEngineSetting? {
        val engine = engine(engineId) ?: return null
        val disabledIds = engine.disabledVoiceIds.toMutableSet()
        if (enabled) {
            disabledIds.remove(voiceId)
        } else {
            disabledIds.add(voiceId)
        }
        val updated = engine.copy(disabledVoiceIds = disabledIds.toList().sorted())
        saveEngine(updated)
        return engine(updated.id)
    }

    fun setAllVoicesEnabled(
        engineId: String,
        voiceIds: List<String>,
        enabled: Boolean
    ): TtsEngineSetting? {
        val engine = engine(engineId) ?: return null
        val updated = engine.copy(
            disabledVoiceIds = if (enabled) {
                emptyList()
            } else {
                voiceIds.filter { it.isNotBlank() }.distinct().sorted()
            }
        )
        saveEngine(updated)
        return engine(updated.id)
    }

    private fun importEngine(engine: TtsEngineSetting): TtsEngineSetting {
        if (engine.id in defaultTemplateIds()) {
            saveDeletedEngineIds(deletedEngineIds() - engine.id)
        }
        val existing = engines().associateBy { it.id }
        val imported = if (engine.id in existing) {
            engine.copy(enabled = existing.getValue(engine.id).enabled)
        } else {
            engine
        }
        val merged = if (imported.id in existing) {
            engines().map { if (it.id == imported.id) imported else it }
        } else {
            engines() + imported
        }
        saveEngines(merged)
        appDb.ttsVoiceDao.deleteByEngine(imported.id)
        appDb.ttsEngineRuntimeDao.deleteByEngine(imported.id)
        return engine(imported.id) ?: imported
    }

    private fun parseImportEngineText(text: String): List<TtsEngineSetting> {
        scriptEngineFromScript(text)?.let { return listOf(it.withImportSafeId()) }
        val element = runCatching { JsonParser.parseString(text) }.getOrNull()
            ?: throw IllegalArgumentException("不支持的朗读引擎格式")
        if (element.isJsonObject) {
            parseEngineFromJsonObject(element.asJsonObject)?.let { return listOf(it.withImportSafeId()) }
            HttpTTS.fromJson(text).getOrNull()
                ?.let { importLegacyHttpTtsCandidate(it) }
                ?.let { return listOf(it) }
        }
        if (element.isJsonArray) {
            val engines = element.asJsonArray.mapNotNull { item ->
                item.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.let { parseEngineFromJsonObject(it)?.withImportSafeId() }
            }
            if (engines.isNotEmpty()) {
                return engines
            }
            val legacyEngines = HttpTTS.fromJsonArray(text).getOrNull()
                .orEmpty()
                .mapNotNull { importLegacyHttpTtsCandidate(it) }
                .distinctBy { it.id }
            if (legacyEngines.isNotEmpty()) {
                return legacyEngines
            }
        }
        throw IllegalArgumentException("不支持的朗读引擎格式")
    }

    private fun parseEngineFromJsonObject(jsonObject: JsonObject): TtsEngineSetting? {
        val parsed = runCatching {
            GSON.fromJson(jsonObject, TtsEngineSetting::class.java)
        }.getOrNull() ?: return null
        return parsed.normalizedOrNull()?.takeIf { engine ->
            engine.type == TtsEngineType.SCRIPT && engine.script.isNotBlank()
        }
    }

    private fun importLegacyHttpTtsCandidate(source: HttpTTS): TtsEngineSetting? {
        return if (isLegacyMultiTtsForwarder(source)) {
            scriptEngineFromAsset("multitts_forwarder.js")?.copy(enabled = true)
        } else {
            null
        }
    }

    private fun TtsEngineSetting.withImportSafeId(): TtsEngineSetting {
        if (id !in engines().map { it.id }) {
            return this
        }
        if (id in defaultTemplateIds()) {
            return this
        }
        val suffix = System.currentTimeMillis()
        val newId = "${id}_$suffix"
        val newName = "$name 副本"
        return copy(
            id = newId,
            name = newName,
            script = script.replaceFirst(
                Regex("""(?m)^(\s*//\s*@uuid\s+).*$"""),
                "$1$newId"
            ).replaceFirst(
                Regex("""(?m)^(\s*//\s*@name\s+).*$"""),
                "$1$newName"
            )
        )
    }

    fun saveRuntimeParams(
        engineId: String,
        speed: Int,
        volume: Int,
        pitch: Int
    ): TtsEngineSetting? {
        appDb.ttsEngineRuntimeDao.upsert(
            TtsEngineRuntimeEntity(
                engineId = engineId,
                speed = speed.coerceIn(0, 100),
                volume = volume.coerceIn(0, 100),
                pitch = pitch.coerceIn(0, 100),
                updatedAt = System.currentTimeMillis()
            )
        )
        val updated = engine(engineId)
        if (activeEngineId() == engineId) {
            ReadAloud.httpTtsEngineV2 = updated?.takeIf {
                it.type == TtsEngineType.SCRIPT
            }
            ReadAloud.upTtsSpeechRate(appCtx)
        }
        return updated
    }

    fun voices(engineId: String): List<TtsVoice> {
        return appDb.ttsVoiceDao.getByEngine(engineId).map { it.toVoice() }
    }

    fun voice(engineId: String, voiceId: String?): TtsVoice? {
        if (voiceId.isNullOrBlank()) {
            return null
        }
        return appDb.ttsVoiceDao.get(engineId, voiceId)?.toVoice()
            ?: engine(engineId)?.voices?.firstOrNull { it.id == voiceId }
    }

    fun voiceCounts(): Map<String, Int> {
        return appDb.ttsVoiceDao.countByEngine().associate { it.engineId to it.count }
    }

    private fun savedEngines(): List<TtsEngineSetting> {
        val json = appCtx.getPrefString(PreferKey.ttsEngineV2SettingsJson)
        val normalized = GSON.fromJsonArray<TtsEngineSetting>(json)
            .getOrDefault(emptyList())
            .mapNotNull { it.normalizedOrNull() }
        if (json?.contains("\"last_voice_update_time\"") == true) {
            appCtx.putPrefString(PreferKey.ttsEngineV2SettingsJson, GSON.toJson(normalized))
        }
        return normalized
    }

    private fun TtsEngineSetting.normalizedOrNull(): TtsEngineSetting? {
        val safeId = safeString { id }.takeIf { it.isNotBlank() } ?: return null
        val safeType = runCatching { type }.getOrNull() ?: TtsEngineType.SCRIPT
        return TtsEngineSetting(
            id = safeId,
            name = safeString { name }.ifBlank { "未命名朗读引擎" },
            type = safeType,
            enabled = runCatching { enabled }.getOrDefault(true),
            builtIn = runCatching { builtIn }.getOrDefault(false),
            enginePackage = safeNullableString { enginePackage },
            url = safeString { url },
            script = safeString { script },
            optionValues = safeOptionValues(),
            contentType = safeString { contentType }.ifBlank { "audio/x-wav" },
            concurrentRate = safeNullableString { concurrentRate },
            loginUrl = safeNullableString { loginUrl },
            loginUi = safeNullableString { loginUi },
            loginCheckJs = safeNullableString { loginCheckJs },
            header = safeNullableString { header },
            jsLib = safeNullableString { jsLib },
            enabledCookieJar = runCatching { enabledCookieJar }.getOrNull(),
            voicesUrl = safeNullableString { voicesUrl },
            voicesParser = safeString { voicesParser }.ifBlank { "auto" },
            baseUrl = safeString { baseUrl },
            activeVoiceId = safeNullableString { activeVoiceId },
            defaultSpeed = safeInt(50) { defaultSpeed }.coerceIn(0, 100),
            defaultVolume = safeInt(50) { defaultVolume }.coerceIn(0, 100),
            defaultPitch = safeInt(50) { defaultPitch }.coerceIn(0, 100),
            voicesPath = safeString { voicesPath }.ifBlank { "/voices" },
            synthesisPath = safeString { synthesisPath }.ifBlank { "/forward" },
            textParam = safeString { textParam }.ifBlank { "text" },
            voiceParam = safeString { voiceParam }.ifBlank { "voice" },
            speedParam = safeString { speedParam }.ifBlank { "speed" },
            volumeParam = safeString { volumeParam }.ifBlank { "volume" },
            pitchParam = safeString { pitchParam }.ifBlank { "pitch" },
            voices = safeVoices(),
            disabledVoiceIds = safeStringList { disabledVoiceIds },
        )
    }

    private fun TtsEngineSetting.withRuntimeState(): TtsEngineSetting {
        val storedVoices = voices(id)
        val runtime = appDb.ttsEngineRuntimeDao.get(id)
        return copy(
            runtimeSpeed = runtime?.speed,
            runtimeVolume = runtime?.volume,
            runtimePitch = runtime?.pitch,
            runtimeVoices = storedVoices.takeIf { it.isNotEmpty() },
            lastVoiceUpdateTime = appDb.ttsVoiceDao.lastUpdatedAt(id) ?: 0L
        )
    }

    private fun TtsEngineSetting.withUpdatedDefaultScript(builtIn: TtsEngineSetting): TtsEngineSetting {
        if (id !in defaultTemplateIds()) {
            return this
        }
        val updatedUrl = if (url in setOf(
                OLD_MULTITTS_FORWARDER_URL,
                OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME,
                OLD_MULTITTS_FORWARDER_URL_FIXED_VOLUME_NO_VOICE
            )
        ) {
            DEFAULT_MULTITTS_FORWARDER_URL
        } else {
            url
        }
        val replaceScript = shouldReplaceDefaultScriptWith(builtIn)
        return copy(
            url = updatedUrl,
            script = if (replaceScript) {
                builtIn.script
            } else {
                script
            },
            defaultSpeed = builtIn.defaultSpeed,
            defaultVolume = builtIn.defaultVolume,
            defaultPitch = builtIn.defaultPitch,
            sampleText = if (replaceScript) builtIn.sampleText else sampleText
        )
    }

    private fun TtsEngineSetting.shouldReplaceDefaultScriptWith(
        builtIn: TtsEngineSetting
    ): Boolean {
        if (script.isBlank()) {
            return true
        }
        if (
            id in defaultTemplateIds() &&
            script.contains("params.speed * 2") &&
            !builtIn.script.contains("params.speed * 2")
        ) {
            return true
        }
        val shouldUpdateMultiTtsTemplate = id in setOf(MULTITTS_FORWARDER_ID, OPTIONS_EXAMPLE_ID) &&
                script.contains("function parseMultiTtsVoices(body)") &&
                (
                        script.contains("typeof body === \"string\" ? JSON.parse(body) : body") ||
                                script.contains("Object.keys(catalog).forEach") ||
                                script.contains("style: item.style || item.desc || \"\"")
                        ) &&
                builtIn.script.contains("JSON.parse(String(body || \"{}\"))")
        val shouldUpdateStaticPreviewText = id == STATIC_VOICES_EXAMPLE_ID &&
                script.contains("sample_text: \"这是一段朗读试听。\"") &&
                builtIn.script.contains(DEFAULT_TTS_PREVIEW_TEXT)
        val shouldUpdateNextEdgeProxy = id == NEXT_EDGE_PROXY_ID &&
                (
                        script.contains("// @version 1.0.0") ||
                                script.contains("// @version 1.0.1") ||
                                script.contains("// @version 1.0.2") ||
                                script.contains("// @version 1.0.3") ||
                                script.contains("// @version 1.0.4")
                        ) &&
                builtIn.script.contains("// @version 1.0.5")
        return shouldUpdateMultiTtsTemplate ||
                shouldUpdateStaticPreviewText ||
                shouldUpdateNextEdgeProxy
    }

    private fun TtsEngineSetting.shouldClearVoiceCacheFor(updated: TtsEngineSetting): Boolean {
        return type == TtsEngineType.SCRIPT &&
                updated.type == TtsEngineType.SCRIPT &&
                (script != updated.script || optionValues != updated.optionValues)
    }

    private fun TtsEngineSetting.forConfigSave(): TtsEngineSetting {
        return copy(
            runtimeSpeed = null,
            runtimeVolume = null,
            runtimePitch = null,
            runtimeVoices = null,
            lastVoiceUpdateTime = 0L
        )
    }

    fun normalizeEditedEngine(
        parsed: TtsEngineSetting,
        source: TtsEngineSetting
    ): Result<TtsEngineSetting> {
        val normalized = parsed.normalizedOrNull()
            ?: return Result.failure(IllegalArgumentException("缺少引擎 id"))
        if (normalized.name.isBlank()) {
            return Result.failure(IllegalArgumentException("名称不能为空"))
        }
        if (source.type == TtsEngineType.SCRIPT &&
            normalized.script.isBlank()
        ) {
            return Result.failure(IllegalArgumentException("script 不能为空"))
        }
        return Result.success(
            normalized.copy(
                id = source.id,
                type = source.type,
                builtIn = source.builtIn,
                enginePackage = source.enginePackage,
                runtimeSpeed = source.runtimeSpeed,
                runtimeVolume = source.runtimeVolume,
                runtimePitch = source.runtimePitch,
                runtimeVoices = source.runtimeVoices,
                lastVoiceUpdateTime = source.lastVoiceUpdateTime
            )
        )
    }

    fun normalizeEditedEngineJson(
        json: String,
        source: TtsEngineSetting
    ): Result<TtsEngineSetting> {
        val jsonObject = runCatching {
            JsonParser.parseString(json).asJsonObject
        }.getOrElse {
            return Result.failure(IllegalArgumentException("源码不是 JSON 对象"))
        }
        val validationError = validateEditedEngineJson(jsonObject)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val parsed = runCatching {
            GSON.fromJson(jsonObject, TtsEngineSetting::class.java)
        }.getOrElse {
            return Result.failure(IllegalArgumentException(it.localizedMessage ?: "源码解析失败"))
        }
        val normalized = normalizeEditedEngine(parsed, source).getOrElse {
            return Result.failure(it)
        }
        return Result.success(
            normalized.copy(
                enabled = normalized.enabled.takeIf { jsonObject.has("enabled") }
                    ?: source.enabled,
                builtIn = normalized.builtIn.takeIf { jsonObject.has("built_in") }
                    ?: source.builtIn,
                defaultSpeed = normalized.defaultSpeed.takeIf { jsonObject.has("default_speed") }
                    ?: source.defaultSpeed,
                defaultVolume = normalized.defaultVolume.takeIf { jsonObject.has("default_volume") }
                    ?: source.defaultVolume,
                defaultPitch = normalized.defaultPitch.takeIf { jsonObject.has("default_pitch") }
                    ?: source.defaultPitch
            )
        )
    }

    private fun validateEditedEngineJson(jsonObject: JsonObject): String? {
        val textKeys = listOf("id", "name", "type", "url", "script")
        textKeys.forEach { key ->
            jsonObject[key]?.let { element ->
                if (element.isJsonNull || !element.isJsonPrimitive) {
                    return "$key 必须是文本"
                }
                if (key in listOf("id", "name") && element.asString.isBlank()) {
                    return "$key 不能为空"
                }
            }
        }
        val booleanKeys = listOf("enabled", "built_in", "enabledCookieJar", "enabled_cookie_jar")
        booleanKeys.forEach { key ->
            jsonObject[key]?.let { element ->
                if (element.isJsonNull || !element.isBooleanPrimitive()) {
                    return "$key 必须是 true 或 false"
                }
            }
        }
        val numberKeys = listOf("default_speed", "default_volume", "default_pitch")
        numberKeys.forEach { key ->
            jsonObject[key]?.let { element ->
                if (element.isJsonNull || !element.isNumberPrimitive()) {
                    return "$key 必须是数字"
                }
            }
        }
        jsonObject["voices"]?.let { element ->
            if (!element.isJsonNull && !element.isJsonArray) {
                return "voices 必须是数组"
            }
        }
        jsonObject["disabled_voice_ids"]?.let { element ->
            if (!element.isJsonNull && !element.isJsonArray) {
                return "disabled_voice_ids 必须是数组"
            }
        }
        jsonObject["option_values"]?.let { element ->
            if (!element.isJsonNull && !element.isJsonObject) {
                return "option_values 必须是对象"
            }
        }
        return null
    }

    private fun JsonElement.isBooleanPrimitive(): Boolean {
        return runCatching { asJsonPrimitive.isBoolean }.getOrDefault(false)
    }

    private fun JsonElement.isNumberPrimitive(): Boolean {
        return runCatching { asJsonPrimitive.isNumber }.getOrDefault(false)
    }

    private fun TtsEngineSetting.safeVoices(): List<TtsVoice> {
        return runCatching { voices }.getOrNull()
            .orEmpty()
            .mapNotNull { voice ->
                val id = runCatching { voice.id }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = runCatching { voice.name }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: id
                TtsVoice(
                    id = id,
                    name = name,
                    language = runCatching { voice.language }.getOrNull(),
                    gender = runCatching { voice.gender }.getOrNull(),
                    style = runCatching { voice.style }.getOrNull(),
                    tags = runCatching { voice.tags }.getOrNull().orEmpty(),
                    sampleText = runCatching { voice.sampleText }.getOrNull()
                )
            }.distinctBy { it.id }
    }

    private fun TtsEngineSetting.safeOptionValues(): Map<String, String> {
        return runCatching { optionValues }.getOrNull()
            .orEmpty()
            .mapValues { it.value }
            .filterKeys { it.isNotBlank() }
    }

    private inline fun safeStringList(block: () -> List<String>?): List<String> {
        return runCatching { block() }.getOrNull()
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private inline fun safeString(block: () -> String?): String {
        return runCatching { block() }.getOrNull().orEmpty()
    }

    private inline fun safeNullableString(block: () -> String?): String? {
        return safeString(block).takeIf { it.isNotBlank() }
    }

    private inline fun safeInt(defaultValue: Int, block: () -> Int): Int {
        return runCatching { block() }.getOrDefault(defaultValue)
    }

    private fun TtsVoice.toEntity(engineId: String, updatedAt: Long): TtsVoiceEntity {
        return TtsVoiceEntity(
            engineId = engineId,
            id = id,
            name = name,
            language = language,
            gender = gender,
            style = style,
            tagsJson = GSON.toJson(tags),
            sampleText = sampleText,
            extraJson = extra?.let { GSON.toJson(it) } ?: "{}",
            updatedAt = updatedAt
        )
    }

    private fun TtsVoiceEntity.toVoice(): TtsVoice {
        return TtsVoice(
            id = id,
            name = name,
            language = language,
            gender = gender,
            style = style,
            tags = GSON.fromJsonArray<String>(tagsJson).getOrDefault(emptyList()),
            sampleText = sampleText,
            extra = GSON.fromJsonObject<JsonObject>(extraJson).getOrNull()
                ?.takeIf { it.size() > 0 }
        )
    }

    private fun savedEnginesWithSystemDefaultDisabled(): List<TtsEngineSetting> {
        val savedEngines = savedEngines()
        if (appCtx.getPrefBoolean(PreferKey.ttsEngineV2SystemDisabledApplied, false)) {
            return savedEngines
        }
        val updated = savedEngines.map { engine ->
            if (engine.type == TtsEngineType.SYSTEM && engine.enabled) {
                engine.copy(enabled = false)
            } else {
                engine
            }
        }
        if (updated != savedEngines) {
            saveEngines(updated)
        }
        appCtx.putPrefBoolean(PreferKey.ttsEngineV2SystemDisabledApplied, true)
        return updated
    }

    private fun builtInEngines(): List<TtsEngineSetting> {
        return buildList {
            add(
                TtsEngineSetting(
                    id = SYSTEM_DEFAULT_ID,
                    name = "系统默认 TTS",
                    type = TtsEngineType.SYSTEM,
                    enabled = false,
                    builtIn = true,
                    defaultSpeed = 100,
                    defaultVolume = 50,
                    defaultPitch = 50
                )
            )
            addAll(systemTtsEngines())
            addAll(defaultScriptEngines())
        }
    }

    internal fun optionsExampleBuiltInEngine(): TtsEngineSetting {
        return scriptEngineFromAsset("script_options_example.js")
            ?: error("缺少默认 TTS 脚本: script_options_example.js")
    }

    internal fun staticVoicesExampleBuiltInEngine(): TtsEngineSetting {
        return scriptEngineFromAsset("static_voices_example.js")
            ?: error("缺少默认 TTS 脚本: static_voices_example.js")
    }

    private fun defaultScriptEngines(): List<TtsEngineSetting> {
        return appCtx.assets.list(DEFAULT_TTS_ASSET_DIR)
            .orEmpty()
            .filter { it.endsWith(".js", ignoreCase = true) }
            .sortedWith(
                compareBy<String> {
                    DEFAULT_SCRIPT_ASSETS.indexOf(it).takeIf { index -> index >= 0 }
                        ?: Int.MAX_VALUE
                }.thenBy { it }
            )
            .mapNotNull { scriptEngineFromAsset(it) }
    }

    internal fun scriptEngineFromAsset(fileName: String): TtsEngineSetting? {
        val path = "$DEFAULT_TTS_ASSET_DIR/$fileName"
        val script = runCatching {
            appCtx.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return scriptEngineFromScript(script)
    }

    internal fun scriptEngineFromScript(script: String): TtsEngineSetting? {
        val metadata = parseScriptMetadata(script)
        val id = metadata["uuid"]?.takeIf { it.isNotBlank() } ?: return null
        val name = metadata["name"]?.takeIf { it.isNotBlank() } ?: return null
        val url = metadata["url"].orEmpty()
        return TtsEngineSetting(
            id = id,
            name = name,
            type = TtsEngineType.SCRIPT,
            enabled = metadata["enabled"].toScriptBoolean(defaultValue = true),
            builtIn = false,
            url = "",
            script = script,
            contentType = metadata["audiotype"]?.takeIf { it.isNotBlank() }
                ?: metadata["contenttype"]?.takeIf { it.isNotBlank() }
                ?: "audio/x-wav",
            enabledCookieJar = metadata["cookiejar"].toScriptBoolean(defaultValue = false),
            sampleText = metadata["sampletext"]?.takeIf { it.isNotBlank() },
            defaultSpeed = metadata["defaultspeed"].toScriptInt(defaultValue = 50),
            defaultVolume = metadata["defaultvolume"].toScriptInt(defaultValue = 50),
            defaultPitch = metadata["defaultpitch"].toScriptInt(defaultValue = 50),
            baseUrl = url.takeIf { it.startsWith("http://") || it.startsWith("https://") }.orEmpty(),
            synthesisPath = "/forward"
        )
    }

    internal fun parseScriptMetadata(script: String): Map<String, String> {
        val metadata = linkedMapOf<String, String>()
        val regex = Regex("""^\s*//\s*@([A-Za-z][\w-]*)\s*(.*)$""")
        script.lineSequence().forEach { line ->
            val match = regex.find(line) ?: return@forEach
            metadata[match.groupValues[1].lowercase()] = match.groupValues[2].trim()
        }
        return metadata
    }

    private fun String?.toScriptBoolean(defaultValue: Boolean): Boolean {
        return when (this?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> defaultValue
        }
    }

    private fun String?.toScriptInt(defaultValue: Int): Int {
        return this?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: defaultValue
    }

    private fun systemTtsEngines(): List<TtsEngineSetting> {
        val packageManager = appCtx.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfos.mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val packageName = serviceInfo.packageName?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager).toString()
                .takeIf { it.isNotBlank() }
                ?: serviceInfo.applicationInfo?.loadLabel(packageManager)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: packageName
            TtsEngineSetting(
                id = SYSTEM_ENGINE_PREFIX + packageName,
                name = label,
                type = TtsEngineType.SYSTEM,
                enabled = false,
                builtIn = true,
                enginePackage = packageName,
                defaultSpeed = 100,
                defaultVolume = 50,
                defaultPitch = 50
            )
        }.distinctBy { it.id }
    }

    private fun selectFirstEnabledEngine() {
        val nextId = engines().firstOrNull { it.enabled }?.id.orEmpty()
        appCtx.putPrefString(PreferKey.ttsEngineV2ActiveId, nextId)
        ReadAloud.upReadAloudClass()
    }

    private fun deletedEngineIds(): Set<String> {
        val json = appCtx.getPrefString(PreferKey.ttsEngineV2DeletedIds)
        return GSON.fromJsonArray<String>(json).getOrDefault(emptyList()).toSet()
    }

    private fun saveDeletedEngineIds(ids: Set<String>) {
        appCtx.putPrefString(PreferKey.ttsEngineV2DeletedIds, GSON.toJson(ids.toList()))
    }

    private fun defaultTemplateIds(): Set<String> {
        return setOf(
            MULTITTS_FORWARDER_ID,
            NEXT_EDGE_PROXY_ID,
            OPTIONS_EXAMPLE_ID,
            STATIC_VOICES_EXAMPLE_ID
        )
    }

    private fun retiredTemplateIds(): Set<String> {
        return setOf(
            BAIDU_TTS_ID,
            ALIYUN_TTS_ID,
            NEXT_FORWARDER_ID
        )
    }
}
