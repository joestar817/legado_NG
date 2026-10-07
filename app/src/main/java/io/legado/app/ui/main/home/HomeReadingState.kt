package io.legado.app.ui.main.home

/** A transient snapshot of the existing reading records, shared by every widget size and skin. */
internal data class HomeReadingState(
    val totalReadTime: Long = 0L,
    val recordCount: Int = 0,
    val recentRecords: List<HomeReadingRecord> = emptyList(),
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
)

internal data class HomeReadingRecord(
    val bookName: String,
    val readTime: Long,
    val lastRead: Long,
)
