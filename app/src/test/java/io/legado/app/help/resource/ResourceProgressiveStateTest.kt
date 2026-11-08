package io.legado.app.help.resource

import org.junit.Assert.*
import org.junit.Test

class ResourceProgressiveStateTest {
    private val category = ResourceCategory("fonts", "字体", "font", "resources/fonts/index.json", 1)
    private val another = category.copy(id = "more-fonts", index = "resources/more-fonts/index.json")
    private val item = ResourceItem(id = "font.example", name = "示例", author = "作者", versionCode = 1,
        payload = ResourcePayload("resources/fonts/example/font.ttf", "ttf", 32, "b".repeat(64)))
    private val entry = ResourceEntry(category, "a".repeat(40), item, "c".repeat(40))
    private val local = ResourceInstallation(entry = entry, target = "local", baseline = item.payload.sha256)
    private val snapshot = ResourceSnapshot(catalogCommit = entry.catalogCommit,
        catalog = ResourceCatalog(1, entry.sourceCommit, listOf(category, another)), entries = listOf(entry))

    @Test fun partialSnapshotAcceptsOnlyCompleteDeclaredGroups() {
        ResourceProtocol.validateLoadedSnapshot(snapshot, setOf(category.id))
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validateSnapshot(snapshot) }
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validateLoadedSnapshot(snapshot, emptySet()) }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.validateLoadedSnapshot(snapshot.copy(entries = emptyList()), setOf(category.id))
        }
        assertThrows(IllegalArgumentException::class.java) { ResourceProtocol.validateLoadedSnapshot(snapshot, setOf("unknown")) }
    }

    @Test fun pendingGroupDoesNotMeanRemovedSourceOrCompletedUpdates() {
        val state = ResourceCenterState(snapshot = snapshot, installations = listOf(local), localChecked = true,
            loadedCategories = setOf(category.id), pendingCategories = setOf(another.id))
        assertFalse(state.sourceChecked(entry))
        assertFalse(state.updatesReady())
        val complete = state.copy(loadedCategories = setOf(category.id, another.id), pendingCategories = emptySet())
        assertTrue(complete.sourceChecked(entry))
        assertTrue(complete.updatesReady())
        assertFalse(complete.copy(accessDenied = true, snapshot = null).sourceChecked(entry))
        assertFalse(complete.copy(accessDenied = true, snapshot = null).updatesReady())
    }

    @Test fun emptyLocalLibraryIsIndependentFromRemoteAccessButMustBeChecked() {
        assertFalse(ResourceCenterState().updatesReady())
        assertTrue(ResourceCenterState(localChecked = true, accessDenied = true).updatesReady())
        assertFalse(ResourceCenterState(localChecked = true, storageError = "读取失败").updatesReady())
        val known = ResourceCenterState(installations = listOf(local), localChecked = true, accessDenied = true)
        assertEquals(listOf(local), known.downloadedInstallations())
        assertFalse(known.updatesReady())
    }
}
