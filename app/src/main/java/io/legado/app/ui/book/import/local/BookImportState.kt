package io.legado.app.ui.book.import.local

import io.legado.app.model.localBook.BookImportPhase
import java.util.zip.ZipException

internal data class BookImportItem(
    val key: String,
    val name: String,
    val phase: BookImportPhase = BookImportPhase.WAITING,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: ImportFailureInfo? = null,
) {
    fun elapsed(now: Long): Long = startedAt?.let { ((finishedAt ?: now) - it).coerceAtLeast(0) } ?: 0
    val canRetry get() = phase == BookImportPhase.FAILED || phase == BookImportPhase.STOPPED
}

internal data class BookImportBatch(
    val title: String,
    val items: List<BookImportItem>,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val previousElapsed: Long = 0,
    val stopRequested: Boolean = false,
) {
    val running get() = finishedAt == null
    val successes get() = items.count { it.phase == BookImportPhase.SUCCESS }
    val failures get() = items.count { it.phase == BookImportPhase.FAILED }
    val stopped get() = items.count { it.phase == BookImportPhase.STOPPED }
    fun elapsed(now: Long) = previousElapsed + ((finishedAt ?: now) - startedAt).coerceAtLeast(0)

    fun progress(key: String, phase: BookImportPhase, now: Long, bytes: Long = 0, total: Long = 0,
                 error: ImportFailureInfo? = null): BookImportBatch = copy(items = items.map {
        if (it.key != key || it.phase.isFinished) it else it.copy(
            phase = phase, startedAt = it.startedAt ?: now,
            finishedAt = now.takeIf { phase.isFinished }, bytes = bytes, total = total, error = error,
        )
    })

    fun finish(now: Long, stopped: Boolean, error: ImportFailureInfo): BookImportBatch = copy(
        finishedAt = now,
        items = items.map {
            if (it.phase.isFinished) it else it.copy(
                phase = if (stopped) BookImportPhase.STOPPED else BookImportPhase.FAILED,
                finishedAt = now,
                error = error.takeUnless { stopped },
            )
        },
    )

    fun retry(now: Long): BookImportBatch = copy(
        items = items.map { if (it.canRetry) BookImportItem(it.key, it.name) else it },
        startedAt = now, finishedAt = null, previousElapsed = elapsed(now), stopRequested = false,
    )
}

internal data class ImportFailureInfo(val summary: String, val detail: String) {
    companion object {
        fun from(error: Exception): ImportFailureInfo {
            val messages = generateSequence<Throwable>(error) { it.cause?.takeUnless { cause -> cause === it } }
                .take(8).map { it.localizedMessage ?: it.javaClass.simpleName }.distinct().toList()
            val detail = messages.joinToString("\n")
            val summary = when {
                error is SecurityException -> "没有文件读写权限"
                error is ZipException -> "文件损坏或不完整"
                detail.contains("OPF", ignoreCase = true) -> "EPUB 缺少书籍信息"
                detail.contains("empty", ignoreCase = true) -> "文件内容为空"
                else -> messages.first().lineSequence().first().take(100)
            }
            return ImportFailureInfo(summary, detail)
        }
    }
}
