package io.legado.app.help.config

import io.legado.app.help.resource.ResourceInstallMode
import org.junit.Assert.*
import org.junit.Test

class ReadPresetResourceLibraryTest {
    @Test fun installAndUpdateAppendWithoutChangingTheExistingPreset() {
        val original = ReadBookConfig.Config(name = "敦煌绮笺", bgType = 0, bgStr = "#123456")
        val configs = mutableListOf(original)
        val selectedIndex = 0
        val first = ReadBookConfig.Config(name = "敦煌绮笺", bgType = 0, bgStr = "#FEECD3")
        appendReadPresetResource(configs, first, "resource-v1", "a".repeat(64)) { saved ->
            assertEquals(2, saved.size)
        }
        val update = ReadBookConfig.Config(name = "敦煌绮笺", bgType = 0, bgStr = "#ABCDEF")
        appendReadPresetResource(configs, update, "resource-v2", "b".repeat(64)) { }
        assertSame(original, configs[selectedIndex])
        assertEquals("#123456", original.bgStr)
        assertEquals(listOf("敦煌绮笺", "敦煌绮笺 (2)", "敦煌绮笺 (3)"), configs.map { it.name })
        assertEquals("a".repeat(64), readPresetResourceHash(configs, "resource-v1"))
        assertEquals("b".repeat(64), readPresetResourceHash(configs, "resource-v2"))
    }

    @Test fun failedPersistenceDoesNotLeaveAnInstalledPreset() {
        val original = ReadBookConfig.Config(name = "原预设")
        val configs = mutableListOf(original)
        assertThrows(IllegalStateException::class.java) {
            appendReadPresetResource(configs, ReadBookConfig.Config(name = "新预设"), "new", "a".repeat(64)) {
                throw IllegalStateException("write failed")
            }
        }
        assertEquals(listOf(original), configs)
        assertNull(readPresetResourceHash(configs, "new"))
    }

    @Test fun duplicateOwnershipIsRejectedInsteadOfOverwritingAUserPreset() {
        val existing = ReadBookConfig.Config(name = "用户预设", ngResourcePresetTarget = "same", ngResourcePresetSha256 = "a".repeat(64))
        val configs = mutableListOf(existing)
        assertThrows(IllegalArgumentException::class.java) {
            appendReadPresetResource(configs, ReadBookConfig.Config(), "same", "b".repeat(64)) { }
        }
        assertEquals(listOf(existing), configs)
    }

    @Test fun updateFindsItsOwnPresetEvenIfUserRenamedIt() {
        val configs = listOf(
            ReadBookConfig.Config(name = "敦煌绮笺"),
            ReadBookConfig.Config(name = "我的敦煌", ngResourcePresetTarget = "source", ngResourcePresetSha256 = "a".repeat(64)),
        )
        val conflict = readPresetResourceConflict(configs, "敦煌绮笺", "source", "b".repeat(64), setOf(1))!!
        assertTrue(conflict.isUpdate)
        assertEquals(listOf(1), conflict.candidates.map { it.index })
        assertTrue(conflict.candidates.single().selected)
    }

    @Test fun sameNameReturnsAllTargetsAndReplacementKeepsPositionAndName() {
        val first = ReadBookConfig.Config(name = "同名", bgStr = "#111111")
        val second = ReadBookConfig.Config(name = "同名", bgStr = "#222222")
        val configs = mutableListOf(first, second)
        val conflict = readPresetResourceConflict(configs, "同名", null, "a".repeat(64), setOf(1))!!
        assertFalse(conflict.isUpdate)
        assertEquals(listOf(0, 1), conflict.candidates.map { it.index })
        val new = ReadBookConfig.Config(name = "包名", bgStr = "#333333")
        replaceReadPresetResource(configs, new, conflict.candidates[1], "new-source", "a".repeat(64)) { }
        assertSame(first, configs[0])
        assertSame(new, configs[1])
        assertEquals("同名", new.name)
        assertEquals("a".repeat(64), readPresetResourceHash(configs, "new-source"))
    }

    @Test fun replacementRefusesEditedTargetAndRollsBackFailedWrite() {
        val original = ReadBookConfig.Config(name = "原预设", bgStr = "#111111")
        val configs = mutableListOf(original)
        val candidate = readPresetResourceConflict(configs, "原预设", null, "a".repeat(64), emptySet())!!.candidates.single()
        original.bgStr = "#222222"
        assertThrows(IllegalArgumentException::class.java) {
            replaceReadPresetResource(configs, ReadBookConfig.Config(), candidate, "new", "a".repeat(64)) { }
        }
        assertSame(original, configs.single())
        val fresh = readPresetResourceConflict(configs, "原预设", null, "a".repeat(64), emptySet())!!.candidates.single()
        assertThrows(IllegalStateException::class.java) {
            replaceReadPresetResource(configs, ReadBookConfig.Config(), fresh, "new", "a".repeat(64)) {
                throw IllegalStateException("write failed")
            }
        }
        assertSame(original, configs.single())
    }

    @Test fun conflictRequiresAnExplicitCurrentChoiceAndRechecksChanges() {
        val config = ReadBookConfig.Config(name = "同名", bgStr = "#111111")
        val configs = listOf(config)
        val conflict = readPresetResourceConflict(configs, "同名", null, "a".repeat(64), emptySet())!!
        val chosen = conflict.candidates.single()
        assertFalse(readPresetConfirmationValid(conflict, null, ResourceInstallMode.NORMAL, null))
        assertTrue(readPresetConfirmationValid(conflict, conflict, ResourceInstallMode.COPY, null))
        assertFalse(readPresetConfirmationValid(conflict, conflict, ResourceInstallMode.REPLACE, null))
        assertTrue(readPresetConfirmationValid(conflict, conflict, ResourceInstallMode.REPLACE, chosen))
        config.bgStr = "#222222"
        val changed = readPresetResourceConflict(configs, "同名", null, "a".repeat(64), emptySet())!!
        assertFalse(readPresetConfirmationValid(changed, conflict, ResourceInstallMode.REPLACE, chosen))
        assertFalse(readPresetConfirmationValid(null, conflict, ResourceInstallMode.COPY, null))
    }

    @Test fun updateReplacesOwnedPresetInPlaceWithFreshOwnership() {
        val original = ReadBookConfig.Config(name = "自定义名称", ngResourcePresetTarget = "source",
            ngResourcePresetSha256 = "a".repeat(64))
        val configs = mutableListOf(original)
        val conflict = readPresetResourceConflict(configs, "原包名", "source", "b".repeat(64), setOf(0))!!
        val replacement = ReadBookConfig.Config(name = "原包名", bgStr = "#ABCDEF")
        replaceReadPresetResource(configs, replacement, conflict.candidates.single(), "new-source", "b".repeat(64)) { }
        assertSame(replacement, configs.single())
        assertEquals("自定义名称", replacement.name)
        assertNull(readPresetResourceHash(configs, "source"))
        assertEquals("b".repeat(64), readPresetResourceHash(configs, "new-source"))
    }
}
