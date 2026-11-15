package io.legado.app.help.resource

import io.legado.app.help.home.HomeWidgetSkinRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WidgetSkinReplacementPolicyTest {
    private val local = HomeWidgetSkinRecord("ng.home_skin.storybook", "绘本书屋", "a".repeat(64), 400)

    @Test fun localImportAlwaysRequiresExplicitReplacement() {
        val normal = assertThrows(ResourceConflict::class.java) {
            requireWidgetSkinReplacement(local, local, null, ResourceInstallMode.NORMAL)
        }
        assertEquals(local, normal.widgetSkinConflict)

        val unconfirmed = assertThrows(ResourceConflict::class.java) {
            requireWidgetSkinReplacement(local, local, null, ResourceInstallMode.REPLACE)
        }
        assertEquals(local, unconfirmed.widgetSkinConflict)
        assertThrows(ResourceConflict::class.java) {
            requireWidgetSkinReplacement(local, local, local, ResourceInstallMode.NORMAL)
        }
        requireWidgetSkinReplacement(local, local, local, ResourceInstallMode.REPLACE)
    }

    @Test fun changedOrRemovedLocalSkinCannotUseStaleConfirmation() {
        val changed = local.copy(sha256 = "b".repeat(64))
        val conflict = assertThrows(ResourceConflict::class.java) {
            requireWidgetSkinReplacement(changed, local, local, ResourceInstallMode.REPLACE)
        }
        assertEquals(changed, conflict.widgetSkinConflict)
        val removed = assertThrows(ResourceConflict::class.java) {
            requireWidgetSkinReplacement(null, local, local, ResourceInstallMode.REPLACE)
        }
        assertNull(removed.widgetSkinConflict)
    }

    @Test fun emptyAndResourceOwnedSkinsKeepNormalInstallAndUpdate() {
        requireWidgetSkinReplacement(null, null, null, ResourceInstallMode.NORMAL)
        val downloaded = local.copy(fromResource = true)
        requireWidgetSkinReplacement(downloaded, downloaded, null, ResourceInstallMode.NORMAL)
    }
}
