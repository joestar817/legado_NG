package io.legado.app.ui.main.home

import com.google.gson.JsonParser
import io.legado.app.help.home.HomeWidgetSkinRenderer
import io.legado.app.help.home.InstalledHomeWidgetSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeWidgetLayoutTest {
    @Test
    fun missingSkinKeepsSavedSizeAndBecomesSelectableOnlyWithAnInstalledPack() {
        val widget = HomeWidgetInstance("existing", "listening", "story-small")
        val saved = HomeWidgetLayoutCodec.encode(listOf(widget))
        val empty = HomeWidgetSkinSelection(emptyList())
        assertEquals(setOf("basic", "white"), empty.choices("listening").map { it.styleId }.toSet())
        assertEquals("small", empty.render(widget).id)
        val renderer = HomeWidgetSkinRenderer.STORYBOOK
        val selection = HomeWidgetSkinSelection(listOf(
            InstalledHomeWidgetSkin(renderer.resourceId, "绘本书屋", renderer, File("storybook.zip"), "test"),
        ))
        assertEquals("story-small", selection.render(widget).id)
        assertTrue(selection.allows("storybook"))
        assertFalse(selection.allows("night"))
        assertEquals(listOf(widget), HomeWidgetLayoutCodec.decode(saved))
    }

    @Test
    fun blueFishCanCoexistWithStorybookAndIsNotSelectableUntilInstalled() {
        val widget = HomeWidgetInstance("existing", "listening", "blue-fish-small")
        val story = HomeWidgetSkinRenderer.STORYBOOK
        val fish = HomeWidgetSkinRenderer.BLUE_FISH
        val storySkin = InstalledHomeWidgetSkin(story.resourceId, "绘本书屋", story, File("story.zip"), "story")
        val fishSkin = InstalledHomeWidgetSkin(fish.resourceId, "蓝色大肥鱼", fish, File("fish.zip"), "fish")
        val before = HomeWidgetSkinSelection(listOf(storySkin))
        assertFalse(before.allows("blue_fish"))
        assertEquals("small", before.render(widget).id)
        val after = HomeWidgetSkinSelection(listOf(storySkin, fishSkin))
        assertTrue(after.allows("storybook"))
        assertTrue(after.allows("blue_fish"))
        assertEquals(widget.variantId, after.render(widget).id)
        assertEquals("blue-fish-large", HomeWidgetCatalog.variantForSize("listening", widget.variantId, HomeWidgetSize.LARGE)?.id)
        assertEquals(listOf(widget), HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(listOf(widget))))
    }

    @Test
    fun savingAVariantPreservesPendingOrderRemovalAndAddedInstances() {
        val saved = HomeWidgetCatalog.defaults()
        val draft = listOf(
            HomeWidgetInstance("new_listening", "listening", "small"),
            saved[2], saved[1],
        )
        val changed = changeHomeWidgetVariant(draft, "new_listening", "large")!!
        assertEquals(draft.map { it.id }, changed.map { it.id })
        assertEquals("large", changed.first().variantId)
        assertSame(draft[1], changed[1])
        assertSame(draft[2], changed[2])
        assertFalse(changed.any { it.id == saved.first().id })
        assertEquals(changed, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(changed)))
    }

    @Test
    fun savingAnUnchangedVariantStillReturnsTheEntirePendingDraft() {
        val draft = HomeWidgetCatalog.defaults().reversed().dropLast(1)
        assertEquals(draft, changeHomeWidgetVariant(draft, draft.first().id, draft.first().variantId))
    }

    @Test
    fun aStaleEditorTargetOrUnavailableVariantCannotCommitOtherDraftChanges() {
        val draft = HomeWidgetCatalog.defaults().reversed()
        assertEquals(null, changeHomeWidgetVariant(draft, "removed_widget", "large"))
        assertEquals(null, changeHomeWidgetVariant(draft, draft.first().id, "unavailable_style"))
        assertEquals(null, changeHomeWidgetVariant(emptyList(), "missing", "small"))
        assertEquals(HomeWidgetCatalog.defaults().reversed(), draft)
    }

    @Test
    fun changingSizeKeepsTheChosenStyleFamily() {
        val variants = listOf(
            HomeWidgetVariant("basic-small", HomeWidgetSize.SMALL, 0),
            HomeWidgetVariant("basic-large", HomeWidgetSize.LARGE, 0),
            HomeWidgetVariant("paper-small", HomeWidgetSize.SMALL, 0, styleId = "paper"),
            HomeWidgetVariant("paper-large", HomeWidgetSize.LARGE, 0, styleId = "paper"),
        )
        assertEquals("paper-large", selectHomeWidgetSize(variants, "paper-small", HomeWidgetSize.LARGE)?.id)
        assertEquals("paper-small", selectHomeWidgetSize(variants, "paper-large", HomeWidgetSize.SMALL)?.id)
        assertEquals("basic-large", selectHomeWidgetSize(variants, "basic-small", HomeWidgetSize.LARGE)?.id)
    }

    @Test
    fun missingStyleUsesOnlyAnAvailableVariantOfTheRequestedSize() {
        val variants = listOf(
            HomeWidgetVariant("basic-large", HomeWidgetSize.LARGE, 0),
            HomeWidgetVariant("paper-small", HomeWidgetSize.SMALL, 0, styleId = "paper"),
        )
        assertEquals("basic-large", selectHomeWidgetSize(variants, "paper-small", HomeWidgetSize.LARGE)?.id)
        assertEquals("paper-small", selectHomeWidgetSize(variants, "unknown", HomeWidgetSize.SMALL)?.id)
        assertEquals(null, selectHomeWidgetSize(emptyList(), "unknown", HomeWidgetSize.LARGE))
    }

    @Test
    fun everyModuleOffersTheSameStyleFamiliesForItsAvailableSizes() {
        HomeWidgetCatalog.types.forEach { type ->
            type.variants.map { it.size }.distinct().forEach { size ->
                val choices = HomeWidgetCatalog.variantsForSize(type.id, size)
                assertEquals(listOf("basic", "storybook", "night", "white", "blue_fish"), choices.map { it.styleId })
                assertTrue(choices.all { it.size == size })
            }
        }
        assertEquals(
            listOf("small", "large", "story-small", "story-large", "night-small", "night-large", "white-small", "white-large", "blue-fish-small", "blue-fish-large"),
            HomeWidgetCatalog.type("listening")!!.variants.map { it.id },
        )
        assertEquals(null, HomeWidgetCatalog.variantForSize("unknown", "small", HomeWidgetSize.LARGE))
    }

    @Test
    fun calendarSkinChangesPreserveInstancesAndOtherPendingWidgets() {
        val widgets = HomeWidgetCatalog.defaults().reversed() +
            HomeWidgetInstance("calendar-one", "calendar", "large") +
            HomeWidgetInstance("calendar-two", "calendar", "night-large")
        HomeWidgetCatalog.type("calendar")!!.variants.forEach { variant ->
            val changed = changeHomeWidgetVariant(widgets, "calendar-one", variant.id)!!
            assertEquals(widgets.map { it.id }, changed.map { it.id })
            assertEquals(variant.id, changed.first { it.id == "calendar-one" }.variantId)
            widgets.filter { it.id != "calendar-one" }.forEach { other ->
                assertSame(other, changed.first { it.id == other.id })
            }
            assertEquals(changed, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(changed)))
        }
        assertEquals(listOf("default_updates", "default_listening", "default_reading"),
            HomeWidgetCatalog.defaults().map { it.id })
        assertEquals(listOf("large", "large", "large"), HomeWidgetCatalog.defaults().map { it.variantId })
    }

    @Test
    fun registeredListeningStylesKeepTheirFamilyWhenChangingSize() {
        val variants = HomeWidgetCatalog.type("listening")!!.variants
        variants.forEach { variant ->
            val targetSize = if (variant.size == HomeWidgetSize.SMALL) HomeWidgetSize.LARGE else HomeWidgetSize.SMALL
            val target = HomeWidgetCatalog.variantForSize("listening", variant.id, targetSize)!!
            assertEquals(variant.styleId, target.styleId)
            assertEquals(variant.styleTitleRes, target.styleTitleRes)
            assertEquals(targetSize, target.size)
            assertEquals(variant.id, HomeWidgetCatalog.variantForSize("listening", target.id, variant.size)?.id)
        }
    }

    @Test
    fun mixedListeningStylesAndSizesRoundTripWithoutChangingDefaults() {
        val widgets = listOf(
            HomeWidgetInstance("story_large", "listening", "story-large"),
            HomeWidgetInstance("reading", "reading", "small"),
            HomeWidgetInstance("story", "listening", "story-small"),
            HomeWidgetInstance("night", "listening", "night-large"),
            HomeWidgetInstance("basic", "listening", "small"),
        )
        assertEquals(widgets, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(widgets)))
        assertEquals(listOf(2, 1, 1, 2, 1), widgets.map { HomeWidgetCatalog.variant(it)!!.size.columns })
        assertEquals(listOf("large", "large", "large"), HomeWidgetCatalog.defaults().map { it.variantId })
    }

    @Test
    fun updatesSkinsKeepBothSizesAndPreserveTheExistingLayoutContract() {
        val variants = HomeWidgetCatalog.type("updates")!!.variants
        assertEquals(listOf("small", "large", "story-small", "story-large", "night-small", "night-large", "white-small", "white-large", "blue-fish-small", "blue-fish-large"),
            variants.map { it.id })
        val original = HomeWidgetCatalog.defaults()
        variants.forEach { variant ->
            val otherSize = if (variant.size == HomeWidgetSize.SMALL) HomeWidgetSize.LARGE else HomeWidgetSize.SMALL
            val switched = HomeWidgetCatalog.variantForSize("updates", variant.id, otherSize)!!
            assertEquals(variant.styleId, switched.styleId)
            assertEquals(variant.id, HomeWidgetCatalog.variantForSize("updates", switched.id, variant.size)?.id)
            val changed = changeHomeWidgetVariant(original, "default_updates", variant.id)!!
            original.filter { it.id != "default_updates" }.forEach { other ->
                assertSame(other, changed.first { it.id == other.id })
            }
            assertEquals(original.map { it.id }, changed.map { it.id })
            assertEquals(changed, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(changed)))
        }
        assertEquals(listOf("default_updates", "default_listening", "default_reading"), original.map { it.id })
        assertEquals(listOf("large", "large", "large"), original.map { it.variantId })
    }

    @Test
    fun readingSkinsKeepTheirFamilyAndRoundTripWithoutChangingOtherWidgets() {
        val readingVariants = HomeWidgetCatalog.type("reading")!!.variants
        val original = HomeWidgetCatalog.defaults()
        readingVariants.forEach { variant ->
            val otherSize = if (variant.size == HomeWidgetSize.SMALL) HomeWidgetSize.LARGE else HomeWidgetSize.SMALL
            val switched = HomeWidgetCatalog.variantForSize("reading", variant.id, otherSize)!!
            assertEquals(variant.styleId, switched.styleId)
            val changed = changeHomeWidgetVariant(original, "default_reading", variant.id)!!
            original.filter { it.id != "default_reading" }.forEach { other ->
                assertSame(other, changed.first { it.id == other.id })
            }
            assertEquals(changed, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(changed)))
        }
        assertEquals(listOf("large", "large", "large"), HomeWidgetCatalog.defaults().map { it.variantId })
    }

    @Test
    fun brightWhiteSupportsEveryExistingSizeWithoutAddingASmallCalendar() {
        val widgets = HomeWidgetCatalog.types.flatMap { type ->
            val whiteVariants = type.variants.filter { it.styleId == "white" }
            val expectedSizes = if (type.id == "calendar") listOf(HomeWidgetSize.LARGE)
                else listOf(HomeWidgetSize.SMALL, HomeWidgetSize.LARGE)
            assertEquals(expectedSizes, whiteVariants.map { it.size })
            whiteVariants.map { variant ->
                HomeWidgetInstance("${type.id}-${variant.id}", type.id, variant.id)
            }
        }
        assertEquals(widgets, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(widgets)))
        assertEquals(listOf("large", "large", "large"),
            HomeWidgetCatalog.defaults().map { it.variantId })
    }

    @Test
    fun retiredPaperVariantsAreRejectedInsteadOfChangingSavedStyles() {
        listOf("paper-small", "paper-large").forEach { variantId ->
            val widget = HomeWidgetInstance("retired", "listening", variantId)
            assertEquals(null, HomeWidgetCatalog.variant(widget))
            assertInvalid(HomeWidgetLayoutCodec.encode(listOf(widget)))
        }
    }

    @Test
    fun layoutsKeepInstanceIdentityAndMixedSizes() {
        val widgets = listOf(
            HomeWidgetInstance("one", "reading", "large"),
            HomeWidgetInstance("two", "reading", "small"),
            HomeWidgetInstance("three", "listening", "small"),
        )
        assertEquals(widgets, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(widgets)))
        assertEquals(listOf(2, 1, 1), widgets.map { HomeWidgetCatalog.variant(it)!!.size.columns })
    }

    @Test
    fun serializationHasExplicitStableKeys() {
        val obj = JsonParser.parseString(HomeWidgetLayoutCodec.encode(HomeWidgetCatalog.defaults())).asJsonObject
        assertEquals(setOf("version", "widgets"), obj.keySet())
        assertEquals(setOf("id", "type", "variant"), obj["widgets"].asJsonArray[0].asJsonObject.keySet())
    }

    @Test
    fun anExplicitEmptyLayoutStaysEmpty() {
        assertTrue(HomeWidgetLayoutCodec.decode("""{"version":1,"widgets":[]}""").isEmpty())
    }

    @Test
    fun duplicateIdsAreRejectedEvenWhenWidgetTypesDiffer() {
        assertInvalid("""{"version":1,"widgets":[{"id":"same","type":"listening","variant":"small"},{"id":"same","type":"reading","variant":"large"}]}""")
    }

    @Test
    fun missingFieldsAndNonStringIdsAreRejected() {
        assertInvalid("""{"version":1,"widgets":[{"type":"reading","variant":"small"}]}""")
        assertInvalid("""{"version":1,"widgets":[{"id":3,"type":"reading","variant":"small"}]}""")
        assertInvalid("""{"version":1,"widgets":null}""")
    }

    @Test
    fun unknownTypesVariantsAndVersionsAreNotSilentlyMigrated() {
        assertInvalid("""{"version":1,"widgets":[{"id":"one","type":"unknown","variant":"small"}]}""")
        assertInvalid("""{"version":1,"widgets":[{"id":"one","type":"reading","variant":"unknown"}]}""")
        assertInvalid("""{"version":2,"widgets":[]}""")
    }

    @Test
    fun movingUsesStableIdsAfterAStyleChange() {
        val widgets = listOf(
            HomeWidgetInstance("one", "reading", "large"),
            HomeWidgetInstance("two", "reading", "small"),
            HomeWidgetInstance("three", "listening", "small"),
        )
        val changed = widgets.map { if (it.id == "two") it.copy(variantId = "large") else it }
        val moved = moveHomeWidget(changed, "two", "three")
        assertEquals(listOf("one", "three", "two"), moved.map { it.id })
        assertEquals("large", moved.last().variantId)
    }

    @Test
    fun invalidDragTargetsDoNotChangeTheLayout() {
        val widgets = HomeWidgetCatalog.defaults()
        assertSame(widgets, moveHomeWidget(widgets, widgets.first().id, "home_add_widget"))
        assertSame(widgets, moveHomeWidget(widgets, "missing", widgets.first().id))
        assertSame(widgets, moveHomeWidget(widgets, widgets.first().id, widgets.first().id))
        assertFalse(widgets.isEmpty())
    }

    private fun assertInvalid(json: String) {
        assertTrue(runCatching { HomeWidgetLayoutCodec.decode(json) }.isFailure)
    }
}
