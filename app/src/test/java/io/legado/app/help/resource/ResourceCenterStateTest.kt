package io.legado.app.help.resource

import org.junit.Assert.*
import io.legado.app.help.home.HomeWidgetSkinRenderer
import io.legado.app.help.home.InstalledHomeWidgetSkin
import java.io.File
import org.junit.Test

class ResourceCenterStateTest {
    private fun record(id: String, type: String = "theme") = ResourceInstallation(
        entry = ResourceEntry(
            category = ResourceCategory(id = type, type = type),
            item = ResourceItem(id = id, versionCode = 1, payload = ResourcePayload(engineId = if (type == "tts") id else null)),
        ),
        target = "target-$id",
        baseline = "baseline-$id",
    )

    @Test fun missingResourcesAreNotDownloadedAcrossAllTypes() {
        listOf("font", "background", "read_preset", "tts", "theme").forEach { type ->
            val local = record(type, type)
            val state = ResourceCenterState(installations = listOf(local), localStates = mapOf(type to ResourceLocalState.MISSING))
            assertTrue(state.downloadedInstallations().isEmpty())
            assertNull(state.installed(type))
            assertEquals("未下载", ResourceProtocol.versionState(local.entry.copy(item = local.entry.item.copy(versionCode = 2)), state.installed(type)))
            assertEquals(listOf(local), state.installations)
        }
    }

    @Test fun modifiedAndUncheckedRecordsRemainDownloadedEvenWithReadError() {
        val records = listOf(record("present"), record("modified"), record("unchecked"), record("missing"))
        val state = ResourceCenterState(
            installations = records,
            localStates = mapOf("present" to ResourceLocalState.PRESENT, "modified" to ResourceLocalState.MODIFIED, "missing" to ResourceLocalState.MISSING),
            storageError = "无法读取安装记录",
        )
        assertEquals(listOf("present", "modified", "unchecked"), state.downloadedInstallations().map { it.entry.item.id })
        assertNotNull(state.installed("modified"))
        assertNotNull(state.installed("unchecked"))
    }

    @Test fun restoredResourceReturnsWithoutLosingItsTargetOrBaseline() {
        val local = record("restored")
        val missing = ResourceCenterState(installations = listOf(local), localStates = mapOf("restored" to ResourceLocalState.MISSING))
        val restored = missing.withLocalInstallations(listOf(local), mapOf("restored" to ResourceLocalState.PRESENT))
        assertEquals(listOf(local), restored.downloadedInstallations())
        assertEquals(local, restored.installed("restored"))
        assertEquals("已下载", ResourceProtocol.versionState(local.entry, restored.installed("restored")))
    }

    @Test fun newMissingStateClearsStaleTerminalTasksButKeepsRunningAndOtherTasks() {
        val conflict = ResourceTask(message = "本地资源冲突", running = false, conflict = true)
        val running = ResourceTask(message = "下载中", running = true)
        val other = ResourceTask(message = "连接超时", running = false)
        val state = ResourceCenterState(tasks = mapOf("missing" to conflict, "active" to running, "other" to other))
        val next = state.withLocalInstallations(
            listOf(record("missing"), record("active"), record("other")),
            mapOf("missing" to ResourceLocalState.MISSING, "active" to ResourceLocalState.MISSING, "other" to ResourceLocalState.PRESENT),
        )
        assertFalse(next.tasks.containsKey("missing"))
        assertEquals(running, next.tasks["active"])
        assertEquals(other, next.tasks["other"])
    }

    @Test fun failedNewDownloadAfterMissingCheckRemainsVisibleOnNextCheck() {
        val failure = ResourceTask(message = "网络连接失败，请重试", running = false)
        val state = ResourceCenterState(localStates = mapOf("missing" to ResourceLocalState.MISSING), tasks = mapOf("missing" to failure))
        val next = state.withLocalInstallations(listOf(record("missing")), state.localStates)
        assertEquals(failure, next.tasks["missing"])
        assertNull(next.installed("missing"))
    }

