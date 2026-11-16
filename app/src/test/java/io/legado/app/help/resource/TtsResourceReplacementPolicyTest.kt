package io.legado.app.help.resource

import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TtsResourceReplacementPolicyTest {
    private fun engine(script: String, type: TtsEngineType = TtsEngineType.SCRIPT) =
        TtsEngineSetting(id = "shared", name = "朗读引擎", type = type, script = script)

    @Test
    fun identicalLocalScriptCanJoinResourceUpdatesWithoutReplacement() {
        val script = engine("same")
        requireTtsResourceReplacement(script, script, null, ResourceInstallMode.NORMAL, null)
    }

    @Test
    fun differentLocalScriptNeedsConfirmationForTheExactVersions() {
        val local = engine("edited")
        val remote = engine("published")
        val conflict = assertThrows(ResourceConflict::class.java) {
            requireTtsResourceReplacement(local, remote, null, ResourceInstallMode.NORMAL, null)
        }.ttsConflict!!
        assertEquals("shared", conflict.engineId)
        requireTtsResourceReplacement(local, remote, null, ResourceInstallMode.REPLACE, conflict)

        assertThrows(ResourceConflict::class.java) {
            requireTtsResourceReplacement(engine("edited again"), remote, null, ResourceInstallMode.REPLACE, conflict)
        }
        assertThrows(ResourceConflict::class.java) {
            requireTtsResourceReplacement(local, engine("new publication"), null, ResourceInstallMode.REPLACE, conflict)
        }
    }

    @Test
    fun systemEngineCannotBeReplacedAndRecordedResourceUsesExistingUpdatePath() {
        assertThrows(ResourceConflict::class.java) {
            requireTtsResourceReplacement(engine("same", TtsEngineType.SYSTEM), engine("same"), null,
                ResourceInstallMode.REPLACE, null)
        }
        requireTtsResourceReplacement(engine("old"), engine("new"),
            ResourceInstallation(baseline = ResourceProtocol.hash("old".toByteArray())),
            ResourceInstallMode.REPLACE, null)
    }
}
