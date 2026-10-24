package io.legado.app.help.bgm

/** Presentation renders names/reasons only. Transient retry keys are not displayed or logged. */
data class LocalMusicImportResult(
    val imported: Int,
    val duplicates: Int,
    val failures: List<LocalMusicImportFailure>,
    val skipped: Int = 0,
    val stopped: Boolean = false,
    val duplicateKeys: Set<String> = emptySet(),
) {
    val failed: Int get() = failures.size
}

data class LocalMusicImportFailure(val name: String, val reason: String, val sourceKey: String = "")

internal data class LocalMusicImportProgress(val processed: Int, val total: Int, val name: String)
