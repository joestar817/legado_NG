package io.legado.app.ui.config

import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiModelModality
import io.legado.app.help.ai.AiModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelEditSheetTest {

    @Test
    fun `known chat model exposes registry capabilities`() {
        val defaults = AiModel(id = "gpt-4o").registryCapabilityDefaults()

        assertEquals(AiModelType.CHAT, defaults?.type)
        assertTrue(AiModelModality.TEXT in defaults!!.inputModalities)
        assertTrue(AiModelModality.IMAGE in defaults.inputModalities)
        assertTrue(AiModelModality.TEXT in defaults.outputModalities)
        assertTrue(AiModelAbility.TOOL in defaults.abilities)
    }

    @Test
    fun `known image model exposes image type`() {
        val defaults = AiModel(id = "gemini-2.5-flash-image").registryCapabilityDefaults()

        assertEquals(AiModelType.IMAGE, defaults?.type)
        assertTrue(AiModelModality.IMAGE in defaults!!.outputModalities)
    }

    @Test
    fun `unknown model has no registry defaults`() {
        assertNull(AiModel(id = "my-private-model-xyz").registryCapabilityDefaults())
        assertNull(AiModel(id = "").registryCapabilityDefaults())
    }
}
