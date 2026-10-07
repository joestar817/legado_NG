package io.legado.app.ui.main.home

import java.time.LocalDate
import java.time.YearMonth

internal data class HomeCalendarSelection(
    val month: YearMonth,
    val selectedDate: LocalDate? = null,
)

internal data class HomeCalendarState(
    val today: LocalDate = LocalDate.now(),
    val dailyReadTimes: Map<Long, Long> = emptyMap(),
    val loadedMonths: Set<YearMonth> = emptySet(),
    val loadFailed: Boolean = false,
)

internal sealed interface HomeCalendarAction {
    data class ShowMonth(val month: YearMonth) : HomeCalendarAction
    data class SelectDate(val date: LocalDate) : HomeCalendarAction
    data object Today : HomeCalendarAction
    data object Retry : HomeCalendarAction
}

internal fun HomeCalendarState.selectionFor(
    id: String,
    selections: Map<String, HomeCalendarSelection>,
): HomeCalendarSelection = selections[id] ?: HomeCalendarSelection(YearMonth.from(today))

/** Monday is column zero; outside-month cells are blank, without inventing adjacent-day data. */
internal fun homeCalendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val count = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return List(count) { index ->
        (index - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }.chunked(7)
}

/** A missing row is unknown, including dates before daily collection was introduced. */
internal fun HomeCalendarState.readTime(date: LocalDate): Long? =
    if (date > today) null else dailyReadTimes[date.toEpochDay()]?.coerceAtLeast(0L)

internal data class HomeCalendarSummary(val readTime: Long, val readingDays: Int)

internal fun HomeCalendarState.summary(month: YearMonth): HomeCalendarSummary? {
    if (month !in loadedMonths) return null
    val times = (1..month.lengthOfMonth()).mapNotNull { readTime(month.atDay(it)) }
    if (times.isEmpty()) return null
    return HomeCalendarSummary(times.sum(), times.count { it > 0L })
}

internal fun updateHomeCalendarSelection(
    current: HomeCalendarSelection,
    action: HomeCalendarAction,
    today: LocalDate,
): HomeCalendarSelection = when (action) {
    is HomeCalendarAction.ShowMonth -> HomeCalendarSelection(action.month)
    is HomeCalendarAction.SelectDate -> if (YearMonth.from(action.date) == current.month)
        current.copy(selectedDate = action.date) else current
    HomeCalendarAction.Today -> HomeCalendarSelection(YearMonth.from(today), today)
    HomeCalendarAction.Retry -> current
}

/** Null stays unknown; a positive sub-minute session must never be rendered as zero. */
internal fun homeCalendarMinutes(readTime: Long?): Long? = readTime?.coerceAtLeast(0L)?.div(60_000L)
