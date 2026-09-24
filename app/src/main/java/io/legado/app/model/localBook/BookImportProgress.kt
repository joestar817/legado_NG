package io.legado.app.model.localBook

enum class BookImportPhase {
    WAITING, EXTRACTING, COPYING, PARSING, SUCCESS, FAILED, STOPPED;

    val isFinished get() = this == SUCCESS || this == FAILED || this == STOPPED
}

/** In-memory import events; no reader or bookshelf state is owned here. */
interface BookImportObserver {
    fun beforeEntry(key: String)
    fun onProgress(key: String, phase: BookImportPhase, bytes: Long = 0, total: Long = 0)
    fun onFailure(key: String, error: Exception)
}
