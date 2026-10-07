package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.google.gson.annotations.SerializedName

/** A confirmed directory growth found locally for an existing shelf book, never inferred history. */
@Entity(
    tableName = "bookUpdates",
    primaryKeys = ["epochDay", "bookUrl", "origin"],
    indices = [Index(value = ["bookUrl", "origin", "epochDay"])],
)
data class BookUpdate(
    @SerializedName("epochDay") val epochDay: Long,
    @SerializedName("bookUrl") val bookUrl: String,
    @SerializedName("origin") val origin: String,
    @SerializedName("latestChapterTitle") val latestChapterTitle: String?,
    @SerializedName("foundAt") val foundAt: Long,
    @SerializedName("totalChapterNum") val totalChapterNum: Int,
    @ColumnInfo(defaultValue = "NULL")
    @SerializedName("baselineChapterNum") val baselineChapterNum: Int? = null,
)

/** Null includes pre-counting records; an unknown day is never presented as zero chapters. */
internal fun bookUpdateChapterCount(record: BookUpdate): Int? {
    val baseline = record.baselineChapterNum ?: return null
    return if (baseline > 0 && record.totalChapterNum > baseline) record.totalChapterNum - baseline else null
}

/** A higher response advances the same day while preserving its first baseline, including null. */
internal fun mergeBookUpdateIfNewer(previous: BookUpdate?, incoming: BookUpdate): BookUpdate? {
    if (previous == null) return incoming
    if (previous.epochDay != incoming.epochDay || previous.bookUrl != incoming.bookUrl ||
        previous.origin != incoming.origin || incoming.totalChapterNum <= previous.totalChapterNum) return null
    return incoming.copy(baselineChapterNum = previous.baselineChapterNum)
}
