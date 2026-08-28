package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NgVisualSystemTest {

    @Test
    fun `liquid glass is the default while explicit selections remain stable`() {
        assertEquals(NgVisualSystem.LIQUID_GLASS, NgVisualSystem.DEFAULT)
        assertEquals(NgVisualSystem.LIQUID_GLASS, NgVisualSystem.fromStoredValue(null))
        assertEquals(NgVisualSystem.LIQUID_GLASS, NgVisualSystem.fromStoredValue("unknown"))
        assertEquals(
            NgVisualSystem.TRANSPARENT_GLASS,
            NgVisualSystem.fromStoredValue("transparent_glass"),
        )
        assertEquals(
            NgVisualSystem.LIQUID_GLASS,
            NgVisualSystem.fromStoredValue("liquid_glass"),
        )
    }
}
