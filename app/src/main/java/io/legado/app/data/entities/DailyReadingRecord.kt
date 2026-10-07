package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/** Local calendar dates observed by the daily tracker; existing cumulative records are not backfilled. */
@Entity(tableName = "dailyReadingRecords")
data class DailyReadingRecord(
    @PrimaryKey
    @SerializedName("epochDay")
    val epochDay: Long,
    @ColumnInfo(defaultValue = "0")
    @SerializedName("readTime")
    val readTime: Long = 0L,
)
