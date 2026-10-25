package io.legado.app.help.tts

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import io.legado.app.constant.AppPattern
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.htmlunit.corejs.javascript.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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
    fun parseVoices_readsStyleOptionsFromExtra() {
        val voices = TtsHttpForwarderClient.parseVoices(
            """
            [
              {
                "id": "v1",
                "name": "鹿游",
                "extra": {
                  "styles": [
                    { "id": "angry", "name": "愤怒", "value": "angry" },
                    { "id": "calm", "name": "平静", "value": "calm" }
                  ]
                }
              }
            ]
            """.trimIndent()
        )

        val styles = voices[0].styleOptions()
        assertEquals(2, styles.size)
        assertEquals("angry", styles[0].id)
        assertEquals("愤怒", styles[0].displayName)
        assertEquals("calm", voices[0].styleById("calm")?.scriptValue)
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
    fun audioCacheKey_changesWithStyle() {
        val key1 = TtsScriptEngineClient.audioCacheKey(engine, "文本", "voice-a", "angry")
        val key2 = TtsScriptEngineClient.audioCacheKey(engine, "文本", "voice-a", "calm")

        assertNotEquals(key1, key2)
    }

    @Test
    fun audioCacheKey_changesWithSynthesisContext() {
        val key1 = TtsScriptEngineClient.audioCacheKey(engine, "文本", "voice-a")
        val key2 = TtsScriptEngineClient.audioCacheKey(
            engine = engine,
            text = "文本",
            voiceId = "voice-a",
            synthesisContext = TtsSynthesisContext(
                mode = TtsSynthesisContext.Mode.PERFORMANCE,
                role = TtsRoleContext(name = "安秋月", gender = "female"),
                scene = TtsSceneContext(title = "丢失生活费", text = "她低着头抽泣。")
            )
        )

        assertNotEquals(key1, key2)
    }

    @Test
    fun synthesisContext_serializesApprovedItemsForScript() {
        val json = Gson().toJson(
            TtsSynthesisContext(
                mode = TtsSynthesisContext.Mode.PERFORMANCE,
                role = TtsRoleContext(name = "安秋月", gender = "female"),
                scene = TtsSceneContext(
                    title = "丢失生活费",
                    text = "安秋月独自在陌生城市丢失生活费。\n陈升追问后，她忍不住哭起来。",
                    contextTexts = listOf(
                        "安秋月独自在陌生城市丢失生活费。",
                        "陈升追问后，她忍不住哭起来。"
                    )
                )
            )
        )
        val scene = JsonParser.parseString(json).asJsonObject.getAsJsonObject("scene")

        assertEquals(2, scene.getAsJsonArray("context_texts").size())
        assertEquals("安秋月独自在陌生城市丢失生活费。", scene.getAsJsonArray("context_texts")[0].asString)
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
        assertFalse(engine.enabled)
        assertEquals(false, engine.builtIn)
        assertTrue(engine.supportsVoiceFetch())
        assertTrue(engine.script.contains("// @uuid script_options_example"))
        assertTrue(engine.script.contains("// @version 1.0.5"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
        assertTrue(engine.script.contains("function options()"))
        assertTrue(engine.script.contains("type: \"text\""))
        assertTrue(engine.script.contains("type: \"password\""))
        assertTrue(engine.script.contains("type: \"randomNumber\""))
        assertTrue(engine.script.contains("digits: 13"))
        assertTrue(engine.script.contains("allowLeadingZero: false"))
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
    fun scriptOption_supportsReusableRandomNumberType() {
        val options = Gson().fromJson(
            """
            [
              {"key":"camel","type":"randomNumber","digits":6,"allowLeadingZero":true},
              {"key":"snake","type":"random_number"},
              {"key":"kebab","type":"random-number"}
            ]
            """.trimIndent(),
            Array<TtsScriptOption>::class.java
        ).toList()

        assertEquals(
            listOf("random_number", "random_number", "random_number"),
            options.map { it.normalizedType }
        )
        assertEquals(6, options.first().randomNumberDigits)
        assertTrue(options.first().randomNumberAllowsLeadingZero)
        repeat(20) {
            val randomNumber = generateTtsRandomNumber()
            assertEquals(13, randomNumber.length)
            assertTrue(randomNumber.all { it in '0'..'9' })
            assertTrue(randomNumber.first() != '0')
            assertTrue(isValidTtsRandomNumber(randomNumber))
        }
        assertTrue(isValidTtsRandomNumber("012345", digits = 6, allowLeadingZero = true))
        assertFalse(isValidTtsRandomNumber("012345", digits = 6, allowLeadingZero = false))
        assertFalse(isValidTtsRandomNumber(null))
        assertFalse(isValidTtsRandomNumber(""))
        assertFalse(isValidTtsRandomNumber("123456789012"))
        assertFalse(isValidTtsRandomNumber("12345678901234"))
        assertFalse(isValidTtsRandomNumber("123456789012x"))
        assertFalse(isValidTtsRandomNumber("１２３４５６７８９０１２３"))
    }

    @Test
    fun scriptOption_doesNotUseBusinessFieldNameAsRandomNumberType() {
        assertEquals(
            "text",
            TtsScriptOption(key = "deviceId", type = "deviceId").normalizedType
        )
    }

    @Test
    fun scriptOption_invalidSchemaIsNotTreatedAsEmptySchema() {
        assertEquals(emptyList<TtsScriptOption>(), TtsScriptEngineClient.parseOptionsResult(null))
        assertEquals(emptyList<TtsScriptOption>(), TtsScriptEngineClient.parseOptionsResult("[]"))
        assertThrows(JsonSyntaxException::class.java) {
            TtsScriptEngineClient.parseOptionsResult("{\"token\":\"secret\"}")
        }
        assertThrows(JsonSyntaxException::class.java) {
            TtsScriptEngineClient.parseOptionsResult("[{}]")
        }
        assertThrows(JsonSyntaxException::class.java) {
            TtsScriptEngineClient.parseOptionsResult("[{\"key\":\"\"}]")
        }
        assertThrows(JsonSyntaxException::class.java) {
            TtsScriptEngineClient.parseOptionsResult(
                "[{\"key\":\"token\"},{\"key\":\"token\"}]"
            )
        }
    }

    @Test
    fun effectiveOptionValues_materializesStableRandomNumberBeforeFormLoads() {
        val option = TtsScriptOption(
            key = "deviceId",
            type = "random_number",
            digits = 13,
            allowLeadingZero = false
        )
        val first = engine.effectiveOptionValues(listOf(option)).getValue("deviceId")
        val second = engine.effectiveOptionValues(listOf(option)).getValue("deviceId")

        assertTrue(isValidTtsRandomNumber(first))
        assertEquals(first, second)

        val explicit = "1234567890123"
        val saved = engine.copy(optionValues = mapOf("deviceId" to explicit))
        assertEquals(explicit, saved.effectiveOptionValues(listOf(option)).getValue("deviceId"))

        repeat(80) { index ->
            engine.effectiveOptionValues(
                listOf(option.copy(key = "generated-$index"))
            )
        }
        assertEquals(first, engine.effectiveOptionValues(listOf(option)).getValue("deviceId"))
    }

    @Test
    fun staticVoicesExampleBuiltInEngine_usesScriptVoicesFunction() {
        val engine = scriptEngineFromAssetFile("static_voices_example.js")

        assertEquals(TtsEngineStore.STATIC_VOICES_EXAMPLE_ID, engine.id)
        assertEquals("内置发音人示例", engine.name)
        assertEquals(TtsEngineType.SCRIPT, engine.type)
        assertFalse(engine.enabled)
        assertEquals(false, engine.builtIn)
        assertEquals(true, engine.supportsVoiceFetch())
        assertEquals(emptyList<TtsVoice>(), engine.voices)
        assertEquals(emptyList<TtsVoice>(), engine.effectiveVoices())
        assertTrue(engine.script.contains("// @uuid script_static_voices_example"))
        assertTrue(engine.script.contains("// @version 1.0.3"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
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

        assertFalse(engine.enabled)
        assertTrue(engine.script.contains("// @version 1.0.4"))
        assertEquals(50, engine.defaultSpeed)
        assertEquals(50, engine.defaultVolume)
        assertEquals(50, engine.defaultPitch)
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
        assertTrue(engine.script.contains("function parseMultiTtsVoices(body)"))
        assertTrue(engine.script.contains("JSON.parse(String(body || \"{}\"))"))
        assertFalse(engine.script.contains("Object.keys(catalog)"))
        assertFalse(engine.script.contains("item.desc"))
        assertFalse(engine.script.contains("params.speed * 2"))
        assertTrue(engine.script.contains("extra: item"))
    }

    @Test
    fun nextEdgeProxyBuiltInEngine_declaresStyleOptions() {
        val engine = scriptEngineFromAssetFile("next_edge_proxy.js")

        assertEquals(TtsEngineStore.NEXT_EDGE_PROXY_ID, engine.id)
        assertEquals("Next Edge TTS", engine.name)
        assertEquals(TtsEngineType.SCRIPT, engine.type)
        assertEquals(true, engine.enabled)
        assertEquals("audio/mpeg", engine.contentType)
        assertEquals("https://libretts.is-an.org/api/tts", engine.baseUrl)
        assertTrue(engine.supportsVoiceFetch())
        assertTrue(engine.script.contains("// @version 1.0.11"))
        assertTrue(engine.script.contains("function voiceCatalog()"))
        assertFalse(engine.script.contains("key: \"api\""))
        assertFalse(engine.script.contains("5.45.99.149"))
        assertFalse(engine.script.contains("146.56.188.115"))
        assertFalse(engine.script.contains("36.248.181.23"))
        assertTrue(engine.supportsCapability(TtsEngineCapability.STYLE_TAGS))
        assertTrue(engine.supportsCapability(TtsEngineCapability.EMOTION))
        assertFalse(engine.supportsCapability(TtsEngineCapability.EMOTION_INTENSITY))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
        assertEquals("前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。", engine.sampleText)
        assertTrue(engine.script.contains("STYLE_NAMES"))
        assertTrue(engine.script.contains("profile: \"少女感-温柔旁白\""))
        assertTrue(engine.script.contains("profile: \"女童/少女-稚嫩清亮\""))
        assertTrue(engine.script.contains("categories: [\"News\", \"Novel\"]"))
        assertTrue(engine.script.contains("selected_style"))
        assertTrue(engine.script.contains("style_value"))
        assertTrue(engine.script.contains("function automaticStyleValue(voice, ctx)"))
        assertTrue(engine.script.contains("ctx.synthesis.expressive"))
        assertFalse(engine.script.contains("0.5s"))
        assertFalse(engine.script.contains("0.7s"))
        assertFalse(engine.script.contains("0.9s"))
        assertFalse(engine.script.contains("sample_text: \"前不见古人，后不见来者。念天地之悠悠，独怆然而涕下。\""))
        assertTrue(engine.script.contains("zh-CN-XiaoxiaoNeural"))
        assertTrue(engine.script.contains("name: \"云希\""))
        assertTrue(engine.script.contains("|| \"zh-CN-YunxiNeural\""))
        assertTrue(engine.script.contains("function synthesize(text, voice, params, options, ctx)"))
    }

    @Test
    fun nextEdgeProxyDefaultsToYunxiWithoutOverwritingExistingVoice() {
        val engine = scriptEngineFromAssetFile("next_edge_proxy.js")
        val voices = listOf(
            TtsVoice(id = "zh-CN-XiaoxiaoNeural", name = "晓晓"),
            TtsVoice(id = TtsEngineStore.NEXT_EDGE_DEFAULT_VOICE_ID, name = "云希")
        )

        assertEquals(
            TtsEngineStore.NEXT_EDGE_DEFAULT_VOICE_ID,
            TtsEngineStore.resolveActiveVoiceId(engine, voices)
        )
        assertEquals(
            "zh-CN-XiaoxiaoNeural",
            TtsEngineStore.resolveActiveVoiceId(
                engine.copy(activeVoiceId = "zh-CN-XiaoxiaoNeural"),
                voices
            )
        )
    }

    @Test
    fun nextEdgeLibreTtsRequestEncodesTextAndUsesFixedEndpoint() {
        val text = "字面文本：{{1+1}}，@Js:，<js>1+1</js>，& +?，😀"
        val request = evaluateNextEdgeScript(
            "synthesize(${Gson().toJson(text)}, null, {}, " +
                "{api:'http://5.45.99.149:8075/tts',timeout:'45'}, {})"
        )
        val url = request["url"].asString.toHttpUrl()

        assertEquals("https", url.scheme)
        assertEquals("libretts.is-an.org", url.host)
        assertEquals("/api/tts", url.encodedPath)
        assertEquals(setOf("t", "v", "r", "p", "style", "o"), url.queryParameterNames)
        assertEquals(text, url.queryParameter("t"))
        assertEquals(TtsEngineStore.NEXT_EDGE_DEFAULT_VOICE_ID, url.queryParameter("v"))
        assertEquals("0", url.queryParameter("r"))
        assertEquals("0", url.queryParameter("p"))
        assertEquals("", url.queryParameter("style"))
        assertEquals("mp3", url.queryParameter("o"))
        assertEquals("GET", request["method"].asString)
        assertEquals("audio/mpeg", request["audioContentType"].asString)
        assertEquals(45, request["timeout"].asInt)
        assertEquals(1, request["retry"].asInt)
        assertFalse(AppPattern.JS_PATTERN.matcher(request["url"].asString).find())
        assertFalse(AppPattern.EXP_PATTERN.matcher(request["url"].asString).find())
    }

    @Test
    fun nextEdgeLibreTtsManualStyleTakesPriorityOverExpressiveContext() {
        listOf("selected_style:{value:'sad'}", "style_value:'sad'").forEach { style ->
            val request = evaluateNextEdgeScript(
                "synthesize('手选风格', {id:'zh-CN-XiaoxiaoNeural',$style," +
                    "extra:{azure_styles:['sad','cheerful']}}, " +
                    "{speed:80,pitch:20}, {}, {synthesis:{expressive:{emotion:'开心'}}})"
            )
            val url = request["url"].asString.toHttpUrl()

            assertEquals("zh-CN-XiaoxiaoNeural", url.queryParameter("v"))
            assertEquals("sad", url.queryParameter("style"))
            assertEquals("60", url.queryParameter("r"))
            assertEquals("-60", url.queryParameter("p"))
            assertNull(url.queryParameter("s"))
        }
    }

    @Test
    fun nextEdgeLibreTtsAutomaticStyleRespectsVoiceSupportAndParamBounds() {
        listOf("['cheerful']" to "cheerful", "[]" to "").forEach { (styles, expected) ->
            val request = evaluateNextEdgeScript(
                "synthesize('自动风格', {id:'zh-CN-YunxiNeural'," +
                    "extra:{azure_styles:$styles}}, {speed:1000,pitch:-100}, {}, " +
                    "{synthesis:{expressive:{emotion:'开心'}}})"
            )
            val url = request["url"].asString.toHttpUrl()

            assertEquals(expected, url.queryParameter("style"))
            assertEquals("100", url.queryParameter("r"))
            assertEquals("-100", url.queryParameter("p"))
        }
    }

    @Test
    fun microsoftTranslatorDefaultsToYunxiWithoutOverwritingExistingVoice() {
        val engine = scriptEngineFromAssetFile("microsoft_translator.js")
        val voices = listOf(
            TtsVoice(id = "zh-CN-XiaoxiaoNeural", name = "晓晓"),
            TtsVoice(id = TtsEngineStore.MICROSOFT_TRANSLATOR_DEFAULT_VOICE_ID, name = "云希")
        )

        assertTrue(engine.enabled)
        assertEquals(TtsEngineStore.MICROSOFT_TRANSLATOR_ID, engine.id)
        assertEquals(
            TtsEngineStore.MICROSOFT_TRANSLATOR_DEFAULT_VOICE_ID,
            TtsEngineStore.resolveActiveVoiceId(engine, voices)
        )
        assertEquals(
            "zh-CN-XiaoxiaoNeural",
            TtsEngineStore.resolveActiveVoiceId(
                engine.copy(activeVoiceId = "zh-CN-XiaoxiaoNeural"),
                voices
            )
        )
    }

    @Test
    fun microsoftTranslatorIsDefaultRegardlessOfSavedListOrder() {
        val edge = scriptEngineFromAssetFile("next_edge_proxy.js").copy(enabled = true)
        val translator = scriptEngineFromAssetFile("microsoft_translator.js")
        val engines = listOf(edge, translator)

        assertEquals(translator.id, TtsEngineStore.resolveActiveEngine(engines, null)?.id)
        assertEquals(translator.id, TtsEngineStore.resolveActiveEngine(engines, "deleted")?.id)
        assertEquals(edge.id, TtsEngineStore.resolveActiveEngine(engines, edge.id)?.id)
    }

    @Test
    fun microsoftTranslatorChineseCatalogUpdatePreservesUserSettings() {
        val builtIn = scriptEngineFromAssetFile("microsoft_translator.js")
        val saved = builtIn.copy(
            script = builtIn.script.replaceFirst("// @version 1.0.1", "// @version 1.0.0"),
            name = "我的微软翻译",
            enabled = false,
            activeVoiceId = "zh-CN-XiaoxiaoNeural",
            optionValues = mapOf("timeout" to "60")
        )

        val updated = TtsEngineStore.updateDefaultScriptForTest(saved, builtIn)

        assertEquals(builtIn.script, updated.script)
        assertEquals(saved.name, updated.name)
        assertEquals(saved.enabled, updated.enabled)
        assertEquals(saved.activeVoiceId, updated.activeVoiceId)
        assertEquals(saved.optionValues, updated.optionValues)
        assertFalse(TtsEngineStore.preservesVoiceCatalogOnDefaultUpgrade(saved, updated))
    }

    @Test
    fun microsoftTranslatorPreservesLiteralTextAcrossRequestRuleBoundary() {
        val text = "字面文本：{{1+1}}，@Js:，<js>1+1</js>，&，😀"
        val engine = scriptEngineFromAssetFile("microsoft_translator.js")
        val context = Context.enter()
        val ssml = try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            context.evaluateString(scope, engine.script, "microsoft_translator.js", 1, null)
            Context.toString(
                context.evaluateString(
                    scope,
                    "buildTranslatorSsml(${Gson().toJson(text)}, null, {}, {});",
                    "literal-text", 1, null
                )
            )
        } finally {
            Context.exit()
        }
        val request = TtsScriptEngineClient.TtsScriptRequest(
            url = "https://southeastasia.tts.speech.microsoft.com/cognitiveservices/v1",
            method = "POST",
            body = ssml,
            requestContentType = "application/ssml+xml"
        )
        val rule = request.toAnalyzeUrlRule()

        assertFalse(AppPattern.JS_PATTERN.matcher(rule).find())
        assertFalse(AppPattern.EXP_PATTERN.matcher(rule).find())
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(ssml.byteInputStream(Charsets.UTF_8))
        assertEquals(
            text,
            document.getElementsByTagNameNS("http://www.w3.org/2001/10/synthesis", "prosody")
                .item(0).textContent
        )
    }

    @Test
    fun unavailableMicrosoftTranslatorDoesNotOverrideEnabledEngines() {
        val edge = scriptEngineFromAssetFile("next_edge_proxy.js").copy(enabled = true)
        val translator = scriptEngineFromAssetFile("microsoft_translator.js").copy(enabled = false)

        assertEquals(
            edge.id,
            TtsEngineStore.resolveActiveEngine(listOf(edge, translator), translator.id)?.id
        )
        assertEquals(edge.id, TtsEngineStore.resolveActiveEngine(listOf(edge), null)?.id)
        assertNull(TtsEngineStore.resolveActiveEngine(listOf(translator), null))
    }

    @Test
    fun nextEdgeLazyCatalogUpgradePreservesExistingVoiceDirectory() {
        val upgraded = scriptEngineFromAssetFile("next_edge_proxy.js")
        listOf("1.0.8", "1.0.9", "1.0.10").forEach { version ->
            val saved = upgraded.copy(
                script = upgraded.script.replaceFirst("// @version 1.0.11", "// @version $version")
            )

            assertTrue(TtsEngineStore.preservesVoiceCatalogOnDefaultUpgrade(saved, upgraded))
        }
        assertFalse(
            TtsEngineStore.preservesVoiceCatalogOnDefaultUpgrade(
                saved = upgraded.copy(
                    script = upgraded.script.replaceFirst("// @version 1.0.11", "// @version 1.0.7")
                ),
                upgraded = upgraded
            )
        )
    }

    @Test
    fun firstUseRoleDefaultsSelectMicrosoftTranslatorAndYunxi() {
        val defaults = resolveFirstUseTtsRoleDefaults(
            currentMultiRoleEngineId = null,
            currentNarratorEngineId = null,
            currentNarratorVoiceId = null,
            translatorAvailable = true
        )

        assertEquals(TtsEngineStore.MICROSOFT_TRANSLATOR_ID, defaults.multiRoleEngineId)
        assertEquals(TtsEngineStore.MICROSOFT_TRANSLATOR_ID, defaults.narratorEngineId)
        assertEquals(TtsEngineStore.MICROSOFT_TRANSLATOR_DEFAULT_VOICE_ID, defaults.narratorVoiceId)
    }

    @Test
    fun firstUseRoleDefaultsPreserveExistingSelections() {
        val defaults = resolveFirstUseTtsRoleDefaults(
            currentMultiRoleEngineId = "dialogue_engine",
            currentNarratorEngineId = "narrator_engine",
            currentNarratorVoiceId = "narrator_voice",
            translatorAvailable = true
        )

        assertEquals("dialogue_engine", defaults.multiRoleEngineId)
        assertEquals("narrator_engine", defaults.narratorEngineId)
        assertEquals("narrator_voice", defaults.narratorVoiceId)
    }

    @Test
    fun firstUseRoleDefaultsDoNotRecreateUnavailableMicrosoftTranslator() {
        val defaults = resolveFirstUseTtsRoleDefaults(
            currentMultiRoleEngineId = null,
            currentNarratorEngineId = null,
            currentNarratorVoiceId = null,
            translatorAvailable = false
        )

        assertNull(defaults.multiRoleEngineId)
        assertNull(defaults.narratorEngineId)
        assertNull(defaults.narratorVoiceId)
    }

    @Test
    fun mimoBuiltInEngineConsumesExpressiveFieldsAsInstructions() {
        val engine = scriptEngineFromAssetFile("mimo_v25_tts.js")

        assertEquals(TtsEngineStore.MIMO_V25_TTS_ID, engine.id)
        assertTrue(engine.script.contains("// @version 1.0.2"))
        assertTrue(engine.supportsCapability(TtsEngineCapability.STYLE_TAGS))
        assertTrue(engine.supportsCapability(TtsEngineCapability.EMOTION))
        assertTrue(engine.supportsCapability(TtsEngineCapability.EMOTION_INTENSITY))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT))
        assertFalse(engine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
        assertTrue(engine.script.contains("function expressiveInstruction(ctx)"))
        assertTrue(engine.script.contains("expressive.style_concepts"))
        assertTrue(engine.script.contains("expressive.emotion"))
        assertTrue(engine.script.contains("expressive.intensity"))
        assertTrue(engine.script.contains("buildUserInstruction(voice, params, options, ctx)"))
    }

    @Test
    fun expressiveBuiltInUpgradeRefreshesScriptAndCapabilitiesTogether() {
        val nextEdge = scriptEngineFromAssetFile("next_edge_proxy.js")
        val savedNextEdge = TtsEngineStore.scriptEngineFromScript(
            nextEdge.script
                .replace("// @version 1.0.11", "// @version 1.0.5")
                .replace(Regex("// @capabilities style_tags,emotion\\r?\\n"), "")
        )!!
        val updatedNextEdge = TtsEngineStore.updateDefaultScriptForTest(savedNextEdge, nextEdge)

        assertEquals(nextEdge.script, updatedNextEdge.script)
        assertEquals(nextEdge.capabilities, updatedNextEdge.capabilities)

        val mimo = scriptEngineFromAssetFile("mimo_v25_tts.js")
        val savedMimo = TtsEngineStore.scriptEngineFromScript(
            mimo.script
                .replace("// @version 1.0.2", "// @version 1.0.0")
                .replace(
                    Regex("// @capabilities style_tags,emotion,emotion_intensity\\r?\\n"),
                    ""
                )
        )!!
        val updatedMimo = TtsEngineStore.updateDefaultScriptForTest(savedMimo, mimo)

        assertEquals(mimo.script, updatedMimo.script)
        assertEquals(mimo.capabilities, updatedMimo.capabilities)
    }

    @Test
    fun nextEdgeLibreTtsUpgradePreservesUserSettingsWithoutMigratingStoredEndpoint() {
        val builtIn = scriptEngineFromAssetFile("next_edge_proxy.js")
        for (patchVersion in 0..10) {
            val saved = builtIn.copy(
                script = builtIn.script.replaceFirst(
                    "// @version 1.0.11", "// @version 1.0.$patchVersion"
                ),
                name = "我的 Edge",
                baseUrl = "http://146.56.188.115:8080/tts",
                enabled = patchVersion % 2 == 0,
                activeVoiceId = "zh-CN-XiaoxiaoNeural",
                disabledVoiceIds = listOf("zh-CN-YunjianNeural"),
                optionValues = mapOf(
                    "api" to "http://146.56.188.115:8080/tts",
                    "timeout" to "45"
                )
            )

            val updated = TtsEngineStore.updateDefaultScriptForTest(saved, builtIn)

            assertEquals(builtIn.script, updated.script)
            assertEquals(builtIn.baseUrl, updated.baseUrl)
            assertEquals(saved.name, updated.name)
            assertEquals(saved.enabled, updated.enabled)
            assertEquals(saved.activeVoiceId, updated.activeVoiceId)
            assertEquals(saved.disabledVoiceIds, updated.disabledVoiceIds)
            assertEquals(saved.optionValues, updated.optionValues)
        }
        val options = evaluateNextEdgeScript("({schema:options()})")["schema"].asJsonArray
        val optionSchema = Gson().fromJson(options, Array<TtsScriptOption>::class.java).toList()
        val savedOptions = builtIn.copy(
            optionValues = mapOf("api" to "http://example.com/tts", "timeout" to "45")
        )

        assertEquals(listOf("timeout"), optionSchema.map { it.safeKey })
        assertEquals(mapOf("timeout" to "45"), savedOptions.effectiveOptionValues(optionSchema))
    }

    @Test
    fun nextEdgeLibreTtsUpgradeDoesNotDowngradeFutureScriptVersion() {
        val builtIn = scriptEngineFromAssetFile("next_edge_proxy.js")
        listOf("1.0.11", "1.0.12", "1.0.100", "2.0.0").forEach { version ->
            val saved = builtIn.copy(
                script = builtIn.script.replaceFirst("// @version 1.0.11", "// @version $version") +
                    "\n// retained script"
            )

            assertEquals(saved.script, TtsEngineStore.updateDefaultScriptForTest(saved, builtIn).script)
        }
    }

    @Test
    fun stepAudioBuiltInEngine_declaresScenePerformanceCapabilities() {
        val engine = scriptEngineFromAssetFile("stepaudio_25_tts.js")

        assertEquals(TtsEngineStore.STEPAUDIO_25_TTS_ID, engine.id)
        assertEquals("阶跃星辰 StepAudio 2.5 TTS", engine.name)
        assertEquals(TtsEngineType.SCRIPT, engine.type)
        assertEquals(false, engine.enabled)
        assertEquals(false, engine.builtIn)
        assertEquals("audio/wav", engine.contentType)
        assertEquals(2, engine.maxConcurrency)
        assertTrue(engine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT))
        assertTrue(engine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION))
        assertFalse(engine.supportsCapability(TtsEngineCapability.PERSONA))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_SPEED))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_VOLUME))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SYNTHESIS_PITCH))
        assertFalse(engine.script.contains("key: \"synthesisSpeed\""))
        assertTrue(engine.script.contains("speed: synthesisSpeed(params)"))
        assertTrue(engine.script.contains("volume: synthesisVolume(params)"))
        assertTrue(engine.script.contains("// @version 1.0.7"))
        assertTrue(engine.script.contains("https://api.stepfun.com/step_plan/v1/audio/speech"))
        assertFalse(engine.script.contains("\"https://api.stepfun.com/v1/audio/speech\""))
        assertTrue(engine.script.contains("key: \"outputFormat\""))
        assertTrue(engine.script.contains("defaultValue: \"wav\""))
        assertTrue(engine.script.contains("key: \"sampleRate\""))
        assertTrue(engine.script.contains("defaultValue: \"48000\""))
        assertTrue(engine.script.contains("options.sampleRate || 48000"))
        assertFalse(engine.script.contains("sample_rate: 24000"))
        assertTrue(engine.script.contains("response_format: outputFormat(options)"))
        assertFalse(engine.script.contains("stream_format:"))
        assertTrue(engine.script.contains("format === \"mp3\" ? \"audio/mpeg\" : \"audio/wav\""))
        assertTrue(engine.script.contains("Step Plan API Key"))
        assertTrue(engine.script.contains("zixinnansheng"))
        assertTrue(engine.script.contains("function collectSceneInstruction(ctx)"))
        assertTrue(engine.script.contains("payload.instruction = sceneInstruction"))
        assertTrue(engine.script.contains("（\" + actorInstruction + \"）"))

        val wavKey = TtsScriptEngineClient.audioCacheKey(
            engine.copy(optionValues = mapOf("outputFormat" to "wav", "sampleRate" to "48000")),
            "测试"
        )
        val mp3Key = TtsScriptEngineClient.audioCacheKey(
            engine.copy(optionValues = mapOf("outputFormat" to "mp3", "sampleRate" to "48000")),
            "测试"
        )
        val wav24kKey = TtsScriptEngineClient.audioCacheKey(
            engine.copy(optionValues = mapOf("outputFormat" to "wav", "sampleRate" to "24000")),
            "测试"
        )
        assertNotEquals(wavKey, mp3Key)
        assertNotEquals(wavKey, wav24kKey)
    }

    @Test
    fun mosslandBuiltInEngine_usesFullVvCloneCatalogWithoutRuntimeVoiceRequest() {
        val engine = scriptEngineFromAssetFile("mossland_tts.js")

        assertEquals(TtsEngineStore.MOSSLAND_TTS_ID, engine.id)
        assertEquals("Mossland", engine.name)
        assertTrue(engine.supportsVoiceFetch())
        assertTrue(engine.supportsCapability(TtsEngineCapability.CASTING_METADATA))
        assertFalse(engine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT))
        assertTrue(engine.script.contains("// @version 1.3.0"))
        assertTrue(engine.script.contains("var MOSS_VOICES = ["))
        assertEquals(238, Regex("\"provider_speaker\":").findAll(engine.script).count())
        assertTrue(engine.script.contains("\"name\": \"清爽男大\""))
        assertTrue(engine.script.contains("\"name\": \"落寞老妇\""))
        assertTrue(engine.script.contains("\"id\": \"8af9d875-5faa-4c5d-8fb4-ac174a13d30c\""))
        assertTrue(engine.script.contains("\"catalog_category\": \"有声书\""))
        assertFalse(engine.script.contains("\"catalog_category\": \"影视配音\""))
        assertTrue(engine.script.contains("\"profile_source\": \"vv_clone_catalog\""))
        assertTrue(engine.script.contains("\"age_min\":"))
        assertTrue(engine.script.contains("\"vv_style\":"))
        assertTrue(engine.script.contains("\"vv_tags\":"))
        assertTrue(engine.script.contains("\"persona\":"))
        assertFalse(engine.script.contains("/v1/audio/voices"))
        assertFalse(engine.script.contains("java.ajax"))
        assertFalse(engine.script.contains("manualVoiceId"))
        assertFalse(engine.script.contains("\"name\": \"低磁男攻音\""))
        assertFalse(engine.script.contains("\"name\": \"治愈放松女声\""))
        assertFalse(engine.script.contains("\"profile_source\": \"provider_catalog\""))
        assertTrue(engine.script.contains("delivery_method: \"audio\""))
    }

    @Test
    fun mosslandBuiltInUpgrade_replacesOldCatalogAndPreservesUserSettings() {
        val builtIn = scriptEngineFromAssetFile("mossland_tts.js")
        val oldScript = builtIn.script
            .replace("// @name Mossland", "// @name mossland")
            .replace("// @version 1.3.0", "// @version 1.2.0")
            .replace(
                "\"profile_source\": \"vv_clone_catalog\"",
                "\"profile_source\": \"provider_catalog\""
            )
        val saved = TtsEngineStore.scriptEngineFromScript(oldScript)!!.copy(
            enabled = true,
            optionValues = mapOf("apiKey" to "saved-key", "outputFormat" to "wav"),
            activeVoiceId = "old-film-voice",
            disabledVoiceIds = listOf("disabled-voice")
        )

        val updated = TtsEngineStore.updateDefaultScriptForTest(saved, builtIn)

        assertEquals("Mossland", updated.name)
        assertEquals(builtIn.script, updated.script)
        assertEquals(builtIn.capabilities, updated.capabilities)
        assertEquals(saved.enabled, updated.enabled)
        assertEquals(saved.optionValues, updated.optionValues)
        assertEquals(null, updated.activeVoiceId)
        assertTrue(updated.disabledVoiceIds.isEmpty())
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
            // @sampleText 试听文本
            function options() { return []; }
            """.trimIndent()
        )

        assertEquals("示例", metadata["name"])
        assertEquals("demo_tts", metadata["uuid"])
        assertEquals("true", metadata["cookiejar"])
        assertEquals("42", metadata["defaultspeed"])
        assertEquals("60", metadata["defaultvolume"])
        assertEquals("55", metadata["defaultpitch"])
        assertEquals("试听文本", metadata["sampletext"])
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
            // @sampleText 试听文本
            function synthesize(text, voice, params, options, ctx) { return {}; }
            """.trimIndent()
        )!!

        assertEquals(42, engine.defaultSpeed)
        assertEquals(60, engine.defaultVolume)
        assertEquals(55, engine.defaultPitch)
        assertEquals("试听文本", engine.sampleText)
    }

    @Test
    fun scriptEngineFromScript_parsesCapabilities() {
        val engine = TtsEngineStore.scriptEngineFromScript(
            """
            // @name 场景演绎示例
            // @uuid performance_tts
            // @capabilities persona，scene_context|performance_instruction|casting_metadata
            function synthesize(text, voice, params, options, ctx) { return {}; }
            """.trimIndent()
        )!!

        assertTrue(engine.supportsCapability(TtsEngineCapability.PERSONA))
        assertTrue(engine.supportsCapability(TtsEngineCapability.SCENE_CONTEXT))
        assertTrue(engine.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION))
        assertTrue(engine.supportsCapability(TtsEngineCapability.CASTING_METADATA))
        assertFalse(engine.supportsCapability("emotion"))
    }

    @Test
    fun normalizeEditedEngine_restoresCapabilitiesFromScriptHeader() {
        val script = """
            // @name 场景演绎示例
            // @uuid performance_tts_normalized
            // @capabilities persona,scene_context,performance_instruction,casting_metadata
            function synthesize(text, voice, params, options, ctx) { return {}; }
        """.trimIndent()
        val source = TtsEngineStore.scriptEngineFromScript(script)!!
        val restored = TtsEngineStore.normalizeEditedEngine(
            parsed = source.copy(capabilities = emptySet()),
            source = source
        ).getOrThrow()

        assertTrue(restored.supportsCapability(TtsEngineCapability.PERSONA))
        assertTrue(restored.supportsCapability(TtsEngineCapability.SCENE_CONTEXT))
        assertTrue(restored.supportsCapability(TtsEngineCapability.PERFORMANCE_INSTRUCTION))
        assertTrue(restored.supportsCapability(TtsEngineCapability.CASTING_METADATA))
    }

    @Test
    fun normalizeEditedEngine_usesCurrentScriptHeaderAsCapabilitySourceOfTruth() {
        val sourceScript = """
            // @name 场景演绎示例
            // @uuid performance_tts_authoritative_header
            // @capabilities scene_context,performance_instruction
            function synthesize(text, voice, params, options, ctx) { return {}; }
        """.trimIndent()
        val source = TtsEngineStore.scriptEngineFromScript(sourceScript)!!
        val edited = source.copy(
            script = sourceScript.replace(
                "// @capabilities scene_context,performance_instruction\n",
                ""
            ),
            capabilities = source.capabilities
        )

        val normalized = TtsEngineStore.normalizeEditedEngine(
            parsed = edited,
            source = source
        ).getOrThrow()

        assertEquals(emptySet<String>(), normalized.capabilities)
    }

    @Test
    fun scriptEngineFromScript_leavesSampleTextBlankWhenHeaderMissing() {
        val engine = TtsEngineStore.scriptEngineFromScript(
            """
            // @name 示例
            // @uuid demo_tts_without_sample
            function synthesize(text, voice, params, options, ctx) { return {}; }
            """.trimIndent()
        )!!

        assertEquals(null, engine.sampleText)
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

    private fun evaluateNextEdgeScript(expression: String): JsonObject {
        val engine = scriptEngineFromAssetFile("next_edge_proxy.js")
        val context = Context.enter()
        return try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            val scope = context.initStandardObjects()
            context.evaluateString(scope, engine.script, "next_edge_proxy.js", 1, null)
            JsonParser.parseString(
                Context.toString(
                    context.evaluateString(scope, "JSON.stringify($expression);", "next-edge-test", 1, null)
                )
            ).asJsonObject
        } finally {
            Context.exit()
        }
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
