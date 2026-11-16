package io.legado.app.help.resource

import com.google.gson.Gson
import io.legado.app.help.tts.TtsEngineStore
import org.junit.Assert.*
import org.junit.Test

class OfflineTtsResourceProtocolTest {
    private val entry = ResourceEntry(
        ResourceCategory("tts", "朗读引擎", "tts", "resources/tts/index.json", 1), "a".repeat(40),
        ResourceItem(id = "tts.microsoft-offline", name = "微软中文离线语音", author = "Microsoft", versionCode = 1,
            payload = ResourcePayload("resources/tts/microsoft-offline/bundle.zip", "zip", 133633284, "b".repeat(64),
                TtsEngineStore.MICROSOFT_OFFLINE_ID, release = ResourceReleaseAsset("tts-microsoft-offline-1.0.2", "voices.zip")),
            requirements = ResourceRequirements(capabilities = listOf(MICROSOFT_OFFLINE_TTS_CAPABILITY))),
    )

    @Test fun largeBundleUsesFixedReleaseAndCapabilityGate() {
        ResourceProtocol.validateIndex(ResourceCatalog(1, entry.sourceCommit), entry.category,
            ResourceIndex(1, entry.sourceCommit, "tts", listOf(entry.item)))
        assertNull(ResourceProtocol.incompatibility(entry, Long.MAX_VALUE))
        assertEquals("https://github.com/joestar817/legado-resources/releases/download/tts-microsoft-offline-1.0.2/voices.zip",
            ResourceProtocol.payloadUrl(entry))
        assertNotNull(ResourceProtocol.incompatibility(entry.copy(item = entry.item.copy(requirements = null)), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(entry.copy(item = entry.item.copy(payload = entry.item.payload.copy(
            size = ResourceProtocol.MAX_OFFLINE_TTS_BYTES + 1))), Long.MAX_VALUE))
    }

    @Test fun releaseCannotEscapeRepositoryOrBeUsedForScripts() {
        listOf("../x", "a..b", "a/b", "a%2fb", "https://example.org", "x?token=a").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                ResourceProtocol.payloadUrl(entry.copy(item = entry.item.copy(payload = entry.item.payload.copy(
                    release = ResourceReleaseAsset(it, "voices.zip")))))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateRelease(entry.item.payload.copy(format = "js"), "tts")
        }
    }

    @Test fun legacyScriptAndNewReleaseMetadataRoundTrip() {
        val gson = Gson()
        val before = """{"path":"resources/tts/engine.js","format":"js","size":32,"sha256":"${"c".repeat(64)}","engineId":"old"}"""
        assertNull(gson.fromJson(before, ResourcePayload::class.java).release)
        val encoded = gson.toJson(entry.item.payload)
        assertTrue(encoded.contains("\"release\""))
        assertTrue(encoded.contains("\"tag\""))
        assertEquals(entry.item.payload, gson.fromJson(encoded, ResourcePayload::class.java))
        val script = entry.copy(item = entry.item.copy(payload = entry.item.payload.copy(format = "js", release = null)))
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.versionState(script, ResourceInstallation(entry = entry, target = "offline", baseline = "b".repeat(64)))
        }
    }
}
