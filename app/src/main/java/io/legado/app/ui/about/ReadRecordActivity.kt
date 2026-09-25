package io.legado.app.ui.about

import android.os.Bundle
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getInt
import io.legado.app.utils.putInt
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** 阅读记录。页面渲染使用 Compose，数据与跳转行为沿用原实现。 */
class ReadRecordActivity : BaseActivity<ComposeActivityBinding>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val bindNgToolbarMenu: Boolean = false

    private var items by mutableStateOf<List<ReadRecordUiItem>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var totalReadTime by mutableLongStateOf(0L)
    private var recordCount by mutableIntStateOf(0)
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var recordEnabled by mutableStateOf(AppConfig.enableReadRecord)
    private var deleting by mutableStateOf(false)
    private var clearAllDialogVisible by mutableStateOf(false)
    private var loadJob: Job? = null
    private var sortMode by mutableIntStateOf(LocalConfig.getInt(READ_RECORD_SORT_KEY))
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initContent()
        onBackPressedDispatcher.addCallback(this) {
            if (searchExpanded) closeSearch() else finish()
        }
        loadData()
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                ReadRecordScreen(
                    items = items,
                    totalReadTime = formatDuring(totalReadTime),
                    recordCount = recordCount,
                    query = query,
                    searchExpanded = searchExpanded,
                    sortMode = sortMode,
                    recordEnabled = recordEnabled,
                    deleting = deleting,
                    clearAllDialogVisible = clearAllDialogVisible,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchExpandedChange = { expanded ->
                        if (expanded) searchExpanded = true else closeSearch()
                    },
                    onQueryChange = {
                        query = it
                        loadData()
                    },
                    onSortChange = ::changeSort,
                    onRecordEnabledChange = ::changeRecordEnabled,
                    onClearAllRequest = { clearAllDialogVisible = true },
                    onClearAllDismiss = { clearAllDialogVisible = false },
                    onClearAllConfirm = ::clearAll,
                    onItemClick = ::openItem,
                    onDeleteConfirm = ::deleteItems,
                )
            }
        }
    }

    private fun closeSearch() {
        searchExpanded = false
        if (query.isNotEmpty()) {
            query = ""
            loadData()
        }
    }

    private fun changeSort(mode: Int) {
        if (sortMode == mode) return
        sortMode = mode
        LocalConfig.putInt(READ_RECORD_SORT_KEY, mode)
        loadData()
    }

    private fun changeRecordEnabled(enabled: Boolean) {
        recordEnabled = enabled
        AppConfig.enableReadRecord = enabled
    }

    private fun loadData() {
        loadJob?.cancel()
        val searchKey = query
        val currentSort = sortMode
        loadJob = lifecycleScope.launch {
            val result = withContext(IO) {
                val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
                val fullDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentYear = SimpleDateFormat("yyyy", Locale.getDefault())
                val thisYear = currentYear.format(System.currentTimeMillis())
                val records = appDb.readRecordDao.search(searchKey).let { records ->
                    when (currentSort) {
                        SORT_READING_DURATION -> records.sortedByDescending { it.readTime }
                        SORT_LAST_READ -> records.sortedByDescending { it.lastRead }
                        else -> records.sortedWith { first, second ->
                            first.bookName.cnCompare(second.bookName)
                        }
                    }
                }
                val booksByName = if (records.isEmpty()) {
                    emptyMap()
                } else {
                    appDb.bookDao.findByName(*records.map { it.bookName }.toTypedArray())
                        .groupBy { it.name }
                }
                records.map { record ->
                    ReadRecordUiItem(
                        record = record,
                        book = booksByName[record.bookName]?.firstOrNull(),
                        // Records are grouped by title; only show an author for an unambiguous match.
                        author = booksByName[record.bookName]?.singleOrNull()?.author.orEmpty(),
                        durationText = formatDuring(record.readTime),
                        lastReadText = if (record.lastRead > 0) {
                            if (currentYear.format(record.lastRead) == thisYear) {
                                dateFormat.format(record.lastRead)
                            } else {
                                fullDateFormat.format(record.lastRead)
                            }
                        } else {
                            ""
                        },
                    )
                }.let { Triple(it, appDb.readRecordDao.allTime, appDb.readRecordDao.recordCount) }
            }
            items = result.first
            totalReadTime = result.second
            recordCount = result.third
        }
    }

    private fun openItem(item: ReadRecordUiItem) {
        lifecycleScope.launch {
            val book = item.book ?: withContext(IO) {
                appDb.bookDao.findByName(item.record.bookName).firstOrNull()
            }
            if (book == null) {
                SearchActivity.start(this@ReadRecordActivity, item.record.bookName)
            } else {
                startActivityForBook(book)
            }
        }
    }

    private fun clearAll() {
        clearAllDialogVisible = false
        lifecycleScope.launch {
            withContext(IO) { appDb.readRecordDao.clear() }
            loadData()
        }
    }

    private fun deleteItems(bookNames: List<String>, onComplete: () -> Unit) {
        if (deleting || bookNames.isEmpty()) return
        deleting = true
        lifecycleScope.launch {
            try {
                withContext(IO) {
                    appDb.withTransaction {
                        // Keep one atomic operation while respecting SQLite's bind limit.
                        bookNames.distinct().chunked(900).forEach(appDb.readRecordDao::deleteByNames)
                    }
                }
                onComplete()
                loadData()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage ?: getString(R.string.read_record_delete_failed))
            } finally {
                deleting = false
            }
        }
    }

    private fun formatDuring(mss: Long): String {
        val days = mss / (1000 * 60 * 60 * 24)
        val hours = mss % (1000 * 60 * 60 * 24) / (1000 * 60 * 60)
        val minutes = mss % (1000 * 60 * 60) / (1000 * 60)
        val seconds = mss % (1000 * 60) / 1000
        val dayText = if (days > 0) "${days}天" else ""
        val hourText = if (hours > 0) "${hours}小时" else ""
        val minuteText = if (minutes > 0) "${minutes}分钟" else ""
        val secondText = if (seconds > 0) "${seconds}秒" else ""
        return "$dayText$hourText$minuteText$secondText".ifBlank { "0秒" }
    }

    private companion object {
        const val READ_RECORD_SORT_KEY = "readRecordSort"
        const val SORT_READING_DURATION = 1
        const val SORT_LAST_READ = 2
    }
}
