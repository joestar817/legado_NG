package io.legado.app.help.tts

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TtsOfflineSerializationTest {
    private val gson = Gson()

    @Test
    fun oldScriptSettingsRemainReadableWithoutAnOfflineRevision() {
        val old = gson.fromJson(
            """{"id":"old","name":"旧脚本","type":"script","active_voice_id":"voice"}""",
            TtsEngineSetting::class.java,
        )
        assertEquals(TtsEngineType.SCRIPT, old.type)
        assertEquals("voice", old.activeVoiceId)
        assertNull(old.offlineRevision)
    }

    @Test
    fun offlineEnginePersistsItsTypeAndPinnedRevision() {
        val engine = TtsEngineSetting(
            id = "offline", name = "离线语音", type = TtsEngineType.OFFLINE,
            offlineRevision = "engine:revision", activeVoiceId = "voice",
            enabled = true, runtimeOfflineIssue = "暂不可用",
        )
        val json = gson.toJsonTree(engine).asJsonObject
        assertEquals("offline", json.get("type").asString)
        assertEquals("engine:revision", json.get("offline_revision").asString)
        assertFalse(json.has("runtimeOfflineIssue"))
        val restored = gson.fromJson(json, TtsEngineSetting::class.java)
        assertEquals(TtsEngineType.OFFLINE, restored.type)
        assertEquals(engine.offlineRevision, restored.offlineRevision)
        assertEquals(engine.activeVoiceId, restored.activeVoiceId)
        assertEquals(true, restored.enabled)
        assertNull(restored.runtimeOfflineIssue)
    }
}
