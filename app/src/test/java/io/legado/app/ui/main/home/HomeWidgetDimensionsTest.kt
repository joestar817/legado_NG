package io.legado.app.ui.main.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWidgetDimensionsTest {
    @Test fun `basic small cards do not measure the illustrated skins`() {
        listOf("listening", "reading").forEach { typeId ->
            val basic = HomeWidgetCatalog.type(typeId)!!.variants.first { it.id == "small" }
            assertEquals(listOf(basic), homeWidgetMeasureVariants(typeId, HomeWidgetSize.SMALL, basic))
        }
    }

    @Test fun `decorated small probes retain their artwork without reusing the compact probe`() {
        listOf("storybook", "night").forEach { style ->
            val listening = HomeWidgetCatalog.variantsForSize("listening", HomeWidgetSize.SMALL).first { it.styleId == style }
            val reading = HomeWidgetCatalog.variantsForSize("reading", HomeWidgetSize.SMALL).first { it.styleId == style }
            assertEquals(setOf("storybook", "night"),
                homeWidgetMeasureVariants("listening", HomeWidgetSize.SMALL, listening).map { it.styleId }.toSet())
            assertEquals(listOf(reading), homeWidgetMeasureVariants("reading", HomeWidgetSize.SMALL, reading))
        }
    }

    @Test fun `large cards only measure their selected composition`() {
        listOf("listening", "reading").forEach { typeId ->
            HomeWidgetCatalog.variantsForSize(typeId, HomeWidgetSize.LARGE).forEach { variant ->
                assertEquals(listOf(variant), homeWidgetMeasureVariants(typeId, HomeWidgetSize.LARGE, variant))
            }
        }
    }

    private fun dimensions(header: Int = 48, smallBody: Int = 232,
        compactHeader: Int = 48, compactBody: Int = 160) = HomeWidgetDimensions(
        columns = 2,
        widths = mapOf(HomeWidgetSize.SMALL to 164.dp, HomeWidgetSize.LARGE to 340.dp),
        headers = mapOf(HomeWidgetSize.SMALL to header.dp),
        bodies = mapOf(HomeWidgetSize.SMALL to smallBody.dp),
        largeHeaders = mapOf("listening" to 48.dp, "reading" to 48.dp),
        largeBodies = mapOf(
            HomeWidgetBodyKey("listening", "basic") to 105.dp,
            HomeWidgetBodyKey("listening", "storybook") to 139.dp,
            HomeWidgetBodyKey("listening", "night") to 138.dp,
            HomeWidgetBodyKey("reading", "basic") to 176.dp,
        ),
        compactSmallHeader = compactHeader.dp,
        compactSmallBody = compactBody.dp,
    )

    @Test fun `the two compact glass small cards share a shorter frame and editing reserve`() {
        val dimensions = dimensions()
        listOf("listening", "reading").forEach { typeId ->
            assertEquals(208.dp, dimensions.height(HomeWidgetSize.SMALL, false, typeId, "basic"))
            assertEquals(256.dp, dimensions.height(HomeWidgetSize.SMALL, true, typeId, "basic"))
            listOf("storybook", "night").forEach { styleId ->
                assertEquals(280.dp, dimensions.height(HomeWidgetSize.SMALL, false, typeId, styleId))
                assertEquals(328.dp, dimensions.height(HomeWidgetSize.SMALL, true, typeId, styleId))
            }
        }
    }

    @Test fun `compact and decorated mixed small groups expand independently`() {
        val decoratedExpanded = dimensions(header = 72, smallBody = 300)
        val compactExpanded = dimensions(compactHeader = 64, compactBody = 250)
        listOf("listening", "reading").forEach { typeId ->
            assertEquals(208.dp, decoratedExpanded.height(HomeWidgetSize.SMALL, false, typeId, "basic"))
            assertEquals(372.dp, decoratedExpanded.height(HomeWidgetSize.SMALL, false, typeId, "storybook"))
            assertEquals(314.dp, compactExpanded.height(HomeWidgetSize.SMALL, false, typeId, "basic"))
            assertEquals(280.dp, compactExpanded.height(HomeWidgetSize.SMALL, false, typeId, "night"))
            assertEquals(208.dp, dimensions().height(HomeWidgetSize.SMALL, false, typeId, "basic"))
        }
        assertEquals(40.dp, compactExpanded.height(HomeWidgetSize.SMALL, false, "updates", "basic"))
        assertEquals(153.dp, compactExpanded.height(HomeWidgetSize.LARGE, false, "listening", "basic"))
    }

    @Test fun `large heights follow their own content and reserve only the editing tools`() {
        val dimensions = dimensions()
        assertEquals(153.dp, dimensions.height(HomeWidgetSize.LARGE, false, "listening", "basic"))
        assertEquals(187.dp, dimensions.height(HomeWidgetSize.LARGE, false, "listening", "storybook"))
        assertEquals(186.dp, dimensions.height(HomeWidgetSize.LARGE, false, "listening", "night"))
        assertEquals(224.dp, dimensions.height(HomeWidgetSize.LARGE, false, "reading", "basic"))
        assertEquals(201.dp, dimensions.height(HomeWidgetSize.LARGE, true, "listening", "basic"))
    }

    @Test fun `large expanded content does not stretch another type or skin`() {
        val expanded = dimensions().copy(
            largeHeaders = mapOf("listening" to 48.dp, "reading" to 72.dp),
            largeBodies = dimensions().largeBodies + (HomeWidgetBodyKey("reading", "basic") to 260.dp),
        )
        assertEquals(332.dp, expanded.height(HomeWidgetSize.LARGE, false, "reading", "basic"))
        assertEquals(153.dp, expanded.height(HomeWidgetSize.LARGE, false, "listening", "basic"))
        assertEquals(187.dp, expanded.height(HomeWidgetSize.LARGE, false, "listening", "storybook"))
        assertEquals(224.dp, dimensions().height(HomeWidgetSize.LARGE, false, "reading", "basic"))
    }

    @Test fun `expanded small content keeps editing reserve and can return to normal budget`() {
        val expanded = dimensions(header = 72, smallBody = 300)
        assertEquals(420.dp, expanded.height(HomeWidgetSize.SMALL, true))
        assertEquals(280.dp, dimensions().height(HomeWidgetSize.SMALL, false))
    }

    @Test fun `an unmeasured large card has no inherited body minimum`() {
        assertEquals(48.dp, HomeWidgetSize.LARGE.standardHeight())
        assertEquals(48.dp, dimensions().height(HomeWidgetSize.LARGE, false, "updates", "basic"))
    }

    @Test fun `tools keep three 48dp touch targets at row boundaries`() {
        assertEquals(48.dp, homeWidgetToolHeight(152.dp))
        assertEquals(96.dp, homeWidgetToolHeight(151.dp))
        assertEquals(96.dp, homeWidgetToolHeight(104.dp))
        assertEquals(144.dp, homeWidgetToolHeight(103.dp))
    }

    @Test fun `single column fallback preserves body touch area`() {
        assertEquals(2, homeWidgetGridColumns(236.dp))
        assertEquals(1, homeWidgetGridColumns(235.dp))
        assertEquals(1, homeWidgetGridColumns(236.dp, fontScale = 2f))
        assertEquals(2, homeWidgetGridColumns(268.dp, fontScale = 2f))
    }

    @Test fun `only displayed small news cards require a three-cell minimum width`() {
        val defaults = HomeWidgetCatalog.defaults()
        assertEquals(0.dp, homeWidgetMinimumSmallWidth(defaults))
        assertEquals(2, homeWidgetGridColumns(299.dp))
        HomeWidgetCatalog.variantsForSize("updates", HomeWidgetSize.SMALL).forEach { variant ->
            val widgets = defaults + HomeWidgetInstance("news-small", "updates", variant.id)
            val minimum = homeWidgetMinimumSmallWidth(widgets)
            assertEquals(144.dp, minimum)
            assertEquals(1, homeWidgetGridColumns(299.dp, minimumSmallWidth = minimum))
            assertEquals(2, homeWidgetGridColumns(300.dp, minimumSmallWidth = minimum))
            assertEquals(2, homeWidgetGridColumns(300.dp, fontScale = 2f, minimumSmallWidth = minimum))
        }
        assertEquals(0.dp, homeWidgetMinimumSmallWidth(defaults +
            HomeWidgetInstance("news-large", "updates", "night-large")))
    }

    @Test fun `news skins share the same small body measure while large uses the selected skin`() {
        HomeWidgetCatalog.variantsForSize("updates", HomeWidgetSize.SMALL).forEach { variant ->
            assertEquals(listOf("small"),
                homeWidgetMeasureVariants("updates", HomeWidgetSize.SMALL, variant).map { it.id })
        }
        HomeWidgetCatalog.variantsForSize("updates", HomeWidgetSize.LARGE).forEach { variant ->
            assertEquals(listOf(variant), homeWidgetMeasureVariants("updates", HomeWidgetSize.LARGE, variant))
        }
    }

    @Test fun `small news cards use their measured body without a shared 280dp floor`() {
        val measured = dimensions().copy(updatesSmallHeader = 40.dp, updatesSmallBody = 180.dp)
        listOf("basic", "storybook", "night").forEach { styleId ->
            assertEquals(40.dp, dimensions().header(HomeWidgetSize.SMALL, "updates", styleId))
            assertEquals(0.dp, dimensions().body(HomeWidgetSize.SMALL, "updates", styleId))
            assertEquals(220.dp, measured.height(HomeWidgetSize.SMALL, false, "updates", styleId))
            assertEquals(268.dp, measured.height(HomeWidgetSize.SMALL, true, "updates", styleId))
        }
    }

    @Test fun `small news height is isolated from both other small groups`() {
        val otherGroupsExpanded = dimensions(header = 72, smallBody = 300,
            compactHeader = 64, compactBody = 250).copy(updatesSmallHeader = 40.dp, updatesSmallBody = 180.dp)
        listOf("basic", "storybook", "night").forEach { styleId ->
            assertEquals(220.dp, otherGroupsExpanded.height(HomeWidgetSize.SMALL, false, "updates", styleId))
        }
        val newsExpanded = dimensions().copy(updatesSmallHeader = 72.dp, updatesSmallBody = 310.dp)
        assertEquals(382.dp, newsExpanded.height(HomeWidgetSize.SMALL, false, "updates", "basic"))
        assertEquals(430.dp, newsExpanded.height(HomeWidgetSize.SMALL, true, "updates", "night"))
        listOf("reading", "listening").forEach { typeId ->
            assertEquals(208.dp, newsExpanded.height(HomeWidgetSize.SMALL, false, typeId, "basic"))
            assertEquals(280.dp, newsExpanded.height(HomeWidgetSize.SMALL, false, typeId, "storybook"))
            assertEquals(280.dp, newsExpanded.height(HomeWidgetSize.SMALL, false, typeId, "night"))
        }
        assertEquals(153.dp, newsExpanded.height(HomeWidgetSize.LARGE, false, "listening", "basic"))
        assertEquals(48.dp, newsExpanded.height(HomeWidgetSize.LARGE, false, "updates", "basic"))
    }

    @Test fun `calendar body heights stay isolated by instance even with the same style`() {
        val first = homeWidgetBodyKey("calendar", "basic", "four-weeks")
        val second = homeWidgetBodyKey("calendar", "basic", "six-weeks")
        val dimensions = dimensions().copy(largeHeaders = mapOf("calendar" to 48.dp),
            largeBodies = mapOf(first to 308.dp, second to 412.dp))
        assertEquals(356.dp, dimensions.height(HomeWidgetSize.LARGE, false, "calendar", "basic", "four-weeks"))
        assertEquals(460.dp, dimensions.height(HomeWidgetSize.LARGE, false, "calendar", "basic", "six-weeks"))
        assertEquals(508.dp, dimensions.height(HomeWidgetSize.LARGE, true, "calendar", "basic", "six-weeks"))
        assertEquals(HomeWidgetBodyKey("reading", "basic"), homeWidgetBodyKey("reading", "basic", "reading-one"))
        assertEquals(HomeWidgetBodyKey("reading", "basic"), homeWidgetBodyKey("reading", "basic", "reading-two"))
    }
}
