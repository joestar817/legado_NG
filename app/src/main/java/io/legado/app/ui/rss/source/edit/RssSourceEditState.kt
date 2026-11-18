package io.legado.app.ui.rss.source.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.google.gson.annotations.SerializedName
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.source.edit.SourceEditorWindow

internal class RssSourceEditState {
    var source by mutableStateOf(RssSource(), referentialEqualityPolicy())
    var savedForm = RssSource()
    var draftBase = RssSource()
    var selectedTab by mutableIntStateOf(0)
    var focusedField by mutableStateOf<RssEditorSelection?>(null)
    var sourceRevision by mutableIntStateOf(0)
    var fieldValueRevision by mutableIntStateOf(0)
    var revision = 0L
    var initialized = false
    var pendingExit by mutableStateOf(false)
    val history = RssSourceEditorHistory()
    val draftTextValues = mutableStateMapOf<String, String>()
}

internal data class RssSourceEditorDraft(
    @SerializedName("window") val window: SourceEditorWindow,
    @SerializedName("original") val original: RssSource?,
    @SerializedName("savedForm") val savedForm: RssSource?,
    @SerializedName("base") val base: RssSource?,
    @SerializedName("source") val source: RssSource?,
    @SerializedName("fields") val fields: Map<String, String>,
)
