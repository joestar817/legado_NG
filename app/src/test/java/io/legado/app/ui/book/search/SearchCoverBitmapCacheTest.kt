package io.legado.app.ui.book.search

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SearchCoverBitmapCacheTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun reusesIdenticalSourceAndSizeWithoutChangingPixels() {
        val source = patternedBitmap(Color.RED, Color.BLUE)
        val firstDrawable = BitmapDrawable(resources, source)
        val secondDrawable = BitmapDrawable(resources, source)
        val expected = firstDrawable.toBitmap(18, 24)
        val cache = SearchCoverBitmapCache()

        val first = cache.toImageBitmap(firstDrawable, 18, 24).asAndroidBitmap()
        val again = cache.toImageBitmap(secondDrawable, 18, 24).asAndroidBitmap()

        assertSame(first, again)
        assertPixelsEqual(expected, first)
    }

    @Test
    fun differentSourcesAndSizesDoNotShareScaledBitmaps() {
        val cache = SearchCoverBitmapCache()
        val firstDrawable = BitmapDrawable(resources, patternedBitmap(Color.RED, Color.BLUE))
        val secondDrawable = BitmapDrawable(resources, patternedBitmap(Color.GREEN, Color.YELLOW))
        val first = cache.toImageBitmap(firstDrawable, 18, 24).asAndroidBitmap()
        val otherSource = cache.toImageBitmap(secondDrawable, 18, 24).asAndroidBitmap()
        val otherSize = cache.toImageBitmap(firstDrawable, 15, 20).asAndroidBitmap()

        assertNotSame(first, otherSource)
        assertNotSame(first, otherSize)
        assertPixelsEqual(secondDrawable.toBitmap(18, 24), otherSource)
        assertPixelsEqual(firstDrawable.toBitmap(15, 20), otherSize)
    }

    @Test
    fun nonBitmapDrawablesKeepTheOriginalConversion() {
        val drawable = ColorDrawable(Color.MAGENTA)
        val actual = SearchCoverBitmapCache().toImageBitmap(drawable, 18, 24).asAndroidBitmap()
        assertPixelsEqual(drawable.toBitmap(18, 24), actual)
    }

    @Test
    fun evictsScaledImagesWhenTheCacheExceedsItsBudget() {
        val drawable = BitmapDrawable(resources, patternedBitmap(Color.RED, Color.BLUE))
        val cache = SearchCoverBitmapCache()
        val first = cache.toImageBitmap(drawable, 750, 750).asAndroidBitmap()
        cache.toImageBitmap(drawable, 751, 751)
        val afterEviction = cache.toImageBitmap(drawable, 750, 750).asAndroidBitmap()
        assertNotSame(first, afterEviction)
        assertPixelsEqual(first, afterEviction)
    }

    private fun patternedBitmap(first: Int, second: Int): Bitmap =
        Bitmap.createBitmap(12, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(first)
            setPixel(4, 6, second)
        }

    private fun assertPixelsEqual(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(actual.width * actual.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        assertArrayEquals(expectedPixels, actualPixels)
    }
}