    @Test fun retainedRecordStillRejectsChangedTypeOrEngineIdentityBeforeTargetReuse() {
        val local = record("missing", "tts")
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.versionState(local.entry.copy(category = local.entry.category.copy(type = "font")), local)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceProtocol.versionState(local.entry.copy(item = local.entry.item.copy(payload = local.entry.item.payload.copy(engineId = "other"))), local)
        }
        // 内容已不存在时可以重新获取旧版本，身份检查仍然执行，版本标签不参与目标复用。
        val newer = local.copy(entry = local.entry.copy(item = local.entry.item.copy(versionCode = 2)))
        assertEquals("本地版本较新", ResourceProtocol.versionState(local.entry, newer))
    }

    @Test fun failedDownloadProducedDuringLocalCheckIsNotCleared() {
        val oldFailure = ResourceTask(message = "连接超时", running = false)
        val checked = ResourceCenterState(tasks = mapOf("missing" to oldFailure))
        // 即使新失败文案相同，也不能把它误认成检查开始前的旧任务。
        val newFailure = oldFailure.copy()
        val latest = checked.copy(tasks = mapOf("missing" to newFailure))
        val next = latest.withLocalInstallations(
            listOf(record("missing")), mapOf("missing" to ResourceLocalState.MISSING), checked.tasks,
        )
        assertSame(newFailure, next.tasks["missing"])
    }

    @Test fun updatesIncludeEveryResourceTypeAndRetainLocallyModifiedConflicts() {
        val records = listOf("read_preset", "tts", "theme").map { record(it, it) }
        val remote = records.map { it.entry.copy(item = it.entry.item.copy(versionCode = 2)) }
        val conflict = ResourceTask(message = "本地资源冲突", running = false, conflict = true)
        val state = ResourceCenterState(
            snapshot = ResourceSnapshot(entries = remote),
            installations = records,
            localStates = records.associate { it.entry.item.id to ResourceLocalState.MODIFIED },
            tasks = mapOf("tts" to conflict),
        )
        assertEquals(remote, state.updatableEntries())
        assertSame(conflict, state.tasks["tts"])
    }

    @Test fun importedSkinBelongsToHomeWithoutBecomingADownloadOrAutomaticUpdate() {
        val id = "ng.home_skin.storybook"
        val digest = "f".repeat(64)
        val skin = InstalledHomeWidgetSkin(id, "绘本书屋", HomeWidgetSkinRenderer.STORYBOOK, File("local.zip"), digest)
        val remote = ResourceEntry(
            category = ResourceCategory(type = HOME_WIDGET_SKIN_TYPE),
            sourceCommit = "a".repeat(40),
            item = ResourceItem(id = id, versionCode = 8L, payload = ResourcePayload(sha256 = "e".repeat(64))),
        )
        val state = ResourceCenterState(widgetSkins = mapOf(id to skin), localChecked = true,
            snapshot = ResourceSnapshot(entries = listOf(remote)))
        assertTrue(state.downloadedInstallations().isEmpty())
        assertTrue(state.hasInstalled(remote))
        assertTrue(state.updatesReady())
        assertTrue(state.updatableEntries().isEmpty())
        assertTrue(state.isLocalWidgetSkin(remote))
        assertTrue(state.isLocalWidgetSkin(remote.copy(item = remote.item.copy(payload = ResourcePayload(sha256 = digest)))))
        assertFalse(state.isLocalWidgetSkin(remote.copy(category = ResourceCategory(type = "theme"))))
        assertEquals("本地已导入", state.versionLabel(remote))

        val receipt = ResourceInstallation(entry = remote.copy(item = remote.item.copy(versionCode = 1)), baseline = digest)
        val previousDownload = state.copy(installations = listOf(receipt),
            localStates = mapOf(id to ResourceLocalState.MODIFIED))
        assertTrue(previousDownload.downloadedInstallations().isEmpty())
        assertTrue(previousDownload.updatableEntries().isEmpty())

        val downloaded = previousDownload.copy(widgetSkins = mapOf(id to skin.copy(fromResource = true)),
            localStates = mapOf(id to ResourceLocalState.PRESENT))
        assertFalse(downloaded.isLocalWidgetSkin(remote))
        assertEquals(listOf(receipt), downloaded.downloadedInstallations())
        assertEquals(listOf(remote), downloaded.updatableEntries())
    }

    @Test fun listeningPackagesAndThemesKeepIndependentDownloadsAndUpdates() {
        val theme = record("theme")
        val effect = record("player-effect", LISTENING_EFFECT_RESOURCE_TYPE)
        val records = listOf(theme, effect)
        val remote = records.map { it.entry.copy(item = it.entry.item.copy(versionCode = 2)) }
        val state = ResourceCenterState(snapshot = ResourceSnapshot(entries = remote), installations = records)

        assertEquals(records, state.downloadedInstallations())
        assertEquals(remote, state.updatableEntries())
        assertSame(effect, state.installed(effect.entry.item.id))
    }

    @Test fun standaloneReadingResourcesKeepReceiptsButAreNotListedOrUpdated() {
        val records = listOf(record("font", "font"), record("background", "background"), record("preset", "read_preset"))
        val remote = records.map { it.entry.copy(item = it.entry.item.copy(versionCode = 2)) }
        val state = ResourceCenterState(snapshot = ResourceSnapshot(entries = remote), installations = records)
        assertEquals(listOf(records.last()), state.downloadedInstallations())
        assertEquals(listOf(remote.last()), state.updatableEntries())
        assertSame(records.first(), state.installed("font"))
        assertSame(records[1], state.installed("background"))
    }

    @Test fun updatesExcludeMissingUninstalledAndRemovedRemoteResources() {
        val present = record("present")
        val missing = record("missing")
        val removed = record("removed")
        val candidates = listOf(present, missing, record("new")).map {
            it.entry.copy(item = it.entry.item.copy(versionCode = 2))
        }
        val state = ResourceCenterState(
            snapshot = ResourceSnapshot(entries = candidates),
            installations = listOf(present, missing, removed),
            localStates = mapOf("missing" to ResourceLocalState.MISSING),
        )
        assertEquals(listOf(candidates.first()), state.updatableEntries())
        assertTrue(state.copy(snapshot = null).updatableEntries().isEmpty())
    }

    @Test fun updatesRejectChangedIdentitySourceAnomaliesAndNonUpgrades() {
        val records = listOf("type", "engine", "repository", "source", "same", "older").map { record(it, "tts") }
        val remote = records.map { local ->
            val newer = local.entry.copy(item = local.entry.item.copy(versionCode = 2))
            when (local.entry.item.id) {
                "type" -> newer.copy(category = newer.category.copy(type = "font"))
                "engine" -> newer.copy(item = newer.item.copy(payload = newer.item.payload.copy(engineId = "changed")))
                "source" -> local.entry.copy(item = local.entry.item.copy(payload = local.entry.item.payload.copy(sha256 = "changed")))
                "same" -> local.entry
                "older" -> local.entry.copy(item = local.entry.item.copy(versionCode = 0))
                else -> newer
            }
        }
        val state = ResourceCenterState(
            snapshot = ResourceSnapshot(entries = remote),
            installations = records.map { if (it.entry.item.id == "repository") it.copy(repository = "other/resources") else it },
        )
        assertTrue(state.updatableEntries().isEmpty())
        assertFalse(ResourceProtocol.isUpdate(remote.first(), record("different", "tts")))
    }

    @Test fun updateCountShrinksAfterInstallationAndPreservesOtherTaskResults() {
        val records = listOf(record("done"), record("failed"), record("cancelled"))
        val remote = records.map { it.entry.copy(item = it.entry.item.copy(versionCode = 2)) }
        val failure = ResourceTask(message = "连接超时", running = false)
        val cancelled = ResourceTask(message = "已取消", running = false)
        val state = ResourceCenterState(
            snapshot = ResourceSnapshot(entries = remote),
            installations = records,
            tasks = mapOf("failed" to failure, "cancelled" to cancelled),
            updatingAll = true,
        )
        val updated = state.withLocalInstallations(
            listOf(records.first().copy(entry = remote.first())) + records.drop(1),
            records.associate { it.entry.item.id to ResourceLocalState.PRESENT },
        )
        assertEquals(listOf("failed", "cancelled"), updated.updatableEntries().map { it.item.id })
        assertSame(failure, updated.tasks["failed"])
        assertSame(cancelled, updated.tasks["cancelled"])
        assertTrue(updated.updatingAll)
    }

    @Test fun capturedUpdateDoesNotChangeWhenCatalogRefreshesAndStopsAfterSameVersionInstalled() {
        val local = record("queued")
        val version2 = local.entry.copy(item = local.entry.item.copy(versionCode = 2))
        val version3 = local.entry.copy(item = local.entry.item.copy(versionCode = 3))
        val state = ResourceCenterState(snapshot = ResourceSnapshot(entries = listOf(version2)), installations = listOf(local))
        val candidates = state.updatableEntries()
        val refreshed = state.copy(snapshot = ResourceSnapshot(entries = listOf(version3)))
        assertEquals(listOf(version2), candidates)
        assertEquals(listOf(version3), refreshed.updatableEntries())
        assertTrue(ResourceProtocol.isUpdate(candidates.single(), refreshed.installed("queued")))
        assertFalse(ResourceProtocol.isUpdate(candidates.single(), local.copy(entry = version2)))
        assertFalse(ResourceProtocol.isUpdate(candidates.single(), local.copy(entry = version3)))
    }
}
