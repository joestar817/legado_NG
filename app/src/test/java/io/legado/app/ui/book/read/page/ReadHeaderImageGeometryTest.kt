package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadHeaderImageGeometryTest {
    @Test
    fun bothDecorationsShareAvailableHeightWithoutConsumingTheReservedBody() {
        assertEquals(300 to 200, readDecorationImageHeights(600, 400, 500))
        assertEquals(0 to 250, readDecorationImageHeights(0, 600, 250))
        assertEquals(250 to 0, readDecorationImageHeights(600, 0, 250))
        assertEquals(100 to 200, readDecorationImageHeights(100, 200, 500))
        assertEquals(0 to 0, readDecorationImageHeights(600, 400, 0))
        assertEquals(0 to 0, readDecorationImageHeights(-1, -1, -1))
        val huge = readDecorationImageHeights(Int.MAX_VALUE, Int.MAX_VALUE, 1000)
        assertEquals(500 to 500, huge)
    }

    @Test
    fun invalidDimensionsDoNotReserveBodySpace() {
        listOf(
            intArrayOf(0, 1920, 540, 600),
            intArrayOf(-1, 1920, 540, 600),
            intArrayOf(1080, 0, 540, 600),
            intArrayOf(1080, -1, 540, 600),
            intArrayOf(1080, 1920, 0, 600),
            intArrayOf(1080, 1920, Int.MIN_VALUE, 600),
            intArrayOf(1080, 1920, 540, 0),
            intArrayOf(1080, 1920, 540, -1),
        ).forEach { dimensions ->
            assertEquals(dimensions.contentToString(), 0, readHeaderImageHeight(
                dimensions[0], dimensions[1], dimensions[2], dimensions[3],
            ))
        }
    }

    @Test
    fun bannerUsesWholeImageAspectRatioAtDifferentViewportWidths() {
        assertEquals(304, readHeaderImageHeight(1080, 1920, 540, 1000))
        assertEquals(152, readHeaderImageHeight(540, 1920, 540, 1000))
        assertEquals(540, readHeaderImageHeight(1920, 1920, 540, 1000))
    }

    @Test
    fun tallImagesRespectAvailableHeight() {
        assertEquals(400, readHeaderImageHeight(600, 100, 1000, 400))
        assertEquals(1, readHeaderImageHeight(600, 100, 1000, 1))
    }

    @Test
    fun positiveSubpixelImagesKeepOnePixelWithoutOverflow() {
        assertEquals(1, readHeaderImageHeight(1, Int.MAX_VALUE, 1, 400))
        assertEquals(Int.MAX_VALUE, readHeaderImageHeight(
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
        ))
        assertEquals(400, readHeaderImageHeight(Int.MAX_VALUE, 1, Int.MAX_VALUE, 400))
    }
}
