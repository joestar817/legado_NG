package io.legado.app.model

import android.os.SystemClock
import io.legado.app.constant.AppLog
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import splitties.init.appCtx

/** The last explicit listening bookmark, independent of the current playback session. */
internal object ListeningHistoryStore {
    private const val PREF_KEY = "lastListeningHistory"
    private val mutableCurrent = MutableStateFlow(readSaved())
    val current: StateFlow<ListeningHistoryEntry?> = mutableCurrent.asStateFlow()
    private val writePolicy = ListeningHistoryWritePolicy(mutableCurrent.value)

    @Synchronized
    fun record(source: ListeningHistorySource, bookUrl: String, chapterIndex: Int, position: Int) {
        if (bookUrl.isBlank() || chapterIndex < 0 || position < 0) return
        val entry = ListeningHistoryEntry(source, bookUrl, chapterIndex, position)
        if (mutableCurrent.value == entry) return
        mutableCurrent.value = entry
        saveIfNeeded(entry, flush = false)
    }

    @Synchronized
    fun flush() {
        mutableCurrent.value?.let { saveIfNeeded(it, flush = true) }
    }

    private fun saveIfNeeded(entry: ListeningHistoryEntry, flush: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!writePolicy.shouldWrite(entry, now, flush)) return
        appCtx.putPrefString(PREF_KEY, ListeningHistoryCodec.encode(entry))
        writePolicy.didWrite(entry, now)
    }

    private fun readSaved(): ListeningHistoryEntry? {
        return try {
            appCtx.getPrefString(PREF_KEY)?.let(ListeningHistoryCodec::decode)
        } catch (error: Exception) {
            AppLog.put("读取上次听书记录失败", error)
            null
        }
    }
}
