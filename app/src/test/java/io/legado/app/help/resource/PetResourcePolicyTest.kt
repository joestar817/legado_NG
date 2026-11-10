package io.legado.app.help.resource

import io.legado.app.help.pet.PetInstallOrigin
import io.legado.app.help.pet.PetLocalRecord
import io.legado.app.help.pet.PetLocalStatus
import io.legado.app.help.pet.PetPluginManifest
import org.junit.Assert.*
import org.junit.Test

class PetResourcePolicyTest {
    private val localHash = "a".repeat(64)
    private val onlineHash = "b".repeat(64)
    private val category = ResourceCategory("pets", "宠物", "pet", "resources/pets/index.json", 1)

    private fun entry(version: Long = 2, hash: String = onlineHash) = ResourceEntry(
        category = category,
        sourceCommit = "c".repeat(40),
        catalogCommit = "d".repeat(40),
        item = ResourceItem(id = "test-resource", name = "测试", versionCode = version,
            payload = ResourcePayload(path = "pets/test.zip", format = "zip", size = 100,
                sha256 = hash, pluginId = "test_pet")),
    )

    private fun local(version: Long = 1, hash: String = localHash, origin: PetInstallOrigin? = PetInstallOrigin(),
        status: PetLocalStatus = PetLocalStatus.READY) = PetLocalRecord(
        "test_pet", PetPluginManifest(2, "pet", 2, "test_pet", "测试", version, "example.Pet"),
        null, hash, 100, origin, 1, status,
    )

    private fun origin(hash: String = localHash) = PetInstallOrigin(
        channel = "resource", resourceId = "test-resource", repository = ResourceProtocol.REPOSITORY,
        catalogCommit = "e".repeat(40), sourceCommit = "f".repeat(40), sha256 = hash,
    )

    private fun receipt(hash: String = localHash) = ResourceInstallation(
        entry = entry(1, hash), target = "test_pet", baseline = hash, installedAt = 1,
    )

    @Test
    fun catalogPreviewReceiptMustMatchTheInstalledPet() {
        val pet = local()
        val exact = receipt()
        val mismatches = listOf(
            receipt(onlineHash),
            exact.copy(baseline = onlineHash),
            exact.copy(target = "another_pet"),
            exact.copy(entry = exact.entry.copy(item = exact.entry.item.copy(
                payload = exact.entry.item.payload.copy(pluginId = "another_pet"),
            ))),
        )
        assertNull(PetResourcePolicy.matchingReceipt(pet, mismatches))
        assertEquals(exact, PetResourcePolicy.matchingReceipt(pet, mismatches + exact))
    }

    private fun state(entry: ResourceEntry, pet: PetLocalRecord, receipt: ResourceInstallation? = null) =
        ResourceCenterState(
            snapshot = ResourceSnapshot(catalog = ResourceCatalog(1, "c".repeat(40), listOf(category)), entries = listOf(entry)),
            installations = listOfNotNull(receipt), petInstallations = mapOf(pet.id to pet),
            localChecked = true, loadedCategories = setOf(category.id),
        )

    @Test fun localImportsNeverJoinUpdateAllEvenWhenAnOldReceiptSurvives() {
        val latest = entry()
        for (oldReceipt in listOf(null, receipt())) {
            val pet = local()
            assertFalse(PetResourcePolicy.autoUpdate(latest, pet, oldReceipt))
            assertTrue(PetResourcePolicy.localChange(latest, pet, oldReceipt))
            assertTrue(state(latest, pet, oldReceipt).updatableEntries().isEmpty())
        }
    }

    @Test fun sameVersionDifferentBytesAlwaysNeedExplicitConfirmation() {
        val latest = entry(1)
        for (pet in listOf(local(), local(origin = origin()), local(origin = null))) {
            val oldReceipt = receipt()
            assertTrue(PetResourcePolicy.localChange(latest, pet, oldReceipt))
            assertFalse(PetResourcePolicy.autoUpdate(latest, pet, oldReceipt))
            assertEquals("本地修改版", state(latest, pet, oldReceipt).versionLabel(latest))
            assertTrue(state(latest, pet, oldReceipt).updatableEntries().isEmpty())
        }
    }

