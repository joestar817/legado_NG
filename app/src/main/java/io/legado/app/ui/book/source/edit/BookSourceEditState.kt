package io.legado.app.ui.book.source.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.source.edit.SourceEditorWindow
import io.legado.app.ui.widget.text.EditEntity
import java.util.ArrayDeque

internal class BookSourceEditState {
    val tabs = List(6) { ArrayList<EditEntity>() }
    var draftBase = BookSource()
    var savedForm = BookSource()
    var importedWeight: Int? = null
    var originalTypeIndex = 0
    var controls by mutableStateOf(BookSourceEditControls())
    var selectedTab by mutableIntStateOf(0)
    var sourceRevision by mutableIntStateOf(0)
    var fieldValueRevision by mutableIntStateOf(0)
    var revision = 0L
    var initialized = false
    var autoComplete by mutableStateOf(false)
    var focusedField by mutableStateOf<BookSourceEditorSelection?>(null)
    var pendingExit by mutableStateOf(false)
    val undo = mutableMapOf<BookSourceFieldId, ArrayDeque<BookSourceFieldSnapshot>>()
    val redo = mutableMapOf<BookSourceFieldId, ArrayDeque<BookSourceFieldSnapshot>>()
}

internal data class BookSourceFieldSnapshot(val text: String, val start: Int, val end: Int)

internal data class BookSourceEditorDraft(
    @SerializedName("window") val window: SourceEditorWindow,
    @SerializedName("original") val original: BookSource?,
    @SerializedName("savedForm") val savedForm: BookSource?,
    @SerializedName("base") val base: BookSource?,
    @SerializedName("fields") val fields: List<Map<String, String?>>,
    @SerializedName("typeIndex") val typeIndex: Int,
    @SerializedName("controls") val controls: BookSourceDraftControls,
    @SerializedName("importedWeight") val importedWeight: Int?,
)

internal data class BookSourceDraftControls(
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("enabledExplore") val enabledExplore: Boolean,
    @SerializedName("cookieJar") val cookieJar: Boolean,
    @SerializedName("eventListener") val eventListener: Boolean,
    @SerializedName("customButton") val customButton: Boolean,
)
