package io.legado.app.help.resource

import org.junit.Assert.*
import org.junit.Test

class EffectThemeResourceCompatibilityTest {
    private fun theme(format: String, capabilities: List<String>) = ResourceEntry(
        ResourceCategory("themes", "主题", "theme", "resources/themes/index.json", 1),
        "a".repeat(40),
        ResourceItem(id = "theme.example", name = "示例主题", versionCode = 1,
            payload = ResourcePayload("resources/themes/example.zip", format, 100, "b".repeat(64)),
            requirements = ResourceRequirements(capabilities = capabilities)),
    )

    @Test fun completeEffectZipUsesDeclaredHostApi() {
        assertNull(ResourceProtocol.incompatibility(theme("zip", listOf("theme.effect.1")), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(theme("zip", listOf("theme.effect.2")), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(theme("zip", listOf("pet.api.2")), Long.MAX_VALUE))
    }

    @Test fun listeningEffectDownloadsArePaused() {
        val source = theme("zip", listOf("listening.effect.1"))
        val listening = source.copy(category = source.category.copy(type = "listening_effect"))
        assertFalse(ResourceProtocol.isBrowsableType(listening.category.type))
        assertNotNull(ResourceProtocol.incompatibility(listening, Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(source, Long.MAX_VALUE))
    }

    @Test fun ordinaryThemeCompatibilityIsUnchanged() {
        assertNull(ResourceProtocol.incompatibility(theme("ngtheme", emptyList()), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(theme("zip", emptyList()), Long.MAX_VALUE))
    }
}
