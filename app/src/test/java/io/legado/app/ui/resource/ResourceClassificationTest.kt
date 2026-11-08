package io.legado.app.ui.resource

import io.legado.app.help.resource.ResourceCategory
import io.legado.app.help.resource.ResourceEntry
import io.legado.app.help.resource.ResourceItem
import org.junit.Assert.*
import org.junit.Test

class ResourceClassificationTest {
    private fun entry(id: String, type: String, category: String = type, tags: List<String> = emptyList()) = ResourceEntry(
        category = ResourceCategory(id = category, name = category, type = type),
        item = ResourceItem(id = id, name = id, author = "Author", tags = tags),
    )

    @Test fun purposesSeparateReadingAppearanceAndListeningWithoutChangingEntries() {
        val entries = listOf(entry("font-a", "font"), entry("paper", "background"),
            entry("theme-a", "theme"), entry("script", "tts"))
        assertEquals(entries.take(2), filterResourceEntries(entries, ResourcePurpose.READING, "", ""))
        assertSame(entries[2], filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "", "").single())
        assertSame(entries[3], filterResourceEntries(entries, ResourcePurpose.LISTENING, "", "").single())
        assertTrue(filterResourceEntries(entries, ResourcePurpose.PETS, "", "").isEmpty())
    }

    @Test fun listeningEffectPackagesAreHiddenFromTheResourceCenter() {
        val theme = entry("app-cats", "theme")
        val effect = entry("player-cats", "listening_effect")
        val script = entry("script", "tts")
        val entries = listOf(theme, effect, script)
        assertEquals(listOf(theme, script), filterResourceEntries(entries, null, "", ""))
        assertEquals(listOf(theme), filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "", ""))
        assertEquals(listOf(script), filterResourceEntries(entries, ResourcePurpose.LISTENING, "", ""))
        assertEquals(listOf("theme", "tts"), resourceTypeOptions(entries.map { it.category }, null).map { it.key })
        assertEquals(setOf("tts"), resourcePriorityTypes(ResourcePurpose.LISTENING, effect.category.type))
    }

    @Test fun aTypeCanSpanMultipleCatalogCategoriesAndHaveOnlyOneMenuOption() {
        val entries = listOf(entry("body", "font", "body-fonts"), entry("title", "font", "title-fonts"),
            entry("paper", "background"))
        val types = resourceTypeOptions(entries.map { it.category }, ResourcePurpose.READING)
        assertEquals(listOf(ResourceTypeOption("font", "字体"), ResourceTypeOption("background", "背景")), types)
        assertEquals(entries.take(2), filterResourceEntries(entries, ResourcePurpose.READING, "font", ""))
    }

    @Test fun purposeTypeAndSearchAreCombinedAndSearchStillIncludesAuthorAndTags() {
        val entries = listOf(entry("title", "font", tags = listOf("Heading")), entry("paper", "background"))
        assertEquals(listOf(entries[0]), filterResourceEntries(entries, ResourcePurpose.READING, "font", " HEADING "))
        assertEquals(entries, filterResourceEntries(entries, ResourcePurpose.READING, "", "author"))
        assertTrue(filterResourceEntries(entries, ResourcePurpose.READING, "background", "Heading").isEmpty())
        assertTrue(filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "font", "").isEmpty())
    }

    @Test fun unknownTypesRemainReachableFromAllAndAreNotMisclassified() {
        val unknown = entry("future", "future-type", "未来资源")
        assertEquals(listOf(unknown), filterResourceEntries(listOf(unknown), null, "", ""))
        assertEquals(listOf(ResourceTypeOption("future-type", "未来资源")),
            resourceTypeOptions(listOf(unknown.category), null))
        ResourcePurpose.entries.forEach {
            assertTrue(filterResourceEntries(listOf(unknown), it, "", "").isEmpty())
        }
    }

    @Test fun menusUseOnlyCatalogTypesAndDoNotInventUnimplementedInstallKinds() {
        val categories = listOf(entry("font", "font").category, entry("theme", "theme").category)
        assertEquals(listOf("font"), resourceTypeOptions(categories, ResourcePurpose.READING).map { it.key })
        assertTrue(resourceTypeOptions(categories, ResourcePurpose.PETS).isEmpty())
        assertEquals(listOf("font", "theme"), resourceTypeOptions(categories, null).map { it.key })
    }
}
