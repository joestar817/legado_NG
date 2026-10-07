package io.legado.app.ui.main.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HomeCalendarStateTest {
    @Test fun `Monday-first calendar covers exactly four five and six weeks`() {
        listOf(YearMonth.of(2021, 2) to 4, YearMonth.of(2026, 10) to 5,
            YearMonth.of(2026, 8) to 6).forEach { (month, count) ->
            val weeks = homeCalendarWeeks(month)
            assertEquals(count, weeks.size)
            assertTrue(weeks.all { it.size == 7 })
            assertEquals((1..month.lengthOfMonth()).toList(), weeks.flatten().filterNotNull().map { it.dayOfMonth })
            weeks.forEach { week ->
                week.forEachIndexed { index, date ->
                    if (date != null) assertEquals(DayOfWeek.of(index + 1), date.dayOfWeek)
                }
            }
        }
    }

    @Test fun `leap years and century exceptions do not create or drop dates`() {
        listOf(2000 to 29, 2024 to 29, 2025 to 28, 2100 to 28).forEach { (year, days) ->
            assertEquals(days, homeCalendarWeeks(YearMonth.of(year, 2)).flatten().filterNotNull().size)
        }
    }

    @Test fun `December and January navigation clears the previous selection`() {
        val today = LocalDate.of(2026, 12, 31)
        val start = HomeCalendarSelection(YearMonth.from(today), today)
        val next = updateHomeCalendarSelection(start, HomeCalendarAction.ShowMonth(start.month.plusMonths(1)), today)
        assertEquals(YearMonth.of(2027, 1), next.month)
        assertNull(next.selectedDate)
        assertEquals(start.month, updateHomeCalendarSelection(next,
            HomeCalendarAction.ShowMonth(next.month.minusMonths(1)), today).month)
        assertEquals(start, updateHomeCalendarSelection(next, HomeCalendarAction.Today, today))
    }

    @Test fun `selecting another day never changes today or another instance`() {
        val today = LocalDate.of(2026, 10, 5)
        val state = HomeCalendarState(today = today)
        val selections = mapOf("first" to HomeCalendarSelection(YearMonth.of(2026, 8)))
        val first = state.selectionFor("first", selections)
        val selected = updateHomeCalendarSelection(first, HomeCalendarAction.SelectDate(LocalDate.of(2026, 8, 30)), today)
        assertEquals(YearMonth.of(2026, 8), selected.month)
        assertEquals(LocalDate.of(2026, 8, 30), selected.selectedDate)
        assertEquals(today, state.today)
        assertEquals(HomeCalendarSelection(YearMonth.of(2026, 10)), state.selectionFor("second", selections))
        assertEquals(selected, updateHomeCalendarSelection(selected,
            HomeCalendarAction.SelectDate(today), today))
    }

    @Test fun `missing past records remain unknown and future records are hidden`() {
        val today = LocalDate.of(2026, 10, 5)
        val month = YearMonth.from(today)
        val state = HomeCalendarState(today, mapOf(
            today.toEpochDay() to 40_000L,
            today.plusDays(1).toEpochDay() to 900_000L,
        ), setOf(month))
        assertNull(state.readTime(today.minusDays(1)))
        assertNull(state.readTime(today.plusDays(1)))
        assertEquals(40_000L, state.readTime(today))
        assertEquals(HomeCalendarSummary(40_000L, 1), state.summary(month))
        assertNull(state.summary(month.minusMonths(1)))
        assertNull(state.copy(dailyReadTimes = emptyMap()).summary(month))
    }

    @Test fun `month totals count only positive reading days in that month`() {
        val today = LocalDate.of(2026, 10, 5)
        val state = HomeCalendarState(today, mapOf(
            LocalDate.of(2026, 9, 30).toEpochDay() to 9_000_000L,
            LocalDate.of(2026, 10, 1).toEpochDay() to 3_600_000L,
            LocalDate.of(2026, 10, 2).toEpochDay() to 0L,
            today.toEpochDay() to 35_000L,
        ), setOf(YearMonth.from(today)))
        assertEquals(HomeCalendarSummary(3_635_000L, 2), state.summary(YearMonth.from(today)))
        assertEquals(0L, state.readTime(LocalDate.of(2026, 10, 2)))
    }

    @Test fun `minute conversion retains unknown and distinguishes positive sub-minute sessions`() {
        assertNull(homeCalendarMinutes(null))
        assertEquals(0L, homeCalendarMinutes(59_999L))
        assertEquals(1L, homeCalendarMinutes(60_000L))
        assertEquals(90L, homeCalendarMinutes(5_400_000L))
    }

    @Test fun `retry keeps the selected month and last successful snapshot`() {
        val date = LocalDate.of(2026, 10, 5)
        val selection = HomeCalendarSelection(YearMonth.from(date), date.minusDays(1))
        val state = HomeCalendarState(date, mapOf(date.toEpochDay() to 60_000L), setOf(selection.month))
        assertEquals(selection, updateHomeCalendarSelection(selection, HomeCalendarAction.Retry, date))
        assertEquals(state.summary(selection.month), state.copy(loadFailed = true).summary(selection.month))
        assertEquals(state.readTime(date), state.copy(loadFailed = true).readTime(date))
    }

    @Test fun `calendar exposes three large skins with one storybook large default`() {
        val calendar = HomeWidgetCatalog.type("calendar")!!
        assertEquals(listOf("large", "story-large", "night-large"), calendar.variants.map { it.id })
        assertEquals(listOf("basic", "storybook", "night"), calendar.variants.map { it.styleId })
        assertTrue(calendar.variants.all { it.size == HomeWidgetSize.LARGE })
        calendar.variants.forEach { variant ->
            assertNull(HomeWidgetCatalog.variantForSize("calendar", variant.id, HomeWidgetSize.SMALL))
            assertEquals(variant, HomeWidgetCatalog.variantForSize("calendar", variant.id, HomeWidgetSize.LARGE))
        }
        assertEquals(listOf(HomeWidgetInstance("default_calendar", "calendar", "story-large")),
            HomeWidgetCatalog.defaults().filter { it.typeId == "calendar" })
        val widgets = HomeWidgetCatalog.defaults() + calendar.variants.mapIndexed { index, variant ->
            HomeWidgetInstance("calendar-$index", "calendar", variant.id)
        }
        assertEquals(widgets, HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(widgets)))
        listOf("small", "story-small", "night-small").forEach { variantId ->
            assertNull(changeHomeWidgetVariant(widgets, "calendar-0", variantId))
            assertTrue(runCatching { HomeWidgetLayoutCodec.decode(HomeWidgetLayoutCodec.encode(
                listOf(HomeWidgetInstance("calendar", "calendar", variantId)),
            )) }.isFailure)
        }
    }
}
