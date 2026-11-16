package io.legado.app.help.tts

import com.google.gson.JsonParser
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsResourceManagementTest {
    private fun engine(id: String, type: TtsEngineType = TtsEngineType.SCRIPT) =
        TtsEngineSetting(id = id, name = id, type = type)

    @Test
    fun retiringExamplesKeepsOtherEnginesAndTheirSettings() {
        val kept = engine("user-engine").copy(optionValues = mapOf("token" to "personal"))
        val system = engine("system", TtsEngineType.SYSTEM)
        val saved = listOf(
            engine(TtsEngineStore.OPTIONS_EXAMPLE_ID),
            kept,
            engine(TtsEngineStore.STATIC_VOICES_EXAMPLE_ID),
            system,
        )

        assertEquals(listOf(kept, system), TtsEngineStore.withoutRetiredTtsExamples(saved))
        assertEquals(listOf(kept, system), TtsEngineStore.withoutRetiredTtsExamples(listOf(kept, system)))
    }

    @Test
    fun defaultAndResourceScriptsAreDeletableButSystemEnginesAreNot() {
        assertTrue(TtsEngineStore.isDeletableEngine(engine("bundled").copy(builtIn = true)))
        assertTrue(TtsEngineStore.isDeletableEngine(engine("resource").copy(resourceManaged = true)))
        assertFalse(TtsEngineStore.isDeletableEngine(engine("system", TtsEngineType.SYSTEM)))
    }

    @Test
    fun resourceManagedScriptIsNotReplacedByBundledUpgrade() {
        val saved = engine(TtsEngineStore.NEXT_EDGE_PROXY_ID).copy(
            script = "// @version 1.0.10",
            resourceManaged = true,
            optionValues = mapOf("timeout" to "60"),
            defaultSpeed = 35,
        )
        val bundled = saved.copy(script = "// @version 1.0.11", defaultSpeed = 50)

        assertEquals(saved, TtsEngineStore.updateDefaultScriptForTest(saved, bundled))
        assertEquals(
            bundled.script,
            TtsEngineStore.updateDefaultScriptForTest(saved.copy(resourceManaged = false), bundled).script,
        )
    }

    @Test
    fun sourceFlagRoundTripsAndOlderSavedEnginesDefaultToLocal() {
        val json = GSON.toJson(engine("sample").copy(resourceManaged = true))
        assertTrue(JsonParser.parseString(json).asJsonObject.get("resource_managed").asBoolean)
        assertTrue(GSON.fromJson(json, TtsEngineSetting::class.java).resourceManaged)

        val legacy = JsonParser.parseString(json).asJsonObject.apply { remove("resource_managed") }
        assertFalse(GSON.fromJson(legacy, TtsEngineSetting::class.java).resourceManaged)
    }
}
