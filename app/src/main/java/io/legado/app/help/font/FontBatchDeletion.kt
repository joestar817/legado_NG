package io.legado.app.help.font

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class FontDeletionFailure(val entry: FontFileEntry, val reason: String)

internal data class FontBatchDeletionResult(
    val deleted: List<FontFileEntry>,
    val failures: List<FontDeletionFailure>,
)

/** A batch is a sequence of independently checked single-file deletions, never a directory delete. */
internal object FontBatchDeletion {
    suspend fun delete(
        entries: List<FontFileEntry>,
        onDeleted: (FontFileEntry) -> Unit = {},
        deleteFile: suspend (FontFileEntry) -> Unit = { FontDirectoryCatalog.delete(it) },
    ): FontBatchDeletionResult {
        val deleted = mutableListOf<FontFileEntry>()
        val failures = mutableListOf<FontDeletionFailure>()
        for (entry in entries.distinctBy { it.id }) {
            currentCoroutineContext().ensureActive()
            // Finish and report this file if the host disappears; cancellation stops the next file.
            // Keep the caller's dispatcher: only the existing file API dispatches IO.
            withContext(NonCancellable) {
                val failure = try {
                    deleteFile(entry)
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    FontDeletionFailure(entry, error.localizedMessage ?: "未能删除，请重试")
                }
                if (failure == null) {
                    deleted += entry
                    onDeleted(entry)
                } else {
                    failures += failure
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return FontBatchDeletionResult(deleted, failures)
    }
}
