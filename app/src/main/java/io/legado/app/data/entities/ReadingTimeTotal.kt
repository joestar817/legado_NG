package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/** Historical footprint total plus subsequent elapsed intervals, independent of per-book sums. */
@Entity(tableName = "readingTimeTotal")
data class ReadingTimeTotal(
    @PrimaryKey
    @SerializedName("id")
    val id: Int = 1,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("readTime")
    val readTime: Long = 0L,
)
