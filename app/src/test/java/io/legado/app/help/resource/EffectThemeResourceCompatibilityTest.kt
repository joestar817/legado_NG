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

    @Test fun listeningEffectsRequireTheirOwnZipAndCapability() {
        val source = theme("zip", listOf("listening.effect.1"))
        val listening = source.copy(category = source.category.copy(type = LISTENING_EFFECT_RESOURCE_TYPE))
        assertTrue(ResourceProtocol.isBrowsableType(listening.category.type))
        assertNull(ResourceProtocol.incompatibility(listening, Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(source, Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(
            listening.copy(item = theme("zip", listOf("theme.effect.1")).item), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(
            listening.copy(item = theme("zip", emptyList()).item), Long.MAX_VALUE))
        assertNotNull(ResourceProtocol.incompatibility(
            listening.copy(item = theme("ngtheme", listOf("listening.effect.1")).item), Long.MAX_VALUE))
    }

    @Test fun ordinaryThemeCompatibilityIsUnchanged() {
        assertNull(ResourceProtocol.incompatibility(theme("ngtheme", emptyList()), Long.MAX_VALUE))
        assertNull(ResourceProtocol.incompatibility(theme("zip", emptyList()), Long.MAX_VALUE))
    }

    @Test fun listeningEffectsUseSquareCatalogPreviews() {
        val sha = "b".repeat(64)
        val thumbnail = ResourcePreviewImage("previews/$sha.png", 1024, sha, 256, 256)
        val preview = ResourceFontPreview(thumbnail, thumbnail.copy(width = 768, height = 768))
        assertTrue(ResourceProtocol.supportsCatalogPreview(LISTENING_EFFECT_RESOURCE_TYPE))
        assertFalse(ResourceProtocol.supportsCatalogPreview("theme"))
        ResourceProtocol.validatePreview(preview, LISTENING_EFFECT_RESOURCE_TYPE)
        val wideDetail = thumbnail.copy(width = 960, height = 420)
        assertTrue(runCatching {
            ResourceProtocol.validatePreview(preview.copy(detail = wideDetail), LISTENING_EFFECT_RESOURCE_TYPE)
        }.isFailure)
    }
}
