package io.legado.app.ui.resource

import io.legado.app.help.resource.ResourceCategory
import io.legado.app.help.resource.HOME_WIDGET_SKIN_TYPE
import io.legado.app.help.resource.ResourceEntry
import io.legado.app.help.resource.ResourceItem
import org.junit.Assert.*
import org.junit.Test

class ResourceClassificationTest {
    private fun entry(id: String, type: String, category: String = type, tags: List<String> = emptyList()) = ResourceEntry(
        category = ResourceCategory(id = category, name = category, type = type),
        item = ResourceItem(id = id, name = id, author = "Author", tags = tags),
    )

    @Test fun widgetSkinsHaveTheirOwnAppearanceFilterAndPriority() {
        val theme = entry("theme", "theme")
        val skin = entry("skin", HOME_WIDGET_SKIN_TYPE)
        val entries = listOf(theme, skin, entry("pet", "pet"))
        assertEquals(listOf(theme, skin), filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "", ""))
        assertEquals(listOf(skin), filterResourceEntries(entries, ResourcePurpose.APPEARANCE, HOME_WIDGET_SKIN_TYPE, ""))
        assertEquals(setOf(HOME_WIDGET_SKIN_TYPE), resourcePriorityTypes(ResourcePurpose.APPEARANCE, HOME_WIDGET_SKIN_TYPE))
    }

    @Test fun purposesSeparateReadingAppearanceAndListeningWithoutChangingEntries() {
        val entries = listOf(entry("preset", "read_preset"), entry("font-a", "font"), entry("paper", "background"),
            entry("theme-a", "theme"), entry("script", "tts"))
        assertEquals(listOf(entries[0]), filterResourceEntries(entries, ResourcePurpose.READING, "", ""))
        assertSame(entries[3], filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "", "").single())
        assertSame(entries[4], filterResourceEntries(entries, ResourcePurpose.LISTENING, "", "").single())
        assertTrue(filterResourceEntries(entries, ResourcePurpose.PETS, "", "").isEmpty())
    }

    @Test fun listeningEffectsHaveAnIndependentFilterFromThemePackages() {
        val theme = entry("app-cats", "theme")
        val effect = entry("player-cats", "listening_effect")
        val script = entry("script", "tts")
        val entries = listOf(theme, effect, script)
        assertEquals(entries, filterResourceEntries(entries, null, "", ""))
        assertEquals(listOf(theme), filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "", ""))
        assertEquals(listOf(effect, script), filterResourceEntries(entries, ResourcePurpose.LISTENING, "", ""))
        assertEquals(listOf(effect), filterResourceEntries(entries, ResourcePurpose.LISTENING, effect.category.type, ""))
        assertEquals(listOf("theme", "listening_effect", "tts"), resourceTypeOptions(entries.map { it.category }, null).map { it.key })
        assertEquals(setOf("listening_effect"), resourcePriorityTypes(ResourcePurpose.LISTENING, effect.category.type))
    }

    @Test fun aTypeCanSpanMultipleCatalogCategoriesAndHaveOnlyOneMenuOption() {
        val entries = listOf(entry("one", "read_preset", "reading-a"), entry("two", "read_preset", "reading-b"),
            entry("paper", "background"))
        val types = resourceTypeOptions(entries.map { it.category }, ResourcePurpose.READING)
        assertEquals(listOf(ResourceTypeOption("read_preset", "阅读预设")), types)
        assertEquals(entries.take(2), filterResourceEntries(entries, ResourcePurpose.READING, "read_preset", ""))
    }

    @Test fun purposeTypeAndSearchAreCombinedAndSearchStillIncludesAuthorAndTags() {
        val entries = listOf(entry("title", "read_preset", tags = listOf("Heading")), entry("paper", "read_preset"))
        assertEquals(listOf(entries[0]), filterResourceEntries(entries, ResourcePurpose.READING, "read_preset", " HEADING "))
        assertEquals(entries, filterResourceEntries(entries, ResourcePurpose.READING, "", "author"))
        assertTrue(filterResourceEntries(entries, ResourcePurpose.READING, "background", "Heading").isEmpty())
        assertTrue(filterResourceEntries(entries, ResourcePurpose.APPEARANCE, "read_preset", "").isEmpty())
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
        val categories = listOf(entry("preset", "read_preset").category, entry("theme", "theme").category)
        assertEquals(listOf("read_preset"), resourceTypeOptions(categories, ResourcePurpose.READING).map { it.key })
        assertTrue(resourceTypeOptions(categories, ResourcePurpose.PETS).isEmpty())
        assertEquals(listOf("read_preset", "theme"), resourceTypeOptions(categories, null).map { it.key })
    }
}
