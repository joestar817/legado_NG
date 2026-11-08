package io.legado.app.help.resource

import com.google.gson.Gson
import io.legado.app.help.font.LocalFontAsset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResourceFontDeletionTest {
    @get:Rule val folder = TemporaryFolder()
    private val firstHash = "a".repeat(64)
    private val secondHash = "b".repeat(64)

    private fun record(id: String, path: String, type: String = "font", hash: String = firstHash) = ResourceInstallation(
        entry = ResourceEntry(
            category = ResourceCategory(id = type, type = type),
            item = ResourceItem(id = id, payload = ResourcePayload(sha256 = hash)),
        ),
        target = path,
        baseline = hash,
    )

    @Test fun deletingSharedFontRemovesEveryLinkedFontButNotOtherTypesOrPaths() {
        val selected = File(folder.root, "font.ttf").absolutePath
        val sameNameElsewhere = File(folder.root, "other/font.ttf").absolutePath
        val records = listOf(
            record("font.one", selected), record("font.two", selected),
            record("font.other", sameNameElsewhere), record("background.one", selected, "background"),
        )
        assertEquals(listOf("font.one", "font.two"),
            ResourceFontDeletionPolicy.recordsToRemove(records, listOf(selected)).map { it.entry.item.id })
    }

    @Test fun newDeleteRequestTargetsOnePhysicalFileNotItsEqualContentCopies() {
        val selected = File(folder.root, "download/font.ttf").canonicalPath
        val other = File(folder.root, "legacy/font.ttf").canonicalPath
        val asset = LocalFontAsset(firstHash, "字体", selected, listOf(selected), 123)
        val records = listOf(record("font.selected", selected), record("font.other", other))

        val target = ResourceFontDeletionPolicy.singlePath(asset)

        assertEquals(selected, target)
        assertEquals(listOf("font.selected"),
            ResourceFontDeletionPolicy.recordsToRemove(records, listOf(target)).map { it.entry.item.id })
    }

    @Test fun newDeleteRequestCannotExpandToTwoPathsOrMismatchTheConfirmedPath() {
        val selected = File(folder.root, "font.ttf").canonicalPath
        val other = File(folder.root, "copy.ttf").canonicalPath
        val asset = LocalFontAsset(firstHash, "字体", selected, listOf(selected), 123)
        listOf(
            asset.copy(paths = listOf(selected, other)), asset.copy(paths = listOf(other)),
            asset.copy(paths = emptyList()), asset.copy(id = "invalid"),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { ResourceFontDeletionPolicy.singlePath(invalid) }
        }
    }

    @Test fun recoveryDoesNotDeleteAnotherCopyAddedAfterConfirmationEvenWithSameHash() {
        val original = File(folder.root, "old.ttf").absolutePath
        val later = File(folder.root, "new.ttf").absolutePath
        val journal = ResourceFontDeletion(id = firstHash, path = original, paths = listOf(original), size = 3)
        val records = listOf(record("font.old", original), record("font.new", later))
        journal.validate()
        assertEquals(listOf("font.old"),
            ResourceFontDeletionPolicy.recordsToRemove(records, journal.paths).map { it.entry.item.id })
    }

    @Test fun payloadCleanupWaitsForBothOtherResourceOwnersAndActiveReaders() {
        val thirdHash = "c".repeat(64)
        val candidates = setOf(firstHash, secondHash, thirdHash, "../other")
        assertEquals(setOf(thirdHash), ResourceFontDeletionPolicy.cleanablePayloads(
            candidates, retained = setOf(firstHash), leased = setOf(secondHash),
        ))
        assertEquals(setOf(secondHash, thirdHash), ResourceFontDeletionPolicy.cleanablePayloads(
            candidates, retained = setOf(firstHash), leased = emptySet(),
        ))
    }

    @Test fun installedFontsReleasePayloadsWhileOtherResourceOwnersAndLeasesRetainTheirs() {
        val thirdHash = "c".repeat(64)
        val path = File(folder.root, "resource.ttf").absolutePath
        val records = listOf(
            record("font.one", path), record("background.one", path, "background", secondHash),
        )
        val entries = listOf(
            record("font.catalog", path).entry,
            record("tts.catalog", path, "tts", thirdHash).entry,
        )
        val retained = ResourceFontDeletionPolicy.payloadsNeededByOtherResources(records, entries)

        assertEquals(setOf(secondHash, thirdHash), retained)
        assertEquals(setOf(firstHash), ResourceFontDeletionPolicy.cleanablePayloads(
            setOf(firstHash, secondHash, thirdHash), retained, emptySet(),
        ))
        assertTrue(ResourceFontDeletionPolicy.cleanablePayloads(
            setOf(firstHash, secondHash, thirdHash), retained, setOf(firstHash),
        ).isEmpty())
    }

    @Test fun interruptedDeletionCancelsWhenRemainingContentChanges() {
        val result = ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(null, secondHash)) {
            error("Content conflict should cancel without relying on usage data")
        }
        assertEquals("字体已变化，已取消未完成的删除，请重新确认", result)
    }

    @Test fun interruptedDeletionCancelsWhenFontBecomesUsed() {
        val result = ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(null, firstHash)) {
            listOf("界面字体")
        }
        assertEquals("字体正在使用，已取消未完成的删除，请重新确认", result)
    }

    @Test fun recoveryAllowsOnlyTheConfirmedUseWhileTheFileStillExists() {
        val confirmed = listOf("界面字体", "阅读预设「秋山书意」·标题")
        assertNull(ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(firstHash), confirmed) {
            listOf("界面字体")
        })
        assertNotNull(ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(firstHash), confirmed) {
            confirmed + "当前书籍·正文"
        })
        assertNotNull(ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(secondHash), confirmed) {
            error("A confirmed use cannot authorize deleting different bytes")
        })
    }

    @Test fun recoveryCanFinishSwitchingReferencesAfterConfirmedFileIsGone() {
        assertNull(ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(null), listOf("界面字体")) {
            error("No file remains to delete; only the reference reset should run")
        })
    }

    @Test fun missingFilesCanResumeButBrokenUsageInspectionCannotAuthorizeDeletion() {
        assertNull(ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(null, firstHash)) {
            emptyList()
        })
        assertThrows(IllegalStateException::class.java) {
            ResourceFontDeletionPolicy.recoveryCancellation(firstHash, listOf(firstHash)) {
                error("损坏的书籍独立预设")
            }
        }
    }

    @Test fun deletionJournalRoundTripsWithStableFieldNamesAfterFilesDisappear() {
        val original = File(folder.root, "removed.ttf").absolutePath
        val value = ResourceFontDeletion(
            id = firstHash, name = "测试字体", path = original, paths = listOf(original),
            size = 123, payloadHashes = listOf(firstHash, secondHash),
            confirmedUsages = listOf("界面字体"),
        )
        val json = Gson().toJson(value)
        assertTrue(json.contains("\"payloadHashes\""))
        assertTrue(json.contains("\"confirmedUsages\""))
        assertTrue(json.contains("\"schemaVersion\""))
        val restored = Gson().fromJson(json, ResourceFontDeletion::class.java)
        restored.validate()
        assertEquals(value, restored)
        assertEquals(listOf(original), restored.asset().paths)
    }

    @Test fun corruptDeletionJournalCannotExpandTheDeleteScope() {
        val original = File(folder.root, "font.ttf").absolutePath
        val valid = ResourceFontDeletion(id = firstHash, path = original, paths = listOf(original))
        listOf(
            valid.copy(schemaVersion = 2), valid.copy(id = "bad"),
            valid.copy(paths = emptyList()), valid.copy(path = "elsewhere"),
            valid.copy(path = "relative.ttf", paths = listOf("relative.ttf")),
            valid.copy(paths = listOf(original, original)), valid.copy(payloadHashes = listOf("../payload")),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
        }
    }
}
