package io.legado.app.help.config

import org.junit.Assert.*
import org.junit.Test

class NgEffectPackageKindTest {
    @Test fun eachFormatHasOnePurposeAndOneDirectory() {
        assertEquals(NgEffectPackageKind.THEME, NgEffectPackageKind.fromFormat("reading-ng-theme"))
        assertEquals(NgEffectPackageKind.LISTENING_EFFECT, NgEffectPackageKind.fromFormat("reading-ng-listening-effect"))
        assertNotEquals(NgEffectPackageKind.THEME.directory, NgEffectPackageKind.LISTENING_EFFECT.directory)
        assertThrows(IllegalStateException::class.java) { NgEffectPackageKind.fromFormat("scene-only") }
    }

    @Test fun sameIdHasIndependentRuntimeIdentity() {
        assertNotEquals(NgEffectPackageKind.runtimeKey("example.cats", "app"),
            NgEffectPackageKind.runtimeKey("example.cats", "player"))
    }

    @Test fun dualUseAndWrongPurposeAreRejected() {
        val app = NgEffectPackageKind.THEME
        val player = NgEffectPackageKind.LISTENING_EFFECT
        assertTrue(app.acceptsTargets(listOf("app")))
        assertTrue(player.acceptsTargets(listOf("player")))
        for (targets in listOf(emptyList(), listOf("app", "player"), listOf("app", "app"))) {
            assertFalse(app.acceptsTargets(targets))
            assertFalse(player.acceptsTargets(targets))
        }
        assertFalse(app.acceptsTargets(listOf("player")))
        assertFalse(player.acceptsTargets(listOf("app")))
        assertThrows(IllegalArgumentException::class.java) {
            NgEffectThemeDescriptor(entryClass = "example.Cats", targets = listOf("app", "player")).validate()
        }
    }
}
