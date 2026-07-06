package io.legado.app.help.tts

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TtsHttpForwarderClientTest {

    private val engine = TtsEngineSetting(
        id = "multitts_forwarder",
        name = "MultiTTS 转发器",
        type = TtsEngineType.SCRIPT,
        script = """
            function synthesize(text, voice, params, options, ctx) {
                return {
                    url: "http://localhost:8774/forward?text=" + encodeURIComponent(text),
                    method: "GET"
                };
            }
        """.trimIndent(),
        baseUrl = "http://localhost:8774"
    )

    @Test
    fun buildSynthesisUrl_encodesTextAndParams() {
        val url = TtsHttpForwarderClient.buildSynthesisUrl(
            engine = engine,
            text = "你好 世界",
            voiceId = "zh-CN-XiaoxiaoNeural",
            speed = 50,
            volume = 60,
            pitch = 40
        )

        assertEquals("localhost", url.host)
        assertEquals("/forward", url.encodedPath)
        assertEquals("你好 世界", url.queryParameter("text"))
        assertEquals("zh-CN-XiaoxiaoNeural", url.queryParameter("voice"))
        assertEquals("50", url.queryParameter("speed"))
        assertEquals("60", url.queryParameter("volume"))
        assertEquals("40", url.queryParameter("pitch"))
    }

    @Test
    fun effectiveSynthesisUrl_usesRuntimeVoiceAndParams() {
        val url = engine.effectiveSynthesisUrl()

        assertEquals(
            "http://localhost:8774/forward?volume={{speakVolume}}&speed={{speakSpeed}}&voice={{voiceId}}&pitch={{speakPitch}}&text={{java.encodeURI(speakText)}}",
            url
        )
    }

    @Test
    fun effectiveVoicesUrl_requiresExplicitVoicesUrl() {
        val custom = engine.copy(baseUrl = "http://localhost:8774", voicesUrl = null)

        assertEquals("", custom.effectiveVoicesUrl())
        assertFalse(custom.supportsVoiceFetch())
    }

    @Test
    fun parseVoices_supportsArrayObjects() {
        val voices = TtsHttpForwarderClient.parseVoices(
            """
            [
              {"id":"v1","name":"晓晓","language":"zh-CN","gender":"female"},
              {"id":"v2","name":"云溪","language":"zh-CN","gender":"male"}
            ]
            """.trimIndent()
        )

        assertEquals(2, voices.size)
        assertEquals("v1", voices[0].id)
        assertEquals("晓晓", voices[0].name)
        assertEquals("v2", voices[1].id)
        assertEquals("云溪", voices[1].name)
    }

    @Test
    fun parseVoices_preservesExplicitExtraObject() {
        val voices = TtsHttpForwarderClient.parseVoices(
            """
            [
              {
                "id": "v1",
                "name": "鹿游",
                "extra": {
                  "speakerId": "spk_1"
                }
              }
            ]
            """.trimIndent()
        )

        assertEquals("spk_1", voices[0].extra?.get("speakerId")?.asString)
        assertEquals(false, voices[0].extra?.has("extra"))
    }

    @Test
    fun parseVoices_rejectsMultiTtsCatalogResponse() {
        val voices = TtsHttpForwarderClient.parseVoices(
            """
            {
              "success": true,
              "data": {
                "count": 2,
                "catalog": {
                  "microsoft": [
                    {
                      "id": "microsoft_zh-CN-XiaoxiaoNeural",
                      "name": "晓晓",
                      "gender": "female",
                      "locale": "zh-CN",
                      "desc": "zh-CN,Xiaoxiao",
                      "type": "offline"
                    },
                    {
                      "id": "microsoft_zh-CN-YunxiNeural",
                      "name": "云希",
                      "gender": "male",
                      "locale": "zh-CN",
                      "desc": "zh-CN,Yunxi",
                      "type": "offline"
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(emptyList<TtsVoice>(), voices)
    }

    @Test
    fun parseVoices_rejectsJsonStringPrimitive() {
        val body = """
            [
              {"id":"v1","name":"晓晓"}
            ]
        """.trimIndent()

        val voices = TtsHttpForwarderClient.parseVoices(Gson().toJson(body))

        assertEquals(emptyList<TtsVoice>(), voices)
    }

    @Test
    fun parseVoices_rejectsNameToIdMap() {
        val voices = TtsHttpForwarderClient.parseVoices(
            """
            {
              "晓晓": "zh-CN,Xiaoxiao",
              "云希": "zh-CN,Yunxi"
            }
            """.trimIndent()
        )

        assertEquals(emptyList<TtsVoice>(), voices)
    }

    @Test
    fun audioCacheKey_changesWithVoice() {
        val key1 = TtsScriptEngineClient.audioCacheKey(engine, "文本", "voice-a")
        val key2 = TtsScriptEngineClient.audioCacheKey(engine, "文本", "voice-b")

        assertNotEquals(key1, key2)
    }

    @Test
    fun engineJson_persistsStaticVoicesButNotRuntimeVoiceCache() {
        val json = Gson().toJson(
            engine.copy(
                voices = listOf(TtsVoice(id = "static-voice", name = "静态音色")),
                runtimeVoices = listOf(TtsVoice(id = "runtime-voice", name = "运行时音色")),
                lastVoiceUpdateTime = 123L
            )
        )

        assertEquals(true, json.contains("\"voices\""))
        assertEquals(true, json.contains("static-voice"))
        assertFalse(json.contains("runtime-voice"))
        assertFalse(json.contains("last_voice_update_time"))
    }

    @Test
    fun optionsExampleBuiltInEngine_listsSupportedOptionTypes() {
        val engine = scriptEngineFromAssetFile("script_options_example.js")

        assertEquals(TtsEngineStore.OPTIONS_EXAMPLE_ID, engine.id)
        assertEquals("脚本选项示例", engine.name)
        assertEquals(TtsEngineType.SCRIPT, engine.type)
        assertEquals(false, engine.builtIn)
        assertTrue(engine.supportsVoiceFetch())
        assertTrue(engine.script.contains("// @uuid script_options_example"))
        assertTrue(engine.script.contains("// @version 1.0.3"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.script.contains("function options()"))
        assertTrue(engine.script.contains("type: \"text\""))
        assertTrue(engine.script.contains("type: \"password\""))
        assertTrue(engine.script.contains("type: \"number\""))
        assertTrue(engine.script.contains("type: \"select\""))
        assertTrue(engine.script.contains("label: \"WAV 音频\""))
        assertTrue(engine.script.contains("type: \"boolean\""))
        assertTrue(engine.script.contains("function voices(options, ctx)"))
        assertTrue(engine.script.contains("function parseMultiTtsVoices(body)"))
        assertTrue(engine.script.contains("JSON.parse(String(body || \"{}\"))"))
        assertFalse(engine.script.contains("Object.keys(catalog)"))
        assertFalse(engine.script.contains("item.desc"))
        assertFalse(engine.script.contains("params.speed * 2"))
        assertTrue(engine.script.contains("return parseMultiTtsVoices(java.ajax"))
        assertTrue(engine.script.contains("function synthesize(text, voice, params, options, ctx)"))
        assertEquals("http://localhost:8774", engine.baseUrl)
        assertEquals(emptyMap<String, String>(), engine.optionValues)
    }

    @Test
    fun staticVoicesExampleBuiltInEngine_usesScriptVoicesFunction() {
        val engine = scriptEngineFromAssetFile("static_voices_example.js")

        assertEquals(TtsEngineStore.STATIC_VOICES_EXAMPLE_ID, engine.id)
        assertEquals("内置发音人示例", engine.name)
        assertEquals(TtsEngineType.SCRIPT, engine.type)
        assertEquals(false, engine.builtIn)
        assertEquals(true, engine.supportsVoiceFetch())
        assertEquals(emptyList<TtsVoice>(), engine.voices)
        assertEquals(emptyList<TtsVoice>(), engine.effectiveVoices())
        assertTrue(engine.script.contains("// @uuid script_static_voices_example"))
        assertTrue(engine.script.contains("// @version 1.0.2"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.script.contains("function options()"))
        assertTrue(engine.script.contains("function voices(options, ctx)"))
        assertTrue(engine.script.contains("microsoft_zh-CN-XiaoxiaoNeural"))
        assertTrue(engine.script.contains(DEFAULT_TTS_PREVIEW_TEXT))
        assertFalse(engine.script.contains("这是一段朗读试听。"))
        assertTrue(engine.script.contains("voice.extra && voice.extra.shortName"))
        assertTrue(engine.script.contains("function synthesize(text, voice, params, options, ctx)"))
        assertFalse(engine.script.contains("java.ajax(baseUrl(options) + \"/voices\")"))
    }

    @Test
    fun multiTtsBuiltInEngine_convertsVoicesInScript() {
        val engine = scriptEngineFromAssetFile("multitts_forwarder.js")

        assertTrue(engine.script.contains("// @version 1.0.3"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.script.contains("function parseMultiTtsVoices(body)"))
        assertTrue(engine.script.contains("JSON.parse(String(body || \"{}\"))"))
        assertFalse(engine.script.contains("Object.keys(catalog)"))
        assertFalse(engine.script.contains("item.desc"))
        assertFalse(engine.script.contains("params.speed * 2"))
        assertTrue(engine.script.contains("extra: item"))
    }

    @Test
    fun scriptMetadata_parsesHeaderComments() {
        val metadata = TtsEngineStore.parseScriptMetadata(
            """
            // @name 示例
            // @version 1.0.0
            // @uuid demo_tts
            // @cookieJar true
            // @defaultSpeed 42
            // @defaultVolume 60
            // @defaultPitch 55
            function options() { return []; }
            """.trimIndent()
        )

        assertEquals("示例", metadata["name"])
        assertEquals("demo_tts", metadata["uuid"])
        assertEquals("true", metadata["cookiejar"])
        assertEquals("42", metadata["defaultspeed"])
        assertEquals("60", metadata["defaultvolume"])
        assertEquals("55", metadata["defaultpitch"])
    }

    @Test
    fun scriptEngineFromScript_parsesDefaultParams() {
        val engine = TtsEngineStore.scriptEngineFromScript(
            """
            // @name 示例
            // @uuid demo_tts_defaults
            // @defaultSpeed 42
            // @defaultVolume 60
            // @defaultPitch 55
            function synthesize(text, voice, params, options, ctx) { return {}; }
            """.trimIndent()
        )!!

        assertEquals(42, engine.defaultSpeed)
        assertEquals(60, engine.defaultVolume)
        assertEquals(55, engine.defaultPitch)
    }

    @Test
    fun effectiveVoices_prefersRuntimeVoiceCacheOverConfigVoices() {
        val engine = TtsEngineSetting(
            id = "voice-source-test",
            name = "voice source test",
            type = TtsEngineType.SCRIPT,
            voices = listOf(TtsVoice(id = "config", name = "配置音色")),
            runtimeVoices = listOf(TtsVoice(id = "runtime", name = "运行时音色"))
        )

        assertEquals(listOf("runtime"), engine.effectiveVoices().map { it.id })
    }

    @Test
    fun scriptOption_toleratesMissingOptionalFields() {
        val options = Gson().fromJson(
            """
            [
              {"key":"baseUrl","label":"服务地址","type":"text"},
              {"key":"flag","label":"开关"},
              {"label":"缺少 key"}
            ]
            """.trimIndent(),
            Array<TtsScriptOption>::class.java
        ).toList()

        assertEquals("baseUrl", options[0].safeKey)
        assertEquals("text", options[0].normalizedType)
        assertEquals(emptyList<TtsScriptOptionValue>(), options[0].safeValues)
        assertEquals("flag", options[1].safeKey)
        assertEquals("text", options[1].normalizedType)
        assertEquals("", options[2].safeKey)
    }

    @Test
    fun scriptOption_supportsSelectLabelValueObjects() {
        val options = Gson().fromJson(
            """
            [
              {
                "key": "quality",
                "label": "音质",
                "type": "select",
                "values": [
                  {"label": "普通音质", "value": "normal"},
                  {"label": "高品质", "value": "high"}
                ],
                "defaultValue": "high"
              }
            ]
            """.trimIndent(),
            Array<TtsScriptOption>::class.java
        ).toList()

        assertEquals(
            listOf(
                TtsScriptOptionValue("普通音质", "normal"),
                TtsScriptOptionValue("高品质", "high")
            ),
            options[0].safeValues
        )
    }

    @Test
    fun scriptRequest_parsesAudioAndRequestContentTypes() {
        val request = TtsScriptEngineClient.parseSynthesisRequest(
            """
            {
              "url": "https://example.com/tts",
              "method": "POST",
              "headers": {"Authorization": "Bearer token"},
              "body": {"text": "hello"},
              "requestContentType": "application/json",
              "audioContentType": "audio/mpeg",
              "responseType": "json",
              "audioExtract": "${'$'}.data.audio",
              "audioEncoding": "base64",
              "timeout": 15,
              "retry": 1
            }
            """.trimIndent(),
            engine
        )

        assertEquals("https://example.com/tts", request.url)
        assertEquals("POST", request.method)
        assertEquals("application/json", request.requestContentType)
        assertEquals("audio/mpeg", request.audioContentType)
        assertEquals(true, request.isJsonResponse)
        assertEquals("${'$'}.data.audio", request.audioExtract)
        assertEquals("base64", request.normalizedAudioEncoding)
        assertEquals(15_000L, request.timeoutMillis)
        assertEquals(1, request.retry)
        assertTrue(request.toAnalyzeUrlRule().contains("Content-Type"))
    }

    @Test
    fun scriptRequest_legacyContentTypeMeansAudioContentType() {
        val request = TtsScriptEngineClient.parseSynthesisRequest(
            """{"url":"https://example.com/tts","contentType":"audio/x-wav"}""",
            engine
        )

        assertEquals("audio/x-wav", request.audioContentType)
        assertEquals(null, request.requestContentType)
    }

    @Test
    fun scriptRequest_extractsAudioValueByJsonPath() {
        val body = """
            {
              "data": {
                "items": [
                  {"audio": "AAA="}
                ]
              }
            }
        """.trimIndent()

        assertEquals(
            "AAA=",
            TtsScriptEngineClient.extractAudioValue(body, "${'$'}.data.items[0].audio")
        )
    }

    @Test
    fun voiceJson_preservesExtraObject() {
        val voice = Gson().fromJson(
            """
            {
              "id": "v1",
              "name": "鹿游",
              "extra": {
                "speakerId": "spk_1",
                "model": "novel"
              }
            }
            """.trimIndent(),
            TtsVoice::class.java
        )

        assertEquals("spk_1", voice.extra?.get("speakerId")?.asString)
        assertEquals(
            "novel",
            JsonParser.parseString(Gson().toJson(voice)).asJsonObject
                .getAsJsonObject("extra")
                .get("model")
                .asString
        )
    }

    @Test
    fun engineEnabledVoices_excludesDisabledVoiceIds() {
        val engine = TtsEngineSetting(
            id = "voice-enabled-test",
            name = "voice enabled test",
            type = TtsEngineType.SCRIPT,
            voices = listOf(
                TtsVoice(id = "v1", name = "Voice 1"),
                TtsVoice(id = "v2", name = "Voice 2")
            ),
            disabledVoiceIds = listOf("v2")
        )

        assertEquals(listOf("v1"), engine.enabledVoices().map { it.id })
        assertEquals(false, engine.isVoiceEnabled(engine.voices[1]))
    }

    @Test
    fun previewText_usesDefaultPreviewTextForAllLanguages() {
        assertEquals(DEFAULT_TTS_PREVIEW_TEXT, TtsVoice(id = "zh", name = "zh", language = "zh-CN").previewText())
        assertEquals(DEFAULT_TTS_PREVIEW_TEXT, TtsVoice(id = "ja", name = "ja", language = "ja-JP").previewText())
        assertEquals(DEFAULT_TTS_PREVIEW_TEXT, TtsVoice(id = "ko", name = "ko", language = "ko-KR").previewText())
        assertEquals(DEFAULT_TTS_PREVIEW_TEXT, TtsVoice(id = "en", name = "en", language = "en-US").previewText())
    }

    @Test
    fun normalizeEditedEngineJson_rejectsBrokenNullableSourceJson() {
        val result = TtsEngineStore.normalizeEditedEngineJson(
            """
            {
              "id": "edited",
              "name": null,
              "type": null,
              "enabled": null,
              "url": "http://localhost:8774/forward?text={{speakText}}",
              "voices": null,
              "default_speed": null,
              "default_volume": 160,
              "default_pitch": -8
            }
            """.trimIndent(),
            engine
        )

        assertEquals(true, result.isFailure)
    }

    @Test
    fun normalizeEditedEngineJson_acceptsNullStaticVoices() {
        val normalized = TtsEngineStore.normalizeEditedEngineJson(
            """
            {
              "id": "edited",
              "name": "MultiTTS 转发器",
              "type": "script",
              "enabled": true,
              "script": "function synthesize(text, voice, params, options, ctx){return {url:'http://localhost:8774/forward?text='+encodeURIComponent(text)}}",
              "voices": null,
              "default_speed": 50,
              "default_volume": 160,
              "default_pitch": -8
            }
            """.trimIndent(),
            engine
        ).getOrThrow()

        assertEquals(engine.id, normalized.id)
        assertEquals("MultiTTS 转发器", normalized.name)
        assertEquals(true, normalized.enabled)
        assertEquals(emptyList<TtsVoice>(), normalized.voices)
        assertEquals(50, normalized.defaultSpeed)
        assertEquals(100, normalized.defaultVolume)
        assertEquals(0, normalized.defaultPitch)
    }

    @Test
    fun normalizeEditedEngine_rejectsMissingIdSourceJson() {
        val parsed = Gson().fromJson(
            """
            {
              "name": "坏配置",
              "type": "script",
              "script": "function synthesize(text, voice, params, options, ctx){return {url:'http://localhost:8774/forward?text='+encodeURIComponent(text)}}"
            }
            """.trimIndent(),
            TtsEngineSetting::class.java
        )

        assertEquals(true, TtsEngineStore.normalizeEditedEngine(parsed, engine).isFailure)
    }

    private fun scriptEngineFromAssetFile(fileName: String): TtsEngineSetting {
        val scriptFile = listOf(
            File("src/main/assets/defaultData/tts/$fileName"),
            File("app/src/main/assets/defaultData/tts/$fileName")
        ).first { it.isFile }
        val script = scriptFile.readText()
        return TtsEngineStore.scriptEngineFromScript(script)
            ?: error("invalid script asset: $fileName")
    }

}
