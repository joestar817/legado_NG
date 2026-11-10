package io.legado.app.ui.pet

import io.legado.app.help.pet.PetLocalStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetManagerStateTest {
    @Test
    fun detailCanUseAReadyPetWhileEntryIsOff() {
        val state = PetManagerState(loading = false, enabled = false)
        assertTrue(state.canUseFromDetail(PetLocalStatus.READY))
        assertFalse(state.canUseFromCard(PetLocalStatus.READY))
        assertTrue(state.copy(enabled = true).canUseFromCard(PetLocalStatus.READY))
    }

    @Test
    fun detailAllowsRetryButBlocksInvalidPets() {
        val state = PetManagerState(loading = false)
        assertTrue(state.canUseFromDetail(PetLocalStatus.LOAD_ERROR))
        assertFalse(state.canUseFromDetail(PetLocalStatus.MISSING))
        assertFalse(state.canUseFromDetail(PetLocalStatus.INCOMPATIBLE))
        assertFalse(state.canUseFromDetail(PetLocalStatus.CORRUPT))
    }

    @Test
    fun detailBlocksUseWhileLoadingOrBusy() {
        assertFalse(PetManagerState(loading = true).canUseFromDetail(PetLocalStatus.READY))
        assertFalse(PetManagerState(loading = false, busy = true).canUseFromDetail(PetLocalStatus.READY))
    }
}
