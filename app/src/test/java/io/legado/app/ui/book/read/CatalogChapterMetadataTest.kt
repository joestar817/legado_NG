package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogChapterMetadataTest {
    @Test
    fun reportedBareCountIsRemovedEvenWhenStoredCountDiffers() {
        assertEquals(CatalogChapterMetadata("2411字", null), catalogChapterMetadata("2345", "2411字", cached = true))
        assertEquals(CatalogChapterMetadata("2085字", null), catalogChapterMetadata("2085", "2085字", cached = true))
    }

    @Test
    fun uncachedChaptersNeverShowCountsEvenWhenTheStoredFieldIsPresent() {
        for (count in listOf("2129", "2771", "2138", "2166", "2263")) {
            val uncached = catalogChapterMetadata(count, null)
            assertEquals(CatalogChapterMetadata(null, null), uncached)
            assertEquals(uncached, catalogChapterMetadata(count, "${count}字"))
            assertEquals(uncached, catalogChapterMetadata(count, ""))
            assertEquals(CatalogChapterMetadata("${count}字", null),
                catalogChapterMetadata(count, "${count}字", cached = true))
        }
    }

    @Test
    fun sourceCountNeverSuppliesAMissingLocalCount() {
        for (value in listOf("2345", "2345字", "字数：2345", "1.2万字", "2,345字")) {
            for (cached in listOf(false, true)) {
                assertEquals(CatalogChapterMetadata(null, null), catalogChapterMetadata(value, null, cached))
            }
        }
    }

    @Test
    fun timeAndOtherMetadataSurviveRemovingSourceCount() {
        assertEquals(
            CatalogChapterMetadata(null, "2026-10-08 20:55  VIP"),
            catalogChapterMetadata("更新时间：2026-10-08 20:55  章节字数：2345字  VIP", null),
        )
    }

    @Test
    fun sourceCountIsRemovedWhenTheStoredCountTakesPrecedence() {
        assertEquals(
            CatalogChapterMetadata("2411字", "2026/10/08"),
            catalogChapterMetadata("时间：2026/10/08，字数：2345", "2411字", cached = true),
        )
    }

    @Test
    fun plainDateAndTimestampAreNeverCounts() {
        for (tag in listOf("2026-10-08", "20:55", "20261008", "1791464100", "1791464100000")) {
            assertEquals(tag, catalogChapterMetadata(tag, "2345字").description)
            assertEquals(CatalogChapterMetadata(null, tag), catalogChapterMetadata(tag, null))
        }
    }

    @Test
    fun unknownSourceInformationIsRetained() {
        val tag = "番外 · 已修订 · 赠送章节"
        assertEquals(tag, catalogChapterMetadata(tag, "123字").description)
    }

    @Test
    fun countWithUnitAlongsideDateMovesOutOfTheDescription() {
        assertEquals(CatalogChapterMetadata(null, "2026-10-08"),
            catalogChapterMetadata("2026-10-08 · 2345字", null))
        assertEquals("第2345字有修订", catalogChapterMetadata("第2345字有修订", null).description)
    }

    @Test
    fun separatorsAroundRemovedCountAreCleanedWithoutDroppingOtherFields() {
        assertEquals("VIP | 已修订", catalogChapterMetadata("VIP | 字数：2345字 | 已修订", null).description)
    }

    @Test
    fun blankValuesDoNotCreateAnEmptySecondLine() {
        assertEquals(CatalogChapterMetadata(null, null), catalogChapterMetadata("  ", " "))
        assertNull(catalogChapterMetadata("字数：2345字", "2345字").description)
    }
}