    @Test fun newerLocalVersionIsNeverAutomaticallyDowngraded() {
        val latest = entry(2)
        val pet = local(version = 3, origin = origin())
        assertFalse(PetResourcePolicy.autoUpdate(latest, pet, receipt()))
        assertEquals("本地版本较新", state(latest, pet, receipt()).versionLabel(latest))
        assertTrue(state(latest, pet, receipt()).updatableEntries().isEmpty())
    }

    @Test fun verifiedOriginAllowsHigherVersionAndRejectsMismatchedReceipts() {
        val latest = entry()
        val pet = local(origin = origin())
        assertEquals(listOf(latest), state(latest, pet).updatableEntries())
        for (badOrigin in listOf(origin().copy(sha256 = onlineHash), origin().copy(resourceId = "other"),
            origin().copy(repository = "other/repository"))) {
            assertFalse(PetResourcePolicy.autoUpdate(latest, local(origin = badOrigin), receipt()))
        }
    }

    @Test fun legacyReceiptMustMatchTheActualInstalledBytesAndIdentity() {
        val latest = entry()
        val pet = local(origin = null)
        assertTrue(PetResourcePolicy.autoUpdate(latest, pet, receipt()))
        assertEquals(listOf(latest), state(latest, pet, receipt()).updatableEntries())
        assertFalse(PetResourcePolicy.autoUpdate(latest, pet, null))
        for (badReceipt in listOf(receipt().copy(baseline = onlineHash), receipt().copy(target = "other_pet"),
            receipt().copy(repository = "other/repository"), receipt().copy(entry = entry(1, onlineHash)))) {
            assertFalse(PetResourcePolicy.autoUpdate(latest, pet, badReceipt))
        }
    }

    @Test fun identicalLocalPackageIsAvailableWithoutInventingAnOnlineReceipt() {
        val latest = entry(1, localHash)
        val pet = local()
        val state = state(latest, pet)
        assertTrue(PetResourcePolicy.identical(latest, pet))
        assertTrue(state.hasInstalled(latest))
        assertFalse(state.isLocalPetChange(latest))
        assertEquals("已下载", state.versionLabel(latest))
        assertTrue(state.installations.isEmpty())
        assertTrue(state.updatableEntries().isEmpty())
        assertEquals("local", state.pet(latest)?.origin?.channel)
    }

    @Test fun missingPackageIsNotAvailableOrAutomaticallyUpdated() {
        val latest = entry()
        val pet = local(origin = origin(), status = PetLocalStatus.MISSING)
        val state = state(latest, pet, receipt())
        assertFalse(state.hasInstalled(latest))
        assertEquals("文件缺失", state.versionLabel(latest))
        assertTrue(state.updatableEntries().isEmpty())
    }
    @Test fun normalInstallCanRepairOnlyAnIdenticalMissingOrCorruptPackage() {
        val same = entry(1, localHash)
        for (status in listOf(PetLocalStatus.MISSING, PetLocalStatus.CORRUPT)) {
            assertTrue(PetResourcePolicy.replacementAllowed(same, local(status = status), ResourceInstallMode.NORMAL))
        }
        assertFalse(PetResourcePolicy.replacementAllowed(same, local(), ResourceInstallMode.NORMAL))
    }

    @Test fun differentBytesStillNeedExplicitReplacementEvenForDamagedFiles() {
        val changed = entry(1, onlineHash)
        for (status in listOf(PetLocalStatus.READY, PetLocalStatus.MISSING, PetLocalStatus.CORRUPT)) {
            val pet = local(status = status)
            assertFalse(PetResourcePolicy.replacementAllowed(changed, pet, ResourceInstallMode.NORMAL))
            assertTrue(PetResourcePolicy.replacementAllowed(changed, pet, ResourceInstallMode.REPLACE))
        }
    }

    @Test fun corruptPackageWithValidOriginIsExcludedFromAutomaticUpdates() {
        val latest = entry()
        val pet = local(origin = origin(), status = PetLocalStatus.CORRUPT)
        assertTrue(PetResourcePolicy.owned(latest, pet, receipt()))
        assertFalse(PetResourcePolicy.autoUpdate(latest, pet, receipt()))
        assertTrue(state(latest, pet, receipt()).updatableEntries().isEmpty())
        assertEquals("文件损坏", state(latest, pet, receipt()).versionLabel(latest))
    }

}
