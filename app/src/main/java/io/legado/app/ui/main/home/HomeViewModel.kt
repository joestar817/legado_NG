package io.legado.app.ui.main.home

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.room.withTransaction
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.bookUpdateChapterCount
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.ui.main.BookNewsRefreshState
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

internal data class HomeUiState(
    val widgets: List<HomeWidgetInstance> = emptyList(),
    val draft: List<HomeWidgetInstance>? = null,
    val layoutReadFailed: Boolean = false,
    val singleEditId: String? = null,
) {
    val pendingEdit: Boolean get() = draft != null
    val editing: Boolean get() = pendingEdit && singleEditId == null
    val displayedWidgets: List<HomeWidgetInstance> get() = draft ?: widgets
}

class HomeViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(loadState())
    internal val state = mutableState.asStateFlow()
    private val mutableReadingState = MutableStateFlow(HomeReadingState())
    internal val readingState = mutableReadingState.asStateFlow()
    private val mutableCalendarState = MutableStateFlow(HomeCalendarState())
    internal val calendarState = mutableCalendarState.asStateFlow()
    private val mutableCalendarSelections = MutableStateFlow(loadCalendarSelections())
    internal val calendarSelections = mutableCalendarSelections.asStateFlow()
    private val calendarRefresh = MutableStateFlow(0L)
    private val mutableUpdatesState = MutableStateFlow(HomeUpdatesState())
    internal val updatesState = mutableUpdatesState.asStateFlow()
    private val updatesRefresh = MutableStateFlow(0L)
    private val widgetPreviewTypes = MutableStateFlow<Set<String>>(emptySet())

    /** A drawer preview may read local records without joining the layout or checking book sources. */
    internal fun setWidgetPreviewTypes(typeIds: Set<String>) {
        widgetPreviewTypes.value = typeIds.filterTo(mutableSetOf()) { HomeWidgetCatalog.type(it) != null }
    }

    internal fun retryUpdates() {
        if (!mutableState.value.editing) updatesRefresh.value++
    }

    internal fun updateUpdatesCheckState(check: BookNewsRefreshState) {
        mutableUpdatesState.update { state ->
            state.copy(checking = check.checking, lastCheckedAt = check.lastCheckedAt)
        }
    }

    /** Every card and preview consumes one snapshot of today's discovered updates. */
    internal suspend fun observeUpdates() = coroutineScope {
        updateUpdatesDate(LocalDate.now())
        launch {
            while (isActive) {
                delay(30_000L)
                updateUpdatesDate(LocalDate.now())
            }
        }
        val visible = combine(mutableState, widgetPreviewTypes) { state, previewTypes ->
            "updates" in previewTypes || state.displayedWidgets.any { it.typeId == "updates" }
        }.distinctUntilChanged()
        val dates = mutableUpdatesState.map { it.today }.distinctUntilChanged()
        combine(visible, dates,
            appDb.invalidationTracker.createFlow("books", "bookUpdates").conflate(), updatesRefresh,
        ) { shown, day, _, _ -> day.takeIf { shown } }
            .collect { day -> day?.let { refreshUpdatesState(it) } }
    }

    private fun updateUpdatesDate(today: LocalDate) {
        mutableUpdatesState.update { state ->
            // A new day resets the dated books without losing an ongoing directory check.
            if (state.today == today) state else state.copy(today = today, books = emptyList(),
                loaded = false, loadFailed = false)
        }
    }

    private suspend fun refreshUpdatesState(day: LocalDate) {
        try {
            val books = withContext(IO) {
                appDb.withTransaction {
                    val dao = appDb.bookUpdateDao
                    val currentBooks = dao.getBooksForDay(day.toEpochDay())
                        .filter { !it.isLocal && !it.isNotShelf }
                        .associateBy { it.bookUrl to it.origin }
                    dao.getForDay(day.toEpochDay()).mapNotNull { update ->
                        currentBooks[update.bookUrl to update.origin]?.let { book ->
                            HomeUpdateBook(book, bookUpdateChapterCount(update), update.foundAt)
                        }
                    }
                }
            }
            updateUpdatesDate(LocalDate.now())
            mutableUpdatesState.update { state ->
                if (state.today == day) state.copy(books = books, loaded = true, loadFailed = false)
                else state
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateUpdatesDate(LocalDate.now())
            mutableUpdatesState.update { state ->
                if (state.today == day) state.copy(loadFailed = true) else state
            }
            AppLog.put("加载首页今日书讯失败", error)
        }
    }

    /** The visible cards, measurement probes and previews share instance-owned month state. */
    internal fun onCalendarAction(id: String, action: HomeCalendarAction) {
        if (mutableState.value.editing || mutableState.value.widgets.none { it.id == id && it.typeId == "calendar" }) return
        if (action is HomeCalendarAction.ShowMonth && action.month.year !in 1..9999) return
        if (action == HomeCalendarAction.Retry) {
            calendarRefresh.value++
            return
        }
        val calendar = mutableCalendarState.value
        val current = calendar.selectionFor(id, mutableCalendarSelections.value)
        val next = updateHomeCalendarSelection(current, action, calendar.today)
        mutableCalendarSelections.value = mutableCalendarSelections.value + (id to next)
        savedState[CALENDAR_KEY] = Bundle().apply {
            mutableCalendarSelections.value.forEach { (key, selection) ->
                putStringArray(key, arrayOf(selection.month.toString(), selection.selectedDate?.toString().orEmpty()))
            }
        }
        if (action == HomeCalendarAction.Today) calendarRefresh.value++
    }

    private fun loadCalendarSelections(): Map<String, HomeCalendarSelection> {
        val bundle = savedState.get<Bundle>(CALENDAR_KEY) ?: return emptyMap()
        return bundle.keySet().mapNotNull { key ->
            runCatching {
                val values = bundle.getStringArray(key) ?: return@mapNotNull null
                key to HomeCalendarSelection(YearMonth.parse(values[0]),
                    values.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(LocalDate::parse))
            }.getOrNull()
        }.toMap()
    }

    /** Resumed-only subscription also refreshes the local date after midnight or zone changes. */
    internal suspend fun observeCalendarRecords() = coroutineScope {
        mutableCalendarState.value = mutableCalendarState.value.copy(today = LocalDate.now())
        launch {
            while (isActive) {
                delay(30_000L)
                val today = LocalDate.now()
                if (today != mutableCalendarState.value.today) {
                    mutableCalendarState.value = mutableCalendarState.value.copy(today = today)
                }
            }
        }
        val months = combine(mutableState, mutableCalendarSelections,
            mutableCalendarState.map { it.today }.distinctUntilChanged(), widgetPreviewTypes) { state, selections, today, previewTypes ->
            state.displayedWidgets.filter { it.typeId == "calendar" }.map {
                selections[it.id]?.month ?: YearMonth.from(today)
            }.toSet().let { displayed ->
                if ("calendar" in previewTypes) displayed + YearMonth.from(today) else displayed
            }
        }.distinctUntilChanged()
        combine(months, appDb.invalidationTracker.createFlow("dailyReadingRecords").conflate(), calendarRefresh) {
            selected, _, _ -> selected
        }
            .collect { selected -> refreshCalendarState(selected) }
    }

    private suspend fun refreshCalendarState(months: Set<YearMonth>) {
        if (months.isEmpty()) return
        try {
            val records = withContext(IO) {
                appDb.withTransaction {
                    months.flatMap { month ->
                        appDb.dailyReadingRecordDao.getBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
                    }.associate { it.epochDay to it.readTime }
                }
            }
            val old = mutableCalendarState.value
            // Bound data to displayed months; failures below retain the last successful snapshot.
            mutableCalendarState.value = old.copy(dailyReadTimes = records,
                loadedMonths = months, loadFailed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableCalendarState.value = mutableCalendarState.value.copy(loadFailed = true)
            AppLog.put("加载首页阅读日历失败", error)
        }
    }

    /** Collected only while the home page is resumed; each collection reads an initial snapshot. */
    internal suspend fun observeReadingRecords() {
        appDb.invalidationTracker.createFlow("readRecord").conflate().collect {
            refreshReadingState()
        }
    }

    private suspend fun refreshReadingState() {
        try {
            val snapshot = withContext(IO) {
                appDb.withTransaction {
                    val dao = appDb.readRecordDao
                    HomeReadingState(
                        totalReadTime = dao.homeTotalReadTime,
                        recordCount = dao.recordCount,
                        recentRecords = dao.recentForHome.map { record ->
                            HomeReadingRecord(record.bookName, record.readTime, record.lastRead)
                        },
                        loaded = true,
                    )
                }
            }
            mutableReadingState.value = snapshot
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Keep the last real snapshot instead of replacing historical data with zeroes.
            mutableReadingState.value = mutableReadingState.value.copy(loadFailed = true)
            AppLog.put("加载首页阅读足迹失败", error)
        }
    }

    private fun loadState(): HomeUiState = runCatching {
        val stored = getApplication<Application>().getPrefString(LAYOUT_KEY)
        val widgets = stored?.let(HomeWidgetLayoutCodec::decode) ?: HomeWidgetCatalog.defaults()
        val draft = savedState.get<String>(DRAFT_KEY)?.let(HomeWidgetLayoutCodec::decode)
        HomeUiState(widgets, draft, singleEditId = if (draft != null) savedState[SINGLE_EDIT_KEY] else null)
    }.getOrElse { HomeUiState(layoutReadFailed = true) }

    internal fun beginEditing() {
        beginWidgetEditing(null)
    }

    internal fun beginSingleEditing(id: String) {
        beginWidgetEditing(id)
    }

    private fun beginWidgetEditing(singleEditId: String?) {
        val current = mutableState.value
        val updated = beginHomeWidgetEditing(current, singleEditId) ?: return
        if (updated != current) updateEditingState(updated)
    }

    internal fun cancelEditing(): Boolean {
        if (!mutableState.value.pendingEdit) return false
        savedState.remove<String>(DRAFT_KEY)
        savedState.remove<String>(SINGLE_EDIT_KEY)
        mutableState.value = cancelHomeWidgetEditing(mutableState.value)
        return true
    }

    internal fun saveEditing() {
        val draft = mutableState.value.draft ?: return
        persistLayout(draft)
    }

    private fun persistLayout(widgets: List<HomeWidgetInstance>) {
        getApplication<Application>().putPrefString(LAYOUT_KEY, HomeWidgetLayoutCodec.encode(widgets))
        savedState.remove<String>(DRAFT_KEY)
        savedState.remove<String>(SINGLE_EDIT_KEY)
        mutableState.value = HomeUiState(widgets = widgets)
    }

    internal fun addWidget(typeId: String, variantId: String): Boolean {
        val updated = addOrReplaceHomeWidget(mutableState.value, typeId, variantId, UUID.randomUUID().toString())
            ?: return false
        applyWidgetMutation(updated)
        return true
    }

    internal fun saveVariant(id: String, variantId: String): Boolean {
        val updated = saveHomeWidgetVariant(mutableState.value, id, variantId) ?: return false
        persistLayout(updated.widgets)
        return true
    }

    internal fun saveSuite(styleId: String): Boolean {
        val updated = saveHomeWidgetSuite(mutableState.value, styleId) ?: return false
        persistLayout(updated.widgets)
        return true
    }

    private fun applyWidgetMutation(updated: HomeUiState) {
        val draft = updated.draft
        if (draft != null) updateDraft(draft) else persistLayout(updated.widgets)
    }

    internal fun removeWidget(id: String) {
        val updated = removeHomeWidget(mutableState.value, id) ?: return
        applyWidgetMutation(updated)
    }

    internal fun moveWidget(fromId: String, toId: String) {
        val updated = moveHomeWidgetState(mutableState.value, fromId, toId) ?: return
        applyWidgetMutation(updated)
    }

    internal fun resetLayout() {
        getApplication<Application>().putPrefString(LAYOUT_KEY, null)
        savedState.remove<String>(DRAFT_KEY)
        savedState.remove<String>(SINGLE_EDIT_KEY)
        mutableState.value = HomeUiState(widgets = HomeWidgetCatalog.defaults())
    }

    private fun updateDraft(widgets: List<HomeWidgetInstance>) {
        updateEditingState(mutableState.value.copy(draft = widgets))
    }

    private fun updateEditingState(state: HomeUiState) {
        savedState[DRAFT_KEY] = HomeWidgetLayoutCodec.encode(requireNotNull(state.draft))
        val singleEditId = state.singleEditId
        if (singleEditId == null) savedState.remove<String>(SINGLE_EDIT_KEY)
        else savedState[SINGLE_EDIT_KEY] = singleEditId
        mutableState.value = state
    }

    private companion object {
        const val LAYOUT_KEY = "homeWidgetLayout"
        const val DRAFT_KEY = "homeWidgetDraft"
        const val SINGLE_EDIT_KEY = "homeWidgetSingleEditId"
        const val CALENDAR_KEY = "homeCalendarSelections"
    }
}
