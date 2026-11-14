package io.legado.app.ui.book.read.config

import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.provider.ReadTitleSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadTitleSegmentSelectionTest {
    @Test
    fun noHistoryDefaultsToCountWithoutDiscardingExistingParameters() {
        for (previousType in listOf(ReadTitleSegmentType.NONE, ReadTitleSegmentType.CHAPTER_NUMBER)) {
            val config = ReadBookConfig.Config(
                titleSegType = previousType,
                titleSegCustomType = 0,
                titleSegDistance = 7,
                titleSegFlag = "章",
                titleSegScaling = 1.2f,
            )
            val next = config.preferredCustomTitleSegmentType()
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, next)
            assertEquals(previousType, config.titleSegType)
            config.selectTitleSegmentType(next)
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, config.titleSegType)
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, config.titleSegCustomType)
            assertEquals(7, config.titleSegDistance)
            assertEquals("章", config.titleSegFlag)
            assertEquals(1.2f, config.titleSegScaling, 0f)
        }
    }

    @Test
    fun previousOrCurrentlyActiveCustomRuleTakesPriorityOverTheDefault() {
        for (custom in listOf(
            ReadTitleSegmentType.CHARACTER_COUNT,
            ReadTitleSegmentType.DELIMITER,
            ReadTitleSegmentType.REGEX,
        )) {
            val config = ReadBookConfig.Config(
                titleSegType = custom,
                titleSegCustomType = 0,
                titleSegFlag = "章",
            )
            assertEquals(custom, config.preferredCustomTitleSegmentType())
            config.selectTitleSegmentType(ReadTitleSegmentType.CHAPTER_NUMBER)
            assertEquals(custom, config.titleSegCustomType)
            assertEquals(custom, config.preferredCustomTitleSegmentType())
            config.selectTitleSegmentType(config.preferredCustomTitleSegmentType())
            assertEquals(custom, config.titleSegType)
            assertEquals("章", config.titleSegFlag)
        }
    }
}
