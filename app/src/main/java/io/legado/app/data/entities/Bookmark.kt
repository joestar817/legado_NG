package io.legado.app.data.entities

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "bookmarks",
    indices = [(Index(value = ["bookName", "bookAuthor"], unique = false))]
)
data class Bookmark(
    @PrimaryKey
    val time: Long = System.currentTimeMillis(),
    val bookName: String = "",
    val bookAuthor: String = "",
    var chapterIndex: Int = 0,
    var chapterPos: Int = 0,
    var chapterName: String = "",
    var bookText: String = "",
    var content: String = "",
    @SerializedName("bookmarkType")
    @ColumnInfo(defaultValue = "0")
    var bookmarkType: Int = TYPE_POSITION,
    @SerializedName("endChapterIndex")
    @ColumnInfo(defaultValue = "0")
    var endChapterIndex: Int = 0,
    @SerializedName("endChapterPos")
    @ColumnInfo(defaultValue = "0")
    var endChapterPos: Int = 0,
    @SerializedName("highlightStyle")
    @ColumnInfo(defaultValue = "0")
    var highlightStyle: Int = STYLE_BACKGROUND,
    @SerializedName("highlightColor")
    @ColumnInfo(defaultValue = "-32885")
    var highlightColor: Int = DEFAULT_HIGHLIGHT_COLOR,
) : Parcelable {

    val isTextHighlight: Boolean
        get() = (bookmarkType == TYPE_TEXT_HIGHLIGHT || bookmarkType == TYPE_TEXT_HIGHLIGHT_CANONICAL) &&
            (endChapterIndex > chapterIndex || endChapterPos > chapterPos)

    fun coversChapter(targetChapterIndex: Int): Boolean {
        return isTextHighlight && targetChapterIndex in chapterIndex..endChapterIndex
    }

    fun containsChapterPosition(targetChapterIndex: Int, position: Int): Boolean {
        if (!coversChapter(targetChapterIndex)) return false
        val start = if (targetChapterIndex == chapterIndex) chapterPos else 0
        val end = if (targetChapterIndex == endChapterIndex) endChapterPos else Int.MAX_VALUE
        return position >= start && position < end
    }

    companion object {
        const val TYPE_POSITION = 0
        const val TYPE_TEXT_HIGHLIGHT = 1
        // UTF-16 offsets in the chapter's text before visual indentation/title segmentation.
        const val TYPE_TEXT_HIGHLIGHT_CANONICAL = 2

        const val STYLE_BACKGROUND = 0
        const val STYLE_UNDERLINE = 1
        const val STYLE_WAVY_UNDERLINE = 2

        const val DEFAULT_HIGHLIGHT_COLOR: Int = -32885
        val HIGHLIGHT_COLORS = intArrayOf(
            DEFAULT_HIGHLIGHT_COLOR,
            0xFFA78BFA.toInt(),
            0xFF62A5EF.toInt(),
            0xFF65CF81.toInt(),
            0xFFFFC474.toInt(),
        )
    }
}
